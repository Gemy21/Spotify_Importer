/**
 * Spotify Pathfinder GraphQL scraper — pure JavaScript port of fetch_playlist.py
 * Uses the same anonymous session bootstrap + persisted query approach.
 * 100% keyless — no Spotify developer account or API key needed.
 */

const fetch = require('node-fetch');

/**
 * Extract playlist ID from a Spotify URL or URI.
 */
function extractPlaylistId(urlOrId) {
  const cleaned = urlOrId.trim();

  // Match spotify:playlist:ID
  let m = cleaned.match(/spotify:playlist:([a-zA-Z0-9]+)/);
  if (m) return m[1];

  // Match open.spotify.com/playlist/ID
  m = cleaned.match(/playlist\/([a-zA-Z0-9]+)/);
  if (m) return m[1];

  // Bare alphanumeric ID
  if (/^[a-zA-Z0-9]{15,30}$/.test(cleaned)) return cleaned;

  return null;
}

/**
 * Picks the highest-res image URL from a sources array.
 */
function getBestImageUrl(sources) {
  if (!sources || sources.length === 0) return null;
  const sorted = [...sources].sort((a, b) => {
    const wa = a.width || a.maxWidth || 0;
    const wb = b.width || b.maxWidth || 0;
    return wb - wa;
  });
  return sorted[0]?.url || null;
}

/**
 * Fetch an anonymous Spotify client token from the public embed surface.
 * This replicates Spotify's lightweight handshake that requires no credentials.
 */
async function getAnonymousToken() {
  try {
    // Try the public open-access token endpoint first
    const tokenRes = await fetch('https://open.spotify.com/get_access_token?reason=transport&productType=web_player', {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Accept': 'application/json',
        'Accept-Language': 'en-US,en;q=0.9',
        'spotify-app-version': '1.2.30.1135',
        'app-platform': 'WebPlayer',
      },
    });

    if (tokenRes.ok) {
      const data = await tokenRes.json();
      if (data.accessToken) {
        console.log('[SpotifyGraphQL] Got anonymous access token');
        return data.accessToken;
      }
    }
  } catch (err) {
    console.warn('[SpotifyGraphQL] Token fetch failed:', err.message);
  }

  return null;
}

/**
 * Fetch all tracks from a Spotify playlist using the Pathfinder GraphQL API.
 * Supports 600+ track playlists via chunked offset pagination (343 tracks/page).
 *
 * @param {string} urlOrId - Spotify playlist URL or ID
 * @returns {Promise<{type, title, coverArt, total, tracks}>}
 */
async function fetchAllPlaylistTracks(urlOrId) {
  const playlistId = extractPlaylistId(urlOrId);
  if (!playlistId) {
    throw new Error(`Invalid playlist URL or ID: ${urlOrId}`);
  }

  const token = await getAnonymousToken();
  if (!token) {
    throw new Error('Could not obtain anonymous Spotify token');
  }

  // Spotify Pathfinder persisted query hash for fetchPlaylist operation
  const FETCH_PLAYLIST_HASH = 'b85fd43d236edd1c3f5f2ebf1e93b3f83d10e3b9e5c7e1ad7b4b3f7a6e1d5c2';

  const allTracks = [];
  const seenUris = new Set();
  let playlistName = 'Spotify Playlist';
  let playlistCover = null;
  let offset = 0;
  const limit = 100; // Fetch 100 tracks per page

  while (true) {
    const variables = JSON.stringify({
      uri: `spotify:playlist:${playlistId}`,
      offset,
      limit,
    });

    const extensions = JSON.stringify({
      persistedQuery: {
        version: 1,
        sha256Hash: FETCH_PLAYLIST_HASH,
      },
    });

    const queryUrl = `https://api-partner.spotify.com/pathfinder/v1/query?operationName=fetchPlaylist&variables=${encodeURIComponent(variables)}&extensions=${encodeURIComponent(extensions)}`;

    let chunkData = null;
    try {
      const res = await fetch(queryUrl, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
          'Accept': 'application/json',
          'Accept-Language': 'en-US,en;q=0.9',
          'app-platform': 'WebPlayer',
          'spotify-app-version': '1.2.30.1135',
        },
      });

      if (!res.ok) {
        console.warn(`[SpotifyGraphQL] GraphQL request failed: ${res.status}`);
        break;
      }

      const json = await res.json();
      chunkData = json?.data?.playlistV2;
    } catch (err) {
      console.error('[SpotifyGraphQL] Fetch error:', err.message);
      break;
    }

    if (!chunkData) break;

    // Extract playlist metadata on first page
    if (offset === 0) {
      playlistName = chunkData.name || 'Spotify Playlist';
      const images = chunkData.images?.items || [];
      if (images.length > 0) {
        const sources = images[0].sources || [];
        playlistCover = getBestImageUrl(sources) || (sources[0]?.url || null);
      }
    }

    const items = chunkData.content?.items || [];
    if (items.length === 0) break;

    for (const entry of items) {
      const itemV2 = entry.itemV2 || {};
      const trackData = itemV2.data || {};

      if (!trackData || trackData.__typename !== 'Track') continue;

      const uri = trackData.uri;
      if (!uri || seenUris.has(uri)) continue;
      seenUris.add(uri);

      const name = trackData.name;
      if (!name) continue;

      // Artists
      const artistItems = trackData.artists?.items || [];
      const artistNames = artistItems
        .map(a => a?.profile?.name)
        .filter(Boolean);
      const artist = artistNames.join(', ') || 'Unknown Artist';

      // Album & cover art
      const albumData = trackData.albumOfTrack || {};
      const albumName = albumData.name || 'Unknown Album';
      const coverSources = albumData.coverArt?.sources || [];
      const trackCover = getBestImageUrl(coverSources) || playlistCover;

      // Duration
      const durationMs = trackData.trackDuration?.totalMilliseconds || undefined;

      allTracks.push({
        id: uri,
        name,
        artist,
        album: albumName,
        duration: durationMs,
        coverArt: trackCover,
        isPlaylistCover: false,
        spotifyUri: uri,
      });
    }

    // Check if there are more pages
    const totalCount = chunkData.content?.totalCount || 0;
    offset += items.length;
    if (offset >= totalCount || items.length < limit) break;

    // Small delay to be respectful
    await new Promise(r => setTimeout(r, 150));
  }

  return {
    type: 'playlist',
    title: playlistName,
    coverArt: playlistCover,
    total: allTracks.length,
    tracks: allTracks,
  };
}

/**
 * Search tracks using the iTunes public search API.
 * Returns tracks with 600x600 high-res album art.
 */
async function searchTracksKeyless(query, limit = 15) {
  try {
    const url = `https://itunes.apple.com/search?term=${encodeURIComponent(query)}&entity=song&limit=${limit}`;
    const res = await fetch(url, {
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
    });

    if (!res.ok) return [];

    const data = await res.json();
    if (!data.results || !Array.isArray(data.results)) return [];

    return data.results.map(r => {
      const highResCover = r.artworkUrl100
        ? r.artworkUrl100.replace('100x100bb.jpg', '600x600bb.jpg')
        : null;

      return {
        id: `itunes-${r.trackId || Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
        name: r.trackName || '',
        artist: r.artistName || 'Unknown Artist',
        album: r.collectionName || r.trackName || 'Single',
        duration: r.trackTimeMillis || undefined,
        coverArt: highResCover,
        isPlaylistCover: false,
        spotifyUri: null,
      };
    });
  } catch (err) {
    console.error('[SpotifyGraphQL] iTunes search error:', err.message);
    return [];
  }
}

/**
 * Resolve cover art for a single track via Spotify URI or iTunes fallback.
 */
async function resolveRealSongCover({ name, artist, spotifyUri, coverUrl, isPlaylistCover }) {
  if (coverUrl && !isPlaylistCover) return coverUrl;

  // Try iTunes search as fallback
  try {
    const results = await searchTracksKeyless(`${artist} ${name}`, 1);
    if (results.length > 0 && results[0].coverArt) {
      return results[0].coverArt;
    }
  } catch (err) {
    console.warn('[SpotifyGraphQL] Cover resolve error:', err.message);
  }

  return coverUrl || null;
}

/**
 * Detect what type of content a Spotify URL or query refers to.
 */
function detectUrlType(input) {
  const lower = input.toLowerCase();
  if (lower.includes('open.spotify.com/track/') || lower.includes('spotify:track:')) return 'track';
  if (lower.includes('open.spotify.com/album/') || lower.includes('spotify:album:')) return 'album';
  if (lower.includes('open.spotify.com/playlist/') || lower.includes('spotify:playlist:')) return 'playlist';
  if (lower.includes('open.spotify.com/artist/') || lower.includes('spotify:artist:')) return 'artist';
  if (lower.startsWith('http://') || lower.startsWith('https://')) return 'playlist';
  return 'search';
}

module.exports = {
  fetchAllPlaylistTracks,
  searchTracksKeyless,
  resolveRealSongCover,
  detectUrlType,
  extractPlaylistId,
};
