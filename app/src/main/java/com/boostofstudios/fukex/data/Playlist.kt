package com.boostofstudios.fukex.data
import android.net.Uri
enum class AuthType {
	PIN, PASSWORD
}

data class Playlist(
	val id: String,
	val name: String,
	val uris: List<Uri>,
	val lastIndex: Int = 0,
	val lastPosition: Float = 0f,
	val isHidden: Boolean = false,
	val pinHash: String? = null,
	val pinSalt: String? = null,
	val authType: AuthType = AuthType.PIN
)
