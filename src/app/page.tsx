'use client';

import { useState } from 'react';
import { Search, Loader2, Music, Download } from 'lucide-react';

export default function Home() {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState<string | null>(null);
  const [downloadingBatch, setDownloadingBatch] = useState(false);
  const [tracks, setTracks] = useState<any[]>([]);
  const [selectedTracks, setSelectedTracks] = useState<Set<string>>(new Set());
  const [error, setError] = useState('');

  const fetchPlaylist = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setTracks([]);

    try {
      const res = await fetch('/api/playlist', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url }),
      });

      const data = await res.json();

      if (!res.ok) throw new Error(data.error || 'Failed to fetch playlist');
      
      setTracks(data.tracks);
      setSelectedTracks(new Set());
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = async (track: any, dirHandle?: any) => {
    try {
      const res = await fetch('/api/download', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: track.name, artist: track.artist, album: track.album, coverUrl: track.coverArt }),
      });

      if (!res.ok) throw new Error('Download failed');

      const blob = await res.blob();
      
      // Fetch lyrics
      const lyricsRes = await fetch('/api/lyrics', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: track.name, artist: track.artist }),
      });
      const lyricsData = await lyricsRes.json();
      const lyricsBlob = new Blob([lyricsData.lyrics], { type: 'text/plain' });

      if (dirHandle) {
        // Save Audio
        const fileHandle = await dirHandle.getFileHandle(`${track.name.replace(/[\\/:*?"<>|]/g, '')} - ${track.artist.replace(/[\\/:*?"<>|]/g, '')}.mp3`, { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(blob);
        await writable.close();

        // Save Lyrics
        const lyricsHandle = await dirHandle.getFileHandle(`${track.name.replace(/[\\/:*?"<>|]/g, '')} - ${track.artist.replace(/[\\/:*?"<>|]/g, '')}.txt`, { create: true });
        const lyricsWritable = await lyricsHandle.createWritable();
        await lyricsWritable.write(lyricsBlob);
        await lyricsWritable.close();
      } else {
        // Fallback Audio
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${track.name} - ${track.artist}.mp3`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);

        // Fallback Lyrics
        const lyricsUrl = window.URL.createObjectURL(lyricsBlob);
        const a2 = document.createElement('a');
        a2.href = lyricsUrl;
        a2.download = `${track.name} - ${track.artist}.txt`;
        document.body.appendChild(a2);
        a2.click();
        a2.remove();
        window.URL.revokeObjectURL(lyricsUrl);
      }
    } catch (err: any) {
      console.error(err.message);
      if (!dirHandle) alert(`Failed to download ${track.name}`);
    }
  };

  const handleSingleDownload = async (track: any) => {
    setDownloading(track.id);
    await handleDownload(track);
    setDownloading(null);
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
    for (const trackId of Array.from(selectedTracks)) {
      const track = tracks.find(t => t.id === trackId);
      if (!track) continue;
      
      setDownloading(track.id);
      await handleDownload(track, dirHandle);
      
      if (!dirHandle) {
        // Wait a bit to prevent browser popup block on standard downloads
        await new Promise(r => setTimeout(r, 1000));
      }
    }
    setDownloading(null);
    setDownloadingBatch(false);
    setSelectedTracks(new Set());
  };

  const toggleSelectAll = () => {
    if (selectedTracks.size === tracks.length) {
      setSelectedTracks(new Set());
    } else {
      setSelectedTracks(new Set(tracks.map(t => t.id)));
    }
  };

  const toggleTrack = (id: string) => {
    const newSet = new Set(selectedTracks);
    if (newSet.has(id)) newSet.delete(id);
    else newSet.add(id);
    setSelectedTracks(newSet);
  };

  return (
    <main className="min-h-screen bg-black text-white p-6 selection:bg-emerald-500/30 relative overflow-hidden">
      {/* Liquid Glass Background Effects */}
      <div className="absolute top-[-10%] left-[-10%] w-96 h-96 bg-emerald-600/30 rounded-full mix-blend-screen filter blur-[100px] animate-pulse" />
      <div className="absolute bottom-[-10%] right-[-10%] w-[500px] h-[500px] bg-teal-600/20 rounded-full mix-blend-screen filter blur-[120px]" />
      
      <div className="max-w-3xl mx-auto space-y-8 relative z-10">
        
        {/* Header */}
        <header className="text-center pt-12 space-y-4">
          <div className="inline-flex items-center justify-center p-4 bg-white/5 backdrop-blur-md border border-white/10 shadow-[0_4px_30px_rgba(0,0,0,0.1)] rounded-full mb-4">
            <Music className="w-10 h-10 text-emerald-400 drop-shadow-md" />
          </div>
          <h1 className="text-4xl md:text-5xl font-black tracking-tighter drop-shadow-md">
            Import <span className="text-emerald-400">Spotify</span> Playlists
          </h1>
          <p className="text-white/60 max-w-lg mx-auto text-lg backdrop-blur-sm">
            Paste a public playlist link below to instantly grab all the tracks and download them directly to your device.
          </p>
        </header>

        {/* Input Form */}
        <form onSubmit={fetchPlaylist} className="relative group max-w-xl mx-auto">
          <div className="absolute inset-0 bg-emerald-500/30 blur-2xl rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-700" />
          <div className="relative flex items-center bg-white/10 backdrop-blur-xl border border-white/20 shadow-[0_8px_32px_0_rgba(31,38,135,0.37)] rounded-2xl overflow-hidden focus-within:border-emerald-400/50 focus-within:ring-1 focus-within:ring-emerald-400/50 transition-all">
            <div className="pl-4">
              <Search className="w-5 h-5 text-neutral-500" />
            </div>
            <input
              type="url"
              required
              placeholder="https://open.spotify.com/playlist/..."
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              className="w-full bg-transparent border-none py-4 px-4 text-neutral-200 placeholder:text-neutral-600 focus:outline-none focus:ring-0"
            />
            <button
              type="submit"
              disabled={loading || !url}
              className="mr-2 px-6 py-2 bg-emerald-500 hover:bg-emerald-400 text-neutral-950 font-bold rounded-xl transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : 'Fetch'}
            </button>
          </div>
        </form>

        {/* Error State */}
        {error && (
          <div className="p-4 bg-red-500/20 backdrop-blur-md border border-red-500/30 text-red-200 rounded-xl text-center shadow-lg">
            {error}
          </div>
        )}

        {/* Track List */}
        {tracks.length > 0 && (
          <div className="space-y-4 pt-8 pb-32 animate-in fade-in slide-in-from-bottom-4 duration-700">
            <div className="flex items-center justify-between border-b border-white/10 pb-4">
              <h2 className="text-xl font-bold text-white/90 drop-shadow-sm">
                Found {tracks.length} Tracks
              </h2>
              <button 
                onClick={toggleSelectAll}
                className="text-sm px-4 py-2 bg-white/5 hover:bg-white/10 border border-white/10 rounded-lg transition-colors"
              >
                {selectedTracks.size === tracks.length ? 'Deselect All' : 'Select All'}
              </button>
            </div>
            <div className="space-y-3">
              {tracks.map((track, i) => (
                <div 
                  key={track.id || i}
                  onClick={() => toggleTrack(track.id)}
                  className={`flex items-center gap-4 p-3 bg-white/5 backdrop-blur-lg border ${selectedTracks.has(track.id) ? 'border-emerald-500/50 bg-emerald-500/10' : 'border-white/10 hover:border-white/20'} shadow-[0_4px_20px_rgba(0,0,0,0.1)] rounded-xl transition-all duration-300 group cursor-pointer`}
                >
                  <div className="pl-2">
                    <div className={`w-5 h-5 rounded border flex items-center justify-center transition-colors ${selectedTracks.has(track.id) ? 'bg-emerald-500 border-emerald-500' : 'border-white/30'}`}>
                      {selectedTracks.has(track.id) && <svg className="w-3.5 h-3.5 text-black" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" /></svg>}
                    </div>
                  </div>
                  {track.coverArt ? (
                    <img src={track.coverArt} alt={track.album} className="w-12 h-12 rounded-lg object-cover shadow-md" />
                  ) : (
                    <div className="w-12 h-12 bg-white/10 rounded-lg flex items-center justify-center border border-white/5">
                      <Music className="w-6 h-6 text-white/50" />
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-white/90 truncate drop-shadow-sm">{track.name}</p>
                    <p className="text-sm text-white/60 truncate">{track.artist}</p>
                  </div>
                  <button 
                    onClick={(e) => { e.stopPropagation(); handleSingleDownload(track); }}
                    disabled={downloading === track.id || downloadingBatch}
                    className="p-3 rounded-full bg-white/5 border border-white/10 hover:bg-emerald-500/20 hover:border-emerald-500/30 hover:text-emerald-400 text-white/70 transition-all shadow-sm disabled:opacity-50"
                  >
                    {downloading === track.id ? <Loader2 className="w-5 h-5 animate-spin text-emerald-500" /> : <Download className="w-5 h-5" />}
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Floating Batch Download Bar */}
        {selectedTracks.size > 0 && (
          <div className="fixed bottom-8 left-1/2 -translate-x-1/2 w-full max-w-lg px-4 z-50 animate-in slide-in-from-bottom-10 fade-in duration-300">
            <div className="bg-neutral-900/90 backdrop-blur-xl border border-white/10 p-4 rounded-2xl shadow-2xl flex items-center justify-between">
              <span className="text-white/90 font-medium px-2">
                {selectedTracks.size} {selectedTracks.size === 1 ? 'song' : 'songs'} selected
              </span>
              <button
                onClick={handleBatchDownload}
                disabled={downloadingBatch}
                className="px-6 py-2.5 bg-emerald-500 hover:bg-emerald-400 text-black font-bold rounded-xl transition-colors disabled:opacity-50 flex items-center gap-2"
              >
                {downloadingBatch ? (
                  <>
                    <Loader2 className="w-5 h-5 animate-spin" />
                    Downloading...
                  </>
                ) : (
                  <>
                    <Download className="w-5 h-5" />
                    Download Selected
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </main>
  );
}
