import { NextResponse } from 'next/server';
import ytSearch from 'yt-search';
import { create } from 'youtube-dl-exec';
import NodeID3 from 'node-id3';
import os from 'os';
import path from 'path';
import fs from 'fs/promises';
import { existsSync } from 'fs';
import {
  selectBestCandidate,
  buildSearchQueries,
  CandidateVideo,
} from '@/lib/spotdl-matcher';
import { resolveRealSongCover } from '@/lib/metadata-resolver';

// Resolve binary paths relative to process.cwd() for Next.js compatibility
const isWin = process.platform === 'win32';
const ytDlpBinary = isWin ? 'yt-dlp.exe' : 'yt-dlp';
const ffmpegBinary = isWin ? 'ffmpeg.exe' : 'ffmpeg';

const ytDlpPath = path.join(process.cwd(), 'node_modules', 'youtube-dl-exec', 'bin', ytDlpBinary);
const ffmpegPath = path.join(process.cwd(), 'node_modules', 'ffmpeg-static', ffmpegBinary);

const youtubedl = create(ytDlpPath);

// ── SoundCloud search via soundcloud-scraper ────────────────────────────
async function searchSoundCloud(name: string, artist: string): Promise<string | null> {
  try {
    const { default: SoundCloud } = await import('soundcloud-scraper');
    const client = new SoundCloud.Client();
    const query = artist ? `${artist} ${name}` : name;
    const results = await client.search(query, 'track');

    if (results && Array.isArray(results) && results.length > 0) {
      const cleanName = name.toLowerCase();
      const cleanArtist = artist.toLowerCase();

      for (const result of results as any[]) {
        const title = ((result as any).name || (result as any).title || '').toLowerCase();
        const resultArtist = ((result as any).author?.name || (result as any).user?.username || '').toLowerCase();
        if (title.includes(cleanName) || resultArtist.includes(cleanArtist)) {
          return result.url;
        }
      }
      return results[0].url;
    }
  } catch (err) {
    console.error('SoundCloud search error:', err);
  }
  return null;
}

// ── Audio format file extension mapping ────────────────────────────────
const FORMAT_EXTENSIONS: Record<string, string> = {
  mp3: '.mp3',
  m4a: '.m4a',
  opus: '.opus',
};

const FORMAT_MIME: Record<string, string> = {
  mp3: 'audio/mpeg',
  m4a: 'audio/mp4',
  opus: 'audio/ogg',
};

// ── Quality to yt-dlp audio-quality mapping ────────────────────────────
// yt-dlp uses 0 (best) to 10 (worst) for VBR quality
const QUALITY_MAP: Record<string, string> = {
  best: '0',       // ~320kbps
  high: '2',       // ~256kbps
  standard: '5',   // ~192kbps
  low: '7',        // ~128kbps
};

export async function POST(req: Request) {
  try {
    const {
      name,
      artist,
      album,
      duration,
      coverUrl,
      isPlaylistCover,
      spotifyUri,
      source = 'youtube',        // youtube | youtube-music | soundcloud
      format = 'mp3',            // mp3 | m4a | opus
      quality = 'best',          // best | high | standard | low
    } = await req.json();

    if (!name || !artist) {
      return NextResponse.json({ error: 'Name and artist are required' }, { status: 400 });
    }

    const audioFormat = FORMAT_EXTENSIONS[format] ? format : 'mp3';
    const audioQuality = QUALITY_MAP[quality] || '0';
    const ext = FORMAT_EXTENSIONS[audioFormat];
    const mime = FORMAT_MIME[audioFormat];

    let downloadUrl: string | null = null;
    let selectedVideoDetails: CandidateVideo | null = null;

    // ── Source: SoundCloud ──────────────────────────────────────────────
    if (source === 'soundcloud') {
      const scUrl = await searchSoundCloud(name, artist);
      if (scUrl) {
        downloadUrl = scUrl;
      }
    }

    // ── Source: YouTube / YouTube Music (SpotDL Matcher) ─────────────────
    if (!downloadUrl) {
      const searchQueries = buildSearchQueries(artist, name, source);
      let allVideos: CandidateVideo[] = [];

      for (const query of searchQueries) {
        try {
          const searchResult = await ytSearch(query);
          if (searchResult?.videos?.length) {
            allVideos = allVideos.concat(searchResult.videos);
          }
        } catch (searchErr) {
          console.warn(`Search error for query "${query}":`, searchErr);
        }
      }

      // Convert duration in ms to seconds if needed
      const durationSec = duration
        ? (duration > 1000 ? Math.round(duration / 1000) : Math.round(duration))
        : undefined;

      const bestVideo = selectBestCandidate(allVideos, {
        name,
        artist,
        album,
        durationSec,
      });

      if (!bestVideo || !bestVideo.url) {
        return NextResponse.json(
          { error: `Song not found on ${source === 'soundcloud' ? 'SoundCloud or YouTube' : 'YouTube'}` },
          { status: 404 }
        );
      }

      downloadUrl = bestVideo.url;
      selectedVideoDetails = bestVideo;
      console.log(`[SpotDL Match] Matched "${name} - ${artist}" -> "${bestVideo.title}" (${bestVideo.seconds}s) [${bestVideo.url}]`);
    }

    // ── Download & convert with yt-dlp ─────────────────────────────────
    const tmpDir = os.tmpdir();
    const fileName = `${name.replace(/[\\/:*?"<>|]/g, '')}_${Date.now()}`;
    const tmpFilePath = path.join(tmpDir, `${fileName}${ext}`);

    const ytDlpOptions: any = {
      extractAudio: true,
      audioFormat: audioFormat,
      audioQuality: audioQuality,
      noCheckCertificates: true,
      noWarnings: true,
      preferFreeFormats: true,
      output: tmpFilePath,
      addHeader: ['referer:youtube.com', 'user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64)'],
    };

    if (existsSync(ffmpegPath)) {
      ytDlpOptions.ffmpegLocation = ffmpegPath;
    }

    await youtubedl(downloadUrl!, ytDlpOptions);

    // ── Resolve Real Song Picture (Spotify single vs Playlist search) ────
    // If it's from a playlist, actively resolve the real song cover art
    const targetCover = await resolveRealSongCover({
      name,
      artist,
      spotifyUri,
      coverUrl,
      isPlaylistCover: Boolean(isPlaylistCover),
    });

    // ── ID3 metadata & high-resolution cover tagging (MP3 only) ─────────
    if (audioFormat === 'mp3') {
      try {
        let imageBuffer: Buffer | null = null;

        if (targetCover) {
          try {
            const imgRes = await fetch(targetCover, {
              headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
              },
            });
            if (imgRes.ok) {
              imageBuffer = Buffer.from(await imgRes.arrayBuffer());
            }
          } catch (imgErr) {
            console.warn('Failed to fetch cover image:', imgErr);
          }
        }

        const tags: NodeID3.Tags = {
          title: name,
          artist: artist,
          album: album || name,
        };

        if (imageBuffer) {
          tags.image = {
            mime: 'image/jpeg',
            type: { id: 3, name: 'front cover' },
            description: 'Cover',
            imageBuffer: imageBuffer,
          };
        }

        NodeID3.write(tags, tmpFilePath);
      } catch (tagErr) {
        console.error('ID3 tagging error:', tagErr);
      }
    }

    // ── Read file and respond ──────────────────────────────────────────
    const fileBuffer = await fs.readFile(tmpFilePath);

    // Clean up temp file asynchronously
    fs.unlink(tmpFilePath).catch((err) => console.error('Failed to delete temp file:', err));

    const safeFileName = `${encodeURIComponent(name)} - ${encodeURIComponent(artist)}${ext}`;
    const responseHeaders = new Headers({
      'Content-Type': mime,
      'Content-Disposition': `attachment; filename="${safeFileName}"`,
      'Content-Length': fileBuffer.byteLength.toString(),
      'x-real-cover': encodeURIComponent(targetCover || ''),
    });

    return new NextResponse(fileBuffer, {
      headers: responseHeaders,
    });
  } catch (error: any) {
    console.error('Download error:', error);
    return NextResponse.json({ error: error?.message || 'Failed to download the song' }, { status: 500 });
  }
}
