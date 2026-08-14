package com.boostofstudios.fukex.data
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PlaylistSecurity {
	private const val ITERATIONS = 10_000

	fun newSalt(): String {
		val bytes = ByteArray(16)
		SecureRandom().nextBytes(bytes)
		return Base64.encodeToString(bytes, Base64.NO_WRAP)
	}

	fun hash(secret: String, salt: String): String {
		val digest = MessageDigest.getInstance("SHA-256")
		var current = (salt + secret).toByteArray(Charsets.UTF_8)
		repeat(ITERATIONS) {
			current = digest.digest(current)
		}
		return Base64.encodeToString(current, Base64.NO_WRAP)
	}

	fun verify(secret: String, playlist: Playlist): Boolean {
		val salt = playlist.pinSalt ?: return false
		val expected = playlist.pinHash ?: return false
		val actual = hash(secret, salt)
		if (actual.length != expected.length) return false
		var diff = 0
		for (i in expected.indices) {
			diff = diff or (expected[i].code xor actual[i].code)
		}
		return diff == 0
	}
}
