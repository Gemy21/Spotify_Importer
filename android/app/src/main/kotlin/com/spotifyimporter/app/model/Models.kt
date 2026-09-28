package com.spotifyimporter.app.model

data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0,
    val spotifyUrl: String = "",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val isSelected: Boolean = true
)

enum class DownloadStatus {
    IDLE,
    QUEUED,
    SEARCHING,
    DOWNLOADING,
    TAGGING,
    COMPLETED,
    ERROR
}

data class PlaylistInfo(
    val id: String = "",
    val title: String,
    val owner: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val type: String, // "playlist", "album", "track", "artist", "search"
    val tracks: List<Track>
)

data class DownloadSettings(
    val audioSource: AudioSource = AudioSource.YOUTUBE,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val audioQuality: AudioQuality = AudioQuality.Q320K,
    val embedLyrics: Boolean = true,
    val saveLrcFile: Boolean = true,
    val downloadLocation: String = "Music/SpotifyImporter"
)

enum class AudioSource(val value: String, val label: String) {
    YOUTUBE("youtube", "YouTube Music"),
    SOUNDCLOUD("sound-cloud", "SoundCloud")
}

enum class AudioFormat(val value: String, val extension: String) {
    MP3("mp3", "mp3"),
    M4A("m4a", "m4a"),
    OPUS("opus", "opus")
}

enum class AudioQuality(val value: String, val bitrate: String) {
    Q320K("320k", "320 kbps (Best)"),
    Q256K("256k", "256 kbps (High)"),
    Q192K("192k", "192 kbps (Standard)"),
    Q128K("128k", "128 kbps (Low)")
}

data class LyricsResult(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null
)
