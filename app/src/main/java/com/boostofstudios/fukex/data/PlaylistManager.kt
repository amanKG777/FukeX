package com.boostofstudios.fukex.data
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaylistManager {
	private const val TAG = "PlaylistManager"
	private const val FILE_NAME = "playlists.json"
	private val fileLock = Mutex()

	private fun getFile(context: Context): File {
		return File(context.filesDir, FILE_NAME)
	}

	private fun toJson(playlists: List<Playlist>): String {
		val jsonArray = JSONArray()
		playlists.forEach { playlist ->
			val jsonObject = JSONObject()
			jsonObject.put("id", playlist.id)
			jsonObject.put("name", playlist.name)
			jsonObject.put("lastIndex", playlist.lastIndex)
			jsonObject.put("lastPosition", playlist.lastPosition.toDouble())
			jsonObject.put("isHidden", playlist.isHidden)
			jsonObject.put("pinHash", playlist.pinHash ?: "")
			jsonObject.put("pinSalt", playlist.pinSalt ?: "")
			jsonObject.put("authType", playlist.authType.name)
			val urisJson = JSONArray()
			playlist.uris.forEach { urisJson.put(it.toString()) }
			jsonObject.put("uris", urisJson)
			jsonArray.put(jsonObject)
		}
		return jsonArray.toString()
	}

	suspend fun savePlaylists(context: Context, playlists: List<Playlist>) {
		fileLock.withLock { writeLocked(context, toJson(playlists)) }
	}

	private fun writeLocked(context: Context, json: String) {
		val target = getFile(context)
		val temp = File(target.parentFile, "$FILE_NAME.tmp")
		try {
			temp.writeText(json)
			if (!temp.renameTo(target)) {
				target.writeText(json)
				temp.delete()
			}
		} catch (e: Exception) {
			Log.e(TAG, "Could not save playlists", e)
			temp.delete()
		}
	}

	fun loadPlaylists(context: Context): List<Playlist> {
		val file = getFile(context)
		if (!file.exists()) {
			val prefs = context.getSharedPreferences("fukex_playlists", Context.MODE_PRIVATE)
			val oldJson = prefs.getString("playlists", null)
			if (oldJson != null) {
				try {
					file.writeText(oldJson)
					prefs.edit().remove("playlists").apply()
				} catch (e: Exception) {
					Log.e(TAG, "Could not migrate playlists from preferences", e)
				}
			} else {
				return emptyList()
			}
		}
		val jsonString = try {
			file.readText()
		} catch (e: Exception) {
			Log.e(TAG, "Could not read playlists", e)
			return emptyList()
		}
		val playlists = mutableListOf<Playlist>()
		var needsRewrite = false
		try {
			val jsonArray = JSONArray(jsonString)
			for (i in 0 until jsonArray.length()) {
				val jsonObject = jsonArray.getJSONObject(i)
				val id = jsonObject.getString("id")
				val name = jsonObject.getString("name")
				val lastIndex = jsonObject.optInt("lastIndex", 0)
				val lastPosition = jsonObject.optDouble("lastPosition", 0.0).toFloat()
				val isHidden = jsonObject.optBoolean("isHidden", false)
				val authTypeStr = jsonObject.optString("authType", AuthType.PIN.name)
				val authType = try { AuthType.valueOf(authTypeStr) } catch (e: Exception) { AuthType.PIN }
				var pinHash = jsonObject.optString("pinHash", "").takeIf { it.isNotEmpty() }
				var pinSalt = jsonObject.optString("pinSalt", "").takeIf { it.isNotEmpty() }
				val legacyPin = jsonObject.optString("pin", "").takeIf { it.isNotEmpty() }
				if (pinHash == null && legacyPin != null) {
					pinSalt = PlaylistSecurity.newSalt()
					pinHash = PlaylistSecurity.hash(legacyPin, pinSalt)
					needsRewrite = true
				}
				val urisJson = jsonObject.getJSONArray("uris")
				val uris = mutableListOf<Uri>()
				for (j in 0 until urisJson.length()) {
					val parsed = Uri.parse(urisJson.getString(j))
					val cleaned = SmbCredentialStore.migrateLegacyUri(context, parsed)
					if (cleaned != parsed) needsRewrite = true
					uris.add(cleaned)
				}
				playlists.add(Playlist(id, name, uris, lastIndex, lastPosition, isHidden, pinHash, pinSalt, authType))
			}
		} catch (e: Exception) {
			Log.e(TAG, "Could not parse playlists", e)
		}
		if (needsRewrite) {
			writeLocked(context, toJson(playlists))
		}
		return playlists
	}

	suspend fun updatePlaylistProgress(context: Context, playlistId: String, index: Int, position: Float) {
		fileLock.withLock {
			val playlists = loadPlaylists(context).toMutableList()
			val idx = playlists.indexOfFirst { it.id == playlistId }
			if (idx != -1) {
				val p = playlists[idx]
				if (p.lastIndex != index || Math.abs(p.lastPosition - position) > 0.01f) {
					playlists[idx] = p.copy(lastIndex = index, lastPosition = position)
					writeLocked(context, toJson(playlists))
				}
			}
		}
	}
}
