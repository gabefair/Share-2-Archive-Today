"""Kotlin-facing bridge around vendored yt-dlp (no ffmpeg postprocessors)."""
from __future__ import annotations

import json
import os
from typing import Any, Callable, Optional

from yt_dlp.YoutubeDL import YoutubeDL


def _filesize(fmt: dict[str, Any]) -> Optional[int]:
    for key in ("filesize", "filesize_approx"):
        value = fmt.get(key)
        if isinstance(value, (int, float)) and value > 0:
            return int(value)
    return None


def probe(url: str) -> str:
    """Return JSON: title, duration, formats (height, format_id, has_video/audio, size)."""
    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        # Prefer native HLS; our Android stub disables ffmpeg/external FDs.
        "hls_prefer_native": True,
    }
    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)

    formats_out = []
    for fmt in info.get("formats") or []:
        height = fmt.get("height")
        vcodec = fmt.get("vcodec") or "none"
        acodec = fmt.get("acodec") or "none"
        has_video = vcodec != "none"
        has_audio = acodec != "none"
        if not has_video and not has_audio:
            continue
        formats_out.append(
            {
                "format_id": str(fmt.get("format_id")),
                "ext": fmt.get("ext"),
                "height": int(height) if height else None,
                "tbr": fmt.get("tbr"),
                "vcodec": vcodec,
                "acodec": acodec,
                "has_video": has_video,
                "has_audio": has_audio,
                "filesize": _filesize(fmt),
                "format_note": fmt.get("format_note"),
            }
        )

    payload = {
        "id": info.get("id"),
        "title": info.get("title") or "video",
        "duration": info.get("duration"),
        "uploader": info.get("uploader"),
        "formats": formats_out,
    }
    return json.dumps(payload)


def download(
    url: str,
    format_id: str,
    out_dir: str,
    out_template: str,
    progress_cb: Optional[Callable[[str], None]] = None,
    continuedl: bool = True,
) -> str:
    """Download a single format_id into out_dir. Returns JSON with filepath."""
    os.makedirs(out_dir, exist_ok=True)

    def _emit(payload: dict[str, Any]) -> None:
        if progress_cb is None:
            return
        text = json.dumps(payload)
        # Kotlin fun interface exposes onProgress(String)
        if hasattr(progress_cb, "onProgress"):
            progress_cb.onProgress(text)
        else:
            progress_cb(text)

    def hook(d: dict[str, Any]) -> None:
        status = d.get("status")
        if status == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            done = d.get("downloaded_bytes") or 0
            _emit(
                {
                    "status": "downloading",
                    "downloaded": done,
                    "total": total,
                    "speed": d.get("speed"),
                    "eta": d.get("eta"),
                }
            )
        elif status == "finished":
            _emit({"status": "finished", "filename": d.get("filename")})

    opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "format": format_id,
        "outtmpl": os.path.join(out_dir, out_template),
        "continuedl": continuedl,
        "noprogress": True,
        "progress_hooks": [hook],
        # Never invoke ffmpeg merge/postprocess — Kotlin/Media3 handles muxing.
        "postprocessors": [],
        "keepvideo": False,
        "hls_prefer_native": True,
    }

    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=True)
        filepath = ydl.prepare_filename(info)

    return json.dumps({"filepath": filepath, "title": info.get("title"), "ext": info.get("ext")})


def ping() -> str:
    return f"yt-dlp ok; version entrypoint ready"
