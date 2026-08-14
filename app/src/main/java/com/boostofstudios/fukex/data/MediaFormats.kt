package com.boostofstudios.fukex.data
import java.io.File

val MEDIA_EXTENSIONS = setOf(
	"mp3", "mp4", "flac", "wav", "m4a", "aac", "ogg", "mkv", "webm", "opus", "alac"
)

fun File.isMediaFile(): Boolean = extension.lowercase() in MEDIA_EXTENSIONS

fun String.isMediaUrl(): Boolean = substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS
