package com.boostofstudios.fukex.data

import org.json.JSONException
import org.json.JSONObject

// A version like 1.4.2 or 2.0.0-beta.1, compared the way semver.org describes:
// a pre-release sorts before the release it leads up to, and build metadata is ignored.
data class SemanticVersion(val major: Long, val minor: Long, val patch: Long, val preRelease: List<String>) : Comparable<SemanticVersion> {
	override fun compareTo(other: SemanticVersion): Int {
		compareValues(major, other.major).let { if (it != 0) return it }
		compareValues(minor, other.minor).let { if (it != 0) return it }
		compareValues(patch, other.patch).let { if (it != 0) return it }
		if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
			// 1.0.0 is newer than 1.0.0-rc.1
			return other.preRelease.size.compareTo(preRelease.size)
		}
		for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
			val a = preRelease[i]
			val b = other.preRelease[i]
			val an = a.toLongOrNull()
			val bn = b.toLongOrNull()
			val result = when {
				an != null && bn != null -> an.compareTo(bn)
				an != null -> -1 // numbers sort before words
				bn != null -> 1
				else -> a.compareTo(b)
			}
			if (result != 0) return result
		}
		return preRelease.size.compareTo(other.preRelease.size)
	}

	companion object {
		private val pattern = Regex("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")

		// Tolerates a leading "v" and a missing minor or patch; returns null for anything else
		fun parse(text: String?): SemanticVersion? {
			val m = pattern.matchEntire(text?.trim() ?: return null) ?: return null
			return try {
				SemanticVersion(
					m.groupValues[1].toLong(),
					m.groupValues[2].ifEmpty { "0" }.toLong(),
					m.groupValues[3].ifEmpty { "0" }.toLong(),
					m.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
				)
			} catch (e: NumberFormatException) {
				null
			}
		}

		// True only when remote is strictly newer; an unreadable version never counts as an update
		fun isNewer(current: String?, remote: String?): Boolean {
			val c = parse(current) ?: return false
			val r = parse(remote) ?: return false
			return r > c
		}
	}
}

data class ReleaseInfo(
	val version: String,
	val notes: String,
	val pageUrl: String,
	val apkUrl: String,
	val apkName: String,
	val apkSizeBytes: Long,
	// Lower-case hex SHA-256 that GitHub publishes for the asset, when it has one
	val sha256: String?
)

object ReleaseParser {
	private const val ASSET_PREFIX = "https://github.com/amankg777/fukex/releases/download/"
	private val shaPattern = Regex("[0-9a-f]{64}")

	// Reads GitHub's "latest release" JSON. Returns null for a draft, a pre-release, or a release with no APK.
	fun parse(body: String): ReleaseInfo? = try {
		val json = JSONObject(body)
		if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
			null
		} else {
			val tag = json.getString("tag_name")
			val assets = json.getJSONArray("assets")
			val candidates = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
				it.optString("name").endsWith(".apk", ignoreCase = true) &&
					it.optString("browser_download_url").lowercase().startsWith(ASSET_PREFIX)
			}
			// A release may carry several APKs; take the signed release build, never debug or unsigned ones
			val asset = candidates.firstOrNull { name -> name.optString("name").lowercase().let { "debug" !in it && "unsigned" !in it } }
			if (asset == null || SemanticVersion.parse(tag) == null) {
				null
			} else {
				ReleaseInfo(
					version = tag.removePrefix("v").removePrefix("V"),
					notes = json.optString("body", "").trim(),
					pageUrl = json.optString("html_url").takeIf { it.startsWith("https://github.com/") }
						?: "https://github.com/amankg777/fukex/releases/latest",
					apkUrl = asset.getString("browser_download_url"),
					apkName = asset.getString("name"),
					apkSizeBytes = asset.optLong("size", 0L),
					sha256 = asset.optString("digest").removePrefix("sha256:").lowercase().takeIf { shaPattern.matches(it) }
				)
			}
		}
	} catch (e: JSONException) {
		null
	}
}
