# 🎵 Spotify Importer & Studio Audio Downloader

<div align="center">

![Next.js](https://img.shields.io/badge/Next.js-15-black?style=for-the-badge&logo=next.js)
![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue?style=for-the-badge&logo=typescript)
![TailwindCSS](https://img.shields.io/badge/Tailwind-3.4-38bdf8?style=for-the-badge&logo=tailwindcss)
![Python](https://img.shields.io/badge/Python-3.13-3776ab?style=for-the-badge&logo=python)
![FFmpeg](https://img.shields.io/badge/FFmpeg-Audio%20Engine-green?style=for-the-badge&logo=ffmpeg)
![Spotify](https://img.shields.io/badge/Spotify-100%25%20Keyless-1db954?style=for-the-badge&logo=spotify)

**A high-performance, keyless Spotify importer that fetches complete playlists (600+ songs with zero caps), downloads genuine studio audio, and embeds 640×640 album artwork and synced lyrics — with zero Spotify Premium or API credentials required.**

[Features](#-key-features) • [How It Works (No API Key)](#-how-it-works-without-any-api-key) • [System Architecture](#-system-design--architecture) • [Database & Data Model](#-database--data-model-design) • [Quick Start](#-quick-start)

---

### 🖥️ Application Preview (Importing 600+ Song Playlist)
<img src="public/assets/playlist-preview.png" alt="Spotify Importer UI Preview" width="100%" style="border-radius: 12px; border: 1px solid rgba(29, 185, 84, 0.4); box-shadow: 0 0 25px rgba(29, 185, 84, 0.2);" />

</div>

---

## ✨ Key Features

- **🚀 100% Keyless & Free:** No Spotify Developer account, no Client ID, no Client Secret, and **zero Spotify Premium** required.
- **📈 Unlimited Playlist Imports (600+ Songs):** Bypasses the traditional 100-song embed limit via internal GraphQL pagination to fetch entire libraries in seconds.
- **🎨 Real Song-Level Artwork (640×640):** Extracts genuine individual track album covers from Spotify's CDN rather than repeating the playlist banner across all songs.
- **🎯 spotDL Matching Algorithm:** Studio recording matching using Dice's coefficient string similarity, exponential duration decay penalties, forbidden word filtering (remix/live/karaoke), and verified artist channel boosts.
- **🎧 High-Quality Multi-Format Encoding:** Download in **MP3 (up to 320 kbps)**, **M4A / AAC**, or **OPUS** powered by `yt-dlp` and `ffmpeg`.
- **🏷️ Automated ID3v2 Tagging:** Writes Song Title, Artist, Album, Release Year, Track Number, and full-resolution Cover Art directly into the downloaded audio file tags.
- **📜 Synced Lyrics Fetching:** Fetches time-synced `.lrc` and plain-text lyrics from public open lyrics databases.
- **💎 Spotify Neon Glow UI:** Modern, immersive dark interface with interactive neon-green glows, batch selection, search filtering, and download folder picker.

---

## 🔓 How It Works (Without Any API Key)

In 2026, Spotify made their official Web API require paid Spotify Premium accounts even for basic Client Credentials access. **This application uses a multi-tier reverse-engineered architecture to stay 100% free and functional:**

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                KEYLESS METADATA ENGINE                                 │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│  1. Anonymous Session Bootstrap                                                       │
│     The engine issues a lightweight handshake to Spotify's public web embed surface    │
│     to generate an ephemeral client token without any login credentials.               │
│                                                                                        │
│  2. Spotify Pathfinder GraphQL Querying                                                │
│     Using Spotify's internal Pathfinder endpoint (api-partner.spotify.com), the engine │
│     calls the `fetchPlaylist` operation with persisted query SHA-256 hashes.           │
│                                                                                        │
│  3. Chunked Offset Pagination (343 Tracks/Page)                                        │
│     Unlike public embeds (capped at 100), the GraphQL partner pipeline paginates in    │
│     large chunks (up to 343 songs/query), fetching a 600+ track playlist in < 10s.     │
│                                                                                        │
│  4. True Song Cover Art Extraction                                                     │
│     For each track, the response includes `albumOfTrack.coverArt.sources`. The engine  │
│     extracts the 640×640 JPEG directly from Spotify's `i.scdn.co` CDN.                 │
│                                                                                        │
│  5. Fallback iTunes Catalog Search                                                     │
│     For general search queries or edge-case missing tracks, the system queries Apple's │
│     public iTunes search endpoint to retrieve 600×600 studio metadata with 0 auth.     │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🏗️ System Design & Architecture

### High-Level Architecture Diagram

```mermaid
graph TD
    Client["🌐 Next.js Frontend (React 19 / Tailwind)"]
    
    subgraph "Next.js 15 Backend (App Router)"
        RoutePlaylist["/api/playlist (Playlist & Metadata)"]
        RouteDownload["/api/download (Audio Engine)"]
        RouteLyrics["/api/lyrics (Lyrics Engine)"]
        RouteCover["/api/cover (Cover Art Resolver)"]
        
        PyBridge["Python Bridge (scripts/fetch_playlist.py)"]
        SpotDLMatcher["SpotDL Matcher (lib/spotdl-matcher.ts)"]
        MetaResolver["Metadata Resolver (lib/metadata-resolver.ts)"]
    end
    
    subgraph "External Keyless Providers"
        SpotifyPartner["Spotify Partner GraphQL (api-partner.spotify.com)"]
        SpotifyEmbed["Spotify Embed Scraper (spotify-url-info)"]
        iTunesCatalog["iTunes Search API (Public / Keyless)"]
        YouTubeSearch["YouTube Search Engine (yt-search)"]
        YTDLP["yt-dlp + FFmpeg Audio Streamer"]
        LRCLIB["LRCLIB Open Lyrics API"]
    end

    %% Flow connections
    Client -->|1. Submit Spotify URL| RoutePlaylist
    RoutePlaylist -->|Extract 600+ Tracks| PyBridge
    PyBridge -->|GraphQL Query with Persisted Hash| SpotifyPartner
    RoutePlaylist -.->|Fallback if needed| SpotifyEmbed
    
    Client -->|2. Request Download| RouteDownload
    RouteDownload -->|Find Best Studio Match| SpotDLMatcher
    SpotDLMatcher -->|Search query| YouTubeSearch
    RouteDownload -->|Stream & Convert Audio| YTDLP
    RouteDownload -->|Embed ID3 & Album Art| MetaResolver
    MetaResolver -.->|Cover Art Fallback| iTunesCatalog
    
    Client -->|3. Fetch Lyrics| RouteLyrics
    RouteLyrics -->|Retrieve Synced .lrc| LRCLIB
    
    RouteDownload -->|4. Return Tagged Audio Blob| Client
```

### Download Execution Flow

```mermaid
sequenceDiagram
    autonumber
    actor User as User (Browser)
    participant API as /api/download
    participant Matcher as spotDL Matcher
    participant YT as YouTube / yt-search
    participant YTDLP as yt-dlp + FFmpeg
    participant ID3 as node-id3 Tagging

    User->>API: POST /api/download (Track Details, Target Quality, Format)
    API->>Matcher: Calculate match candidates for "Artist - Title"
    Matcher->>YT: Search official audio & lyrics videos
    YT-->>Matcher: List of top 10 YouTube candidates
    Note over Matcher: Calculates score: Dice coefficient similarity<br/>- Duration decay penalty<br/>- Forbidden word penalty (remix, cover)<br/>+ Official artist channel boost
    Matcher-->>API: Best matched YouTube Video URL (Score: 85-100%)
    API->>YTDLP: Spawn yt-dlp process to stream audio & pipe to ffmpeg
    YTDLP-->>API: Transcoded MP3/M4A buffer (up to 320kbps)
    API->>API: Download high-res 640x640 album cover buffer
    API->>ID3: Write ID3 tags (Title, Artist, Album, Year, Cover Art)
    ID3-->>API: Tagged audio buffer
    API-->>User: File download stream with metadata headers
```

---

## 🗄️ Database & Data Model Design

The application is engineered as a stateless, high-throughput microservice architecture that can run purely in-memory or bind to a persistent cache/database (e.g. SQLite / Redis / PostgreSQL) for large-scale multi-user production deployments:

### Entity-Relationship Diagram (ERD)

```mermaid
erDiagram
    PLAYLIST ||--o{ PLAYLIST_TRACK : contains
    TRACK ||--o{ PLAYLIST_TRACK : referenced_in
    TRACK ||--o{ DOWNLOAD_JOB : downloads
    TRACK ||--o{ AUDIO_CACHE : cached_as
    USER_SESSION ||--o{ DOWNLOAD_JOB : initiates

    PLAYLIST {
        string id PK "Spotify URI / ID"
        string title "Playlist Name"
        string description "Description"
        string cover_url "Playlist Cover Art URL"
        int total_tracks "Total Count"
        datetime updated_at "Last Scraped Timestamp"
    }

    TRACK {
        string id PK "spotify:track:ID"
        string name "Track Name"
        string artist "Primary & Featured Artists"
        string album "Album Name"
        int duration_ms "Duration in Milliseconds"
        string cover_art_url "640x640 CDN URL"
        string release_date "Release ISO Date"
        boolean is_explicit "Explicit Rating"
    }

    PLAYLIST_TRACK {
        string playlist_id FK
        string track_id FK
        int position "Track Index in Playlist"
        datetime added_at "Added Timestamp"
    }

    DOWNLOAD_JOB {
        string id PK "UUID"
        string session_id FK
        string track_id FK
        string format "mp3 | m4a | opus"
        string quality "best | high | standard"
        string status "idle | queued | downloading | done | error"
        string matched_video_id "YouTube Video ID"
        float match_score "SpotDL Score (0-100)"
        datetime completed_at "Finished Timestamp"
    }

    AUDIO_CACHE {
        string cache_key PK "track_id + format + quality"
        string track_id FK
        string file_path "Local Temp File Path"
        int file_size_bytes "Size on Disk"
        datetime expires_at "TTL Timestamp"
    }
```

### TypeScript Data Contracts

```typescript
// Core Track Interface
export interface Track {
  id: string;              // Spotify URI or synthetic ID
  name: string;            // Song title
  artist: string;          // Formatted artist string
  album: string;           // Album name
  duration?: number;       // Duration in milliseconds
  coverArt?: string;       // 640x640 album artwork URL
  isPlaylistCover?: boolean; // True if artwork is still fallback
  spotifyUri?: string;     // spotify:track:xxx
  status: 'idle' | 'queued' | 'downloading' | 'done' | 'error';
  error?: string;
}

// Full Playlist Result Interface
export interface ScrapedPlaylistResult {
  type: 'playlist';
  title: string;
  coverArt: string | null;
  total: number;
  tracks: Track[];
}
```

---

## 🛠️ Tech Stack & Dependencies

| Layer | Technologies Used |
| :--- | :--- |
| **Frontend** | [Next.js 15](https://nextjs.org/) (App Router), React 19, [TailwindCSS](https://tailwindcss.com/), [Lucide React](https://lucide.dev/) |
| **Backend** | Next.js API Routes (Node.js Edge & Server Runtime) |
| **Scraping Engine** | Python 3 + `spotapi` (Spotify Pathfinder GraphQL persisted query scraper) |
| **Matching Engine** | Custom spotDL port with Dice Coefficient string similarity (`string-similarity`) |
| **Audio Pipeline** | `yt-dlp` binary, `ffmpeg` transcoder, `yt-search` |
| **Tagging Engine** | `node-id3` (ID3v2 metadata & APIC picture embedding) |
| **Lyrics Engine** | LRCLIB public API (synced & plain text) |

---

## 🚀 Quick Start

### 1. Prerequisites
- **Node.js**: v18.18+ or v20+
- **Python**: 3.10+ (installed and available on your `PATH`)
- **FFmpeg**: installed and added to system `PATH` (used by `yt-dlp` for audio conversion)

### 2. Install Dependencies
```bash
# Clone the repository
git clone https://github.com/Gemy21/Spotify_Importer.git
cd Spotify_Importer

# Install Node.js dependencies
npm install

# Install Python scraping requirements
python -m pip install spotapi spotipyFree
```

### 3. Run Development Server
```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

---

## 📖 Usage Guide

1. **Import Any Link:**
   - Paste any Spotify Playlist URL (e.g. `https://open.spotify.com/playlist/...`)
   - Paste an Album URL or Track URL
   - Or type a song query directly (e.g. `RealestK Bad`) to search
2. **Hit "Fetch":**
   - The engine automatically retrieves all tracks (even playlists with 600+ songs) along with their genuine 640×640 album covers.
3. **Select & Download:**
   - Click **Select All** or pick individual songs.
   - Click **Download** on any track to download directly.
   - Click **Download Selected** to batch-download all checked songs directly to your chosen folder using the File System Access API.

---

## 📄 License
MIT License. Built for educational and personal use. All music rights belong to their respective copyright holders.
