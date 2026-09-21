import { NextResponse } from 'next/server';
// @ts-ignore — no type definitions available
import spotifyUrlInfoFactory from 'spotify-url-info';
import { extractSpotifyCoverArt, searchTracksKeyless } from '@/lib/metadata-resolver';
import { fetchAllPlaylistTracks } from '@/lib/spotify-scraper';

const spotifyClient = spotifyUrlInfoFactory(fetch);

export async function POST(req: Request) {
  try {
    const { url } = await req.json();

    if (!url) {
      return NextResponse.json({ error: 'URL or search query is required' }, { status: 400 });
    }

    const input = url.trim();

    // Detect input type
    const urlType = detectUrlType(input);

    if (urlType === 'search') {
      // Search mode: use keyless public music database to retrieve real songs with high-res cover art
      const foundTracks = await searchTracksKeyless(input, 15);
      if (foundTracks.length > 0) {
        return NextResponse.json({
          type: 'search',
          title: `Results for "${input}"`,
          coverArt: foundTracks[0].coverArt || null,
          tracks: foundTracks,
        });
      }

      // Fallback if no external search match
      return NextResponse.json({
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

    // For playlists: Use the paginated scraper to get ALL tracks (600+, 1000+, no 100 cap)
    // and genuine song-level cover art for every single track
    if (urlType === 'playlist') {
      try {
        console.log(`[API/playlist] Fetching full playlist via SpotAPI: ${input}`);
        const fullPlaylist = await fetchAllPlaylistTracks(input);
        if (fullPlaylist && fullPlaylist.tracks && fullPlaylist.tracks.length > 0) {
          console.log(`[API/playlist] Successfully retrieved ${fullPlaylist.tracks.length} tracks for "${fullPlaylist.title}"`);
          return NextResponse.json({
            type: 'playlist',
            title: fullPlaylist.title,
            coverArt: fullPlaylist.coverArt,
            tracks: fullPlaylist.tracks,
          });
        }
      } catch (scraperErr) {
        console.warn('[API/playlist] Full playlist scraper failed, falling back to embed client:', scraperErr);
      }
    }

    // Fetch data from Spotify URL without requiring an API key (fallback / albums / tracks)
    const data = await spotifyClient.getData(input);

    if (!data) {
      return NextResponse.json({ error: 'Could not retrieve data from this URL' }, { status: 404 });
    }

    let formattedTracks: any[] = [];
    const mainCover = extractSpotifyCoverArt(data);

    if (urlType === 'track') {
      // Single track: gets the real photo from Spotify directly
      const trackCover = mainCover;

      formattedTracks = [{
        id: data.uri || data.id || `track-${Date.now()}`,
        name: data.title || data.name,
        artist: data.subtitle || data.artist || data.artists?.map((a: any) => a.name).join(', ') || 'Unknown Artist',
        album: data.albumName || data.album?.name || data.album || data.name || '',
        duration: data.duration || data.duration_ms,
        coverArt: trackCover,
        isPlaylistCover: false,
        spotifyUri: data.uri || data.id,
      }];
    } else if (urlType === 'album') {
      // Album: tracks share the genuine album artwork
      const albumCover = mainCover;
      const albumName = data.title || data.name || 'Unknown Album';
      const albumArtist = data.subtitle || data.artist || data.artists?.map((a: any) => a.name).join(', ') || 'Unknown Artist';

      if (data.trackList && data.trackList.length > 0) {
        formattedTracks = data.trackList.map((track: any) => {
          const trackCover = extractSpotifyCoverArt(track) || albumCover;

          return {
            id: track.uri || track.id || `album-track-${Math.random().toString(36).substring(2, 7)}`,
            name: track.title || track.name,
            artist: track.subtitle || track.artist || albumArtist,
            album: track.albumName || albumName,
            duration: track.duration,
            coverArt: trackCover,
            isPlaylistCover: false,
            spotifyUri: track.uri || track.id,
          };
        });
      }
    } else if (urlType === 'artist') {
      // Artist top tracks
      const artistCover = mainCover;
      const artistName = data.title || data.name || 'Unknown Artist';

      if (data.trackList && data.trackList.length > 0) {
        formattedTracks = data.trackList.map((track: any) => {
          const trackCover = extractSpotifyCoverArt(track) || artistCover;

          return {
            id: track.uri || track.id || `artist-track-${Math.random().toString(36).substring(2, 7)}`,
            name: track.title || track.name,
            artist: track.subtitle || track.artist || artistName,
            album: track.albumName || track.album?.name || '',
            duration: track.duration,
            coverArt: trackCover,
            isPlaylistCover: false,
            spotifyUri: track.uri || track.id,
          };
        });
      }
    } else {
      // Playlist: songs initially have the playlist cover, marked as isPlaylistCover: true
      // During download or preview, real song picture is automatically fetched!
      const playlistCover = mainCover;

      if (!data.trackList || data.trackList.length === 0) {
        return NextResponse.json({ error: 'No tracks found' }, { status: 404 });
      }

      formattedTracks = data.trackList.map((track: any) => {
        const individualTrackCover = extractSpotifyCoverArt(track);

        return {
          id: track.uri || track.id || `pl-track-${Math.random().toString(36).substring(2, 7)}`,
          name: track.title || track.name,
          artist: track.subtitle || track.artist || 'Unknown Artist',
          album: track.albumName || track.album?.name || track.album || data.title || data.name || 'Unknown Album',
          duration: track.duration,
          coverArt: individualTrackCover || playlistCover,
          isPlaylistCover: !individualTrackCover,
          spotifyUri: track.uri || track.id,
        };
      });
    }

    if (formattedTracks.length === 0) {
      return NextResponse.json({ error: 'No tracks found for this URL' }, { status: 404 });
    }

    return NextResponse.json({
      type: urlType,
      title: data.title || data.name || '',
      coverArt: mainCover,
      tracks: formattedTracks,
    });
  } catch (error: any) {
    console.error('Error fetching playlist:', error);
    return NextResponse.json(
      { error: 'Failed to fetch data. Ensure the URL is correct and public.' },
      { status: 500 }
    );
  }
}

function detectUrlType(input: string): 'track' | 'album' | 'playlist' | 'artist' | 'search' {
  const lower = input.toLowerCase();

  if (lower.includes('open.spotify.com/track/') || lower.includes('spotify:track:')) {
    return 'track';
  }
  if (lower.includes('open.spotify.com/album/') || lower.includes('spotify:album:')) {
    return 'album';
  }
  if (lower.includes('open.spotify.com/playlist/') || lower.includes('spotify:playlist:')) {
    return 'playlist';
  }
  if (lower.includes('open.spotify.com/artist/') || lower.includes('spotify:artist:')) {
    return 'artist';
  }

  // If it looks like a URL but isn't spotify, still try as playlist
  if (lower.startsWith('http://') || lower.startsWith('https://')) {
    return 'playlist';
  }

  // Otherwise treat as search query
  return 'search';
}
