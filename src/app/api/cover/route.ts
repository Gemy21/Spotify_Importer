import { NextResponse } from 'next/server';
import { resolveRealSongCover } from '@/lib/metadata-resolver';

export async function POST(req: Request) {
  try {
    const body = await req.json();

    // Support batch resolution for playlist tracks
    if (Array.isArray(body.tracks)) {
      const results = await Promise.all(
        body.tracks.slice(0, 20).map(async (t: any) => {
          const realCover = await resolveRealSongCover({
            name: t.name,
            artist: t.artist,
            spotifyUri: t.spotifyUri || t.id,
            coverUrl: t.coverUrl,
            isPlaylistCover: Boolean(t.isPlaylistCover),
          });
          return { id: t.id, coverUrl: realCover };
        })
      );
      return NextResponse.json({ results });
    }

    const { name, artist, spotifyUri, coverUrl, isPlaylistCover } = body;

    if (!name || !artist) {
      return NextResponse.json({ error: 'Name and artist are required' }, { status: 400 });
    }

    const realCover = await resolveRealSongCover({
      name,
      artist,
      spotifyUri,
      coverUrl,
      isPlaylistCover: Boolean(isPlaylistCover),
    });

    return NextResponse.json({ coverUrl: realCover });
  } catch (error: any) {
    console.error('Cover resolve error:', error);
    return NextResponse.json({ error: error?.message || 'Failed to resolve cover' }, { status: 500 });
  }
}
