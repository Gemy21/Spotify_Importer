/**
 * Cover art API route — resolves real track cover art.
 * Port of src/app/api/cover/route.ts for embedded Node.js server.
 */

const express = require('express');
const router = express.Router();
const { resolveRealSongCover } = require('../lib/spotify-graphql');

router.post('/', async (req, res) => {
  try {
    const body = req.body;

    // Batch resolution for playlist tracks
    if (Array.isArray(body.tracks)) {
      const results = await Promise.all(
        body.tracks.slice(0, 20).map(async (t) => {
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
      return res.json({ results });
    }

    const { name, artist, spotifyUri, coverUrl, isPlaylistCover } = body;

    if (!name || !artist) {
      return res.status(400).json({ error: 'Name and artist are required' });
    }

    const realCover = await resolveRealSongCover({
      name,
      artist,
      spotifyUri,
      coverUrl,
      isPlaylistCover: Boolean(isPlaylistCover),
    });

    return res.json({ coverUrl: realCover });
  } catch (error) {
    console.error('[API/cover] Error:', error);
    res.status(500).json({ error: error?.message || 'Failed to resolve cover' });
  }
});

module.exports = router;
