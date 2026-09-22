/**
 * Lyrics API route — fetches synced/plain lyrics from LRCLIB.
 * Port of src/app/api/lyrics/route.ts for embedded Node.js server.
 */

const express = require('express');
const router = express.Router();
const fetch = require('node-fetch');

router.post('/', async (req, res) => {
  try {
    const { name, artist, format = 'plain' } = req.body;

    if (!name || !artist) {
      return res.status(400).json({ error: 'Name and artist are required' });
    }

    const wantSynced = format === 'synced';

    // 1. Try LRCLIB direct lookup
    try {
      const directUrl = `https://lrclib.net/api/get?artist_name=${encodeURIComponent(artist)}&track_name=${encodeURIComponent(name)}`;
      const directRes = await fetch(directUrl);
      if (directRes.ok) {
        const data = await directRes.json();

        if (wantSynced && data.syncedLyrics) {
          return res.json({ lyrics: data.syncedLyrics, format: 'synced', extension: '.lrc' });
        }
        if (data.plainLyrics) {
          return res.json({ lyrics: data.plainLyrics, format: 'plain', extension: '.txt' });
        }
        if (data.syncedLyrics) {
          if (wantSynced) {
            return res.json({ lyrics: data.syncedLyrics, format: 'synced', extension: '.lrc' });
          }
          const plain = data.syncedLyrics.replace(/\[\d{2}:\d{2}\.\d{2,3}\]\s?/g, '');
          return res.json({ lyrics: plain, format: 'plain', extension: '.txt' });
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
            return res.json({ lyrics: match.syncedLyrics, format: 'synced', extension: '.lrc' });
          }
          if (match.plainLyrics) {
            return res.json({ lyrics: match.plainLyrics, format: 'plain', extension: '.txt' });
          }
          if (match.syncedLyrics) {
            if (wantSynced) {
              return res.json({ lyrics: match.syncedLyrics, format: 'synced', extension: '.lrc' });
            }
            const plain = match.syncedLyrics.replace(/\[\d{2}:\d{2}\.\d{2,3}\]\s?/g, '');
            return res.json({ lyrics: plain, format: 'plain', extension: '.txt' });
          }
        }
      }
    } catch (_) { /* fall through */ }

    return res.json({ lyrics: 'Lyrics not found.', format: 'plain', extension: '.txt' });
  } catch (error) {
    console.error('[API/lyrics] Error:', error);
    res.json({ lyrics: 'Failed to fetch lyrics.', format: 'plain', extension: '.txt' });
  }
});

module.exports = router;
