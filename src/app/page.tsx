'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Search, Loader2, Music, Download, Settings, X,
  Disc3, ListMusic, User, AudioLines,
  CheckCircle2, AlertCircle, Clock, Filter,
} from 'lucide-react';

// ── Types ──────────────────────────────────────────────────────────────

type AudioSource = 'youtube' | 'youtube-music' | 'soundcloud';
type AudioFormat = 'mp3' | 'm4a' | 'opus';
type AudioQuality = 'best' | 'high' | 'standard' | 'low';
type LyricsFormat = 'plain' | 'synced';
type UrlType = 'track' | 'album' | 'playlist' | 'artist' | 'search';
type TrackStatus = 'idle' | 'queued' | 'downloading' | 'done' | 'error';

interface Track {
  id: string;
  name: string;
  artist: string;
  album: string;
  duration?: number;
  coverArt?: string;
  isPlaylistCover?: boolean;
  spotifyUri?: string;
  status: TrackStatus;
  error?: string;
}

interface AppSettings {
  source: AudioSource;
  format: AudioFormat;
  quality: AudioQuality;
  lyricsFormat: LyricsFormat;
}

const DEFAULT_SETTINGS: AppSettings = {
  source: 'youtube',
  format: 'mp3',
  quality: 'best',
  lyricsFormat: 'plain',
};

// ── Helpers ────────────────────────────────────────────────────────────

function detectUrlType(input: string): UrlType {
  const lower = input.toLowerCase().trim();
  if (lower.includes('open.spotify.com/track/') || lower.includes('spotify:track:')) return 'track';
  if (lower.includes('open.spotify.com/album/') || lower.includes('spotify:album:')) return 'album';
  if (lower.includes('open.spotify.com/playlist/') || lower.includes('spotify:playlist:')) return 'playlist';
  if (lower.includes('open.spotify.com/artist/') || lower.includes('spotify:artist:')) return 'artist';
  if (lower.startsWith('http://') || lower.startsWith('https://')) return 'playlist';
  return 'search';
}

const URL_TYPE_CONFIG: Record<UrlType, { label: string; icon: any; color: string }> = {
  track: { label: 'Track', icon: Music, color: 'text-[#1ed760] bg-[#1db954]/15 border-[#1db954]/40 shadow-[0_0_12px_rgba(29,185,84,0.3)]' },
  album: { label: 'Album', icon: Disc3, color: 'text-purple-400 bg-purple-500/15 border-purple-500/30' },
  playlist: { label: 'Playlist', icon: ListMusic, color: 'text-[#1ed760] bg-[#1db954]/15 border-[#1db954]/40 shadow-[0_0_12px_rgba(29,185,84,0.3)]' },
  artist: { label: 'Artist', icon: User, color: 'text-amber-400 bg-amber-500/15 border-amber-500/30' },
  search: { label: 'Search', icon: Search, color: 'text-cyan-400 bg-cyan-500/15 border-cyan-500/30' },
};

const SOURCE_OPTIONS: { value: AudioSource; label: string; description: string }[] = [
  { value: 'youtube', label: 'YouTube', description: 'Best for popular tracks & official audio' },
  { value: 'youtube-music', label: 'YouTube Music', description: 'SpotDL accuracy with topic studio tracks' },
  { value: 'soundcloud', label: 'SoundCloud', description: 'Great for indie & remix tracks' },
];

const FORMAT_OPTIONS: { value: AudioFormat; label: string; description: string }[] = [
  { value: 'mp3', label: 'MP3', description: 'Universal compatibility + ID3 cover tagging' },
  { value: 'm4a', label: 'M4A', description: 'Better quality at same size' },
  { value: 'opus', label: 'OPUS', description: 'Smallest file size' },
];

const QUALITY_OPTIONS: { value: AudioQuality; label: string; description: string }[] = [
  { value: 'best', label: 'Best', description: '~320kbps' },
  { value: 'high', label: 'High', description: '~256kbps' },
  { value: 'standard', label: 'Standard', description: '~192kbps' },
  { value: 'low', label: 'Low', description: '~128kbps' },
];

function formatDuration(ms?: number): string {
  if (!ms) return '';
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

// ── Settings Drawer Component ──────────────────────────────────────────

function SettingsDrawer({
  open,
  onClose,
  settings,
  onSettingsChange,
}: {
  open: boolean;
  onClose: () => void;
  settings: AppSettings;
  onSettingsChange: (s: AppSettings) => void;
}) {
  if (!open) return null;

  return (
    <>
      {/* Overlay */}
      <div
        className="fixed inset-0 bg-black/75 z-40 animate-fade-in backdrop-blur-sm"
        onClick={onClose}
      />
      {/* Drawer */}
      <div className="fixed top-0 right-0 h-full w-full max-w-md z-50 animate-slide-in-right">
        <div className="h-full bg-[#0e0e0e] border-l border-white/10 overflow-y-auto">
          {/* Header */}
          <div className="flex items-center justify-between p-6 border-b border-white/10">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-xl bg-[#1db954]/15 border border-[#1db954]/40 shadow-[0_0_15px_rgba(29,185,84,0.3)]">
                <Settings className="w-5 h-5 text-[#1ed760]" />
              </div>
              <h2 className="text-xl font-bold text-white">Settings</h2>
            </div>
            <button
              onClick={onClose}
              className="p-2 rounded-xl hover:bg-white/10 transition-colors cursor-pointer"
            >
              <X className="w-5 h-5 text-white/60" />
            </button>
          </div>

          <div className="p-6 space-y-8">
            {/* Audio Source */}
            <SettingsSection title="Audio Source" subtitle="Where to download audio from">
              {SOURCE_OPTIONS.map(opt => (
                <SettingsOption
                  key={opt.value}
                  label={opt.label}
                  description={opt.description}
                  selected={settings.source === opt.value}
                  onClick={() => onSettingsChange({ ...settings, source: opt.value })}
                />
              ))}
            </SettingsSection>

            {/* Audio Format */}
            <SettingsSection title="Audio Format" subtitle="Output file format">
              {FORMAT_OPTIONS.map(opt => (
                <SettingsOption
                  key={opt.value}
                  label={opt.label}
                  description={opt.description}
                  selected={settings.format === opt.value}
                  onClick={() => onSettingsChange({ ...settings, format: opt.value })}
                />
              ))}
            </SettingsSection>

            {/* Audio Quality */}
            <SettingsSection title="Audio Quality" subtitle="Bitrate / quality level">
              {QUALITY_OPTIONS.map(opt => (
                <SettingsOption
                  key={opt.value}
                  label={opt.label}
                  description={opt.description}
                  selected={settings.quality === opt.value}
                  onClick={() => onSettingsChange({ ...settings, quality: opt.value })}
                />
              ))}
            </SettingsSection>

            {/* Lyrics Format */}
            <SettingsSection title="Lyrics Format" subtitle="How lyrics are saved">
              <SettingsOption
                label="Plain Text"
                description=".txt — just the words"
                selected={settings.lyricsFormat === 'plain'}
                onClick={() => onSettingsChange({ ...settings, lyricsFormat: 'plain' })}
              />
              <SettingsOption
                label="Synced LRC"
                description=".lrc — with timestamps for karaoke"
                selected={settings.lyricsFormat === 'synced'}
                onClick={() => onSettingsChange({ ...settings, lyricsFormat: 'synced' })}
              />
            </SettingsSection>
          </div>
        </div>
      </div>
    </>
  );
}

function SettingsSection({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <h3 className="text-sm font-semibold text-white/90 mb-1">{title}</h3>
      <p className="text-xs text-white/40 mb-3">{subtitle}</p>
      <div className="space-y-2">{children}</div>
    </div>
  );
}

function SettingsOption({
  label,
  description,
  selected,
  onClick,
}: {
  label: string;
  description: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`w-full text-left px-4 py-3 rounded-xl border transition-all duration-200 cursor-pointer ${
        selected
          ? 'bg-[#1db954]/15 border-[#1db954]/60 text-white shadow-[0_0_15px_rgba(29,185,84,0.2)]'
          : 'bg-white/[0.03] border-white/[0.06] text-white/70 hover:bg-white/[0.06] hover:border-[#1db954]/30'
      }`}
    >
      <div className="flex items-center justify-between">
        <div>
          <p className="font-medium text-sm">{label}</p>
          <p className="text-xs mt-0.5 opacity-60">{description}</p>
        </div>
        {selected && (
          <div className="w-2.5 h-2.5 rounded-full bg-[#1ed760] shadow-[0_0_10px_rgba(30,215,96,0.8)]" />
        )}
      </div>
    </button>
  );
}

// ── Track Status Badge ─────────────────────────────────────────────────

function StatusBadge({ status }: { status: TrackStatus }) {
  switch (status) {
    case 'queued':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-500/15 text-amber-300 border border-amber-500/30">
          <Clock className="w-3 h-3 animate-spin" />
          Queued
        </span>
      );
    case 'downloading':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-[#1db954]/20 text-[#1ed760] border border-[#1ed760]/50 shadow-[0_0_12px_rgba(30,215,96,0.4)] animate-pulse">
          <Loader2 className="w-3 h-3 animate-spin" />
          Downloading
        </span>
      );
    case 'done':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-[#1db954]/20 text-[#1ed760] border border-[#1db954]/40 shadow-[0_0_10px_rgba(29,185,84,0.3)]">
          <CheckCircle2 className="w-3 h-3 text-[#1ed760]" />
          Done
        </span>
      );
    case 'error':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-red-500/15 text-red-300 border border-red-500/30">
          <AlertCircle className="w-3 h-3" />
          Error
        </span>
      );
    default:
      return null;
  }
}

// ── Main Page Component ────────────────────────────────────────────────

export default function Home() {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [tracks, setTracks] = useState<Track[]>([]);
  const [resultMeta, setResultMeta] = useState<{ type?: string; title?: string; coverArt?: string | null }>({});
  const [selectedTracks, setSelectedTracks] = useState<Set<string>>(new Set());
  const [downloadingBatch, setDownloadingBatch] = useState(false);
  const [downloadProgress, setDownloadProgress] = useState({ done: 0, total: 0 });
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settings, setSettings] = useState<AppSettings>(DEFAULT_SETTINGS);
  const [filterText, setFilterText] = useState('');

  // Load settings from localStorage
  useEffect(() => {
    try {
      const saved = localStorage.getItem('spotify-importer-settings');
      if (saved) setSettings(JSON.parse(saved));
    } catch {}
  }, []);

  const updateSettings = useCallback((newSettings: AppSettings) => {
    setSettings(newSettings);
    localStorage.setItem('spotify-importer-settings', JSON.stringify(newSettings));
  }, []);

  // Detect URL type live
  const detectedType = url.trim() ? detectUrlType(url.trim()) : null;
  const typeConfig = detectedType ? URL_TYPE_CONFIG[detectedType] : null;

  // ── Auto-resolve real pictures for playlist tracks in background ─────
  const resolvePlaylistCovers = useCallback(async (tracksList: Track[]) => {
    const toResolve = tracksList.filter(t => t.isPlaylistCover);
    if (toResolve.length === 0) return;

    // Resolve in chunks of 4 to update pictures smoothly
    const batchSize = 4;
    for (let i = 0; i < toResolve.length; i += batchSize) {
      const chunk = toResolve.slice(i, i + batchSize);
      try {
        const res = await fetch('/api/cover', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            tracks: chunk.map(t => ({
              id: t.id,
              name: t.name,
              artist: t.artist,
              spotifyUri: t.spotifyUri || t.id,
              coverUrl: t.coverArt,
              isPlaylistCover: true,
            })),
          }),
        });

        if (res.ok) {
          const data = await res.json();
          if (Array.isArray(data.results)) {
            setTracks(prev =>
              prev.map(t => {
                const match = data.results.find((r: any) => r.id === t.id);
                if (match && match.coverUrl) {
                  return { ...t, coverArt: match.coverUrl, isPlaylistCover: false };
                }
                return t;
              })
            );
          }
        }
      } catch (err) {
        console.warn('Background cover resolution batch error:', err);
      }
    }
  }, []);

  // ── Fetch Playlist/Track/Album/Artist ────────────────────────────────

  const fetchTracks = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url.trim()) return;

    setLoading(true);
    setError('');
    setTracks([]);
    setSelectedTracks(new Set());
    setResultMeta({});
    setFilterText('');

    try {
      const res = await fetch('/api/playlist', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: url.trim() }),
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to fetch');

      const formattedTracks: Track[] = (data.tracks || []).map((t: any) => ({
        ...t,
        isPlaylistCover: Boolean(t.isPlaylistCover),
        spotifyUri: t.spotifyUri || t.id,
        status: 'idle' as TrackStatus,
      }));

      setTracks(formattedTracks);
      setResultMeta({ type: data.type, title: data.title, coverArt: data.coverArt });

      // If imported from playlist, search for the real song picture of every song
      if (data.type === 'playlist') {
        resolvePlaylistCovers(formattedTracks);
      }
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // ── Download Logic ───────────────────────────────────────────────────

  const downloadTrack = async (track: Track, dirHandle?: any) => {
    // Update status to downloading
    setTracks(prev => prev.map(t => t.id === track.id ? { ...t, status: 'downloading' } : t));

    try {
      const res = await fetch('/api/download', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: track.name,
          artist: track.artist,
          album: track.album,
          duration: track.duration,
          coverUrl: track.coverArt,
          isPlaylistCover: track.isPlaylistCover,
          spotifyUri: track.spotifyUri || track.id,
          source: settings.source,
          format: settings.format,
          quality: settings.quality,
        }),
      });

      if (!res.ok) throw new Error('Download failed');

      // Check if server resolved and returned a real song cover
      const returnedCover = res.headers.get('x-real-cover');
      let resolvedCover = track.coverArt;
      if (returnedCover) {
        try {
          const decoded = decodeURIComponent(returnedCover);
          if (decoded && decoded !== 'null') resolvedCover = decoded;
        } catch {}
      }

      const blob = await res.blob();
      const ext = settings.format === 'm4a' ? '.m4a' : settings.format === 'opus' ? '.opus' : '.mp3';

      // Fetch lyrics
      const lyricsRes = await fetch('/api/lyrics', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: track.name,
          artist: track.artist,
          format: settings.lyricsFormat,
        }),
      });
      const lyricsData = await lyricsRes.json();
      const lyricsBlob = new Blob([lyricsData.lyrics], { type: 'text/plain' });
      const lyricsExt = lyricsData.extension || '.txt';

      const safeName = `${track.name.replace(/[\\/:*?"<>|]/g, '')} - ${track.artist.replace(/[\\/:*?"<>|]/g, '')}`;

      if (dirHandle) {
        // Audio file
        const fileHandle = await dirHandle.getFileHandle(`${safeName}${ext}`, { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(blob);
        await writable.close();

        // Lyrics file
        const lyricsHandle = await dirHandle.getFileHandle(`${safeName}${lyricsExt}`, { create: true });
        const lyricsWritable = await lyricsHandle.createWritable();
        await lyricsWritable.write(lyricsBlob);
        await lyricsWritable.close();
      } else {
        // Fallback: browser download
        const audioUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = audioUrl;
        a.download = `${safeName}${ext}`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(audioUrl);

        const lyricsUrl = window.URL.createObjectURL(lyricsBlob);
        const a2 = document.createElement('a');
        a2.href = lyricsUrl;
        a2.download = `${safeName}${lyricsExt}`;
        document.body.appendChild(a2);
        a2.click();
        a2.remove();
        window.URL.revokeObjectURL(lyricsUrl);
      }

      // Update state with done status and the confirmed real cover art
      setTracks(prev => prev.map(t => t.id === track.id ? { ...t, status: 'done', coverArt: resolvedCover, isPlaylistCover: false } : t));
    } catch (err: any) {
      console.error(err.message);
      setTracks(prev => prev.map(t => t.id === track.id ? { ...t, status: 'error', error: err.message } : t));
    }
  };

  const handleSingleDownload = async (track: Track) => {
    await downloadTrack(track);
  };

  const handleBatchDownload = async () => {
    if (selectedTracks.size === 0) return;

    let dirHandle: any = null;
    try {
      // @ts-ignore
      if (window.showDirectoryPicker) {
        // @ts-ignore
        dirHandle = await window.showDirectoryPicker({ mode: 'readwrite' });
      }
    } catch (err: any) {
      if (err.name === 'AbortError') return;
      console.log("Directory picker not supported or denied.");
    }

    setDownloadingBatch(true);
    const trackIds = Array.from(selectedTracks);
    setDownloadProgress({ done: 0, total: trackIds.length });

    // Mark all selected as queued
    setTracks(prev =>
      prev.map(t => selectedTracks.has(t.id) ? { ...t, status: 'queued' } : t)
    );

    let completed = 0;
    for (const trackId of trackIds) {
      const track = tracks.find(t => t.id === trackId);
      if (!track) continue;

      await downloadTrack(track, dirHandle);
      completed++;
      setDownloadProgress({ done: completed, total: trackIds.length });

      if (!dirHandle) {
        await new Promise(r => setTimeout(r, 1000));
      }
    }

    setDownloadingBatch(false);
    setSelectedTracks(new Set());
  };

  // ── Selection ────────────────────────────────────────────────────────

  const toggleSelectAll = () => {
    if (selectedTracks.size === filteredTracks.length) {
      setSelectedTracks(new Set());
    } else {
      setSelectedTracks(new Set(filteredTracks.map(t => t.id)));
    }
  };

  const toggleTrack = (id: string) => {
    setSelectedTracks(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  // ── Filtered tracks ─────────────────────────────────────────────────

  const filteredTracks = filterText
    ? tracks.filter(t =>
        t.name.toLowerCase().includes(filterText.toLowerCase()) ||
        t.artist.toLowerCase().includes(filterText.toLowerCase()) ||
        t.album.toLowerCase().includes(filterText.toLowerCase())
      )
    : tracks;

  // ── Render ───────────────────────────────────────────────────────────

  return (
    <main className="min-h-screen bg-[#070707] text-white p-4 sm:p-6 relative overflow-hidden selection:bg-[#1db954]/40 selection:text-[#1ed760]">
      {/* ── Spotify Glowing Ambient Mesh ── */}
      <div className="fixed top-[-25%] left-1/2 -translate-x-1/2 w-[850px] h-[550px] bg-[#1db954]/20 rounded-full blur-[140px] pointer-events-none mix-blend-screen" />
      <div className="fixed top-[30%] left-[-15%] w-[500px] h-[500px] bg-[#1ed760]/15 rounded-full blur-[120px] pointer-events-none mix-blend-screen animate-glow" />
      <div className="fixed bottom-[-20%] right-[-10%] w-[650px] h-[650px] bg-[#1db954]/18 rounded-full blur-[130px] pointer-events-none mix-blend-screen animate-glow" style={{ animationDelay: '2s' }} />

      <div className="max-w-3xl mx-auto space-y-6 relative z-10">

        {/* ── Header ── */}
        <header className="text-center pt-8 sm:pt-12 space-y-4">
          <div className="relative inline-flex items-center justify-center p-4 rounded-2xl mb-2 bg-[#121212] border border-[#1db954]/40 shadow-[0_0_30px_rgba(29,185,84,0.35)] animate-bounce-subtle">
            <AudioLines className="w-10 h-10 text-[#1ed760] drop-shadow-[0_0_15px_rgba(30,215,96,0.8)]" />
            <div className="absolute inset-0 rounded-2xl bg-[#1db954]/15 blur-xl pointer-events-none" />
          </div>

          <h1 className="text-4xl sm:text-5xl md:text-6xl font-black tracking-tight">
            Spotify <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#1ed760] via-[#1db954] to-emerald-300 drop-shadow-[0_0_25px_rgba(30,215,96,0.45)]">Importer</span>
          </h1>

          <p className="text-white/60 max-w-lg mx-auto text-base sm:text-lg leading-relaxed">
            Download tracks, albums, playlists & search results with genuine high-resolution artwork & studio-quality audio.
          </p>

          {/* Settings Chips */}
          <div className="flex items-center justify-center gap-2 flex-wrap pt-2">
            <button
              onClick={() => setSettingsOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm text-white/80 bg-white/[0.04] border border-[#1db954]/30 hover:border-[#1ed760]/60 hover:text-white hover:shadow-[0_0_15px_rgba(29,185,84,0.3)] transition-all cursor-pointer group"
            >
              <Settings className="w-4 h-4 text-[#1ed760] group-hover:rotate-90 transition-transform duration-500" />
              Settings
            </button>
            <span className="px-3 py-1.5 rounded-lg text-xs bg-white/[0.03] border border-white/10 text-white/60">
              {SOURCE_OPTIONS.find(s => s.value === settings.source)?.label}
            </span>
            <span className="px-3 py-1.5 rounded-lg text-xs bg-white/[0.03] border border-white/10 text-white/60 font-semibold">
              {settings.format.toUpperCase()}
            </span>
            <span className="px-3 py-1.5 rounded-lg text-xs bg-white/[0.03] border border-white/10 text-white/60">
              {QUALITY_OPTIONS.find(q => q.value === settings.quality)?.label}
            </span>
          </div>
        </header>

        {/* ── Search / URL Input with Glowing Spotify Halo ── */}
        <form onSubmit={fetchTracks} className="relative group max-w-2xl mx-auto">
          {/* Neon Glow Underlay */}
          <div className="absolute -inset-1 bg-gradient-to-r from-[#1db954] via-[#1ed760] to-[#1db954] rounded-2xl blur-lg opacity-25 group-focus-within:opacity-75 transition duration-500 pointer-events-none" />

          <div className="relative flex items-center bg-[#121212]/90 backdrop-blur-2xl rounded-2xl overflow-hidden border border-[#1db954]/35 focus-within:border-[#1ed760] focus-within:shadow-[0_0_35px_rgba(30,215,96,0.35)] transition-all">
            <div className="pl-4 flex items-center gap-2">
              {typeConfig ? (
                <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-xs font-medium transition-all ${typeConfig.color}`}>
                  <typeConfig.icon className="w-3.5 h-3.5" />
                  {typeConfig.label}
                </div>
              ) : (
                <Search className="w-5 h-5 text-white/40 group-focus-within:text-[#1ed760] transition-colors" />
              )}
            </div>
            <input
              type="text"
              required
              placeholder="Paste Spotify track / playlist / album URL or song title..."
              value={url}
              onChange={e => setUrl(e.target.value)}
              className="w-full bg-transparent border-none py-4 px-3 text-white placeholder:text-white/40 focus:outline-none focus:ring-0 text-sm sm:text-base"
            />
            <button
              type="submit"
              disabled={loading || !url.trim()}
              className="mr-2 px-6 py-2.5 spotify-btn-glow text-black font-extrabold rounded-xl transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2 text-sm cursor-pointer shadow-[0_0_20px_rgba(29,185,84,0.4)]"
            >
              {loading ? <Loader2 className="w-5 h-5 animate-spin text-black" /> : 'Fetch'}
            </button>
          </div>
        </form>

        {/* ── Error State ── */}
        {error && (
          <div className="p-4 bg-red-500/10 border border-red-500/25 text-red-300 rounded-xl text-center text-sm animate-fade-in shadow-[0_0_20px_rgba(239,68,68,0.15)]">
            <AlertCircle className="w-5 h-5 inline-block mr-2 -mt-0.5 text-red-400" />
            {error}
          </div>
        )}

        {/* ── Loading Skeleton with Spotify Green Pulse ── */}
        {loading && (
          <div className="space-y-3 pt-4 animate-fade-in">
            {[1, 2, 3, 4, 5].map(i => (
              <div key={i} className="flex items-center gap-4 p-3.5 spotify-card rounded-xl border border-white/5">
                <div className="w-12 h-12 rounded-lg bg-white/5 animate-pulse relative overflow-hidden">
                  <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[#1db954]/10 to-transparent animate-shimmer" />
                </div>
                <div className="flex-1 space-y-2">
                  <div className="h-4 bg-white/5 rounded w-3/4 animate-pulse" />
                  <div className="h-3 bg-white/5 rounded w-1/2 animate-pulse" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* ── Track List ── */}
        {tracks.length > 0 && !loading && (
          <div className="space-y-4 pt-4 pb-36 animate-fade-in-up">
            {/* Result Header */}
            {resultMeta.title && (
              <div className="flex items-center gap-4 p-4 spotify-card rounded-2xl border border-[#1db954]/30 shadow-[0_0_25px_rgba(29,185,84,0.15)]">
                {resultMeta.coverArt && (
                  <img
                    src={resultMeta.coverArt}
                    alt=""
                    referrerPolicy="no-referrer"
                    className="w-16 h-16 rounded-xl object-cover shadow-[0_0_15px_rgba(0,0,0,0.5)] border border-[#1db954]/30 flex-shrink-0"
                  />
                )}
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-[#1ed760] uppercase tracking-widest font-bold flex items-center gap-1.5 mb-0.5">
                    <span className="w-2 h-2 rounded-full bg-[#1ed760] shadow-[0_0_8px_rgba(30,215,96,0.8)]" />
                    {resultMeta.type || 'Results'}
                  </p>
                  <h2 className="text-lg font-bold text-white truncate">{resultMeta.title}</h2>
                  <p className="text-sm text-white/50">
                    {tracks.length} {tracks.length === 1 ? 'track' : 'tracks'}
                    {resultMeta.type === 'playlist' && (
                      <span className="ml-2 text-xs text-[#1ed760]/90 font-medium">
                        · Real song covers auto-searched
                      </span>
                    )}
                  </p>
                </div>
              </div>
            )}

            {/* Controls Bar */}
            <div className="flex items-center justify-between gap-3 px-1">
              <div className="flex items-center gap-2 flex-1 min-w-0">
                <button
                  onClick={toggleSelectAll}
                  className="text-xs px-3.5 py-2 rounded-lg bg-white/[0.04] border border-white/10 hover:border-[#1db954]/50 hover:text-[#1ed760] hover:shadow-[0_0_12px_rgba(29,185,84,0.25)] transition-all whitespace-nowrap cursor-pointer"
                >
                  {selectedTracks.size === filteredTracks.length && filteredTracks.length > 0 ? 'Deselect All' : 'Select All'}
                </button>
                {tracks.length > 5 && (
                  <div className="relative flex-1 max-w-[220px]">
                    <Filter className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-white/40" />
                    <input
                      type="text"
                      placeholder="Filter tracks..."
                      value={filterText}
                      onChange={e => setFilterText(e.target.value)}
                      className="w-full bg-[#121212] border border-white/10 rounded-lg py-1.5 pl-8 pr-3 text-xs text-white placeholder:text-white/40 focus:outline-none focus:border-[#1ed760] focus:shadow-[0_0_12px_rgba(30,215,96,0.2)] transition-all"
                    />
                  </div>
                )}
              </div>
              <span className="text-xs text-white/50 whitespace-nowrap font-medium">
                {filteredTracks.length} of {tracks.length}
              </span>
            </div>

            {/* Track Cards */}
            <div className="space-y-2">
              {filteredTracks.map((track, i) => (
                <div
                  key={track.id || i}
                  onClick={() => toggleTrack(track.id)}
                  className={`flex items-center gap-3 sm:gap-4 p-3 rounded-xl transition-all duration-200 group cursor-pointer border ${
                    selectedTracks.has(track.id)
                      ? 'border-[#1db954]/60 bg-[#1db954]/[0.08] shadow-[0_0_20px_rgba(29,185,84,0.18)]'
                      : 'bg-[#121212]/80 border-white/[0.06] hover:border-[#1db954]/30 hover:bg-[#181818]'
                  }`}
                >
                  {/* Checkbox */}
                  <div className="pl-1">
                    <div
                      className={`w-5 h-5 rounded-md border-2 flex items-center justify-center transition-all ${
                        selectedTracks.has(track.id)
                          ? 'bg-[#1db954] border-[#1db954] shadow-[0_0_10px_rgba(30,215,96,0.7)]'
                          : 'border-white/20 group-hover:border-[#1db954]/50'
                      }`}
                    >
                      {selectedTracks.has(track.id) && (
                        <svg className="w-3 h-3 text-black" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                        </svg>
                      )}
                    </div>
                  </div>

                  {/* Cover Art with Real Cover vs Playlist indicator */}
                  <div className="relative w-11 h-11 rounded-lg overflow-hidden flex-shrink-0 group/cover">
                    {track.coverArt ? (
                      <img
                        src={track.coverArt}
                        alt={track.album}
                        referrerPolicy="no-referrer"
                        className={`w-full h-full object-cover transition-all duration-300 ${
                          track.isPlaylistCover
                            ? 'opacity-85 brightness-95'
                            : 'ring-1 ring-[#1db954]/40 shadow-[0_0_10px_rgba(29,185,84,0.25)]'
                        }`}
                      />
                    ) : (
                      <div className="w-full h-full bg-[#181818] rounded-lg flex items-center justify-center border border-white/5">
                        <Music className="w-5 h-5 text-white/30" />
                      </div>
                    )}
                    {/* Small dot indicating playlist thumbnail until resolved */}
                    {track.isPlaylistCover && (
                      <div
                        title="Searching real song picture on download"
                        className="absolute bottom-0.5 right-0.5 w-2 h-2 rounded-full bg-amber-400 shadow-[0_0_6px_rgba(251,191,36,0.9)]"
                      />
                    )}
                  </div>

                  {/* Track Info */}
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-sm text-white/95 truncate group-hover:text-[#1ed760] transition-colors">
                      {track.name}
                    </p>
                    <p className="text-xs text-white/50 truncate">
                      {track.artist}
                      {track.album ? ` · ${track.album}` : ''}
                    </p>
                  </div>

                  {/* Duration */}
                  {track.duration && (
                    <span className="text-xs text-white/40 tabular-nums hidden sm:block">
                      {formatDuration(track.duration)}
                    </span>
                  )}

                  {/* Status or Download Button */}
                  {track.status !== 'idle' ? (
                    <div className="w-24 flex justify-end">
                      <StatusBadge status={track.status} />
                    </div>
                  ) : (
                    <button
                      onClick={(e) => { e.stopPropagation(); handleSingleDownload(track); }}
                      disabled={downloadingBatch}
                      title="Download with real cover"
                      className="p-2.5 rounded-xl bg-white/[0.04] border border-white/10 hover:bg-[#1db954]/20 hover:border-[#1ed760]/50 hover:text-[#1ed760] hover:shadow-[0_0_15px_rgba(29,185,84,0.3)] text-white/60 transition-all disabled:opacity-30 cursor-pointer"
                    >
                      <Download className="w-4 h-4" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── Floating Batch Download Bar with Spotify Glowing CTA ── */}
        {selectedTracks.size > 0 && (
          <div className="fixed bottom-6 left-1/2 -translate-x-1/2 w-full max-w-xl px-4 z-30 animate-fade-in-up">
            <div className="bg-[#121212]/95 backdrop-blur-2xl p-4 rounded-2xl border border-[#1db954]/40 shadow-[0_0_45px_rgba(0,0,0,0.9),0_0_25px_rgba(29,185,84,0.25)]">
              {/* Progress bar for batch */}
              {downloadingBatch && downloadProgress.total > 0 && (
                <div className="mb-3">
                  <div className="flex items-center justify-between text-xs text-white/60 mb-1.5">
                    <span className="flex items-center gap-1.5 text-[#1ed760] font-medium">
                      <Loader2 className="w-3.5 h-3.5 animate-spin text-[#1ed760]" />
                      Downloading & Embedding Real Cover Art...
                    </span>
                    <span className="tabular-nums text-white/90 font-bold">{downloadProgress.done} of {downloadProgress.total}</span>
                  </div>
                  <div className="w-full h-1.5 bg-white/10 rounded-full overflow-hidden">
                    <div
                      className="h-full progress-bar rounded-full transition-all duration-500"
                      style={{ width: `${(downloadProgress.done / downloadProgress.total) * 100}%` }}
                    />
                  </div>
                </div>
              )}

              <div className="flex items-center justify-between">
                <span className="text-white/90 font-medium text-sm px-1 flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-[#1ed760] shadow-[0_0_8px_rgba(30,215,96,0.8)]" />
                  {selectedTracks.size} {selectedTracks.size === 1 ? 'song' : 'songs'} selected
                </span>
                <div className="flex items-center gap-2">
                  {!downloadingBatch && (
                    <button
                      onClick={() => setSelectedTracks(new Set())}
                      className="px-3 py-2 text-xs text-white/50 hover:text-white transition-colors cursor-pointer"
                    >
                      Clear
                    </button>
                  )}
                  <button
                    onClick={handleBatchDownload}
                    disabled={downloadingBatch}
                    className="px-5 py-2.5 spotify-btn-glow text-black font-extrabold rounded-xl transition-all disabled:opacity-50 flex items-center gap-2 text-sm cursor-pointer shadow-[0_0_20px_rgba(29,185,84,0.4)]"
                  >
                    {downloadingBatch ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin text-black" />
                        Downloading...
                      </>
                    ) : (
                      <>
                        <Download className="w-4 h-4 text-black" />
                        Download Selected
                      </>
                    )}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ── Settings Drawer ── */}
      <SettingsDrawer
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        settings={settings}
        onSettingsChange={updateSettings}
      />
    </main>
  );
}
