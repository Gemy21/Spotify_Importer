package com.spotifyimporter.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.spotifyimporter.app.model.DownloadStatus
import com.spotifyimporter.app.model.Track
import com.spotifyimporter.app.ui.theme.AccentBright
import com.spotifyimporter.app.ui.theme.SpotifyGreen
import com.spotifyimporter.app.ui.theme.TextMuted
import com.spotifyimporter.app.ui.theme.TextPrimary
import com.spotifyimporter.app.ui.theme.TextSecondary

@Composable
fun TrackCard(
    track: Track,
    onToggleSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = track.isSelected
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .glassSurface(shape, selected = selected, subtle = !selected)
            .clickable { onToggleSelect(track.uid) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionMark(selected)

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF0B100D))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (track.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = track.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.album.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = track.album,
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(
                visible = track.status == DownloadStatus.ERROR && track.errorMessage != null
            ) {
                Text(
                    text = track.errorMessage ?: "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        StatusIndicator(track)
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    val fill by animateColorAsState(if (selected) SpotifyGreen else Color.Transparent)
    val stroke by animateColorAsState(if (selected) SpotifyGreen else Color(0x3DFFFFFF))

    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, stroke, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun StatusIndicator(track: Track) {
    Box(
        modifier = Modifier.width(52.dp),
        contentAlignment = Alignment.Center
    ) {
        when (track.status) {
            DownloadStatus.IDLE -> {
                if (track.durationMs > 0) {
                    val totalSeconds = track.durationMs / 1000
                    Text(
                        text = String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            DownloadStatus.QUEUED, DownloadStatus.SEARCHING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = SpotifyGreen,
                    strokeWidth = 2.dp
                )
            }

            DownloadStatus.DOWNLOADING, DownloadStatus.TAGGING -> {
                CircularProgressIndicator(
                    progress = track.progress / 100f,
                    modifier = Modifier.size(26.dp),
                    color = SpotifyGreen,
                    trackColor = Color(0x26FFFFFF),
                    strokeWidth = 2.5.dp
                )
            }

            DownloadStatus.COMPLETED -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(SpotifyGreen, AccentBright))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Saved",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (track.savedName.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "SAVED",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = SpotifyGreen
                        )
                    }
                }
            }

            DownloadStatus.ERROR -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
