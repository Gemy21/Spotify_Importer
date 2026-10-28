package com.spotifyimporter.app.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Saves finished audio (and optional .lrc/.txt sidecars) into the device's public
 * `Music/SpotifyImporter` folder.
 *
 * On Android 10+ this goes through MediaStore, which needs no storage permission and
 * makes the files appear immediately in every music player. Older devices use the
 * legacy public Music directory.
 */
class MediaStoreSaver(private val context: Context) {

    data class SavedFile(
        val uri: Uri,
        val displayName: String,
        val location: String
    )

    fun saveAudio(
        source: File,
        displayName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String
    ): SavedFile {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveAudioToMediaStore(source, displayName, mimeType, title, artist, album)
            } else {
                saveAudioToLegacyDir(source, displayName, mimeType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore save failed, falling back to app storage", e)
            saveToAppStorage(source, displayName, mimeType)
        }
    }

    /**
     * Saves a lyrics sidecar next to the audio. Tries `Music/SpotifyImporter` first and
     * falls back to `Download/SpotifyImporter` where non-media files are allowed.
     */
    fun saveLyrics(text: String, displayName: String): SavedFile? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveTextToMediaStore(text, displayName)
            } else {
                val dir = legacyMusicDir()
                val file = uniqueFile(dir, displayName)
                file.writeText(text)
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                SavedFile(Uri.fromFile(file), file.name, dir.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save lyrics sidecar", e)
            null
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveAudioToMediaStore(
        source: File,
        displayName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String
    ): SavedFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
        }

        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore refused to create $displayName")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Could not open MediaStore output stream")

            val publish = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, publish, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }

        return SavedFile(uri, displayName, RELATIVE_FOLDER)
    }

    private fun saveAudioToLegacyDir(source: File, displayName: String, mimeType: String): SavedFile {
        val dir = legacyMusicDir()
        val destination = uniqueFile(dir, displayName)
        source.copyTo(destination, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf(mimeType), null)
        return SavedFile(Uri.fromFile(destination), destination.name, dir.absolutePath)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveTextToMediaStore(text: String, displayName: String): SavedFile? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        // Non-media files are only guaranteed to be allowed in Download/ on Android 10+.
        val collections = listOf(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to RELATIVE_FOLDER,
            MediaStore.Downloads.EXTERNAL_CONTENT_URI to "Download/SpotifyImporter"
        )

        for ((collection, relativePath) in collections) {
            val insertValues = ContentValues(values).apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = runCatching { resolver.insert(collection, insertValues) }.getOrNull() ?: continue

            val stream = runCatching { resolver.openOutputStream(uri) }.getOrNull()
            if (stream == null) {
                runCatching { resolver.delete(uri, null, null) }
                continue
            }

            try {
                stream.use { output -> output.write(text.toByteArray(Charsets.UTF_8)) }
                val publish = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, publish, null, null)
                return SavedFile(uri, displayName, relativePath)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                Log.e(TAG, "Sidecar insert failed for $relativePath", e)
            }
        }
        return null
    }

    private fun saveToAppStorage(source: File, displayName: String, mimeType: String): SavedFile {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val dir = File(root, "SpotifyImporter").apply { mkdirs() }
        val destination = uniqueFile(dir, displayName)
        source.copyTo(destination, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf(mimeType), null)
        return SavedFile(Uri.fromFile(destination), destination.name, dir.absolutePath)
    }

    @Suppress("DEPRECATION")
    private fun legacyMusicDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SpotifyImporter"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate

        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val fileName = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            candidate = File(dir, fileName)
            index++
        }
        return candidate
    }

    private companion object {
        const val TAG = "MediaStoreSaver"
        const val RELATIVE_FOLDER = "Music/SpotifyImporter"
    }
}
