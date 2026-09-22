/**
 * Download API route — finds the best YouTube match and streams audio.
 * Replaces yt-dlp binary with @distube/ytdl-core for Android compatibility.
 * Audio is written to a temp file then returned as base64 for the React Native layer to save.
 *
 * Port of src/app/api/download/route.ts for embedded Node.js server.
 */

const express = require('express');
const router = express.Router();
const fs = require('fs');
const fsPromises = require('fs/promises');
const path = require('path');
const os = require('os');
const ytSearch = require('yt-search');
const ytdl = require('@distube/ytdl-core');
const NodeID3 = require('node-id3');
const fetch = require('node-fetch');
const { selectBestCandidate, buildSearchQueries } = require('../lib/spotdl-matcher');
const { resolveRealSongCover } = require('../lib/spotify-graphql');

const FORMAT_EXTENSIONS = { mp3: '.mp3', m4a: '.m4a', opus: '.opus' };
const FORMAT_MIME = { mp3: 'audio/mpeg', m4a: 'audio/mp4', opus: 'audio/ogg' };

// ytdl-core quality mapping
// ytdl-core uses audioQuality: 'highestaudio' | 'lowestaudio'
// For Android we map quality levels to ytdl-core filter options
const QUALITY_FILTER = {
  best: 'highestaudio',
  high: 'highestaudio',
  standard: 'lowestaudio',
  low: 'lowestaudio',
};

/**
 * Streams audio from a YouTube URL to a temp file using ytdl-core.
 * Returns the file path when done.
 */
function streamAudioToFile(videoUrl, outputPath, quality) {
  return new Promise((resolve, reject) => {
    const filter = QUALITY_FILTER[quality] || 'highestaudio';

    const stream = ytdl(videoUrl, {
      quality: filter,
      filter: 'audioonly',
      requestOptions: {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        },
      },
    });

    const writeStream = fs.createWriteStream(outputPath);

    stream.on('error', (err) => {
      writeStream.destroy();
      reject(new Error(`ytdl stream error: ${err.message}`));
    });

    writeStream.on('error', (err) => {
      reject(new Error(`File write error: ${err.message}`));
    });

    writeStream.on('finish', () => {
      resolve(outputPath);
    });

    stream.pipe(writeStream);
  });
}

router.post('/', async (req, res) => {
  try {
    const {
      name,
      artist,
      album,
      duration,
      coverUrl,
      isPlaylistCover,
      spotifyUri,
      source = 'youtube',
      format = 'mp3',
      quality = 'best',
    } = req.body;

    if (!name || !artist) {
      return res.status(400).json({ error: 'Name and artist are required' });
    }

    const audioFormat = FORMAT_EXTENSIONS[format] ? format : 'mp3';
    const ext = FORMAT_EXTENSIONS[audioFormat];
    const mime = FORMAT_MIME[audioFormat];

    // ── YouTube search using SpotDL matcher ─────────────────────────────
    const searchQueries = buildSearchQueries(artist, name, source);
    let allVideos = [];

    for (const query of searchQueries) {
      try {
        const searchResult = await ytSearch(query);
        if (searchResult?.videos?.length) {
          allVideos = allVideos.concat(searchResult.videos);
        }
      } catch (searchErr) {
        console.warn(`[API/download] Search error for "${query}":`, searchErr.message);
      }
    }

    const durationSec = duration
      ? (duration > 1000 ? Math.round(duration / 1000) : Math.round(duration))
      : undefined;

    const bestVideo = selectBestCandidate(allVideos, { name, artist, album, durationSec });

    if (!bestVideo || !bestVideo.url) {
      return res.status(404).json({ error: 'Song not found on YouTube' });
    }

    console.log(`[SpotDL Match] "${name} - ${artist}" -> "${bestVideo.title}" [${bestVideo.url}]`);

    // ── Stream audio to temp file ────────────────────────────────────────
    const safeFileName = `${name.replace(/[\\/:*?"<>|]/g, '')}_${Date.now()}`;
    const tmpFilePath = path.join(os.tmpdir(), `${safeFileName}${ext}`);

    await streamAudioToFile(bestVideo.url, tmpFilePath, quality);

    // ── Resolve real cover art ───────────────────────────────────────────
    const targetCover = await resolveRealSongCover({
      name,
      artist,
      spotifyUri,
      coverUrl,
      isPlaylistCover: Boolean(isPlaylistCover),
    });

    // ── ID3 metadata tagging (MP3 only) ──────────────────────────────────
    if (audioFormat === 'mp3') {
      try {
        let imageBuffer = null;

        if (targetCover) {
          try {
            const imgRes = await fetch(targetCover, {
              headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
            });
            if (imgRes.ok) {
              const arrayBuffer = await imgRes.arrayBuffer();
              imageBuffer = Buffer.from(arrayBuffer);
            }
          } catch (imgErr) {
            console.warn('[API/download] Failed to fetch cover image:', imgErr.message);
          }
        }

        const tags = {
          title: name,
          artist: artist,
          album: album || name,
        };

        if (imageBuffer) {
          tags.image = {
            mime: 'image/jpeg',
            type: { id: 3, name: 'front cover' },
            description: 'Cover',
            imageBuffer,
          };
        }

        NodeID3.write(tags, tmpFilePath);
        console.log(`[API/download] ID3 tags written for "${name}"`);
      } catch (tagErr) {
        console.error('[API/download] ID3 tagging error:', tagErr.message);
      }
    }

    // ── Read file and return as base64 ───────────────────────────────────
    // The React Native layer will decode this and save to MediaStore/Music folder
    const fileBuffer = await fsPromises.readFile(tmpFilePath);
    const base64Audio = fileBuffer.toString('base64');

    // Clean up temp file
    fsPromises.unlink(tmpFilePath).catch(err =>
      console.error('[API/download] Failed to delete temp file:', err.message)
    );

    const safeResponseName = `${name} - ${artist}${ext}`;

    return res.json({
      success: true,
      fileName: safeResponseName,
      mimeType: mime,
      data: base64Audio,
      coverUrl: targetCover || null,
      matchedTitle: bestVideo.title,
      matchedUrl: bestVideo.url,
    });
  } catch (error) {
    console.error('[API/download] Error:', error);
    res.status(500).json({ error: error?.message || 'Failed to download the song' });
  }
});

module.exports = router;
