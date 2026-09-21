import { execFile } from 'child_process';
import path from 'path';

export interface ScrapedTrack {
  id: string;
  name: string;
  artist: string;
  album: string;
  duration?: number;
  coverArt?: string | null;
  isPlaylistCover?: boolean;
  spotifyUri?: string;
}

export interface ScrapedPlaylistResult {
  type: string;
  title: string;
  coverArt: string | null;
  total: number;
  tracks: ScrapedTrack[];
}

/**
 * Extracts a Spotify playlist ID from a URL or URI string.
 */
export function extractPlaylistId(urlOrUri: string): string | null {
  const cleaned = urlOrUri.trim();
  const uriMatch = cleaned.match(/spotify:playlist:([a-zA-Z0-9]+)/);
  if (uriMatch) return uriMatch[1];

  const urlMatch = cleaned.match(/playlist\/([a-zA-Z0-9]+)/);
  if (urlMatch) return urlMatch[1];

  if (/^[a-zA-Z0-9]{15,30}$/.test(cleaned)) {
    return cleaned;
  }

  return null;
}

/**
 * Fetches all tracks for a Spotify playlist using the SpotAPI python engine.
 * Supports playlists with 600+ or thousands of tracks, with no 100-song cap.
 * Retrieves genuine song-level album cover art for every track directly.
 */
export async function fetchAllPlaylistTracks(urlOrId: string): Promise<ScrapedPlaylistResult> {
  const playlistId = extractPlaylistId(urlOrId);
  if (!playlistId) {
    throw new Error(`Invalid playlist URL or ID: ${urlOrId}`);
  }

  const scriptPath = path.join(process.cwd(), 'scripts', 'fetch_playlist.py');

  return new Promise((resolve, reject) => {
    execFile(
      'python',
      [scriptPath, playlistId],
      {
        maxBuffer: 64 * 1024 * 1024, // 64MB buffer for large playlists
        timeout: 60000, // 60s timeout
      },
      (error, stdout, stderr) => {
        if (error) {
          console.error('[SpotifyScraper] Python execution error:', error, stderr);
          return reject(new Error(`Failed to scrape playlist: ${error.message}`));
        }

        try {
          const parsed = JSON.parse(stdout.trim());
          if (parsed.error) {
            return reject(new Error(parsed.error));
          }
          resolve(parsed);
        } catch (parseErr: any) {
          console.error('[SpotifyScraper] Failed to parse JSON output:', parseErr, stdout.slice(0, 300));
          reject(new Error(`Invalid JSON output from playlist fetcher: ${parseErr.message}`));
        }
      }
    );
  });
}
