package com.spotifyimporter.app

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpotifyImporterApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize YoutubeDL and FFmpeg in background thread on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@SpotifyImporterApplication)
                FFmpeg.getInstance().init(this@SpotifyImporterApplication)
                Log.d("SpotifyImporterApp", "YoutubeDL & FFmpeg initialized successfully")
            } catch (e: YoutubeDLException) {
                Log.e("SpotifyImporterApp", "Failed to initialize YoutubeDL", e)
            } catch (e: Exception) {
                Log.e("SpotifyImporterApp", "Error initializing native libraries", e)
            }
        }
    }
}
