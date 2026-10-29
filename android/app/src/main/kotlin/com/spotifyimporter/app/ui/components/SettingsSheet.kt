package com.spotifyimporter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotifyimporter.app.model.AudioFormat
import com.spotifyimporter.app.model.AudioQuality
import com.spotifyimporter.app.model.AudioSource
import com.spotifyimporter.app.model.DownloadSettings
import com.spotifyimporter.app.ui.theme.SpotifyGreen
import com.spotifyimporter.app.ui.theme.TextMuted
import com.spotifyimporter.app.ui.theme.TextPrimary
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
    // Open fully expanded: the sheet is long and a half-height peek hides most settings.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF050706),
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.76f),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 30.dp)
        ) {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                color = TextPrimary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Downloads, tagging and engine",
                fontSize = 12.sp,
                color = TextMuted
            )

            Spacer(Modifier.height(22.dp))

            // Audio source -------------------------------------------------
            SectionLabel("AUDIO SOURCE")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioSource.values().forEach { source ->
                    OptionPill(
                        label = source.label,
                        selected = settings.audioSource == source,
                        onClick = { onSettingsChanged(settings.copy(audioSource = source)) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Format -------------------------------------------------------
            SectionLabel("FORMAT")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioFormat.values().forEach { format ->
                    OptionPill(
                        label = format.value.uppercase(),
                        selected = settings.audioFormat == format,
                        onClick = { onSettingsChanged(settings.copy(audioFormat = format)) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Quality ------------------------------------------------------
            SectionLabel("QUALITY")
            Spacer(Modifier.height(9.dp))
            GlassCard(
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                AudioQuality.values().forEachIndexed { index, quality ->
                    QualityRow(
                        label = quality.bitrate,
                        selected = settings.audioQuality == quality,
                        onClick = { onSettingsChanged(settings.copy(audioQuality = quality)) }
                    )
                    if (index != AudioQuality.values().lastIndex) {
                        GlassDivider()
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Lyrics -------------------------------------------------------
            SectionLabel("LYRICS")
            Spacer(Modifier.height(9.dp))
            GlassCard(
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                SwitchRow(
                    label = "Embed lyrics in audio tag",
                    checked = settings.embedLyrics,
                    onCheckedChange = { onSettingsChanged(settings.copy(embedLyrics = it)) }
                )
                GlassDivider()
                SwitchRow(
                    label = "Save .lrc / .txt file alongside audio",
                    checked = settings.saveLrcFile,
                    onCheckedChange = { onSettingsChanged(settings.copy(saveLrcFile = it)) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Storage ------------------------------------------------------
            SectionLabel("STORAGE")
            Spacer(Modifier.height(9.dp))
            GlassCard(
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "Music/SpotifyImporter",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Audio appears in your music player. Lyrics sidecars are saved alongside it when possible.",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(20.dp))

            // Engine -------------------------------------------------------
            SectionLabel("DOWNLOAD ENGINE")
            Spacer(Modifier.height(9.dp))
            GlassCard(
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "If downloads start failing, update the bundled yt-dlp engine to the latest release.",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                GlassOutlineButton(
                    text = if (isUpdatingEngine) "Updating…" else "Update download engine",
                    onClick = onUpdateEngine,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdatingEngine,
                    loading = isUpdatingEngine,
                    leadingIcon = if (isUpdatingEngine) null else Icons.Default.Refresh
                )
            }

            Spacer(Modifier.height(24.dp))

            GradientButton(
                text = "Done",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) TextPrimary else TextSecondary
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) SpotifyGreen else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (selected) Color.Transparent else Color(0x33FFFFFF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = TextSecondary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = SpotifyGreen,
                checkedBorderColor = SpotifyGreen,
                uncheckedThumbColor = Color(0xFF8C9992),
                uncheckedTrackColor = Color(0x1AFFFFFF),
                uncheckedBorderColor = Color(0x26FFFFFF)
            )
        )
    }
}
