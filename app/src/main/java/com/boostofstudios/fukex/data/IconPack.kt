package com.boostofstudios.fukex.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class IconPackException(message: String) : Exception(message)

data class PackLayer(val pathData: String, val filled: Boolean, val alpha: Float)

data class PackIcon(val layers: List<PackLayer>, val mirror: Boolean)

data class IconPackData(
	val id: String,
	val name: String,
	val version: Int,
	val author: String,
	val strokeWidth: Float,
	val icons: Map<String, PackIcon>
)

// One downloadable pack, as listed in the catalog (iconpacks/index.json on GitHub)
data class CatalogEntry(
	val id: String,
	val name: String,
	val description: String,
	val version: Int,
	val file: String,
	val sha256: String,
	val sizeBytes: Long
)

// A .fxicons file is JSON: a name, a stroke width, and per icon a few vector path layers on a 24x24 grid.
// Everything is validated before it is used, because the file comes from the internet.
object IconPackFormat {
	const val EXTENSION = ".fxicons"
	const val MAX_BYTES = 512 * 1024
	private const val MAX_ICONS = 300
	private const val MAX_LAYERS = 8
	private const val MAX_PATH_LENGTH = 6000
	private val idPattern = Regex("[a-z0-9][a-z0-9-]{0,39}")
	private val iconIdPattern = Regex("[a-z0-9_]{1,40}")
	private val pathPattern = Regex("[MmLlHhVvCcSsQqTtAaZz0-9eE.,+\\-\\s]+")
	private val filePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,60}\\.fxicons")
	private val shaPattern = Regex("[0-9a-f]{64}")

	fun parse(text: String): IconPackData {
		if (text.length > MAX_BYTES) throw IconPackException("Icon pack is too large")
		try {
			val root = JSONObject(text)
			if (root.optInt("format") != 1) throw IconPackException("Unsupported icon pack format")
			val id = root.getString("id")
			if (!idPattern.matches(id)) throw IconPackException("Invalid icon pack id")
			val viewport = root.optDouble("viewport", 24.0)
			if (viewport != 24.0) throw IconPackException("Only 24x24 icon packs are supported")
			val strokeWidth = root.optDouble("strokeWidth", 1.75).toFloat()
			if (strokeWidth !in 0.5f..4f) throw IconPackException("Invalid stroke width")
			val iconsJson = root.getJSONObject("icons")
			if (iconsJson.length() == 0 || iconsJson.length() > MAX_ICONS) throw IconPackException("Invalid number of icons")

			val icons = mutableMapOf<String, PackIcon>()
			for (name in iconsJson.keys()) {
				if (!iconIdPattern.matches(name)) throw IconPackException("Invalid icon name: $name")
				icons[name] = parseIcon(name, iconsJson.getJSONObject(name))
			}
			return IconPackData(
				id = id,
				name = root.optString("name", id).take(60),
				version = root.optInt("version", 1),
				author = root.optString("author", "").take(60),
				strokeWidth = strokeWidth,
				icons = icons
			)
		} catch (e: JSONException) {
			throw IconPackException("The icon pack file is damaged")
		}
	}

	private fun parseIcon(name: String, json: JSONObject): PackIcon {
		val layersJson: JSONArray = json.getJSONArray("layers")
		if (layersJson.length() == 0 || layersJson.length() > MAX_LAYERS) throw IconPackException("Invalid layers in $name")
		val layers = (0 until layersJson.length()).map { i ->
			val layer = layersJson.getJSONObject(i)
			val d = layer.getString("d")
			if (d.length > MAX_PATH_LENGTH || !pathPattern.matches(d)) throw IconPackException("Invalid path in $name")
			val alpha = layer.optDouble("alpha", 1.0).toFloat()
			if (alpha !in 0f..1f) throw IconPackException("Invalid alpha in $name")
			val style = layer.optString("style", "stroke")
			if (style != "fill" && style != "stroke") throw IconPackException("Invalid style in $name")
			PackLayer(d, style == "fill", alpha)
		}
		return PackIcon(layers, json.optBoolean("mirror", false))
	}

	fun parseCatalog(text: String): List<CatalogEntry> {
		try {
			val packs = JSONObject(text).getJSONArray("packs")
			return (0 until packs.length()).mapNotNull { i ->
				val p = packs.getJSONObject(i)
				val entry = CatalogEntry(
					id = p.getString("id"),
					name = p.optString("name", p.getString("id")).take(60),
					description = p.optString("description", "").take(300),
					version = p.getInt("version"),
					file = p.getString("file"),
					sha256 = p.getString("sha256").lowercase(),
					sizeBytes = p.optLong("sizeBytes", 0L)
				)
				// A bad entry is skipped rather than trusted: the file name is joined onto our own base URL
				entry.takeIf {
					idPattern.matches(it.id) && filePattern.matches(it.file) && shaPattern.matches(it.sha256) &&
						it.sizeBytes in 1..MAX_BYTES
				}
			}
		} catch (e: JSONException) {
			throw IconPackException("The icon pack list is damaged")
		}
	}

	fun sha256Hex(bytes: ByteArray): String =
		java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
