package com.boostofstudios.fukex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.playback.LyricsAnnouncer
import com.boostofstudios.fukex.playback.PlaybackEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(engine: PlaybackEngine, hideDetails: Boolean, onBack: () -> Unit) {
	androidx.activity.compose.BackHandler(onBack = onBack)
	val context = LocalContext.current
	val state by engine.state.collectAsState()
	val lyricsState by engine.lyrics.collectAsState()
	val talkBackOn = remember { LyricsAnnouncer.isTalkBackRunning(context) }
	val announceOn = remember { SettingsManager.isLyricsAnnounceEnabled(context) }
	val listState = rememberLazyListState()
	DisposableEffect(engine) {
		engine.lyricsViewerOpen = true
		onDispose { engine.lyricsViewerOpen = false }
	}
	// Moving the list under TalkBack's focus is disorienting, so only follow along without it
	LaunchedEffect(lyricsState.currentLine) {
		if (!talkBackOn && lyricsState.currentLine >= 0) {
			listState.animateScrollToItem((lyricsState.currentLine + 1).coerceAtLeast(0))
		}
	}
	Scaffold(
		topBar = {
			TopAppBar(
				title = {
					Text(
						if (hideDetails) "Lyrics" else "Lyrics: ${state.currentTitle}",
						modifier = Modifier.semantics { heading() }
					)
				},
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
					}
				}
			)
		}
	) { padding ->
		val lyrics = lyricsState.lyrics
		when {
			hideDetails || state.queue.isEmpty() -> Message(padding, if (hideDetails) "Unlock the playlist to see its lyrics." else "Nothing is playing.")
			lyricsState.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
				CircularProgressIndicator(modifier = Modifier.semantics { stateDescription = "Looking for lyrics" })
			}
			lyrics == null -> Message(
				padding,
				if (SettingsManager.isLyricsOnlineEnabled(context)) "No lyrics found for this song."
				else "No lyrics found. Add a .lrc file next to the song, or turn on online lyrics in Settings."
			)
			else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
				item {
					val note = when {
						!lyrics.synced -> "These lyrics aren't timed, so they can't follow the song."
						talkBackOn && !announceOn -> "Turn on \"Read lyrics aloud\" in Settings to have TalkBack read each line as it plays."
						else -> null
					}
					if (note != null) {
						Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
					}
				}
				items(lyrics.lines.size) { i ->
					val isCurrent = i == lyricsState.currentLine
					Text(
						lyrics.lines[i].text,
						style = MaterialTheme.typography.titleMedium,
						fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
						color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 6.dp)
							.semantics { if (isCurrent) stateDescription = "Current line" }
					)
				}
			}
		}
	}
}

@Composable
private fun Message(padding: androidx.compose.foundation.layout.PaddingValues, text: String) {
	Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
		Text(text, style = MaterialTheme.typography.bodyLarge)
	}
}
