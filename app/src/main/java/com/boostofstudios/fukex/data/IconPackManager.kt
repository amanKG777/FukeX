package com.boostofstudios.fukex.data

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "IconPackManager"

// An icon pack that passed validation, with every icon already built into a tintable vector.
class LoadedPack(val data: IconPackData, private val vectors: Map<String, ImageVector>) {
	fun icon(id: String): ImageVector? = vectors[id]
}

// Downloads, stores and serves the optional visual icon pack. Nothing is bundled in the app:
// the pack comes from the iconpacks folder of the FukeX GitHub repository, and every download
// is checked against the SHA-256 listed in the catalog before it is used.
object IconPackManager {
	const val BASE_URL = "https://raw.githubusercontent.com/amanKG777/FukeX/master/iconpacks/"
	private const val DIR = "iconpacks"
	private const val ACTIVE_FILE = "active" + IconPackFormat.EXTENSION

	private val client = OkHttpClient.Builder()
		.callTimeout(30, TimeUnit.SECONDS)
		// Packs only come from our own HTTPS URL, so never follow a redirect elsewhere
		.followRedirects(false)
		.followSslRedirects(false)
		.build()

	private var installedPack: LoadedPack? = null
	private var enabled = true

	private val _installed = MutableStateFlow<LoadedPack?>(null)
	// The pack that is downloaded, whether or not it is switched on
	val installed: StateFlow<LoadedPack?> = _installed.asStateFlow()

	private val _active = MutableStateFlow<LoadedPack?>(null)
	// The pack the UI should draw with; null means the built-in icons
	val active: StateFlow<LoadedPack?> = _active.asStateFlow()

	private fun file(context: Context) = File(File(context.filesDir, DIR), ACTIVE_FILE)

	private fun publish() {
		_installed.value = installedPack
		_active.value = installedPack.takeIf { enabled }
	}

	// Blocking; call off the main thread. A damaged or missing file just means no pack.
	fun load(context: Context) {
		enabled = SettingsManager.isIconPackEnabled(context)
		installedPack = try {
			file(context).takeIf { it.exists() }?.let { build(IconPackFormat.parse(it.readText())) }
		} catch (e: Exception) {
			Log.w(TAG, "Ignoring unreadable icon pack", e)
			file(context).delete()
			null
		}
		publish()
	}

	fun setEnabled(context: Context, value: Boolean) {
		SettingsManager.setIconPackEnabled(context, value)
		enabled = value
		publish()
	}

	fun remove(context: Context) {
		file(context).delete()
		installedPack = null
		publish()
	}

	suspend fun fetchCatalog(): List<CatalogEntry> = withContext(Dispatchers.IO) {
		IconPackFormat.parseCatalog(download("index.json", 64 * 1024).toString(Charsets.UTF_8))
	}

	suspend fun install(context: Context, entry: CatalogEntry) = withContext(Dispatchers.IO) {
		val bytes = download(entry.file, IconPackFormat.MAX_BYTES)
		if (IconPackFormat.sha256Hex(bytes) != entry.sha256) {
			throw IconPackException("The download did not match its checksum, so it was discarded")
		}
		val pack = build(IconPackFormat.parse(bytes.toString(Charsets.UTF_8)))
		val target = file(context)
		target.parentFile?.mkdirs()
		val temp = File(target.parentFile, "$ACTIVE_FILE.tmp")
		temp.writeBytes(bytes)
		if (!temp.renameTo(target)) {
			temp.delete()
			throw IconPackException("Could not save the icon pack")
		}
		SettingsManager.setIconPackEnabled(context, true)
		enabled = true
		installedPack = pack
		publish()
	}

	private fun download(name: String, maxBytes: Int): ByteArray {
		try {
			client.newCall(Request.Builder().url(BASE_URL + name).build()).execute().use { response ->
				if (!response.isSuccessful) throw IconPackException("The server answered ${response.code}")
				val body = response.body ?: throw IconPackException("The server sent nothing")
				val bytes = body.source().let { source ->
					source.request(maxBytes + 1L)
					source.buffer.readByteArray(minOf(source.buffer.size, maxBytes + 1L))
				}
				if (bytes.size > maxBytes) throw IconPackException("The download is larger than expected")
				return bytes
			}
		} catch (e: IOException) {
			throw IconPackException("Could not reach GitHub. Check your internet connection.")
		}
	}

	private fun build(data: IconPackData): LoadedPack {
		val parser = PathParser()
		val vectors = try {
			data.icons.mapValues { (name, icon) ->
				ImageVector.Builder(
					name = name,
					defaultWidth = 24.dp, defaultHeight = 24.dp,
					viewportWidth = 24f, viewportHeight = 24f,
					autoMirror = icon.mirror
				).apply {
					for (layer in icon.layers) {
						val nodes = parser.parsePathString(layer.pathData).toNodes()
						// Colour is irrelevant here: Icon() tints the vector with the current content colour
						val paint = SolidColor(Color.Black)
						addPath(
							pathData = nodes,
							pathFillType = PathFillType.EvenOdd,
							fill = if (layer.filled) paint else null,
							fillAlpha = layer.alpha,
							stroke = if (layer.filled) null else paint,
							strokeAlpha = layer.alpha,
							strokeLineWidth = data.strokeWidth,
							strokeLineCap = StrokeCap.Round,
							strokeLineJoin = StrokeJoin.Round
						)
					}
				}.build()
			}
		} catch (e: IllegalArgumentException) {
			throw IconPackException("The icon pack has a drawing that can't be read")
		}
		return LoadedPack(data, vectors)
	}
}
