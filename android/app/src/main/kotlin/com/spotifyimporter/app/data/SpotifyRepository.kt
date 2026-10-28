package com.spotifyimporter.app.data

import com.spotifyimporter.app.model.PlaylistInfo
import com.spotifyimporter.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Keyless Spotify metadata client.
 *
 * Two tiers are used:
 *  1. Spotify's internal Pathfinder GraphQL endpoint (via the anonymous token that is
 *     embedded in every public embed page). This returns full playlists with genuine
 *     640x640 song-level artwork and no 100-track cap.
 *  2. The public embed page itself, parsed from its `__NEXT_DATA__` JSON, for albums,
 *     tracks, artist top-tracks and as a fallback when Pathfinder is unavailable.
 *
 * If both fail for a Spotify link an empty result with a user-facing error is returned.
 * Non-Spotify input is treated as a search query against the public iTunes API.
 */
class SpotifyRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Cached persisted-query hash for `fetchPlaylist` (refreshed from the web player on failure). */
    @Volatile
    private var fetchPlaylistHash: String? = null

    suspend fun fetchUrlOrSearch(input: String): PlaylistInfo = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext PlaylistInfo(title = "Nothing to import", type = "search", tracks = emptyList())
        }

        val urlType = detectUrlType(trimmed)
            ?: return@withContext searchKeyless(trimmed)

        val spotifyId = extractId(trimmed)
        if (spotifyId.isBlank()) {
            return@withContext PlaylistInfo(
                title = "Invalid Spotify link",
                type = urlType,
                tracks = emptyList(),
                error = "Could not read a Spotify ID from that link."
            )
        }

        // Full paginated playlist import (no 100-track cap).
        if (urlType == "playlist") {
            runCatching { fetchFullPlaylist(spotifyId) }
                .getOrNull()
                ?.takeIf { it.tracks.isNotEmpty() }
                ?.let { return@withContext it }
        }

        // Embed fallback: albums / tracks / artists, or playlists when Pathfinder fails.
        runCatching { fetchFromEmbed(urlType, spotifyId) }
            .getOrNull()
            ?.takeIf { it.tracks.isNotEmpty() }
            ?.let { return@withContext it }

        PlaylistInfo(
            title = "Import failed",
            type = urlType,
            tracks = emptyList(),
            error = "Could not load that Spotify link. Make sure the playlist/album is public and try again."
        )
    }

    suspend fun searchKeyless(query: String, limit: Int = 20): PlaylistInfo = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext PlaylistInfo(title = "Empty Search", type = "search", tracks = emptyList())
        }

        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=$limit"
            val json = JSONObject(httpGet(url))
            val results = json.optJSONArray("results") ?: JSONArray()

            val tracks = mutableListOf<Track>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val rawCover = item.optString("artworkUrl100", "")
                val highResCover = if (rawCover.isNotEmpty()) {
                    rawCover.replace("100x100bb.jpg", "600x600bb.jpg")
                } else ""

                val trackId = "itunes-${item.optLong("trackId")}"
                tracks.add(
                    Track(
                        uid = trackId,
                        id = trackId,
                        name = item.optString("trackName", "Unknown Track"),
                        artist = item.optString("artistName", "Unknown Artist"),
                        album = item.optString("collectionName", "Single"),
                        coverUrl = highResCover,
                        durationMs = item.optLong("trackTimeMillis", 0)
                    )
                )
            }

            if (tracks.isNotEmpty()) {
                return@withContext PlaylistInfo(
                    title = "Results for \"$cleanQuery\"",
                    coverUrl = tracks.firstOrNull()?.coverUrl ?: "",
                    type = "search",
                    tracks = tracks,
                    totalCount = tracks.size
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Last-resort single track so the user can still download by name.
        val fallbackId = "search-${System.currentTimeMillis()}"
        return@withContext PlaylistInfo(
            title = "Search: $cleanQuery",
            type = "search",
            tracks = listOf(
                Track(
                    uid = fallbackId,
                    id = fallbackId,
                    name = cleanQuery,
                    artist = "Unknown Artist",
                    album = "Single"
                )
            ),
            totalCount = 1
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

    // ------------------------------------------------------------------------------------
    // Pathfinder GraphQL (full playlists)
    // ------------------------------------------------------------------------------------

    private fun fetchFullPlaylist(playlistId: String): PlaylistInfo? {
        val token = fetchAnonymousToken("playlist", playlistId) ?: return null
        val uri = "spotify:playlist:$playlistId"

        var hash = fetchPlaylistHash ?: DEFAULT_FETCH_PLAYLIST_HASH
        var response = queryPlaylist(token, uri, offset = 0, limit = PAGE_SIZE, hash = hash)

        if (response.isPersistedQueryFailure()) {
            val discovered = discoverFetchPlaylistHash()
            if (discovered != null && discovered != hash) {
                hash = discovered
                fetchPlaylistHash = discovered
                response = queryPlaylist(token, uri, offset = 0, limit = PAGE_SIZE, hash = hash)
            }
        }

        val firstPlaylist = response.playlistJson() ?: return null
        val title = firstPlaylist.optString("name").ifBlank { "Spotify Playlist" }
        val owner = firstPlaylist.optJSONObject("ownerV2")
            ?.optJSONObject("data")
            ?.optString("name")
            .orEmpty()
        val cover = bestImageFromGroups(firstPlaylist.optJSONObject("images")?.optJSONArray("items")) ?: ""
        val total = firstPlaylist.optJSONObject("content")?.optInt("totalCount", 0) ?: 0

        val tracks = mutableListOf<Track>()
        var offset = 0
        var page: JSONObject? = firstPlaylist

        while (page != null && tracks.size < MAX_TRACKS) {
            val items = page.optJSONObject("content")?.optJSONArray("items") ?: break
            if (items.length() == 0) break

            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                parsePlaylistEntry(entry, offset + i, cover)?.let { tracks.add(it) }
            }

            offset += items.length()
            if (total > 0 && offset >= total) break
            if (items.length() < PAGE_SIZE) break
            if (tracks.size >= MAX_TRACKS) break

            page = queryPlaylist(token, uri, offset, PAGE_SIZE, hash)
                .playlistJson()
        }

        if (tracks.isEmpty()) return null

        val reportedTotal = if (total > 0) total else tracks.size
        return PlaylistInfo(
            id = playlistId,
            title = title,
            owner = owner,
            coverUrl = cover,
            type = "playlist",
            tracks = tracks,
            totalCount = reportedTotal,
            isTruncated = reportedTotal > tracks.size
        )
    }

    private fun parsePlaylistEntry(entry: JSONObject, index: Int, fallbackCover: String): Track? {
        val data = entry.optJSONObject("itemV2")?.optJSONObject("data") ?: return null
        if (data.optString("__typename") != "Track") return null

        val name = data.optString("name")
        if (name.isBlank()) return null

        val uri = data.optString("uri")
        val uid = entry.optString("uid").ifBlank { "pl-$index" }

        val artistsArray = data.optJSONObject("artists")?.optJSONArray("items")
        val artist = joinArtistNames(artistsArray).ifBlank { "Unknown Artist" }

        val albumObject = data.optJSONObject("albumOfTrack")
        val album = albumObject?.optString("name").orEmpty()
        val cover = bestImage(albumObject?.optJSONObject("coverArt")?.optJSONArray("sources"))
            ?: fallbackCover

        val duration = data.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0) ?: 0

        return Track(
            uid = uid,
            id = uri.ifBlank { uid },
            name = name,
            artist = artist,
            album = album,
            coverUrl = cover,
            durationMs = duration,
            spotifyUrl = spotifyUriToWebUrl(uri)
        )
    }

    private data class PartnerResponse(val status: Int, val json: JSONObject?) {
        fun playlistJson(): JSONObject? =
            json?.optJSONObject("data")?.optJSONObject("playlistV2")

        fun isPersistedQueryFailure(): Boolean =
            status == 400 || status == 404 || json?.has("errors") == true
    }

    private fun queryPlaylist(
        token: String,
        uri: String,
        offset: Int,
        limit: Int,
        hash: String
    ): PartnerResponse {
        val variables = JSONObject().apply {
            put("uri", uri)
            put("offset", offset)
            put("limit", limit)
            put("enableWatchFeedEntrypoint", false)
            put("includeEpisodeContentRatingsV2", false)
        }
        val payload = JSONObject().apply {
            put("variables", variables)
            put("operationName", "fetchPlaylist")
            put(
                "extensions",
                JSONObject().put(
                    "persistedQuery",
                    JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", hash)
                    }
                )
            )
        }

        val request = Request.Builder()
            .url("https://api-partner.spotify.com/pathfinder/v2/query")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("app-platform", "WebPlayer")
            .header("User-Agent", USER_AGENT)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                PartnerResponse(response.code, json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            PartnerResponse(0, null)
        }
    }

    /**
     * The anonymous token is published in the `__NEXT_DATA__` blob of every embed page.
     * It is valid for a few hours and requires no login.
     */
    private fun fetchAnonymousToken(type: String, id: String): String? {
        return try {
            val html = httpGet("https://open.spotify.com/embed/$type/$id")
            val root = extractNextData(html) ?: return null
            root.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("settings")
                ?.optJSONObject("session")
                ?.optString("accessToken")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Spotify rotates the persisted-query hash when they ship a new web player. If the
     * bundled hash stops working we scrape the current one from the web-player bundle.
     */
    private fun discoverFetchPlaylistHash(): String? {
        return try {
            val home = httpGet("https://open.spotify.com/")
            val bundleUrl = Regex("https://open\\.spotifycdn\\.com/cdn/build/web-player/web-player\\.[a-f0-9]+\\.js")
                .find(home)
                ?.value
                ?: return null
            val bundle = httpGet(bundleUrl)
            Regex("\"fetchPlaylist\"\\s*,\\s*\"query\"\\s*,\\s*\"([a-f0-9]{64})\"")
                .find(bundle)
                ?.groupValues
                ?.get(1)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ------------------------------------------------------------------------------------
    // Embed fallback (albums / tracks / artists / capped playlists)
    // ------------------------------------------------------------------------------------

    private fun fetchFromEmbed(type: String, id: String): PlaylistInfo? {
        val html = httpGet("https://open.spotify.com/embed/$type/$id")
        val root = extractNextData(html) ?: return null
        val entity = root.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")
            ?: return null

        val title = entity.optString("name")
            .ifBlank { entity.optString("title") }
            .ifBlank { "Spotify $type" }
        val subtitle = entity.optString("subtitle")

        val cover = bestImage(entity.optJSONObject("coverArt")?.optJSONArray("sources"))
            ?: bestImage(entity.optJSONObject("visualIdentity")?.optJSONArray("image"))
            ?: fetchOEmbedCover(type, id)
            ?: ""

        val tracks = mutableListOf<Track>()
        val trackList = entity.optJSONArray("trackList")

        if (trackList != null && trackList.length() > 0) {
            for (i in 0 until trackList.length()) {
                val item = trackList.optJSONObject(i) ?: continue
                val trackName = item.optString("title").ifBlank { item.optString("name") }
                if (trackName.isBlank()) continue

                val uri = item.optString("uri")
                val uid = item.optString("uid").ifBlank { "embed-$type-$i" }
                tracks.add(
                    Track(
                        uid = uid,
                        id = uri.ifBlank { uid },
                        name = trackName,
                        artist = item.optString("subtitle").ifBlank { "Unknown Artist" },
                        album = if (type == "album") title else "",
                        coverUrl = cover,
                        durationMs = item.optLong("duration", 0),
                        spotifyUrl = spotifyUriToWebUrl(uri)
                    )
                )
            }
        } else if (type == "track") {
            val artistsArray = entity.optJSONArray("artists")
            val artist = joinArtistNames(artistsArray).ifBlank { "Unknown Artist" }
            val uri = entity.optString("uri").ifBlank { "spotify:track:$id" }
            tracks.add(
                Track(
                    uid = entity.optString("id").ifBlank { "track-$id" },
                    id = uri,
                    name = title,
                    artist = artist,
                    coverUrl = cover,
                    durationMs = entity.optLong("duration", 0),
                    spotifyUrl = "https://open.spotify.com/track/$id"
                )
            )
        }

        if (tracks.isEmpty()) return null

        val truncated = type == "playlist" && tracks.size >= EMBED_TRACK_CAP
        return PlaylistInfo(
            id = id,
            title = title,
            owner = if (type == "artist") title else subtitle,
            coverUrl = cover,
            type = type,
            tracks = tracks,
            totalCount = tracks.size,
            isTruncated = truncated
        )
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body?.string().orEmpty()
        }
    }

    private fun extractNextData(html: String): JSONObject? {
        val match = NEXT_DATA_REGEX.find(html) ?: return null
        return runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
    }

    /**
     * Album / track / artist embed payloads do not carry artwork, but the public oEmbed
     * endpoint returns a thumbnail for every entity type.
     */
    private fun fetchOEmbedCover(type: String, id: String): String? {
        return try {
            val json = JSONObject(
                httpGet("https://open.spotify.com/oembed?url=https://open.spotify.com/$type/$id")
            )
            upgradeCover(json.optString("thumbnail_url"))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Swaps the 300px / 64px album image variants for the 640px one when possible. */
    private fun upgradeCover(url: String): String? {
        if (url.isBlank()) return null
        return url
            .replace("ab67616d00001e02", "ab67616d0000b273")
            .replace("ab67616d00004851", "ab67616d0000b273")
    }

    private fun bestImage(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        var bestUrl: String? = null
        var bestScore = -1
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (url.isBlank()) continue
            val width = item.optInt("width", item.optInt("maxWidth", 0))
            val height = item.optInt("height", item.optInt("maxHeight", 0))
            val score = if (width > 0 && height > 0) width * height else maxOf(width, height)
            if (score > bestScore) {
                bestScore = score
                bestUrl = url
            }
        }
        return bestUrl
    }

    /** Handles `images: { items: [ { sources: [...] } ] }` (playlist metadata). */
    private fun bestImageFromGroups(groups: JSONArray?): String? {
        if (groups == null || groups.length() == 0) return null
        var bestUrl: String? = null
        var bestScore = -1
        for (i in 0 until groups.length()) {
            val sources = groups.optJSONObject(i)?.optJSONArray("sources") ?: continue
            for (j in 0 until sources.length()) {
                val item = sources.optJSONObject(j) ?: continue
                val url = item.optString("url")
                if (url.isBlank()) continue
                val width = item.optInt("width", 0)
                val height = item.optInt("height", 0)
                val score = if (width > 0 && height > 0) width * height else maxOf(width, height)
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = url
                }
            }
        }
        return bestUrl
    }

    private fun joinArtistNames(items: JSONArray?): String {
        if (items == null) return ""
        val names = mutableListOf<String>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val profile = item.optJSONObject("profile")
            val name = profile?.optString("name").orEmpty().ifBlank {
                item.optString("name").orEmpty()
            }
            if (name.isNotBlank()) names.add(name)
        }
        return names.joinToString(", ")
    }
    private fun spotifyUriToWebUrl(uri: String): String {
        val parts = uri.split(":")
        return if (parts.size == 3 && parts[0] == "spotify") {
            "https://open.spotify.com/${parts[1]}/${parts[2]}"
        } else ""
    }

    private fun detectUrlType(input: String): String? {
        val lower = input.lowercase()
        if (!lower.contains("open.spotify.com") && !lower.contains("spotify:")) return null
        val match = SPOTIFY_ID_REGEX.find(input) ?: return null
        return match.groupValues[1]
    }

    private fun extractId(input: String): String {
        val match = SPOTIFY_ID_REGEX.find(input)
        return match?.groupValues?.get(2) ?: ""
    }

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_TRACKS = 10_000
        private const val EMBED_TRACK_CAP = 100

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val NEXT_DATA_REGEX =
            Regex("<script id=\"__NEXT_DATA__\"[^>]*>([\\s\\S]*?)</script>")

        private val SPOTIFY_ID_REGEX =
            Regex("(track|album|playlist|artist)[/:]([a-zA-Z0-9]+)")

        /** Fallback hash bundled with the app; refreshed automatically when it goes stale. */
        private const val DEFAULT_FETCH_PLAYLIST_HASH =
            "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"
    }
}
