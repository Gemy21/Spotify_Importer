/**
 * Embedded Express server for Spotify Importer Android App.
 * Runs 100% locally on the device (localhost:3001).
 * Serves both the compiled Next.js Web UI and the keyless music API.
 */

const express = require('express');
const cors = require('cors');
const path = require('path');

const playlistRouter = require('./api/playlist');
const downloadRouter = require('./api/download');
const lyricsRouter = require('./api/lyrics');
const coverRouter = require('./api/cover');

const app = express();
const PORT = 3001;

// Middleware
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: Date.now() });
});

// API Routes
app.use('/api/playlist', playlistRouter);
app.use('/api/download', downloadRouter);
app.use('/api/lyrics', lyricsRouter);
app.use('/api/cover', coverRouter);

// Serve the compiled Next.js Web UI
const publicDir = path.join(__dirname, 'public');
app.use(express.static(publicDir));

// Fallback to index.html for client-side routing
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api/')) return next();
  res.sendFile(path.join(publicDir, 'index.html'), (err) => {
    if (err) {
      // If public build not ready yet, return friendly message
      res.status(200).send(`
        <!DOCTYPE html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              body { background: #0a0a0a; color: #fff; font-family: sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }
              .logo { color: #1ed760; font-size: 24px; font-weight: bold; margin-bottom: 12px; }
            </style>
          </head>
          <body>
            <div>
              <div class="logo">🎵 Spotify Importer</div>
              <p>Engine is running on your device.</p>
            </div>
          </body>
        </html>
      `);
    }
  });
});

// Error handler
app.use((err, req, res, next) => {
  console.error('[Server Error]', err);
  res.status(500).json({ error: err.message || 'Internal server error' });
});

app.listen(PORT, '127.0.0.1', () => {
  console.log(`[SpotifyImporter] Embedded local server running on http://127.0.0.1:${PORT}`);
});

module.exports = app;
