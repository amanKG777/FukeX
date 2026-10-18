package com.boostofstudios.fukex.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

data class LibraryTrack(
	val uri: Uri,
	val title: String,
	val artist: String,
	val album: String,
	val genre: String,
	val durationMs: Long
)

enum class LibraryGrouping(val label: String) {
	SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), GENRES("Genres")
}

// Reads the device's music from MediaStore, so tracks don't have to be added to a playlist first.
object MediaLibrary {
	const val UNKNOWN = "Unknown"

	private fun clean(value: String?): String =
		value?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING } ?: UNKNOWN

	// Blocking; call off the main thread.
	fun scan(context: Context): List<LibraryTrack> {
		val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
		val columns = mutableListOf(
			MediaStore.Audio.Media._ID,
			MediaStore.Audio.Media.TITLE,
			MediaStore.Audio.Media.ARTIST,
			MediaStore.Audio.Media.ALBUM,
			MediaStore.Audio.Media.DURATION
		)
		val hasGenreColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
		if (hasGenreColumn) columns.add(MediaStore.Audio.Media.GENRE)
		val genresById = if (hasGenreColumn) emptyMap() else legacyGenres(context)
		val tracks = mutableListOf<LibraryTrack>()
		try {
			context.contentResolver.query(
				collection,
				columns.toTypedArray(),
				"${MediaStore.Audio.Media.IS_MUSIC} != 0",
				null,
				"${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
			)?.use { c ->
				while (c.moveToNext()) {
					val id = c.getLong(0)
					tracks.add(
						LibraryTrack(
							uri = ContentUris.withAppendedId(collection, id),
							title = clean(c.getString(1)),
							artist = clean(c.getString(2)),
							album = clean(c.getString(3)),
							genre = clean(if (hasGenreColumn) c.getString(5) else genresById[id]),
							durationMs = c.getLong(4)
						)
					)
				}
			}
		} catch (e: Exception) {
			Log.e("MediaLibrary", "Could not scan music library", e)
		}
		return tracks
	}

	// Before Android 11 there's no GENRE column, so genres come from the genre membership tables.
	private fun legacyGenres(context: Context): Map<Long, String> {
		val result = mutableMapOf<Long, String>()
		try {
			context.contentResolver.query(
				MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
				arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
				null, null, null
			)?.use { genres ->
				while (genres.moveToNext()) {
					val genreId = genres.getLong(0)
					val name = genres.getString(1) ?: continue
					@Suppress("DEPRECATION")
					val members = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
					context.contentResolver.query(members, arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null)?.use { m ->
						while (m.moveToNext()) result.putIfAbsent(m.getLong(0), name)
					}
				}
			}
		} catch (e: Exception) {
			Log.w("MediaLibrary", "Could not read genres", e)
		}
		return result
	}

	fun groupKey(track: LibraryTrack, grouping: LibraryGrouping): String = when (grouping) {
		LibraryGrouping.SONGS -> ""
		LibraryGrouping.ARTISTS -> track.artist
		LibraryGrouping.ALBUMS -> track.album
		LibraryGrouping.GENRES -> track.genre
	}
}
