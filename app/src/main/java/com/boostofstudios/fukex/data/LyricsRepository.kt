package com.boostofstudios.fukex.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.boostofstudios.fukex.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

// Looks for lyrics in this order: our cache, a .lrc file next to the song, then lrclib.net.
object LyricsRepository {
	private const val TAG = "LyricsRepository"
	private const val USER_AGENT = "FukeX/${BuildConfig.VERSION_NAME} (https://github.com/amankg777/fukex)"

	private data class SongInfo(val title: String?, val artist: String?, val durationSec: Long?)

	// Blocking; call off the main thread.
	fun load(context: Context, uri: Uri, fallbackTitle: String): Lyrics? {
		val cacheFile = cacheFileFor(context, uri)
		if (cacheFile.exists()) {
			return try { LrcParser.parse(cacheFile.readText()) } catch (e: Exception) { null }
		}
		val sidecar = sidecarFile(context, uri)
		if (sidecar != null) {
			try {
				return LrcParser.parse(sidecar.readText())
			} catch (e: Exception) {
				Log.w(TAG, "Could not read ${sidecar.path}", e)
			}
		}
		if (!SettingsManager.isLyricsOnlineEnabled(context)) return null
		val info = songInfo(context, uri, fallbackTitle)
		val text = fetchFromLrclib(info) ?: return null
		try {
			cacheFile.parentFile?.mkdirs()
			cacheFile.writeText(text)
		} catch (e: Exception) {
			Log.w(TAG, "Could not cache lyrics", e)
		}
		return LrcParser.parse(text)
	}

	private fun cacheFileFor(context: Context, uri: Uri): File {
		val digest = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
		val name = digest.joinToString("") { "%02x".format(it) }
		return File(File(context.filesDir, "lyrics"), "$name.lrc")
	}

	private fun localPath(context: Context, uri: Uri): String? = when (uri.scheme) {
		"file" -> uri.path
		"content" -> try {
			context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
				if (c.moveToFirst()) c.getString(0) else null
			}
		} catch (e: Exception) {
			null
		}
		else -> null
	}

	private fun sidecarFile(context: Context, uri: Uri): File? {
		val path = localPath(context, uri) ?: return null
		val base = path.substringBeforeLast('.')
		return listOf("$base.lrc", "$base.LRC").map { File(it) }.firstOrNull { it.isFile && it.canRead() }
	}

	private fun songInfo(context: Context, uri: Uri, fallbackTitle: String): SongInfo {
		if (uri.scheme == "youtube") return SongInfo(fallbackTitle, null, null)
		if (uri.scheme?.startsWith("smb") == true) return SongInfo(fallbackTitle, null, null)
		if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
			try {
				context.contentResolver.query(
					uri,
					arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION),
					null, null, null
				)?.use { c ->
					if (c.moveToFirst()) {
						return SongInfo(c.getString(0) ?: fallbackTitle, c.getString(1)?.takeUnless { it == MediaStore.UNKNOWN_STRING }, c.getLong(2) / 1000)
					}
				}
			} catch (e: Exception) {}
		}
		val retriever = MediaMetadataRetriever()
		return try {
			retriever.setDataSource(context, uri)
			SongInfo(
				retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fallbackTitle,
				retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
				retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000)
			)
		} catch (e: Exception) {
			SongInfo(fallbackTitle, null, null)
		} finally {
			try { retriever.release() } catch (e: Exception) {}
		}
	}

	private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

	private fun httpGet(url: String): String? {
		val connection = URL(url).openConnection() as HttpURLConnection
		return try {
			connection.connectTimeout = 8000
			connection.readTimeout = 8000
			connection.setRequestProperty("User-Agent", USER_AGENT)
			if (connection.responseCode == 200) connection.inputStream.bufferedReader().use { it.readText() } else null
		} catch (e: Exception) {
			Log.w(TAG, "Lyrics request failed", e)
			null
		} finally {
			connection.disconnect()
		}
	}

	private fun lyricsFrom(json: JSONObject): String? =
		json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
			?: json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

	private fun fetchFromLrclib(info: SongInfo): String? {
		val title = info.title?.takeIf { it.isNotBlank() } ?: return null
		if (info.artist != null) {
			val url = buildString {
				append("https://lrclib.net/api/get?track_name=").append(encode(title))
				append("&artist_name=").append(encode(info.artist))
				if (info.durationSec != null && info.durationSec > 0) append("&duration=").append(info.durationSec)
			}
			httpGet(url)?.let { body ->
				try { lyricsFrom(JSONObject(body))?.let { return it } } catch (e: Exception) {}
			}
		}
		val query = listOfNotNull(info.artist, title).joinToString(" ")
		val body = httpGet("https://lrclib.net/api/search?q=${encode(query)}") ?: return null
		return try {
			val results = JSONArray(body)
			val objects = (0 until results.length()).map { results.getJSONObject(it) }
			// Prefer synced lyrics, since only those can follow the song
			val best = objects.firstOrNull { it.optString("syncedLyrics").let { s -> s.isNotBlank() && s != "null" } }
				?: objects.firstOrNull()
			best?.let { lyricsFrom(it) }
		} catch (e: Exception) {
			null
		}
	}
}
