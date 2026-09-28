package com.spotifyimporter.app.data

import com.spotifyimporter.app.model.PlaylistInfo
import com.spotifyimporter.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class SpotifyRepository {

    private val client = OkHttpClient.Builder().build()

    suspend fun fetchUrlOrSearch(input: String): PlaylistInfo = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        val urlType = detectUrlType(trimmed)

        if (urlType == "search") {
            return@withContext searchKeyless(trimmed)
        }

        // Handle Spotify URL / URI
        try {
            val spotifyId = extractId(trimmed)
            val embedUrl = "https://open.spotify.com/embed/$urlType/$spotifyId"
            
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(req).execute()
            val html = response.body?.string() ?: ""

            val playlistInfo = parseSpotifyEmbedHtml(html, urlType, spotifyId)
            if (playlistInfo != null && playlistInfo.tracks.isNotEmpty()) {
                return@withContext playlistInfo
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to searching the input directly if parsing failed
        return@withContext searchKeyless(trimmed)
    }

    suspend fun searchKeyless(query: String, limit: Int = 15): PlaylistInfo = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext PlaylistInfo(title = "Empty Search", type = "search", tracks = emptyList())
        }

        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=$limit"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            val jsonStr = response.body?.string() ?: ""
            val json = JSONObject(jsonStr)
            val results = json.optJSONArray("results") ?: org.json.JSONArray()

            val tracks = mutableListOf<Track>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val rawCover = item.optString("artworkUrl100", "")
                val highResCover = if (rawCover.isNotEmpty()) {
                    rawCover.replace("100x100bb.jpg", "600x600bb.jpg")
                } else ""

                tracks.add(
                    Track(
                        id = "itunes-${item.optLong("trackId")}",
                        name = item.optString("trackName", "Unknown Track"),
                        artist = item.optString("artistName", "Unknown Artist"),
                        album = item.optString("collectionName", "Single"),
                        coverUrl = highResCover,
                        durationMs = item.optLong("trackTimeMillis", 0),
                        spotifyUrl = ""
                    )
                )
            }

            if (tracks.isNotEmpty()) {
                return@withContext PlaylistInfo(
                    title = "Results for \"$cleanQuery\"",
                    coverUrl = tracks.firstOrNull()?.coverUrl ?: "",
                    type = "search",
                    tracks = tracks
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Default single track fallback
        return@withContext PlaylistInfo(
            title = "Search: $cleanQuery",
            type = "search",
            tracks = listOf(
                Track(
                    id = "search-${System.currentTimeMillis()}",
                    name = cleanQuery,
                    artist = "Unknown Artist",
                    album = "Single"
                )
            )
        )
    }

    suspend fun resolveRealCoverUrl(artist: String, trackName: String): String = withContext(Dispatchers.IO) {
        try {
            val info = searchKeyless("$artist $trackName", limit = 1)
            val cover = info.tracks.firstOrNull()?.coverUrl
            if (!cover.isNullOrEmpty()) return@withContext cover
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext ""
    }

    private fun detectUrlType(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("open.spotify.com/track/") || lower.contains("spotify:track:") -> "track"
            lower.contains("open.spotify.com/album/") || lower.contains("spotify:album:") -> "album"
            lower.contains("open.spotify.com/playlist/") || lower.contains("spotify:playlist:") -> "playlist"
            lower.contains("open.spotify.com/artist/") || lower.contains("spotify:artist:") -> "artist"
            lower.startsWith("http://") || lower.startsWith("https://") -> "playlist"
            else -> "search"
        }
    }

    private fun extractId(input: String): String {
        val regex = Regex("(track|album|playlist|artist)[/:]([a-zA-Z0-9]+)")
        val match = regex.find(input)
        return match?.groupValues?.get(2) ?: input.split("/").last().split("?").first()
    }

    private fun parseSpotifyEmbedHtml(html: String, type: String, id: String): PlaylistInfo? {
        try {
            // Find script tag containing resource or initial-state JSON
            val resourceRegex = Regex("<script id=\"(resource|initial-state)\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            val match = resourceRegex.find(html) ?: return null
            val jsonText = match.groupValues[2].trim()
            val json = JSONObject(jsonText)

            var title = "Spotify $type"
            var mainCover = ""
            val tracks = mutableListOf<Track>()

            if (json.has("name")) {
                title = json.optString("name", title)
            } else if (json.has("title")) {
                title = json.optString("title", title)
            }

            // Cover art
            val coverArtObj = json.optJSONObject("coverArt") ?: json.optJSONObject("albumCoverArt")
            if (coverArtObj != null && coverArtObj.has("sources")) {
                val sources = coverArtObj.optJSONArray("sources")
                if (sources != null && sources.length() > 0) {
                    mainCover = sources.getJSONObject(0).optString("url", "")
                }
            }

            // Track listing parsing
            val trackList = json.optJSONArray("trackList") ?: json.optJSONArray("tracks")
            if (trackList != null) {
                for (i in 0 until trackList.length()) {
                    val t = trackList.getJSONObject(i)
                    val tName = t.optString("title", t.optString("name", ""))
                    val tArtist = t.optString("subtitle", t.optString("artist", "Unknown Artist"))
                    val tAlbum = t.optString("albumName", title)
                    val tUri = t.optString("uri", t.optString("id", ""))
                    val tDuration = t.optLong("duration", t.optLong("duration_ms", 0))

                    var tCover = ""
                    val tCoverObj = t.optJSONObject("coverArt") ?: t.optJSONObject("albumCoverArt")
                    if (tCoverObj != null && tCoverObj.has("sources")) {
                        val sources = tCoverObj.optJSONArray("sources")
                        if (sources != null && sources.length() > 0) {
                            tCover = sources.getJSONObject(0).optString("url", "")
                        }
                    }
                    if (tCover.isEmpty()) tCover = mainCover

                    if (tName.isNotEmpty()) {
                        tracks.add(
                            Track(
                                id = if (tUri.isNotEmpty()) tUri else "track-$i",
                                name = tName,
                                artist = tArtist,
                                album = tAlbum,
                                coverUrl = tCover,
                                durationMs = tDuration,
                                spotifyUrl = if (tUri.startsWith("spotify:track:")) "https://open.spotify.com/track/${tUri.removePrefix("spotify:track:")}" else ""
                            )
                        )
                    }
                }
            }

            return PlaylistInfo(
                id = id,
                title = title,
                coverUrl = mainCover,
                type = type,
                tracks = tracks
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
