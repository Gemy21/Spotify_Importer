import { NextResponse } from 'next/server';
import ytSearch from 'yt-search';
import { create } from 'youtube-dl-exec';
import NodeID3 from 'node-id3';
import os from 'os';
import path from 'path';
import fs from 'fs/promises';
import { existsSync } from 'fs';

// Resolve binary paths relative to process.cwd() for Next.js compatibility
const isWin = process.platform === 'win32';
const ytDlpBinary = isWin ? 'yt-dlp.exe' : 'yt-dlp';
const ffmpegBinary = isWin ? 'ffmpeg.exe' : 'ffmpeg';

const ytDlpPath = path.join(process.cwd(), 'node_modules', 'youtube-dl-exec', 'bin', ytDlpBinary);
const ffmpegPath = path.join(process.cwd(), 'node_modules', 'ffmpeg-static', ffmpegBinary);

const youtubedl = create(ytDlpPath);

function selectBestVideo(videos: any[], trackName: string, artistName: string) {
  if (!videos || videos.length === 0) return null;
  
  const cleanTrackName = trackName.toLowerCase();
  const cleanArtistName = artistName.toLowerCase();

  const unwantedKeywords = ['cover', 'live', 'remix', 'reverb', 'sped up', 'slowed', '8d', 'instrumental', 'karaoke'];
  
  const candidates = videos.filter(v => {
    const title = v.title.toLowerCase();
    for (const kw of unwantedKeywords) {
      if (title.includes(kw) && !cleanTrackName.includes(kw)) {
        return false;
      }
    }
    return true;
  });

  const pool = candidates.length > 0 ? candidates : videos;

  let best = pool[0];
  let bestScore = -1;

  for (const v of pool) {
    let score = 0;
    const title = v.title.toLowerCase();
    const author = (v.author?.name || '').toLowerCase();

    if (author.includes(cleanArtistName) || author.includes('- topic')) score += 50;
    if (title.includes('official') || title.includes('audio') || title.includes('topic')) score += 30;
    if (title.includes(cleanTrackName)) score += 20;

    if (v.views) {
      score += Math.log10(v.views) * 5;
    }

    if (score > bestScore) {
      bestScore = score;
      best = v;
    }
  }

  return best;
}

export async function POST(req: Request) {
  try {
    const { name, artist, album, coverUrl } = await req.json();

    if (!name || !artist) {
      return NextResponse.json({ error: 'Name and artist are required' }, { status: 400 });
    }

    // 1. Search YouTube for the song with multiple smart queries
    const searchQueries = [
      `${artist} ${name} official audio`,
      `${artist} ${name} topic`,
      `${artist} ${name}`
    ];
    
    let allVideos: any[] = [];
    for (const query of searchQueries) {
      const searchResult = await ytSearch(query);
      if (searchResult?.videos?.length) {
        allVideos = allVideos.concat(searchResult.videos);
      }
    }

    const video = selectBestVideo(allVideos, name, artist);
    
    if (!video || !video.url) {
      return NextResponse.json({ error: 'Song not found on YouTube' }, { status: 404 });
    }

    // 2. Download and convert to MP3 at maximum audio quality (VBR 0 ~320kbps)
    const tmpDir = os.tmpdir();
    const fileName = `${name.replace(/[\\/:*?"<>|]/g, '')}_${Date.now()}`;
    const tmpFilePath = path.join(tmpDir, `${fileName}.mp3`);
    
    const ytDlpOptions: any = {
      extractAudio: true,
      audioFormat: 'mp3',
      audioQuality: '0', // 0 = Best VBR audio quality
      noCheckCertificates: true,
      noWarnings: true,
      preferFreeFormats: true,
      output: tmpFilePath,
      addHeader: ['referer:youtube.com', 'user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64)']
    };
    
    if (existsSync(ffmpegPath)) {
      ytDlpOptions.ffmpegLocation = ffmpegPath;
    }

    await youtubedl(video.url, ytDlpOptions);

    // 3. Write ID3 Metadata & Album Cover Image using node-id3
    try {
      let imageBuffer: Buffer | null = null;
      const targetCover = coverUrl || video.thumbnail;
      
      if (targetCover) {
        try {
          const imgRes = await fetch(targetCover);
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

    // 4. Read tagged file into buffer
    const fileBuffer = await fs.readFile(tmpFilePath);
    
    // Clean up temp file
    fs.unlink(tmpFilePath).catch(err => console.error("Failed to delete temp file:", err));

    // 5. Return MP3 file to client
    const responseHeaders = new Headers({
      'Content-Type': 'audio/mpeg',
      'Content-Disposition': `attachment; filename="${encodeURIComponent(name)} - ${encodeURIComponent(artist)}.mp3"`,
      'Content-Length': fileBuffer.byteLength.toString(),
    });

    return new NextResponse(fileBuffer, {
      headers: responseHeaders,
    });
  } catch (error: any) {
    console.error('Download error:', error);
    return NextResponse.json({ error: error?.message || 'Failed to download the song' }, { status: 500 });
  }
}


