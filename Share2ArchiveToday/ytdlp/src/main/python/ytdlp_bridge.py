"""Kotlin-facing bridge around vendored yt-dlp (no ffmpeg postprocessors)."""
from __future__ import annotations

import datetime
import json
import os
from collections import deque
from typing import Any, Callable, Optional

import android_shims

android_shims.install()

from yt_dlp.YoutubeDL import YoutubeDL
from yt_dlp.version import __version__ as YTDLP_VERSION

try:
    from yt_dlp.version import RELEASE_GIT_HEAD as YTDLP_GIT_HEAD
except ImportError:  # upstream has moved this around before
    YTDLP_GIT_HEAD = None

MEDIA_EXTS = (
    ".mp4", ".m4a", ".m4v", ".webm", ".mkv", ".mp3", ".opus", ".ogg",
    ".aac", ".flv", ".mov", ".3gp", ".wav", ".ts",
)
SUBTITLE_EXTS = (".vtt", ".srt", ".ass", ".ssa", ".lrc", ".ttml", ".srv1", ".srv2", ".srv3")
THUMBNAIL_EXTS = (".jpg", ".jpeg", ".png", ".webp")

# Set by configure(); yt-dlp otherwise probes $HOME, which is not reliably writable.
_CACHE_DIR: Optional[str] = None


class DownloadCancelled(Exception):
    """Raised from the progress hook when Kotlin signals cancellation."""


def configure(cache_dir: Optional[str] = None) -> str:
    """Point yt-dlp's player/nsig cache at app-private storage. Called once from Kotlin."""
    global _CACHE_DIR
    if cache_dir:
        os.makedirs(cache_dir, exist_ok=True)
        _CACHE_DIR = cache_dir
    return YTDLP_VERSION


class _BridgeLogger:
    """Captures yt-dlp diagnostics for the log sidecar and forwards them to Kotlin.

    yt-dlp drops warnings entirely when no_warnings is set, and those warnings are
    usually the only clue for "probe worked, download failed" reports.
    """

    def __init__(self, sink: Optional[Callable[[str, str], None]] = None, keep: int = 4000):
        self.lines: deque = deque(maxlen=keep)
        self._sink = sink

    def _record(self, level: str, message: Any) -> None:
        text = str(message)
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%H:%M:%S")
        self.lines.append(f"{stamp} {level.upper():7} {text}")
        # Only warnings and errors cross the JNI boundary; yt-dlp routes all of its
        # console chatter through debug(), which would be thousands of calls.
        if self._sink is None or level not in ("warning", "error"):
            return
        try:
            self._sink(level, text)
        except Exception:
            pass

    def debug(self, message: Any) -> None:
        self._record("debug", message)

    def info(self, message: Any) -> None:
        self._record("info", message)

    def warning(self, message: Any, *args: Any, **kwargs: Any) -> None:
        self._record("warning", message)

    def error(self, message: Any, *args: Any, **kwargs: Any) -> None:
        self._record("error", message)


def _filesize(fmt: dict) -> Optional[int]:
    for key in ("filesize", "filesize_approx"):
        value = fmt.get(key)
        if isinstance(value, (int, float)) and value > 0:
            return int(value)
    return None


def _base_opts(**extra: Any) -> dict:
    """Shared YoutubeDL options. FOSS: no site/age filtering - every extractor yt-dlp ships."""
    opts: dict = {
        "quiet": True,
        # Keep warnings flowing to the logger; only the console is silenced.
        "no_warnings": False,
        "noplaylist": True,
        "hls_prefer_native": True,
        "socket_timeout": 30,
        # None = do not skip age-restricted / adult extractors or videos.
        "age_limit": None,
        # Archive integrity: a download with holes must fail loudly rather than be
        # published as though it were complete.
        "skip_unavailable_fragments": False,
    }
    if _CACHE_DIR:
        opts["cachedir"] = _CACHE_DIR
    opts.update(extra)
    return opts


def _extract_info(ydl: YoutubeDL, url: str, *, download: bool) -> dict:
    """Run extract_info; if a site-specific extractor fails, retry with Generic.

    Upstream stops at the first matching extractor. On Android we always give
    Generic a second chance so HTML/direct-media heuristics can still succeed.
    """
    try:
        return ydl.extract_info(url, download=download)
    except Exception as site_err:
        params = dict(ydl.params)
        params["force_generic_extractor"] = True
        try:
            with YoutubeDL(params) as generic:
                return generic.extract_info(url, download=download)
        except Exception:
            # Prefer the site-specific error (usually more informative).
            raise site_err from None


def _resolve_single(ydl: YoutubeDL, url: str, *, download: bool) -> dict:
    """Extract info, descending into the first entry when handed a playlist/channel URL.

    noplaylist does not stop a playlist URL from yielding a playlist result, and a
    playlist result carries no "formats" - which used to surface as "no formats found".
    """
    info = _extract_info(ydl, url, download=download)
    for _ in range(3):
        is_playlist = info.get("_type") in ("playlist", "multi_video")
        if not is_playlist and info.get("formats"):
            break
        entries = info.get("entries")
        if entries is None:
            break
        if not isinstance(entries, list):
            entries = list(entries)
        first = next((e for e in entries if e), None)
        if first is None:
            break
        if first.get("formats"):
            info = first
            break
        nested = first.get("webpage_url") or first.get("url")
        if not nested:
            info = first
            break
        info = _extract_info(ydl, nested, download=download)
    return info


def probe(url: str) -> str:
    """Return JSON: title, duration, formats (height, format_id, has_video/audio, size)."""
    logger = _BridgeLogger()
    opts = _base_opts(skip_download=True, logger=logger)
    with YoutubeDL(opts) as ydl:
        info = _resolve_single(ydl, url, download=False)

    formats_out = []
    for fmt in info.get("formats") or []:
        # yt-dlp uses the string "none" for absent streams. Missing/null means unknown
        # (common for progressive MP4) - do not treat unknown as absent.
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
        # Canonical single-video URL. Downloads use this rather than the shared URL so a
        # playlist/channel share is resolved once instead of on every subsequent call.
        "webpage_url": info.get("webpage_url") or url,
        "extractor": info.get("extractor_key") or info.get("extractor"),
        "formats": formats_out,
    }
    return json.dumps(payload)


def download(
    url: str,
    format_spec: str,
    out_dir: str,
    options_json: str = "{}",
    progress_cb: Optional[Any] = None,
    cancel_cb: Optional[Any] = None,
    log_cb: Optional[Any] = None,
) -> str:
    """Download into out_dir. Returns JSON with the files written + provenance.

    [format_spec] is a yt-dlp format selector. A comma ("137,140") fetches several
    streams in a single extraction without invoking the (unavailable) merger, which is
    how the merge path avoids extracting the page once per stream. The out_template
    must then contain %(format_id)s or the streams overwrite each other.

    options_json keys: out_template, continuedl, archive_metadata, include_comments,
    write_subtitles, subtitle_langs.
    """
    os.makedirs(out_dir, exist_ok=True)
    options = json.loads(options_json or "{}")
    out_template = options.get("out_template") or "%(title).80B [%(id)s].%(ext)s"
    archive_metadata = bool(options.get("archive_metadata"))
    include_comments = bool(options.get("include_comments"))
    write_subtitles = bool(options.get("write_subtitles", archive_metadata))
    subtitle_langs = options.get("subtitle_langs") or ["all"]

    started_at = datetime.datetime.now(datetime.timezone.utc)

    def _call(cb: Any, name: str, *args: Any) -> Any:
        if cb is None:
            return None
        method = getattr(cb, name, None)
        if method is not None:
            return method(*args)
        return cb(*args)

    logger = _BridgeLogger(
        sink=(lambda level, text: _call(log_cb, "onLog", level, text)) if log_cb else None
    )

    def _cancelled() -> bool:
        if cancel_cb is None:
            return False
        try:
            return bool(_call(cancel_cb, "isCancelled"))
        except Exception:
            return False

    def _emit(payload: dict) -> None:
        if progress_cb is None:
            return
        _call(progress_cb, "onProgress", json.dumps(payload))

    # Paths the downloader itself reports as complete.
    finished_files: list = []

    def hook(d: dict) -> None:
        if _cancelled():
            raise DownloadCancelled("Download cancelled")
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
            name = d.get("filename")
            if name:
                finished_files.append(name)
            _emit({"status": "finished", "filename": name})

    opts = _base_opts(
        format=format_spec,
        outtmpl=os.path.join(out_dir, out_template),
        continuedl=bool(options.get("continuedl", True)),
        noprogress=True,
        progress_hooks=[hook],
        postprocessors=[],
        keepvideo=False,
        retries=5,
        fragment_retries=5,
        logger=logger,
    )
    if archive_metadata:
        # Archivist sidecars; opt-in only - info.json can contain personal data.
        opts["writeinfojson"] = True
        opts["writedescription"] = True
        opts["writethumbnail"] = True
        # Comments are a separate opt-in: they can add many minutes and hundreds of
        # megabytes on a popular video.
        opts["getcomments"] = include_comments
    if write_subtitles:
        # Standalone .vtt/.srt need no ffmpeg, and captions are often the only textual
        # record of a video worth archiving.
        opts["writesubtitles"] = True
        opts["writeautomaticsub"] = True
        opts["subtitleslangs"] = list(subtitle_langs)

    with YoutubeDL(opts) as ydl:
        info = _resolve_single(ydl, url, download=True)
        files = _downloaded_files(ydl, info, finished_files, out_dir)

    if not files:
        raise RuntimeError("yt-dlp reported no downloaded file")

    primary = files[0]["path"]
    sidecars = _collect_sidecars(out_dir, [f["path"] for f in files])
    if archive_metadata and logger.lines:
        log_path = _write_log(primary, logger.lines)
        if log_path:
            sidecars.append({"path": log_path, "kind": "log"})

    return json.dumps(
        {
            "filepath": primary,
            "files": files,
            "title": info.get("title"),
            "ext": os.path.splitext(primary)[1].lstrip(".") or info.get("ext"),
            "sidecars": sidecars,
            "provenance": _provenance(url, info, primary, format_spec, started_at, files),
        }
    )


def _downloaded_files(
    ydl: YoutubeDL,
    info: dict,
    finished_files: list,
    out_dir: str,
) -> list:
    """Every file yt-dlp actually wrote, in requested order.

    prepare_filename() recomputes a name and can diverge from what was written (ext
    resolution, sanitization, pre-existing files), so prefer what yt-dlp reports for
    the downloads it actually performed.
    """
    files: list = []
    seen: set = set()

    def add(path, entry=None):
        if not path or path in seen or not os.path.isfile(path):
            return
        seen.add(path)
        entry = entry or {}
        files.append(
            {
                "path": path,
                "format_id": str(entry.get("format_id") or ""),
                "ext": os.path.splitext(path)[1].lstrip("."),
                "bytes": os.path.getsize(path),
                "vcodec": entry.get("vcodec") or "",
                "acodec": entry.get("acodec") or "",
            }
        )

    for entry in info.get("requested_downloads") or []:
        add(entry.get("filepath") or entry.get("_filename"), entry)

    if files:
        return files

    for path in finished_files:
        add(path)
    if files:
        return files

    add(ydl.prepare_filename(info), info)
    if files:
        return files

    # Last resort: newest media file in the (per-download, private) work directory.
    candidates = [
        os.path.join(out_dir, name)
        for name in os.listdir(out_dir)
        if name.lower().endswith(MEDIA_EXTS)
    ]
    if candidates:
        add(max(candidates, key=os.path.getmtime))
    return files


def _collect_sidecars(out_dir: str, media_paths: list) -> list:
    """Everything in the work directory that is not one of the media files.

    The work directory is private to a single download, so a sweep is both simpler and
    more reliable than stem matching - it picks up subtitles whatever language tag they
    carry, and it still works when the media files are named per format id.
    """
    found: list = []
    media = {os.path.basename(p) for p in media_paths}

    try:
        names = sorted(os.listdir(out_dir))
    except OSError:
        return found

    for name in names:
        if name in media:
            continue
        full = os.path.join(out_dir, name)
        if not os.path.isfile(full):
            continue
        lower = name.lower()
        if lower.endswith((".part", ".ytdl", ".tmp")):
            continue
        if lower.endswith(".info.json"):
            kind = "infojson"
        elif lower.endswith(".description"):
            kind = "description"
        elif lower.endswith(SUBTITLE_EXTS):
            kind = "subtitle"
        elif lower.endswith(THUMBNAIL_EXTS):
            kind = "thumbnail"
        elif lower.endswith(MEDIA_EXTS):
            # Another media file (e.g. a stream from an earlier attempt).
            continue
        else:
            kind = "other"
        found.append({"path": full, "kind": kind})
    return found


def _write_log(media_path: str, lines) -> Optional[str]:
    path = os.path.splitext(media_path)[0] + ".ytdlp.log"
    try:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write("\n".join(lines))
            handle.write("\n")
        return path
    except OSError:
        return None


def _provenance(
    original_url: str,
    info: dict,
    filepath: str,
    requested_format: str,
    started_at: datetime.datetime,
    files: Optional[list] = None,
) -> dict:
    """Facts an archivist needs in order to establish where this file came from."""
    downloads = info.get("requested_downloads") or []
    picked = downloads[0] if downloads else {}
    try:
        size = os.path.getsize(filepath)
    except OSError:
        size = None
    streams = [
        {
            "format_id": f.get("format_id"),
            "ext": f.get("ext"),
            "vcodec": f.get("vcodec") or None,
            "acodec": f.get("acodec") or None,
            "bytes": f.get("bytes"),
        }
        for f in (files or [])
    ]
    return {
        # Every stream fetched, so a merge path records both halves and not just the
        # one that happens to be primary.
        "streams": streams,
        "original_url": original_url,
        "webpage_url": info.get("webpage_url"),
        "extractor": info.get("extractor_key") or info.get("extractor"),
        "id": info.get("id"),
        "title": info.get("title"),
        "uploader": info.get("uploader"),
        "uploader_id": info.get("uploader_id"),
        "upload_date": info.get("upload_date"),
        "duration": info.get("duration"),
        "requested_format": requested_format,
        "format_id": picked.get("format_id") or info.get("format_id"),
        "format_note": picked.get("format_note") or info.get("format_note"),
        "vcodec": picked.get("vcodec") or info.get("vcodec"),
        "acodec": picked.get("acodec") or info.get("acodec"),
        "width": picked.get("width") or info.get("width"),
        "height": picked.get("height") or info.get("height"),
        "fps": picked.get("fps") or info.get("fps"),
        "tbr": picked.get("tbr") or info.get("tbr"),
        "protocol": picked.get("protocol") or info.get("protocol"),
        "container": os.path.splitext(filepath)[1].lstrip("."),
        "bytes": size,
        "ytdlp_version": YTDLP_VERSION,
        "ytdlp_git_head": YTDLP_GIT_HEAD,
        "download_started_utc": started_at.isoformat(timespec="seconds"),
        "download_finished_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(
            timespec="seconds"
        ),
    }


def ytdlp_version() -> str:
    return YTDLP_VERSION


def ping() -> str:
    import sys

    sub = sys.modules.get("subprocess")
    stub = getattr(sub, "_android_stub", False) if sub else False
    return (
        f"yt-dlp {YTDLP_VERSION}; subprocess_stub={stub}; "
        f"generic_fallback=1; cachedir={_CACHE_DIR or 'default'}"
    )
