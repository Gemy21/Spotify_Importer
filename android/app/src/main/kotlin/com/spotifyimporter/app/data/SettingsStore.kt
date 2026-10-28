package com.spotifyimporter.app.data

import android.content.Context
import com.spotifyimporter.app.model.AudioFormat
import com.spotifyimporter.app.model.AudioQuality
import com.spotifyimporter.app.model.AudioSource
import com.spotifyimporter.app.model.DownloadSettings

/** Persists the download preferences between app launches. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): DownloadSettings {
        val defaults = DownloadSettings()
        return DownloadSettings(
            audioSource = enumOrDefault(
                prefs.getString(KEY_SOURCE, null),
                AudioSource.values(),
                defaults.audioSource
            ),
            audioFormat = enumOrDefault(
                prefs.getString(KEY_FORMAT, null),
                AudioFormat.values(),
                defaults.audioFormat
            ),
            audioQuality = enumOrDefault(
                prefs.getString(KEY_QUALITY, null),
                AudioQuality.values(),
                defaults.audioQuality
            ),
            embedLyrics = prefs.getBoolean(KEY_EMBED_LYRICS, defaults.embedLyrics),
            saveLrcFile = prefs.getBoolean(KEY_SAVE_LRC, defaults.saveLrcFile),
            downloadLocation = defaults.downloadLocation
        )
    }

    fun save(settings: DownloadSettings) {
        prefs.edit()
            .putString(KEY_SOURCE, settings.audioSource.name)
            .putString(KEY_FORMAT, settings.audioFormat.name)
            .putString(KEY_QUALITY, settings.audioQuality.name)
            .putBoolean(KEY_EMBED_LYRICS, settings.embedLyrics)
            .putBoolean(KEY_SAVE_LRC, settings.saveLrcFile)
            .apply()
    }

    private fun <T : Enum<T>> enumOrDefault(raw: String?, values: Array<T>, default: T): T {
        if (raw.isNullOrBlank()) return default
        return values.firstOrNull { it.name == raw } ?: default
    }

    private companion object {
        const val PREFS_NAME = "spotify_importer_settings"
        const val KEY_SOURCE = "audio_source"
        const val KEY_FORMAT = "audio_format"
        const val KEY_QUALITY = "audio_quality"
        const val KEY_EMBED_LYRICS = "embed_lyrics"
        const val KEY_SAVE_LRC = "save_lrc"
    }
}
