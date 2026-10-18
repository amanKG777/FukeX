package com.boostofstudios.fukex.playback

import com.boostofstudios.fukex.data.PlayerCacheManager
import com.boostofstudios.fukex.data.SettingsManager

fun createFukexPlayer(context: android.content.Context): androidx.media3.exoplayer.ExoPlayer {
	val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
		.setUsage(androidx.media3.common.C.USAGE_MEDIA)
		.setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
		.build()
	val appContext = context.applicationContext
	val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context)
	val customDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
		val defaultDataSource = defaultDataSourceFactory.createDataSource()

		object : androidx.media3.datasource.DataSource {
			val smbDataSource = com.boostofstudios.fukex.data.SmbDataSource(appContext)
			var activeDataSource: androidx.media3.datasource.DataSource? = null

			override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
				defaultDataSource.addTransferListener(transferListener)
				smbDataSource.addTransferListener(transferListener)
			}

			override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
				activeDataSource = if (dataSpec.uri.scheme?.startsWith("smb") == true) smbDataSource else defaultDataSource
				return activeDataSource!!.open(dataSpec)
			}

			override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
				return activeDataSource!!.read(buffer, offset, length)
			}

			override fun getUri(): android.net.Uri? {
				return activeDataSource?.uri
			}

			override fun close() {
				activeDataSource?.close()
				activeDataSource = null
			}
		}
	}
	val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
		.setCache(PlayerCacheManager.getCache(context))
		.setUpstreamDataSourceFactory(customDataSourceFactory)
		.setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
	// Local files are already on disk, so only remote sources go through the cache.
	val routingDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
		object : androidx.media3.datasource.DataSource {
			var delegate: androidx.media3.datasource.DataSource? = null
			val listeners = mutableListOf<androidx.media3.datasource.TransferListener>()

			override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
				listeners.add(transferListener)
			}

			override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
				val factory = if (dataSpec.uri.scheme?.startsWith("smb") == true) {
					cacheDataSourceFactory
				} else {
					customDataSourceFactory
				}
				val source = factory.createDataSource()
				listeners.forEach { source.addTransferListener(it) }
				delegate = source
				return source.open(dataSpec)
			}

			override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
				delegate!!.read(buffer, offset, length)

			override fun getUri(): android.net.Uri? = delegate?.uri

			override fun close() {
				delegate?.close()
				delegate = null
			}
		}
	}
	val resolvingDataSourceFactory = androidx.media3.datasource.ResolvingDataSource.Factory(routingDataSourceFactory, object : androidx.media3.datasource.ResolvingDataSource.Resolver {
		override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
			val uri = dataSpec.uri
			if (uri.scheme == "youtube") {
				try {
					val videoId = (uri.host?.takeIf { it.isNotBlank() } ?: uri.path?.removePrefix("/") ?: uri.toString().substringAfter("youtube://")).substringBefore("?")
					val url = "https://www.youtube.com/watch?v=$videoId"
					val extractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getStreamExtractor(url)
					extractor.fetchPage()
					val audioStreamUrl = extractor.audioStreams.maxByOrNull { it.bitrate }?.content
						?: extractor.videoStreams.maxByOrNull { it.resolution?.substringBefore("p")?.toIntOrNull() ?: 0 }?.content
						?: extractor.hlsUrl
						?: extractor.dashMpdUrl
					
					if (audioStreamUrl != null) {
						return dataSpec.buildUpon().setUri(android.net.Uri.parse(audioStreamUrl)).build()
					} else {
						throw java.io.IOException("No playable streams found for video.")
					}
				} catch (e: Exception) {
					e.printStackTrace()
					java.io.File(context.filesDir, "newpipe_error.log").writeText(android.util.Log.getStackTraceString(e))
					throw java.io.IOException("Failed to extract youtube video: ${e.message}", e)
				}
			}
			return dataSpec
		}
	})
	val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
		.setDataSourceFactory(resolvingDataSourceFactory)
	// Routes decoded audio through the 10-band EQ before it reaches the AudioTrack
	val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
		override fun buildAudioSink(
			context: android.content.Context,
			enableFloatOutput: Boolean,
			enableAudioTrackPlaybackParams: Boolean
		): androidx.media3.exoplayer.audio.AudioSink =
			androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
				.setEnableFloatOutput(enableFloatOutput)
				.setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
				.setAudioProcessors(arrayOf(EqualizerAudioProcessor()))
				.build()
	}
	return androidx.media3.exoplayer.ExoPlayer.Builder(context, renderersFactory)
		.setMediaSourceFactory(mediaSourceFactory)
		.setAudioAttributes(audioAttributes, false)
		.setHandleAudioBecomingNoisy(true)
		.setSkipSilenceEnabled(SettingsManager.isSkipSilenceEnabled(context))
		.build().apply {
			trackSelectionParameters = trackSelectionParameters.buildUpon()
				.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
				.build()
		}
}
