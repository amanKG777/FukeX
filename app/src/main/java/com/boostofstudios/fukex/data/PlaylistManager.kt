package com.boostofstudios.fukex.data
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaylistManager {
	private const val FILE_NAME = "playlists.json"

	private fun getFile(context: Context): File {
		return File(context.filesDir, FILE_NAME)
	}

	fun savePlaylists(context: Context, playlists: List<Playlist>) {
		val jsonArray = JSONArray()
		playlists.forEach { playlist ->
			val jsonObject = JSONObject()
			jsonObject.put("id", playlist.id)
			jsonObject.put("name", playlist.name)
			jsonObject.put("lastIndex", playlist.lastIndex)
			jsonObject.put("lastPosition", playlist.lastPosition.toDouble())
			jsonObject.put("isHidden", playlist.isHidden)
			jsonObject.put("pin", playlist.pin ?: "")
			jsonObject.put("authType", playlist.authType.name)
			val urisJson = JSONArray()
			playlist.uris.forEach { urisJson.put(it.toString()) }
			jsonObject.put("uris", urisJson)
			jsonArray.put(jsonObject)
		}
		try {
			getFile(context).writeText(jsonArray.toString())
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	fun loadPlaylists(context: Context): List<Playlist> {
		val file = getFile(context)
		if (!file.exists()) {
			// Migration from SharedPreferences if file doesn't exist yet
			val prefs = context.getSharedPreferences("fukex_playlists", Context.MODE_PRIVATE)
			val oldJson = prefs.getString("playlists", null)
			if (oldJson != null) {
				try {
					file.writeText(oldJson)
					prefs.edit().remove("playlists").apply()
				} catch (e: Exception) {
					e.printStackTrace()
				}
			} else {
				return emptyList()
			}
		}
		val jsonString = try {
			file.readText()
		} catch (e: Exception) {
			e.printStackTrace()
			return emptyList()
		}
		val playlists = mutableListOf<Playlist>()
		try {
			val jsonArray = JSONArray(jsonString)
			for (i in 0 until jsonArray.length()) {
				val jsonObject = jsonArray.getJSONObject(i)
				val id = jsonObject.getString("id")
				val name = jsonObject.getString("name")
				val lastIndex = jsonObject.optInt("lastIndex", 0)
				val lastPosition = jsonObject.optDouble("lastPosition", 0.0).toFloat()
				val isHidden = jsonObject.optBoolean("isHidden", false)
				val pin = jsonObject.optString("pin", "").takeIf { it.isNotEmpty() }
				val authTypeStr = jsonObject.optString("authType", AuthType.PIN.name)
				val authType = try { AuthType.valueOf(authTypeStr) } catch (e: Exception) { AuthType.PIN }
				val urisJson = jsonObject.getJSONArray("uris")
				val uris = mutableListOf<Uri>()
				for (j in 0 until urisJson.length()) {
					uris.add(Uri.parse(urisJson.getString(j)))
				}
				playlists.add(Playlist(id, name, uris, lastIndex, lastPosition, isHidden, pin, authType))
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return playlists
	}

	fun updatePlaylistProgress(context: Context, playlistId: String, index: Int, position: Float) {
		val playlists = loadPlaylists(context).toMutableList()
		val idx = playlists.indexOfFirst { it.id == playlistId }
		if (idx != -1) {
			val p = playlists[idx]
			if (p.lastIndex != index || Math.abs(p.lastPosition - position) > 0.01f) {
				playlists[idx] = p.copy(lastIndex = index, lastPosition = position)
				savePlaylists(context, playlists)
			}
		}
	}
}
