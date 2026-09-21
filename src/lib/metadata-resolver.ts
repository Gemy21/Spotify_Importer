/**
 * Keyless metadata & cover art resolver.
 * Extracts high-res Spotify cover art (640x640) from public embeds/APIs,
 * and provides keyless public search resolution (via iTunes / public endpoints).
 */

// @ts-ignore — no type definitions available
import spotifyUrlInfoFactory from 'spotify-url-info';

const spotifyClient = spotifyUrlInfoFactory(fetch);

export interface ResolvedTrack {
  id: string;
  name: string;
  artist: string;
  album: string;
  duration?: number;
  coverArt?: string | null;
  releaseDate?: string;
  isPlaylistCover?: boolean;
  spotifyUri?: string;
}

/**
 * Extracts the highest resolution cover art available from a Spotify entity object.
 */
export function extractSpotifyCoverArt(data: any): string | null {
  if (!data) return null;

  // 1. Check visualIdentity images array (Spotify's current web structure)
  if (Array.isArray(data.visualIdentity?.image) && data.visualIdentity.image.length > 0) {
    const sorted = [...data.visualIdentity.image].sort(
      (a, b) => (b.maxWidth || b.maxHeight || 0) - (a.maxWidth || a.maxHeight || 0)
    );
    if (sorted[0]?.url) return sorted[0].url;
  }

  // 2. Check coverArt sources (standard spotify-url-info format)
  if (Array.isArray(data.coverArt?.sources) && data.coverArt.sources.length > 0) {
    const sorted = [...data.coverArt.sources].sort(
      (a, b) => (b.width || b.height || 0) - (a.width || a.height || 0)
    );
    if (sorted[0]?.url) return sorted[0].url;
  }
  if (typeof data.coverArt?.url === 'string') {
    return data.coverArt.url;
  }

  // 3. Check albumCoverArt sources
  if (Array.isArray(data.albumCoverArt?.sources) && data.albumCoverArt.sources.length > 0) {
    return data.albumCoverArt.sources[0].url;
  }
  if (typeof data.albumCoverArt?.url === 'string') {
    return data.albumCoverArt.url;
  }

  // 4. Check album images
  if (Array.isArray(data.album?.images) && data.album.images.length > 0) {
    return data.album.images[0].url;
  }

  // 5. Check visual avatar images (for artists)
  if (Array.isArray(data.visuals?.avatarImage?.sources) && data.visuals.avatarImage.sources.length > 0) {
    return data.visuals.avatarImage.sources[0].url;
  }

  // 6. Direct image property
  if (typeof data.image === 'string' && data.image) {
    return data.image;
  }

  return null;
}

/**
 * Searches for songs using public keyless endpoints (iTunes search API).
 * Returns real tracks with high-res album covers (600x600/1000x1000), artists, albums, and durations.
 */
export async function searchTracksKeyless(query: string, limit: number = 15): Promise<ResolvedTrack[]> {
  try {
    const cleanQuery = query.trim();
    if (!cleanQuery) return [];

    const url = `https://itunes.apple.com/search?term=${encodeURIComponent(cleanQuery)}&entity=song&limit=${limit}`;
    const res = await fetch(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
      },
      next: { revalidate: 3600 },
    });

    if (!res.ok) return [];

    const data = await res.json();
    if (!data.results || !Array.isArray(data.results)) return [];

    return data.results.map((r: any) => {
      // Upgrade standard 100x100 thumbnail to 600x600 high-res cover
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
        releaseDate: r.releaseDate,
        isPlaylistCover: false,
      };
    });
  } catch (err) {
    console.error('Keyless track search error:', err);
    return [];
  }
}

/**
 * Resolves a fallback cover art URL for any track whose cover is missing or low quality.
 */
export async function resolveFallbackCover(artist: string, name: string): Promise<string | null> {
  try {
    const query = `${artist} ${name}`.trim();
    const tracks = await searchTracksKeyless(query, 1);
    if (tracks.length > 0 && tracks[0].coverArt) {
      return tracks[0].coverArt;
    }
  } catch (err) {
    console.warn('Failed to resolve fallback cover art:', err);
  }
  return null;
}

/**
 * Resolves the real song picture:
 * - If single track from Spotify (not playlist cover): keeps the genuine Spotify cover.
 * - If imported from playlist (where Spotify only gave the playlist's picture):
 *   fetches the real song's album art from Spotify track preview or high-res public catalog!
 */
export async function resolveRealSongCover(params: {
  name: string;
  artist: string;
  spotifyUri?: string;
  coverUrl?: string | null;
  isPlaylistCover?: boolean;
}): Promise<string | null> {
  // If we already have a real track cover that is NOT just the playlist graphic, use it
  if (params.coverUrl && !params.isPlaylistCover) {
    return params.coverUrl;
  }

  // 1. If it's a playlist track with a Spotify URI, fetch the real track preview from Spotify directly
  if (params.spotifyUri) {
    try {
      const uriOrUrl = params.spotifyUri.startsWith('spotify:') || params.spotifyUri.startsWith('http')
        ? params.spotifyUri
        : `spotify:track:${params.spotifyUri}`;
      const preview = await spotifyClient.getPreview(uriOrUrl);
      if (preview && preview.image) {
        return preview.image;
      }
    } catch (err) {
      // Fall through to public search
    }
  }

  // 2. Search for real song picture using iTunes / public keyless database (600x600/1000x1000)
  const fallback = await resolveFallbackCover(params.artist, params.name);
  if (fallback) {
    return fallback;
  }

  // 3. Fallback to passed cover if nothing else worked
  return params.coverUrl || null;
}
