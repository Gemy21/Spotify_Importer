package com.spotifyimporter.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotifyimporter.app.model.*
import com.spotifyimporter.app.ui.theme.SpotifyGreen
import com.spotifyimporter.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: DownloadSettings,
    onSettingsChanged: (DownloadSettings) -> Unit,
    onUpdateEngine: () -> Unit,
    isUpdatingEngine: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Download Settings",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Audio Source
            Text(text = "Audio Source", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioSource.values().forEach { source ->
                    FilterChip(
                        selected = settings.audioSource == source,
                        onClick = { onSettingsChanged(settings.copy(audioSource = source)) },
                        label = { Text(source.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Format
            Text(text = "Audio Format", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudioFormat.values().forEach { fmt ->
                    FilterChip(
                        selected = settings.audioFormat == fmt,
                        onClick = { onSettingsChanged(settings.copy(audioFormat = fmt)) },
                        label = { Text(fmt.value.uppercase()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Quality
            Text(text = "Audio Quality", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                AudioQuality.values().forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.audioQuality == quality,
                            onClick = { onSettingsChanged(settings.copy(audioQuality = quality)) },
                            colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen)
                        )
                        Text(
                            text = quality.bitrate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lyrics Toggles
            Text(text = "Lyrics", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Embed lyrics in audio tag", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = settings.embedLyrics,
                    onCheckedChange = { onSettingsChanged(settings.copy(embedLyrics = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = SpotifyGreen)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Save .lrc / .txt file alongside audio", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = settings.saveLrcFile,
                    onCheckedChange = { onSettingsChanged(settings.copy(saveLrcFile = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = SpotifyGreen)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Storage info
            Text(text = "Storage", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Audio is saved to Music/SpotifyImporter and appears in your music player. " +
                    "Lyrics sidecars are saved alongside it when possible.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Engine maintenance
            Text(text = "Download Engine", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "If downloads start failing, update the bundled yt-dlp engine to the latest release.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onUpdateEngine,
                enabled = !isUpdatingEngine,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isUpdatingEngine) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = SpotifyGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Updating…")
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Update download engine")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                Text(text = "Done", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
