import { NextResponse } from 'next/server';
import spotifyUrlInfo from 'spotify-url-info';

const { getTracks } = spotifyUrlInfo(fetch);

export async function POST(req: Request) {
  try {
    const { url } = await req.json();

    if (!url) {
      return NextResponse.json({ error: 'URL is required' }, { status: 400 });
    }

    const data = await spotifyUrlInfo(fetch).getData(url);
    
    if (!data || !data.trackList) {
      return NextResponse.json({ error: 'No tracks found in playlist' }, { status: 404 });
    }

    const playlistCoverArt = data.coverArt?.sources?.[0]?.url || data.coverArt?.url || null;

    // Format tracks to extract only what we need
    const formattedTracks = data.trackList.map((track: any) => {
      // Try to get individual track cover art
      const trackCover =
        track.coverArt?.sources?.[0]?.url ||
        track.coverArt?.url ||
        track.albumCoverArt?.sources?.[0]?.url ||
        track.albumCoverArt?.url ||
        track.album?.coverArt?.sources?.[0]?.url ||
        track.album?.images?.[0]?.url ||
        playlistCoverArt;

      return {
        id: track.uri || track.id,
        name: track.title || track.name,
        artist: track.subtitle || track.artist || 'Unknown Artist',
        album: track.albumName || track.album?.name || track.album || 'Unknown Album',
        duration: track.duration,
        coverArt: trackCover,
      };
    });

    return NextResponse.json({ tracks: formattedTracks });
  } catch (error: any) {
    console.error('Error fetching playlist:', error);
    return NextResponse.json({ error: 'Failed to fetch playlist. Ensure the URL is correct and public.' }, { status: 500 });
  }
}
