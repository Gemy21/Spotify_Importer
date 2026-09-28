package com.spotifyimporter.app.data

import com.spotifyimporter.app.model.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class LyricsRepository {

    private val client = OkHttpClient.Builder().build()

    suspend fun fetchLyrics(artist: String, trackName: String): LyricsResult = withContext(Dispatchers.IO) {
        // 1. Direct get lookup on LRCLIB
        try {
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTrack = URLEncoder.encode(trackName, "UTF-8")
            val url = "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTrack"

            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                val synced = json.optString("syncedLyrics", "").ifEmpty { null }
                val plain = json.optString("plainLyrics", "").ifEmpty { null }
                if (synced != null || plain != null) {
                    return@withContext LyricsResult(syncedLyrics = synced, plainLyrics = plain)
                }
            }
        } catch (e: Exception) {
            // ignore and fallback
        }

        // 2. Search fallback on LRCLIB
        try {
            val query = URLEncoder.encode("$artist $trackName", "UTF-8")
            val url = "https://lrclib.net/api/search?q=$query"

            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string() ?: ""
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val match = array.getJSONObject(0)
                    val synced = match.optString("syncedLyrics", "").ifEmpty { null }
                    val plain = match.optString("plainLyrics", "").ifEmpty { null }
                    return@withContext LyricsResult(syncedLyrics = synced, plainLyrics = plain)
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        return@withContext LyricsResult()
    }
}
