package com.spotifyimporter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotifyimporter.app.ui.theme.AccentBright
import com.spotifyimporter.app.ui.theme.GlassBorderBottom
import com.spotifyimporter.app.ui.theme.GlassBorderTop
import com.spotifyimporter.app.ui.theme.GlassFillBottom
import com.spotifyimporter.app.ui.theme.GlassFillTop
import com.spotifyimporter.app.ui.theme.GlassSelectedBottom
import com.spotifyimporter.app.ui.theme.GlassSelectedTop
import com.spotifyimporter.app.ui.theme.GlassSheen
import com.spotifyimporter.app.ui.theme.OledBlack
import com.spotifyimporter.app.ui.theme.SpotifyGreen
import com.spotifyimporter.app.ui.theme.TextMuted
import com.spotifyimporter.app.ui.theme.TextPrimary
import com.spotifyimporter.app.ui.theme.TextSecondary

/**
 * Pure-black canvas with a single soft emerald aurora and a cooler teal bloom.
 * Drawn once with radial gradients, so it costs nothing per frame on low-end phones.
 */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(OledBlack)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x331ED760), Color(0x121ED760), Color.Transparent),
                        center = Offset(size.width * 0.06f, size.height * 0.01f),
                        radius = size.maxDimension * 0.85f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x2617E0C0), Color(0x0A17E0C0), Color.Transparent),
                        center = Offset(size.width * 1.05f, size.height * 0.58f),
                        radius = size.maxDimension * 0.7f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x1200A3FF), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 1.0f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                // Keep the extreme top and bottom edges pure OLED black.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0x77000000),
                        0.18f to Color.Transparent,
                        0.85f to Color.Transparent,
                        1f to Color(0x99000000)
                    )
                )
            }
    )
}

/**
 * The base glass treatment: translucent fill, a diagonal sheen, and a hairline
 * border that is brightest at the top so it reads as a lit edge.
 */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    selected: Boolean = false,
    subtle: Boolean = false
): Modifier {
    val fill = when {
        selected -> Brush.verticalGradient(listOf(GlassSelectedTop, GlassSelectedBottom))
        subtle -> Brush.verticalGradient(listOf(Color(0x0AFFFFFF), Color(0x03FFFFFF)))
        else -> Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom))
    }
    val stroke = when {
        selected -> Brush.verticalGradient(
            listOf(SpotifyGreen.copy(alpha = 0.55f), SpotifyGreen.copy(alpha = 0.08f))
        )
        subtle -> Brush.verticalGradient(listOf(Color(0x16FFFFFF), Color(0x05FFFFFF)))
        else -> Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom))
    }
    return this
        .clip(shape)
        .background(fill)
        .background(
            Brush.linearGradient(
                listOf(GlassSheen, Color.Transparent, Color(0x04000000))
            )
        )
        .border(1.dp, stroke, shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .glassSurface(shape, selected)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** Full-width emerald CTA with a soft glow, used for the primary action. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 52.dp,
    leadingIcon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = if (enabled) 16.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = SpotifyGreen.copy(alpha = 0.35f),
                spotColor = SpotifyGreen.copy(alpha = 0.55f)
            )
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(SpotifyGreen, AccentBright))
                else Brush.horizontalGradient(listOf(Color(0xFF141B17), Color(0xFF101613)))
            )
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(9.dp))
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (enabled) Color.Black else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.Black else TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                maxLines = 1
            )
        }
    }
}

/** Quiet glass button for secondary actions (Cancel, engine update). */
@Composable
fun GlassOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x24FFFFFF), shape)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = SpotifyGreen,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (enabled) SpotifyGreen else TextMuted,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Selectable glass pill, used for source/format choices. */
@Composable
fun OptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(listOf(SpotifyGreen, AccentBright))
                else SolidColor(Color(0x0FFFFFFF))
            )
            .border(1.dp, if (selected) Color.Transparent else Color(0x1FFFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .glassSurface(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.7.sp
    )
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x0FFFFFFF))
    )
}

@Composable
fun GlassSnackbar(data: SnackbarData) {
    Snackbar(
        snackbarData = data,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFF0C120F),
        contentColor = TextPrimary,
        actionColor = SpotifyGreen,
        actionContentColor = SpotifyGreen
    )
}
