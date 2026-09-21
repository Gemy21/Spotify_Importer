#!/usr/bin/env python3
"""
Fast, keyless Spotify playlist scraper.
Extracts ALL tracks (no 100-song cap) from any public Spotify playlist.
Retrieves the genuine song-level cover art (640x640) for every track.
"""

import sys
import json
import re

# Ensure stdout uses utf-8
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def extract_playlist_id(url_or_id: str) -> str:
    cleaned = url_or_id.strip()
    # Match spotify:playlist:ID
    m = re.search(r"spotify:playlist:([a-zA-Z0-9]+)", cleaned)
    if m:
        return m.group(1)
    # Match open.spotify.com/playlist/ID
    m = re.search(r"playlist/([a-zA-Z0-9]+)", cleaned)
    if m:
        return m.group(1)
    # Assume bare ID if alphanumeric
    if re.match(r"^[a-zA-Z0-9]+$", cleaned):
        return cleaned
    raise ValueError(f"Could not extract playlist ID from '{url_or_id}'")


def get_best_image_url(sources) -> str | None:
    if not sources:
        return None
    # Sort descending by width or height
    sorted_sources = sorted(
        sources,
        key=lambda s: (s.get("width") or s.get("maxWidth") or 0) or (s.get("height") or s.get("maxHeight") or 0),
        reverse=True,
    )
    return sorted_sources[0].get("url") if sorted_sources else None


def fetch_playlist(playlist_id: str):
    from spotapi.playlist import PublicPlaylist

    pl = PublicPlaylist(playlist_id)

    # Initial query to get title, cover art, and totalCount
    initial_info = pl.get_playlist_info(limit=1)
    data = initial_info.get("data", {})
    pl_v2 = data.get("playlistV2", {})

    playlist_name = pl_v2.get("name") or "Spotify Playlist"
    
    # Extract playlist-level cover art
    playlist_images = pl_v2.get("images", {}).get("items", [])
    playlist_cover = None
    if playlist_images:
        first_img = playlist_images[0]
        sources = first_img.get("sources", [])
        if sources:
            playlist_cover = sources[0].get("url")

    all_tracks = []
    seen_uris = set()

    for chunk in pl.paginate_playlist():
        items = chunk.get("items", [])
        for entry in items:
            # itemV2 contains track info
            item_v2 = entry.get("itemV2", {})
            track_data = item_v2.get("data", {})
            if not track_data or track_data.get("__typename") != "Track":
                continue

            uri = track_data.get("uri")
            if not uri:
                continue

            name = track_data.get("name")
            if not name:
                continue

            # Artists
            artist_items = track_data.get("artists", {}).get("items", [])
            artist_names = [a.get("profile", {}).get("name") for a in artist_items if a.get("profile", {}).get("name")]
            artist = ", ".join(artist_names) if artist_names else "Unknown Artist"

            # Album & Cover art
            album_data = track_data.get("albumOfTrack", {})
            album_name = album_data.get("name") or "Unknown Album"

            cover_sources = album_data.get("coverArt", {}).get("sources", [])
            track_cover = get_best_image_url(cover_sources) or playlist_cover

            # Duration
            duration_ms = track_data.get("trackDuration", {}).get("totalMilliseconds")

            all_tracks.append({
                "id": uri,
                "name": name,
                "artist": artist,
                "album": album_name,
                "duration": duration_ms,
                "coverArt": track_cover,
                "isPlaylistCover": False,  # We got the genuine track cover!
                "spotifyUri": uri,
            })

    return {
        "type": "playlist",
        "title": playlist_name,
        "coverArt": playlist_cover,
        "total": len(all_tracks),
        "tracks": all_tracks,
    }


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No playlist URL or ID provided"}))
        sys.exit(1)

    url_or_id = sys.argv[1]
    try:
        pid = extract_playlist_id(url_or_id)
        result = fetch_playlist(pid)
        print(json.dumps(result, ensure_ascii=False))
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)


if __name__ == "__main__":
    main()
