"""Kotlin-facing bridge around vendored yt-dlp (no ffmpeg postprocessors)."""
from __future__ import annotations

import json
import os
from typing import Any, Callable, Optional

import android_shims

android_shims.install()

from yt_dlp.YoutubeDL import YoutubeDL


def _filesize(fmt: dict[str, Any]) -> Optional[int]:
    for key in ("filesize", "filesize_approx"):
        value = fmt.get(key)
        if isinstance(value, (int, float)) and value > 0:
            return int(value)
    return None


def _base_opts(**extra: Any) -> dict[str, Any]:
    """Shared YoutubeDL options. FOSS: no site/age filtering — every extractor yt-dlp ships."""
    opts: dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "hls_prefer_native": True,
        "socket_timeout": 30,
        # None = do not skip age-restricted / adult extractors or videos.
        "age_limit": None,
    }
    opts.update(extra)
    return opts


def _first_site_ie_key(ydl: YoutubeDL, url: str) -> Optional[str]:
    """First non-generic extractor whose URL pattern matches (same order as yt-dlp)."""
    for key, ie in ydl._ies.items():
        if key in ("Generic", "generic"):
            continue
        if not ie.suitable(url):
            continue
        return key
    return None


def _extract_info(ydl: YoutubeDL, url: str, *, download: bool) -> dict[str, Any]:
    """Run extract_info; if a site-specific IE matches and fails, retry with Generic.

    Upstream yt-dlp stops after the first matching IE. On Android we always give
    Generic a second chance so HTML/direct-media heuristics can still succeed.
    """
    site_key = _first_site_ie_key(ydl, url)
    if site_key is None:
        # Nothing site-specific matched — normal path (ends at Generic).
        return ydl.extract_info(url, download=download)

    try:
        return ydl.extract_info(url, download=download, ie_key=site_key)
    except Exception as site_err:
        try:
            return ydl.extract_info(url, download=download, ie_key="Generic")
        except Exception:
            # Prefer the site-specific error (usually more informative).
            raise site_err from None


def probe(url: str) -> str:
    """Return JSON: title, duration, formats (height, format_id, has_video/audio, size)."""
    opts = _base_opts(skip_download=True)
    with YoutubeDL(opts) as ydl:
        info = _extract_info(ydl, url, download=False)

    formats_out = []
    for fmt in info.get("formats") or []:
        # yt-dlp uses the string "none" for absent streams. Missing/null means unknown
        # (common for progressive MP4) — do not treat unknown as absent.
        raw_v = fmt.get("vcodec")
        raw_a = fmt.get("acodec")
        vcodec = "none" if raw_v == "none" else (raw_v or "unknown")
        acodec = "none" if raw_a == "none" else (raw_a or "unknown")
        has_video = vcodec != "none"
        has_audio = acodec != "none"
        if not has_video and not has_audio:
            continue
        proto = str(fmt.get("protocol") or "")
        ext = str(fmt.get("ext") or "")
        note = str(fmt.get("format_note") or "")
        if "mhtml" in proto.lower() or ext.lower() == "mhtml":
            continue
        if "storyboard" in note.lower():
            continue
        height = fmt.get("height")
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
                "protocol": fmt.get("protocol"),
                "language": fmt.get("language"),
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
    archive_metadata: bool = False,
) -> str:
    """Download a single format_id into out_dir. Returns JSON with filepath (+ sidecars)."""
    os.makedirs(out_dir, exist_ok=True)

    def _emit(payload: dict[str, Any]) -> None:
        if progress_cb is None:
            return
        text = json.dumps(payload)
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

    opts = _base_opts(
        format=format_id,
        outtmpl=os.path.join(out_dir, out_template),
        continuedl=continuedl,
        noprogress=True,
        progress_hooks=[hook],
        postprocessors=[],
        keepvideo=False,
        retries=3,
        fragment_retries=3,
    )
    if archive_metadata:
        # Archivist sidecars; opt-in only — info.json may contain personal data / comments.
        opts["writeinfojson"] = True
        opts["getcomments"] = True
        opts["writedescription"] = True
        opts["writethumbnail"] = True

    with YoutubeDL(opts) as ydl:
        info = _extract_info(ydl, url, download=True)
        filepath = ydl.prepare_filename(info)

    sidecars: list[dict[str, str]] = []
    if archive_metadata:
        sidecars = _collect_sidecars(filepath, out_dir)

    return json.dumps(
        {
            "filepath": filepath,
            "title": info.get("title"),
            "ext": info.get("ext"),
            "sidecars": sidecars,
        }
    )


def _collect_sidecars(media_path: str, out_dir: str) -> list[dict[str, str]]:
    """Find yt-dlp metadata sidecars written next to the media file."""
    found: list[dict[str, str]] = []
    seen: set[str] = set()

    def add(path: str, kind: str) -> None:
        if not path or path in seen or not os.path.isfile(path):
            return
        seen.add(path)
        found.append({"path": path, "kind": kind})

    base, _ = os.path.splitext(media_path)
    add(base + ".info.json", "infojson")
    add(base + ".description", "description")
    for ext in (".jpg", ".jpeg", ".png", ".webp"):
        add(base + ext, "thumbnail")

    # Some outtmpl shapes (e.g. video.%(ext)s) use a stem without the final media name.
    try:
        for name in os.listdir(out_dir):
            lower = name.lower()
            full = os.path.join(out_dir, name)
            if lower.endswith(".info.json"):
                add(full, "infojson")
            elif lower.endswith(".description"):
                add(full, "description")
            elif lower.endswith((".jpg", ".jpeg", ".png", ".webp")) and not lower.endswith(
                (".info.json",)
            ):
                # Only keep thumbnails that share a stem with media or look like yt-dlp output.
                stem = os.path.splitext(name)[0]
                media_stem = os.path.splitext(os.path.basename(media_path))[0]
                if stem == media_stem or stem.startswith(media_stem) or media_stem.startswith(stem):
                    add(full, "thumbnail")
    except OSError:
        pass

    return found


def ping() -> str:
    import sys

    sub = sys.modules.get("subprocess")
    stub = getattr(sub, "_android_stub", False) if sub else False
    return f"yt-dlp ok; subprocess_stub={stub}; generic_fallback=1"
