import { NextResponse } from 'next/server';

export async function POST(req: Request) {
  try {
    const { name, artist } = await req.json();

    if (!name || !artist) {
      return NextResponse.json({ error: 'Name and artist are required' }, { status: 400 });
    }

    // 1. Try LRCLIB direct lookup
    try {
      const directUrl = `https://lrclib.net/api/get?artist_name=${encodeURIComponent(artist)}&track_name=${encodeURIComponent(name)}`;
      const directRes = await fetch(directUrl);
      if (directRes.ok) {
        const data = await directRes.json();
        if (data.plainLyrics) {
          return NextResponse.json({ lyrics: data.plainLyrics });
        }
        if (data.syncedLyrics) {
          // Strip timestamp tags from synced lyrics for plain text
          const plain = data.syncedLyrics.replace(/\[\d{2}:\d{2}\.\d{2,3}\]\s?/g, '');
          return NextResponse.json({ lyrics: plain });
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
          if (match.plainLyrics) {
            return NextResponse.json({ lyrics: match.plainLyrics });
          }
          if (match.syncedLyrics) {
            const plain = match.syncedLyrics.replace(/\[\d{2}:\d{2}\.\d{2,3}\]\s?/g, '');
            return NextResponse.json({ lyrics: plain });
          }
        }
      }
    } catch (_) { /* fall through */ }

    return NextResponse.json({ lyrics: 'Lyrics not found.' });
  } catch (error: any) {
    console.error('Lyrics error:', error);
    return NextResponse.json({ lyrics: 'Failed to fetch lyrics.' });
  }
}
