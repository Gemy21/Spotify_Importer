package com.spotifyimporter.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.spotifyimporter.app.data.SettingsStore
import com.spotifyimporter.app.data.SpotifyRepository
import com.spotifyimporter.app.downloader.DownloadQueueManager
import com.spotifyimporter.app.model.DownloadStatus
import com.spotifyimporter.app.model.PlaylistInfo
import com.spotifyimporter.app.ui.components.AuroraBackdrop
import com.spotifyimporter.app.ui.components.GlassCard
import com.spotifyimporter.app.ui.components.GlassIconButton
import com.spotifyimporter.app.ui.components.GlassOutlineButton
import com.spotifyimporter.app.ui.components.GlassSnackbar
import com.spotifyimporter.app.ui.components.GradientButton
import com.spotifyimporter.app.ui.components.SectionLabel
import com.spotifyimporter.app.ui.components.SettingsSheet
import com.spotifyimporter.app.ui.components.TrackCard
import com.spotifyimporter.app.ui.components.glassSurface
import com.spotifyimporter.app.ui.theme.AccentBright
import com.spotifyimporter.app.ui.theme.ErrorRed
import com.spotifyimporter.app.ui.theme.SpotifyGreen
import com.spotifyimporter.app.ui.theme.TextMuted
import com.spotifyimporter.app.ui.theme.TextPrimary
import com.spotifyimporter.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

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

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {}
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppHeader(onSettings = { showSettingsSheet = true })

                    ImportPanel(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        isLoading = isLoading,
                        onPaste = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrEmpty()) inputUrl = clipText
                        },
                        onClear = { inputUrl = "" },
                        onImport = { startImport(inputUrl) }
                    )

                    playlistInfo?.let { info ->
                        CollectionCard(info)
                    }

                    if (tracks.isEmpty() && !isLoading) {
                        EmptyState(modifier = Modifier.weight(1f))
                    } else {
                        val navBottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                top = 4.dp,
                                bottom = (if (isDownloading) 172.dp else 124.dp) + navBottom
                            )
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

                // Fade the list out behind the floating dock so rows do not peek
                // through its translucent margins or the transparent nav bar.
                if (tracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color.Black.copy(alpha = 0.72f),
                                    1f to Color.Black
                                )
                            )
                    )
                }

                BottomDock(
                    visible = tracks.isNotEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isDownloading = isDownloading,
                    totalCount = tracks.size,
                    completedCount = completedCount,
                    selectedCount = selectedCount,
                    downloadableCount = downloadableCount,
                    failedCount = failedCount,
                    allSelected = tracks.isNotEmpty() && tracks.all { it.isSelected },
                    progress = overallProgress,
                    onToggleAll = {
                        val allSelected = tracks.all { it.isSelected }
                        queueManager.selectAll(!allSelected)
                    },
                    onCancel = { queueManager.cancel() },
                    onRetry = { queueManager.retryFailed(settings) },
                    onDownload = { queueManager.startBatchDownload(settings) }
                )

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 74.dp, start = 14.dp, end = 14.dp)
                ) { data ->
                    GlassSnackbar(data)
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

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun AppHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = SpotifyGreen.copy(alpha = 0.30f),
                    spotColor = SpotifyGreen.copy(alpha = 0.70f)
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF6CF7A6), Color(0xFF0FA85C))))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(Color(0x88FFFFFF), Color(0x11FFFFFF))),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = Color(0xFF04120A),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = "SPOTIFY",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.6.sp,
                color = SpotifyGreen
            )
            Text(
                text = "Importer",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
                color = TextPrimary
            )
        }

        Spacer(Modifier.weight(1f))

        GlassIconButton(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            onClick = onSettings
        )
    }
}

// ---------------------------------------------------------------------------
// Import panel
// ---------------------------------------------------------------------------

@Composable
private fun ImportPanel(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onImport: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("IMPORT", modifier = Modifier.weight(1f))
            Text(
                text = "PLAYLIST · ALBUM · TRACK",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x66000000))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 15.dp),
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 14.sp,
                    letterSpacing = 0.1.sp
                ),
                cursorBrush = SolidColor(SpotifyGreen),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "Paste a link or search a song…",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (value.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(onClick = onPaste, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        GradientButton(
            text = if (isLoading) "Fetching…" else "Import / Search",
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && value.isNotBlank(),
            loading = isLoading,
            leadingIcon = if (isLoading) null else Icons.Default.Search
        )
    }
}

// ---------------------------------------------------------------------------
// Collection header
// ---------------------------------------------------------------------------

@Composable
private fun CollectionCard(info: PlaylistInfo) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (info.coverUrl.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(14.dp))
                ) {
                    AsyncImage(
                        model = info.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (info.owner.isNotBlank() && info.owner != info.title) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = info.owner,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(7.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(info.type.uppercase())
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = countLabel(info),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                if (info.isTruncated) {
                    Spacer(Modifier.height(4.dp))
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

private fun countLabel(info: PlaylistInfo): String {
    val total = maxOf(info.totalCount, info.tracks.size)
    return if (total > info.tracks.size) {
        "${info.tracks.size} of $total tracks"
    } else {
        "${info.tracks.size} tracks"
    }
}

@Composable
private fun TypeBadge(label: String) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color(0x1A1ED760))
            .border(1.dp, Color(0x331ED760), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = SpotifyGreen
        )
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .shadow(
                        elevation = 30.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = SpotifyGreen.copy(alpha = 0.10f),
                        spotColor = SpotifyGreen.copy(alpha = 0.25f)
                    )
                    .glassSurface(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Nothing loaded yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Paste a public Spotify playlist, album, track or artist link — or type a song name to search. You can also share a Spotify link straight to this app.",
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HintPill("PLAYLIST")
                HintPill("ALBUM")
                HintPill("TRACK")
            }
        }
    }
}

@Composable
private fun HintPill(label: String) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x14FFFFFF), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            color = TextMuted
        )
    }
}

// ---------------------------------------------------------------------------
// Floating batch dock
// ---------------------------------------------------------------------------

@Composable
private fun BottomDock(
    visible: Boolean,
    modifier: Modifier = Modifier,
    isDownloading: Boolean,
    totalCount: Int,
    completedCount: Int,
    selectedCount: Int,
    downloadableCount: Int,
    failedCount: Int,
    allSelected: Boolean,
    progress: Int,
    onToggleAll: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut()
    ) {
        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .shadow(
                    elevation = 30.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = SpotifyGreen.copy(alpha = 0.08f),
                    spotColor = SpotifyGreen.copy(alpha = 0.18f)
                )
                // The dock floats over a scrolling list, so it needs a nearly solid
                // base; the glass gradient then only adds the lit sheen on top.
                .background(Color(0xF7000000), shape)
                .glassSurface(shape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (isDownloading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Downloading $completedCount/$totalCount",
                        fontSize = 11.sp,
                        letterSpacing = 0.2.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "$progress%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyGreen
                    )
                }

                Spacer(Modifier.height(7.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0x1FFFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((progress / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(SpotifyGreen, AccentBright)))
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val pillShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(Color(0x0FFFFFFF))
                        .border(1.dp, Color(0x1AFFFFFF), pillShape)
                        .clickable(onClick = onToggleAll)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (allSelected) "Deselect all" else "Select all",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SpotifyGreen
                    )
                }

                Spacer(Modifier.width(9.dp))

                Text(
                    text = "$selectedCount/$totalCount",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Spacer(Modifier.weight(1f))

                if (isDownloading) {
                    GlassOutlineButton(
                        text = "Cancel",
                        onClick = onCancel,
                        leadingIcon = Icons.Default.Close
                    )
                } else {
                    if (failedCount > 0) {
                        val retryShape = RoundedCornerShape(50)
                        Row(
                            modifier = Modifier
                                .clip(retryShape)
                                .background(Color(0x14FF5D63))
                                .border(1.dp, Color(0x33FF5D63), retryShape)
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry failed",
                                tint = ErrorRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Retry $failedCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    GradientButton(
                        text = "Download ($downloadableCount)",
                        onClick = onDownload,
                        enabled = downloadableCount > 0,
                        height = 44.dp,
                        leadingIcon = Icons.Default.Download
                    )
                }
            }
        }
    }
}
