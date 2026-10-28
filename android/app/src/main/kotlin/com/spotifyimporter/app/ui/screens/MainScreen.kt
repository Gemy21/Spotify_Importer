package com.spotifyimporter.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.spotifyimporter.app.data.SettingsStore
import com.spotifyimporter.app.data.SpotifyRepository
import com.spotifyimporter.app.downloader.DownloadQueueManager
import com.spotifyimporter.app.model.DownloadSettings
import com.spotifyimporter.app.model.DownloadStatus
import com.spotifyimporter.app.model.PlaylistInfo
import com.spotifyimporter.app.ui.components.SettingsSheet
import com.spotifyimporter.app.ui.components.TrackCard
import com.spotifyimporter.app.ui.theme.ErrorRed
import com.spotifyimporter.app.ui.theme.SpotifyGreen
import com.spotifyimporter.app.ui.theme.SurfaceDark
import com.spotifyimporter.app.ui.theme.TextMuted
import com.spotifyimporter.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    queueManager: DownloadQueueManager,
    spotifyRepo: SpotifyRepository,
    initialUrl: String? = null,
    onInitialUrlConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var inputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var playlistInfo by remember { mutableStateOf<PlaylistInfo?>(null) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var isUpdatingEngine by remember { mutableStateOf(false) }

    val tracks by queueManager.tracksState.collectAsState()
    val isDownloading by queueManager.isDownloading.collectAsState()
    val overallProgress by queueManager.overallProgress.collectAsState()

    val selectedCount = tracks.count { it.isSelected }
    val downloadableCount = tracks.count {
        it.isSelected && it.status != DownloadStatus.COMPLETED
    }
    val completedCount = tracks.count { it.status == DownloadStatus.COMPLETED }
    val failedCount = tracks.count { it.status == DownloadStatus.ERROR }

    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun startImport(raw: String) {
        val target = raw.trim()
        if (target.isEmpty() || isLoading) return
        if (isDownloading) {
            showMessage("Finish or cancel the current download first.")
            return
        }
        isLoading = true
        coroutineScope.launch {
            try {
                val result = spotifyRepo.fetchUrlOrSearch(target)
                val importError = result.error
                when {
                    importError != null -> showMessage(importError)
                    result.tracks.isEmpty() -> showMessage("No tracks found for that link.")
                    else -> {
                        playlistInfo = result
                        queueManager.setTracks(result.tracks)
                    }
                }
            } catch (e: Exception) {
                showMessage(e.localizedMessage ?: "Import failed")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            inputUrl = initialUrl
            startImport(initialUrl)
            onInitialUrlConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Spotify Importer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Import / search input
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste Spotify URL or search a song…") },
                            trailingIcon = {
                                if (inputUrl.isNotEmpty()) {
                                    IconButton(onClick = { inputUrl = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                } else {
                                    IconButton(onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrEmpty()) {
                                            inputUrl = clipText
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.ContentPaste,
                                            contentDescription = "Paste",
                                            tint = SpotifyGreen
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { startImport(inputUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            enabled = !isLoading && inputUrl.isNotBlank()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching…", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import / Search", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Loaded collection header
                playlistInfo?.let { info ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (info.coverUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = info.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = info.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (info.owner.isNotBlank() && info.owner != info.title) {
                                    Text(
                                        text = info.owner,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val total = maxOf(info.totalCount, info.tracks.size)
                                val countLabel = if (total > info.tracks.size) {
                                    "${info.tracks.size} of $total tracks"
                                } else {
                                    "${info.tracks.size} tracks"
                                }
                                Text(
                                    text = "$countLabel • ${info.type.uppercase()}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                if (info.isTruncated) {
                                    Text(
                                        text = "Only part of this ${info.type} could be loaded.",
                                        fontSize = 11.sp,
                                        color = ErrorRed
                                    )
                                }
                            }
                        }
                    }
                }

                if (tracks.isEmpty() && !isLoading) {
                    EmptyState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (isDownloading) 140.dp else 96.dp)
                    ) {
                        items(tracks, key = { it.uid }) { track ->
                            TrackCard(
                                track = track,
                                onToggleSelect = { queueManager.toggleSelectTrack(it) }
                            )
                        }
                    }
                }
            }

            // Batch action bar
            AnimatedVisibility(
                visible = tracks.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceDark,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (isDownloading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading $completedCount/${tracks.size}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "$overallProgress%",
                                    fontSize = 12.sp,
                                    color = SpotifyGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = overallProgress / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = SpotifyGreen,
                                trackColor = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    val allSelected = tracks.all { it.isSelected }
                                    queueManager.selectAll(!allSelected)
                                }) {
                                    Text(
                                        text = if (tracks.all { it.isSelected }) "Deselect All" else "Select All",
                                        color = SpotifyGreen
                                    )
                                }
                                Text(
                                    text = "($selectedCount/${tracks.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }

                            if (isDownloading) {
                                OutlinedButton(
                                    onClick = { queueManager.cancel() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel")
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (failedCount > 0) {
                                        TextButton(onClick = { queueManager.retryFailed(settings) }) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = SpotifyGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry ($failedCount)", color = SpotifyGreen)
                                        }
                                    }
                                    Button(
                                        onClick = { queueManager.startBatchDownload(settings) },
                                        enabled = downloadableCount > 0,
                                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            tint = Color.Black
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Download ($downloadableCount)",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSettingsSheet) {
            SettingsSheet(
                settings = settings,
                onSettingsChanged = {
                    settings = it
                    settingsStore.save(it)
                },
                onUpdateEngine = {
                    if (!isUpdatingEngine) {
                        if (isDownloading) {
                            showMessage("Finish or cancel the current download first.")
                        } else {
                            isUpdatingEngine = true
                            coroutineScope.launch {
                                try {
                                    showMessage(queueManager.updateDownloadEngine())
                                } finally {
                                    isUpdatingEngine = false
                                }
                            }
                        }
                    }
                },
                isUpdatingEngine = isUpdatingEngine,
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Nothing loaded yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Paste a public Spotify playlist, album, track or artist link — or type a song name to search. You can also share a Spotify link straight to this app.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
