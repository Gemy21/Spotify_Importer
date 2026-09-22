/**
 * Playlist API route — handles Spotify URL/search and returns track list.
 * Port of src/app/api/playlist/route.ts for embedded Node.js server.
 */

const express = require('express');
const router = express.Router();
const { fetchAllPlaylistTracks, searchTracksKeyless, detectUrlType } = require('../lib/spotify-graphql');

// Lazy-load spotify-url-info (CommonJS)
let spotifyClient = null;
async function getSpotifyClient() {
  if (!spotifyClient) {
    const fetch = require('node-fetch');
    const spotifyUrlInfoFactory = require('spotify-url-info');
    spotifyClient = spotifyUrlInfoFactory(fetch);
  }
  return spotifyClient;
}

function extractSpotifyCoverArt(data) {
  if (!data) return null;

  if (Array.isArray(data.visualIdentity?.image) && data.visualIdentity.image.length > 0) {
    const sorted = [...data.visualIdentity.image].sort(
      (a, b) => (b.maxWidth || b.maxHeight || 0) - (a.maxWidth || a.maxHeight || 0)
    );
    if (sorted[0]?.url) return sorted[0].url;
  }

  if (Array.isArray(data.coverArt?.sources) && data.coverArt.sources.length > 0) {
    const sorted = [...data.coverArt.sources].sort(
      (a, b) => (b.width || b.height || 0) - (a.width || a.height || 0)
    );
    if (sorted[0]?.url) return sorted[0].url;
  }
  if (typeof data.coverArt?.url === 'string') return data.coverArt.url;

  if (Array.isArray(data.albumCoverArt?.sources) && data.albumCoverArt.sources.length > 0)
    return data.albumCoverArt.sources[0].url;
  if (typeof data.albumCoverArt?.url === 'string') return data.albumCoverArt.url;

  if (Array.isArray(data.album?.images) && data.album.images.length > 0)
    return data.album.images[0].url;

  if (Array.isArray(data.visuals?.avatarImage?.sources) && data.visuals.avatarImage.sources.length > 0)
    return data.visuals.avatarImage.sources[0].url;

  if (typeof data.image === 'string' && data.image) return data.image;

  return null;
}

router.post('/', async (req, res) => {
  try {
    const { url } = req.body;

    if (!url) {
      return res.status(400).json({ error: 'URL or search query is required' });
    }

    const input = url.trim();
    const urlType = detectUrlType(input);

    // Search mode
    if (urlType === 'search') {
      const foundTracks = await searchTracksKeyless(input, 15);
      if (foundTracks.length > 0) {
        return res.json({
          type: 'search',
          title: `Results for "${input}"`,
          coverArt: foundTracks[0].coverArt || null,
          tracks: foundTracks,
        });
      }
      return res.json({
        type: 'search',
        title: `Search: ${input}`,
        coverArt: null,
        tracks: [{
          id: `search-${Date.now()}`,
          name: input,
          artist: 'Unknown Artist',
          album: 'Single',
          duration: undefined,
          coverArt: null,
        }],
      });
    }

    // Playlist: use paginated GraphQL scraper for 600+ tracks
    if (urlType === 'playlist') {
      try {
        console.log(`[API/playlist] Fetching full playlist: ${input}`);
        const fullPlaylist = await fetchAllPlaylistTracks(input);
        if (fullPlaylist && fullPlaylist.tracks && fullPlaylist.tracks.length > 0) {
          console.log(`[API/playlist] Got ${fullPlaylist.tracks.length} tracks for "${fullPlaylist.title}"`);
          return res.json({
            type: 'playlist',
            title: fullPlaylist.title,
            coverArt: fullPlaylist.coverArt,
            tracks: fullPlaylist.tracks,
          });
        }
      } catch (scraperErr) {
        console.warn('[API/playlist] Scraper failed, falling back to embed client:', scraperErr.message);
      }
    }

    // Fallback: spotify-url-info for albums/tracks/artists
    const client = await getSpotifyClient();
    const data = await client.getData(input);

    if (!data) {
      return res.status(404).json({ error: 'Could not retrieve data from this URL' });
    }

    let formattedTracks = [];
    const mainCover = extractSpotifyCoverArt(data);

    if (urlType === 'track') {
      formattedTracks = [{
        id: data.uri || data.id || `track-${Date.now()}`,
        name: data.title || data.name,
        artist: data.subtitle || data.artist || data.artists?.map(a => a.name).join(', ') || 'Unknown Artist',
        album: data.albumName || data.album?.name || data.album || data.name || '',
        duration: data.duration || data.duration_ms,
        coverArt: mainCover,
        isPlaylistCover: false,
        spotifyUri: data.uri || data.id,
      }];
    } else if (urlType === 'album') {
      const albumCover = mainCover;
      const albumName = data.title || data.name || 'Unknown Album';
      const albumArtist = data.subtitle || data.artist || data.artists?.map(a => a.name).join(', ') || 'Unknown Artist';

      if (data.trackList && data.trackList.length > 0) {
        formattedTracks = data.trackList.map(track => ({
          id: track.uri || track.id || `album-track-${Math.random().toString(36).substring(2, 7)}`,
          name: track.title || track.name,
          artist: track.subtitle || track.artist || albumArtist,
          album: track.albumName || albumName,
          duration: track.duration,
          coverArt: extractSpotifyCoverArt(track) || albumCover,
          isPlaylistCover: false,
          spotifyUri: track.uri || track.id,
        }));
      }
    } else if (urlType === 'artist') {
      const artistName = data.title || data.name || 'Unknown Artist';
      if (data.trackList && data.trackList.length > 0) {
        formattedTracks = data.trackList.map(track => ({
          id: track.uri || track.id || `artist-track-${Math.random().toString(36).substring(2, 7)}`,
          name: track.title || track.name,
          artist: track.subtitle || track.artist || artistName,
          album: track.albumName || track.album?.name || '',
          duration: track.duration,
          coverArt: extractSpotifyCoverArt(track) || mainCover,
          isPlaylistCover: false,
          spotifyUri: track.uri || track.id,
        }));
      }
    } else {
      if (!data.trackList || data.trackList.length === 0) {
        return res.status(404).json({ error: 'No tracks found' });
      }
      formattedTracks = data.trackList.map(track => {
        const individualTrackCover = extractSpotifyCoverArt(track);
        return {
          id: track.uri || track.id || `pl-track-${Math.random().toString(36).substring(2, 7)}`,
          name: track.title || track.name,
          artist: track.subtitle || track.artist || 'Unknown Artist',
          album: track.albumName || track.album?.name || track.album || data.title || data.name || 'Unknown Album',
          duration: track.duration,
          coverArt: individualTrackCover || mainCover,
          isPlaylistCover: !individualTrackCover,
          spotifyUri: track.uri || track.id,
        };
      });
    }

    if (formattedTracks.length === 0) {
      return res.status(404).json({ error: 'No tracks found for this URL' });
    }

    return res.json({
      type: urlType,
      title: data.title || data.name || '',
      coverArt: mainCover,
      tracks: formattedTracks,
    });
  } catch (error) {
    console.error('[API/playlist] Error:', error);
    res.status(500).json({ error: 'Failed to fetch data. Ensure the URL is correct and public.' });
  }
});

module.exports = router;
