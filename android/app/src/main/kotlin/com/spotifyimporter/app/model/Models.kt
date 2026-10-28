package com.spotifyimporter.app.model

/**
 * A single downloadable track.
 *
 * [uid] is the stable identity used by the UI/queue. Spotify playlists may contain the
 * same track twice, so [id] (the Spotify URI) is not guaranteed to be unique. [uid] is.
 */
data class Track(
    val id: String,
    val uid: String = id,
    val name: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0,
    val spotifyUrl: String = "",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Int = 0,
    val errorMessage: String? = null,
    val isSelected: Boolean = true,
    val savedName: String = "",
    val savedUri: String = ""
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
    val tracks: List<Track>,
    /** Total number of tracks reported by Spotify (may be larger than [tracks]). */
    val totalCount: Int = tracks.size,
    /** True when only part of the collection could be loaded (e.g. embed fallback). */
    val isTruncated: Boolean = false,
    /** Set when the import failed; [tracks] is empty in that case. */
    val error: String? = null
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
    OPUS("opus", "opus");

    val mimeType: String
        get() = when (this) {
            MP3 -> "audio/mpeg"
            M4A -> "audio/mp4"
            OPUS -> "audio/ogg"
        }
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
