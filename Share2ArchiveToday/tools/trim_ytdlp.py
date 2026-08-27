#!/usr/bin/env python3
"""Trim vendored yt-dlp into a Chaquopy-friendly package tree.

Generates lazy_extractors.py, copies yt_dlp/, drops CLI-only / non-Android pieces.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

DROP_FILES = {
    "yt_dlp/__main__.py",
    # Keep options.py: package __init__ imports parseOpts at load time.
    # Keep update.py: YoutubeDL/options import several symbols from it.
}
DROP_DIRS = {
    "yt_dlp/__pyinstaller",
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--src",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "third_party" / "yt-dlp",
    )
    parser.add_argument(
        "--out",
        type=Path,
        required=True,
        help="Output directory that will contain yt_dlp/ (e.g. build/generated/ytdlp)",
    )
    args = parser.parse_args()

    src: Path = args.src
    out: Path = args.out
    if not (src / "yt_dlp").is_dir():
        print(f"error: yt-dlp package not found at {src / 'yt_dlp'}", file=sys.stderr)
        return 1

    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    # Generate lazy extractors in the source tree (idempotent enough for CI).
    lazy_script = src / "devscripts" / "make_lazy_extractors.py"
    lazy_out = src / "yt_dlp" / "extractor" / "lazy_extractors.py"
    if lazy_script.is_file():
        subprocess.check_call([sys.executable, str(lazy_script), str(lazy_out)], cwd=src)

    dest_pkg = out / "yt_dlp"
    shutil.copytree(
        src / "yt_dlp",
        dest_pkg,
        ignore=shutil.ignore_patterns("__pycache__", "*.pyc", ".mypy_cache"),
    )

    for rel in DROP_FILES:
        path = out / rel
        if path.exists():
            path.unlink()

    for rel in DROP_DIRS:
        path = out / rel
        if path.is_dir():
            shutil.rmtree(path)

    # external.py shells out to aria2c/curl/wget/ffmpeg — unusable in the app sandbox.
    # Keep the public names yt-dlp imports / calls; report unavailable so native FDs are used.
    external = dest_pkg / "downloader" / "external.py"
    if external.exists():
        external.write_text(
            '"""Stub: external downloaders are not available on Android."""\n'
            "from ..utils import DownloadError\n"
            "\n"
            "\n"
            "class ExternalFD:\n"
            "    EXE_NAME = 'external'\n"
            "    SUPPORTED_PROTOCOLS = ()\n"
            "    SUPPORTED_FEATURES = ()\n"
            "\n"
            "    def __init__(self, ydl, params):\n"
            "        self.ydl = ydl\n"
            "        self.params = params\n"
            "\n"
            "    @classmethod\n"
            "    def get_basename(cls):\n"
            "        return cls.__name__[:-2].lower()\n"
            "\n"
            "    @classmethod\n"
            "    def available(cls, path=None):\n"
            "        return False\n"
            "\n"
            "    @classmethod\n"
            "    def supports(cls, info_dict):\n"
            "        return False\n"
            "\n"
            "    @classmethod\n"
            "    def can_download(cls, info_dict, path=None):\n"
            "        return False\n"
            "\n"
            "    def real_download(self, filename, info_dict):\n"
            "        raise DownloadError(\n"
            "            f'{self.get_basename()} is not available on Android; '\n"
            "            'use a progressive/native format instead'\n"
            "        )\n"
            "\n"
            "\n"
            "class FFmpegFD(ExternalFD):\n"
            "    EXE_NAME = 'ffmpeg'\n"
            "    SUPPORTED_PROTOCOLS = (\n"
            "        'http', 'https', 'ftp', 'ftps', 'm3u8', 'm3u8_native',\n"
            "        'rtsp', 'rtmp', 'rtmp_ffmpeg', 'mms', 'http_dash_segments',\n"
            "    )\n"
            "\n"
            "    @classmethod\n"
            "    def can_merge_formats(cls, info_dict, params):\n"
            "        return False\n"
            "\n"
            "\n"
            "def list_external_downloaders():\n"
            "    return []\n"
            "\n"
            "\n"
            "def get_external_downloader(external_downloader):\n"
            "    return None\n"
        )

    print(f"Wrote trimmed yt-dlp to {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
