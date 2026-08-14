package com.boostofstudios.fukex.data
import android.content.Context
import android.net.Uri

object SmbCredentialStore {
	private const val PREFS_NAME = "fukex_smb_credentials"

	data class Credentials(val username: String, val password: String)

	private fun prefs(context: Context) =
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	private fun keyFor(uri: Uri): String? = uri.host?.lowercase()

	fun save(context: Context, uri: Uri, username: String, password: String) {
		val key = keyFor(uri) ?: return
		if (username.isEmpty()) {
			prefs(context).edit().remove("$key.user").remove("$key.pass").apply()
			return
		}
		prefs(context).edit()
			.putString("$key.user", username)
			.putString("$key.pass", password)
			.apply()
	}

	fun load(context: Context, uri: Uri): Credentials? {
		val key = keyFor(uri) ?: return null
		val p = prefs(context)
		val user = p.getString("$key.user", null) ?: return null
		return Credentials(user, p.getString("$key.pass", "") ?: "")
	}

	// Playlists saved by older builds embed user:pass@ in the URI. Move it here and hand back a clean one.
	fun migrateLegacyUri(context: Context, uri: Uri): Uri {
		if (uri.scheme?.startsWith("smb") != true) return uri
		val userInfo = uri.userInfo ?: return uri
		val parts = userInfo.split(":", limit = 2)
		val user = decode(parts[0])
		val pass = if (parts.size > 1) decode(parts[1]) else ""
		val port = uri.port.takeIf { it != -1 }?.let { ":$it" } ?: ""
		val clean = uri.buildUpon().encodedAuthority((uri.host ?: return uri) + port).build()
		save(context, clean, user, pass)
		return clean
	}

	private fun decode(value: String): String =
		try { java.net.URLDecoder.decode(value, "UTF-8") } catch (e: Exception) { value }
}
