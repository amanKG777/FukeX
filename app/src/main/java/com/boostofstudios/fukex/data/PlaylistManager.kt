package com.boostofstudios.fukex.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object PlaylistManager {
    private const val PREFS_NAME = "fukex_playlists"
    private const val KEY_PLAYLISTS = "playlists"

    fun savePlaylists(context: Context, playlists: List<Playlist>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        playlists.forEach { playlist ->
            val jsonObject = JSONObject()
            jsonObject.put("id", playlist.id)
            jsonObject.put("name", playlist.name)
            jsonObject.put("lastIndex", playlist.lastIndex)
            jsonObject.put("lastPosition", playlist.lastPosition.toDouble())
            jsonObject.put("isHidden", playlist.isHidden)
            jsonObject.put("pin", playlist.pin ?: "")
            
            val urisJson = JSONArray()
            playlist.uris.forEach { urisJson.put(it.toString()) }
            jsonObject.put("uris", urisJson)
            
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
    }

    fun loadPlaylists(context: Context): List<Playlist> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
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
                
                val urisJson = jsonObject.getJSONArray("uris")
                val uris = mutableListOf<Uri>()
                for (j in 0 until urisJson.length()) {
                    uris.add(Uri.parse(urisJson.getString(j)))
                }
                playlists.add(Playlist(id, name, uris, lastIndex, lastPosition, isHidden, pin))
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
            // Only update if changed to avoid unnecessary writes
            if (p.lastIndex != index || Math.abs(p.lastPosition - position) > 0.01f) {
                playlists[idx] = p.copy(lastIndex = index, lastPosition = position)
                savePlaylists(context, playlists)
            }
        }
    }
}
