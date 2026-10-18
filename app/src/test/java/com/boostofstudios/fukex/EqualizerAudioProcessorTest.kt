package com.boostofstudios.fukex

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.boostofstudios.fukex.data.EqConfig
import com.boostofstudios.fukex.playback.EqualizerAudioProcessor
import com.boostofstudios.fukex.playback.LiveEqualizer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class EqualizerAudioProcessorTest {
	private val sampleRate = 44100

	@After
	fun resetEqualizer() = LiveEqualizer.apply(EqConfig())

	private fun sine(frequency: Double, samples: Int, amplitude: Double = 0.25): ShortArray =
		ShortArray(samples) { (sin(2 * PI * frequency * it / sampleRate) * amplitude * 32767).toInt().toShort() }

	private fun process(input: ShortArray): ShortArray {
		val processor = EqualizerAudioProcessor()
		processor.configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
		processor.flush()
		val buffer = ByteBuffer.allocateDirect(input.size * 2).order(ByteOrder.nativeOrder())
		input.forEach { buffer.putShort(it) }
		buffer.flip()
		processor.queueInput(buffer)
		val output = processor.output
		return ShortArray(output.remaining() / 2) { output.getShort() }
	}

	private fun peak(samples: ShortArray) = samples.drop(samples.size / 2).maxOf { abs(it.toInt()) }

	@Test
	fun flatEqualizerPassesAudioThroughUnchanged() {
		LiveEqualizer.apply(EqConfig(enabled = true))
		val input = sine(1000.0, 4410)
		assertArrayEquals(input, process(input))
	}

	@Test
	fun disabledEqualizerIgnoresGains() {
		LiveEqualizer.apply(EqConfig(enabled = false, gainsDb = List(10) { 12f }))
		val input = sine(1000.0, 4410)
		assertArrayEquals(input, process(input))
	}

	@Test
	fun boostingABandRaisesThatFrequency() {
		val gains = MutableList(10) { 0f }.apply { this[5] = 6f } // 1 kHz
		LiveEqualizer.apply(EqConfig(enabled = true, gainsDb = gains))
		val input = sine(1000.0, 8820)
		val ratio = peak(process(input)).toDouble() / peak(input)
		// +6 dB is about twice the amplitude
		assertTrue("ratio was $ratio", ratio in 1.8..2.2)
	}

	@Test
	fun boostingABandLeavesDistantFrequenciesAlone() {
		val gains = MutableList(10) { 0f }.apply { this[0] = 12f } // 31 Hz
		LiveEqualizer.apply(EqConfig(enabled = true, gainsDb = gains))
		val input = sine(8000.0, 8820)
		val ratio = peak(process(input)).toDouble() / peak(input)
		assertTrue("ratio was $ratio", ratio in 0.95..1.05)
	}
}
