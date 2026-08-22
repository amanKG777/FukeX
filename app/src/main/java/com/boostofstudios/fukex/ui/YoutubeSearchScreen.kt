package com.boostofstudios.fukex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.boostofstudios.fukex.BuildConfig

data class YoutubeVideo(val id: String, val title: String, val channelTitle: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeSearchScreen(onBack: () -> Unit, onVideoSelected: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YoutubeVideo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search YouTube") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (query.isNotBlank()) {
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val searchExtractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getSearchExtractor(query)
                                    val resultList = withContext(Dispatchers.IO) {
                                        searchExtractor.fetchPage()
                                        val items = searchExtractor.initialPage.items
                                        val list = mutableListOf<YoutubeVideo>()
                                        for (item in items) {
                                            if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                                                val id = item.url.substringAfterLast("v=").substringBefore("&")
                                                val title = item.name
                                                val channelTitle = item.uploaderName
                                                list.add(YoutubeVideo(id, title, channelTitle))
                                            }
                                        }
                                        list
                                    }
                                    results = resultList
                                } catch (e: Exception) {
                                    errorMessage = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    }
                ) {
                    Text("Search")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (isLoading) {
                CircularProgressIndicator()
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn {
                    items(results) { video ->
                        ListItem(
                            headlineContent = { Text(video.title) },
                            supportingContent = { Text(video.channelTitle) },
                            modifier = Modifier.clickable {
                                onVideoSelected("youtube://${video.id}")
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
