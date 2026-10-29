package com.spotifyimporter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.spotifyimporter.app.data.SpotifyRepository
import com.spotifyimporter.app.downloader.DownloadQueueManager
import com.spotifyimporter.app.ui.screens.MainScreen
import com.spotifyimporter.app.ui.theme.SpotifyImporterTheme

class MainActivity : ComponentActivity() {

    private lateinit var queueManager: DownloadQueueManager
    private val spotifyRepo = SpotifyRepository()

    private var pendingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Let the aurora canvas run edge to edge; content keeps its own insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        queueManager = DownloadQueueManager.getInstance(applicationContext)
        pendingUrl = extractSharedUrl(intent)

        checkAndRequestPermissions()

        setContent {
            SpotifyImporterTheme {
                MainScreen(
                    queueManager = queueManager,
                    spotifyRepo = spotifyRepo,
                    initialUrl = pendingUrl,
                    onInitialUrlConsumed = { pendingUrl = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUrl = extractSharedUrl(intent)
    }

    /**
     * Supports both `open.spotify.com` deep links and plain-text shares from the
     * Spotify app's "Share → Copy link" sheet.
     */
    private fun extractSharedUrl(intent: Intent?): String? {
        intent ?: return null

        intent.data?.toString()?.takeIf { it.isNotBlank() }?.let { return it }

        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!shared.isNullOrBlank()) {
            return Regex("https?://open\\.spotify\\.com/\\S+").find(shared)?.value ?: shared
        }
        return null
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        }
    }
}
