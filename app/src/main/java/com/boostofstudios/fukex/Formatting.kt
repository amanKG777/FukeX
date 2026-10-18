package com.boostofstudios.fukex

import android.net.Uri

fun formatTime(ms: Long): String {
	val totalSeconds = if (ms > 0) ms / 1000 else 0
	val hours = totalSeconds / 3600
	val minutes = (totalSeconds % 3600) / 60
	val seconds = totalSeconds % 60
	return if (hours > 0) {
		"%d:%02d:%02d".format(hours, minutes, seconds)
	} else {
		"%02d:%02d".format(minutes, seconds)
	}
}

// Track lists redraw often; this avoids a ContentResolver query per row per frame
private val contentTitleCache = java.util.concurrent.ConcurrentHashMap<String, String>()

fun trackTitle(uri: Uri, index: Int, context: android.content.Context? = null): String {
	if (uri.scheme == "youtube") {
		val titleParam = uri.getQueryParameter("title")
		if (!titleParam.isNullOrBlank()) {
			return Uri.decode(titleParam)
		}
	}
	if (context != null && uri.scheme == "content") {
		contentTitleCache[uri.toString()]?.let { return it }
		// MediaStore has the tagged song title; other providers only know the file name
		val column = if (uri.authority == android.provider.MediaStore.AUTHORITY) android.provider.MediaStore.Audio.Media.TITLE else android.provider.OpenableColumns.DISPLAY_NAME
		try {
			context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
				if (cursor.moveToFirst()) {
					val name = cursor.getString(0)?.takeIf { it.isNotBlank() }
					if (name != null) {
						val title = if (column == android.provider.OpenableColumns.DISPLAY_NAME) name.substringBeforeLast('.') else name
						contentTitleCache[uri.toString()] = title
						return title
					}
				}
			}
		} catch (e: Exception) {}
	}
	val lastSegment = uri.lastPathSegment?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
	val fromSegment = lastSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } }
	if (fromSegment != null) return fromSegment

	val path = uri.path?.let { Uri.decode(it) }
	val fromPath = path?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } }
	if (fromPath != null) return fromPath

	return lastSegment?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Track ${index + 1}"
}

fun formatSize(size: Long): String {
	if (size <= 0) return "0 B"
	val units = arrayOf("B", "KB", "MB", "GB", "TB")
	val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
	return "%.2f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
