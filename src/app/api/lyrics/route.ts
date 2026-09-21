import { NextResponse } from 'next/server';

export async function POST(req: Request) {
  try {
    const { name, artist, format = 'plain' } = await req.json();

    if (!name || !artist) {
      return NextResponse.json({ error: 'Name and artist are required' }, { status: 400 });
    }

    const wantSynced = format === 'synced';

    // 1. Try LRCLIB direct lookup
    try {
      const directUrl = `https://lrclib.net/api/get?artist_name=${encodeURIComponent(artist)}&track_name=${encodeURIComponent(name)}`;
      const directRes = await fetch(directUrl);
      if (directRes.ok) {
        const data = await directRes.json();

        if (wantSynced && data.syncedLyrics) {
          return NextResponse.json({
            lyrics: data.syncedLyrics,
            format: 'synced',
            extension: '.lrc',
          });
        }

        if (data.plainLyrics) {
          return NextResponse.json({
            lyrics: data.plainLyrics,
            format: 'plain',
            extension: '.txt',
          });
        }

        if (data.syncedLyrics) {
          if (wantSynced) {
            return NextResponse.json({
              lyrics: data.syncedLyrics,
              format: 'synced',
              extension: '.lrc',
            });
          }
          // Strip timestamps for plain text fallback
          const plain = data.syncedLyrics.replace(/\[\d{2}:\d{2}\.\d{2,3}\]\s?/g, '');
          return NextResponse.json({
            lyrics: plain,
            format: 'plain',
            extension: '.txt',
          });
        }
      }
    } catch (_) { /* fall through */ }

    // 2. Fallback: LRCLIB search
    try {
      const searchUrl = `https://lrclib.net/api/search?q=${encodeURIComponent(artist + ' ' + name)}`;
      const searchRes = await fetch(searchUrl);
      if (searchRes.ok) {
        const results = await searchRes.json();
        if (Array.isArray(results) && results.length > 0) {
          const match = results[0];

          if (wantSynced && match.syncedLyrics) {
            return NextResponse.json({
              lyrics: match.syncedLyrics,
              format: 'synced',
              extension: '.lrc',
            });
          }

          if (match.plainLyrics) {
            return NextResponse.json({
              lyrics: match.plainLyrics,
              format: 'plain',
              extension: '.txt',
            });
          }

          if (match.syncedLyrics) {
            if (wantSynced) {
              return NextResponse.json({
                lyrics: match.syncedLyrics,
                format: 'synced',
                extension: '.lrc',
              });
            }
            const plain = match.syncedLyrics.replace(/\[\d{2}:\d{2}\.\d{2,3}\]\s?/g, '');
            return NextResponse.json({
              lyrics: plain,
              format: 'plain',
              extension: '.txt',
            });
          }
        }
      }
    } catch (_) { /* fall through */ }

    return NextResponse.json({
      lyrics: 'Lyrics not found.',
      format: 'plain',
      extension: '.txt',
    });
  } catch (error: any) {
    console.error('Lyrics error:', error);
    return NextResponse.json({
      lyrics: 'Failed to fetch lyrics.',
      format: 'plain',
      extension: '.txt',
    });
  }
}
