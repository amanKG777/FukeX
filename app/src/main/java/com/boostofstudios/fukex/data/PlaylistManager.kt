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
	private const val DIR_NAME = "playlists"
	private const val INDEX_FILE = "playlists_index.json"
	private const val OLD_FILE_NAME = "playlists.json"

	private fun getPlaylistsDir(context: Context): File {
		val dir = File(context.filesDir, DIR_NAME)
		if (!dir.exists()) dir.mkdirs()
		return dir
	}

	private fun getIndexFile(context: Context): File {
		return File(getPlaylistsDir(context), INDEX_FILE)
	}

	private fun getPlaylistFile(context: Context, id: String): File {
		return File(getPlaylistsDir(context), "playlist_$id.json")
	}

	private fun migrateIfNeeded(context: Context) {
		val oldFile = File(context.filesDir, OLD_FILE_NAME)
		val oldPrefs = context.getSharedPreferences("fukex_playlists", Context.MODE_PRIVATE)
		val oldJsonFromPrefs = oldPrefs.getString("playlists", null)
		
		val jsonString = if (oldFile.exists()) {
			oldFile.readText()
		} else if (oldJsonFromPrefs != null) {
			oldJsonFromPrefs
		} else {
			return // Nothing to migrate
		}

		try {
			val jsonArray = JSONArray(jsonString)
			val playlists = mutableListOf<Playlist>()
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
			savePlaylists(context, playlists)
			if (oldFile.exists()) oldFile.delete()
			if (oldJsonFromPrefs != null) oldPrefs.edit().remove("playlists").apply()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun playlistToJson(playlist: Playlist): JSONObject {
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
		return jsonObject
	}

	private fun jsonToPlaylist(jsonObject: JSONObject): Playlist {
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
		return Playlist(id, name, uris, lastIndex, lastPosition, isHidden, pin, authType)
	}

	fun savePlaylists(context: Context, playlists: List<Playlist>) {
		val indexArray = JSONArray()
		playlists.forEach { playlist ->
			indexArray.put(playlist.id)
			try {
				getPlaylistFile(context, playlist.id).writeText(playlistToJson(playlist).toString())
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		
		// Delete files for playlists that were removed
		val validIds = playlists.map { it.id }.toSet()
		getPlaylistsDir(context).listFiles()?.forEach { file ->
			if (file.name.startsWith("playlist_") && file.name.endsWith(".json")) {
				val id = file.name.removePrefix("playlist_").removeSuffix(".json")
				if (!validIds.contains(id)) {
					file.delete()
				}
			}
		}

		try {
			getIndexFile(context).writeText(indexArray.toString())
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	fun loadPlaylists(context: Context): List<Playlist> {
		val indexFile = getIndexFile(context)
		if (!indexFile.exists()) {
			migrateIfNeeded(context)
			if (!indexFile.exists()) return emptyList()
		}

		val indexString = try { indexFile.readText() } catch (e: Exception) { return emptyList() }
		val playlists = mutableListOf<Playlist>()
		try {
			val indexArray = JSONArray(indexString)
			for (i in 0 until indexArray.length()) {
				val id = indexArray.getString(i)
				val file = getPlaylistFile(context, id)
				if (file.exists()) {
					try {
						val jsonObject = JSONObject(file.readText())
						playlists.add(jsonToPlaylist(jsonObject))
					} catch (e: Exception) {
						e.printStackTrace()
					}
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "Could not parse playlists", e)
		}
		if (needsRewrite) {
			writeLocked(context, toJson(playlists))
		}
		return playlists
	}

	fun updatePlaylistProgress(context: Context, playlistId: String, index: Int, position: Float) {
		val file = getPlaylistFile(context, playlistId)
		if (!file.exists()) return
		try {
			val jsonObject = JSONObject(file.readText())
			val p = jsonToPlaylist(jsonObject)
			if (p.lastIndex != index || Math.abs(p.lastPosition - position) > 0.01f) {
				val updated = p.copy(lastIndex = index, lastPosition = position)
				file.writeText(playlistToJson(updated).toString())
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
}
