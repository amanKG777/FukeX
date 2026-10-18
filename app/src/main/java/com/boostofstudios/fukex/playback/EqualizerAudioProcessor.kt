package com.boostofstudios.fukex.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.boostofstudios.fukex.data.EqConfig
import com.boostofstudios.fukex.data.EqualizerPresets
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// The EQ the user is hearing right now. Both crossfade players read it, each with its own filter state.
object LiveEqualizer {
	@Volatile var config: EqConfig = EqConfig()
		private set
	@Volatile var version = 0
		private set

	fun apply(newConfig: EqConfig) {
		config = newConfig
		version++
	}
}

// A 10-band peaking EQ (RBJ biquads), since android.media.audiofx.Equalizer
// only offers whatever few bands the device happens to have.
class EqualizerAudioProcessor : BaseAudioProcessor() {
	private val bandCount = EqualizerPresets.BAND_FREQUENCIES.size
	private var appliedVersion = -1
	private var passthrough = true
	private var preampGain = 1.0
	// Normalised coefficients per band: b0, b1, b2, a1, a2
	private val coefficients = Array(bandCount) { DoubleArray(5) }
	private val bandActive = BooleanArray(bandCount)
	// Filter history per band and channel: x1, x2, y1, y2
	private var state = Array(bandCount) { Array(2) { DoubleArray(4) } }

	override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
		// Throwing here would fail playback; NOT_SET just leaves this processor out for other formats
		if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
			return AudioProcessor.AudioFormat.NOT_SET
		}
		state = Array(bandCount) { Array(inputAudioFormat.channelCount) { DoubleArray(4) } }
		appliedVersion = -1
		return inputAudioFormat
	}

	private fun updateCoefficients() {
		val version = LiveEqualizer.version
		if (version == appliedVersion) return
		appliedVersion = version
		val config = LiveEqualizer.config
		val sampleRate = inputAudioFormat.sampleRate.toDouble()
		passthrough = !config.enabled || (config.preampDb == 0f && config.gainsDb.all { it == 0f })
		preampGain = 10.0.pow(config.preampDb / 20.0)
		val q = 1.41 // about one octave per band
		for (band in 0 until bandCount) {
			val gain = config.gainsDb.getOrElse(band) { 0f }.toDouble()
			val frequency = EqualizerPresets.BAND_FREQUENCIES[band].toDouble()
			bandActive[band] = gain != 0.0 && frequency < sampleRate / 2
			if (!bandActive[band]) continue
			val a = 10.0.pow(gain / 40.0)
			val w0 = 2 * PI * frequency / sampleRate
			val alpha = sin(w0) / (2 * q)
			val cosW0 = cos(w0)
			val a0 = 1 + alpha / a
			coefficients[band][0] = (1 + alpha * a) / a0
			coefficients[band][1] = (-2 * cosW0) / a0
			coefficients[band][2] = (1 - alpha * a) / a0
			coefficients[band][3] = (-2 * cosW0) / a0
			coefficients[band][4] = (1 - alpha / a) / a0
		}
	}

	private fun processSample(channel: Int, input: Double): Double {
		var sample = input * preampGain
		for (band in 0 until bandCount) {
			if (!bandActive[band]) continue
			val c = coefficients[band]
			val s = state[band][channel]
			val output = c[0] * sample + c[1] * s[0] + c[2] * s[1] - c[3] * s[2] - c[4] * s[3]
			s[1] = s[0]; s[0] = sample
			s[3] = s[2]; s[2] = output
			sample = output
		}
		return sample.coerceIn(-1.0, 1.0)
	}

	override fun queueInput(inputBuffer: ByteBuffer) {
		val remaining = inputBuffer.remaining()
		if (remaining == 0) return
		updateCoefficients()
		val output = replaceOutputBuffer(remaining)
		if (passthrough) {
			output.put(inputBuffer)
			output.flip()
			return
		}
		val channels = inputAudioFormat.channelCount
		if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
			while (inputBuffer.remaining() >= 2 * channels) {
				for (ch in 0 until channels) {
					val value = processSample(ch, inputBuffer.getShort() / 32768.0)
					output.putShort((value * 32767).toInt().toShort())
				}
			}
		} else {
			while (inputBuffer.remaining() >= 4 * channels) {
				for (ch in 0 until channels) {
					output.putFloat(processSample(ch, inputBuffer.getFloat().toDouble()).toFloat())
				}
			}
		}
		output.flip()
	}

	override fun onFlush() {
		state.forEach { band -> band.forEach { it.fill(0.0) } }
	}

	override fun onReset() {
		appliedVersion = -1
	}
}
