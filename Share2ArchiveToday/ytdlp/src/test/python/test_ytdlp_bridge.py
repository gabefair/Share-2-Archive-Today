"""Host-side tests for ytdlp_bridge against a local HTTP server.

Runs without network access and without a device: a throwaway HTTP server stands in
for a media host, so the Generic extractor, the native HLS downloader, the progress
hooks, sidecar collection and provenance all get exercised in CI.

    python3 ytdlp/src/test/python/test_ytdlp_bridge.py
"""
from __future__ import annotations

import http.server
import json
import os
import shutil
import socketserver
import sys
import tempfile
import threading
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(REPO / "ytdlp" / "src" / "main" / "python"))
sys.path.insert(0, str(REPO / "ytdlp" / "build" / "generated" / "ytdlp"))

import ytdlp_bridge  # noqa: E402

# A tiny but structurally valid MP4 (ftyp + moov stubs + mdat). yt-dlp only needs to
# fetch bytes; nothing here has to decode.
MP4_BYTES = (
    b"\x00\x00\x00\x18ftypisom\x00\x00\x02\x00isomiso2"
    + b"\x00\x00\x00\x08free"
    + b"\x00\x00\x04\x00mdat"
    + bytes(1024)
)
SEGMENT = b"\x47" + bytes(187)  # one MPEG-TS-looking packet


class _Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):  # silence per-request logging
        pass

    def _send(self, body: bytes, content_type: str):
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Accept-Ranges", "bytes")
        self.end_headers()
        self.wfile.write(body)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/video.mp4":
            self._send(MP4_BYTES, "video/mp4")
        elif path == "/stream.m3u8":
            playlist = (
                "#EXTM3U\n"
                "#EXT-X-VERSION:3\n"
                "#EXT-X-TARGETDURATION:1\n"
                "#EXT-X-MEDIA-SEQUENCE:0\n"
                "#EXTINF:1.0,\nseg0.ts\n"
                "#EXTINF:1.0,\nseg1.ts\n"
                "#EXTINF:1.0,\nseg2.ts\n"
                "#EXT-X-ENDLIST\n"
            ).encode()
            self._send(playlist, "application/vnd.apple.mpegurl")
        elif path.startswith("/seg") and path.endswith(".ts"):
            self._send(SEGMENT * 8, "video/mp2t")
        elif path == "/master.m3u8":
            # Separate video renditions + a separate audio group, so yt-dlp reports
            # video-only and audio-only formats that have to be merged.
            playlist = (
                "#EXTM3U\n"
                '#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",'
                'DEFAULT=YES,LANGUAGE="en",URI="audio.m3u8"\n'
                '#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,'
                'CODECS="avc1.42c01e,mp4a.40.2",AUDIO="aud"\n'
                "video360.m3u8\n"
                '#EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720,'
                'CODECS="avc1.4d401f,mp4a.40.2",AUDIO="aud"\n'
                "video720.m3u8\n"
            ).encode()
            self._send(playlist, "application/vnd.apple.mpegurl")
        elif path in ("/video360.m3u8", "/video720.m3u8", "/audio.m3u8"):
            stem = path.strip("/").removesuffix(".m3u8")
            playlist = (
                "#EXTM3U\n"
                "#EXT-X-VERSION:3\n"
                "#EXT-X-TARGETDURATION:1\n"
                "#EXT-X-MEDIA-SEQUENCE:0\n"
                f"#EXTINF:1.0,\n{stem}-0.ts\n"
                f"#EXTINF:1.0,\n{stem}-1.ts\n"
                "#EXT-X-ENDLIST\n"
            ).encode()
            self._send(playlist, "application/vnd.apple.mpegurl")
        elif path == "/gone.ts":
            # Deliberately absent, to prove a lost fragment is not silently tolerated.
            self.send_error(404)
        elif path.endswith(".ts"):
            self._send(SEGMENT * 8, "video/mp2t")
        elif path == "/missing-segment.m3u8":
            playlist = (
                "#EXTM3U\n"
                "#EXT-X-VERSION:3\n"
                "#EXT-X-TARGETDURATION:1\n"
                "#EXTINF:1.0,\nseg0.ts\n"
                "#EXTINF:1.0,\ngone.ts\n"
                "#EXT-X-ENDLIST\n"
            ).encode()
            self._send(playlist, "application/vnd.apple.mpegurl")
        else:
            self.send_error(404)


class _Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


class BridgeTest(unittest.TestCase):
    server: _Server
    base: str

    @classmethod
    def setUpClass(cls):
        cls.server = _Server(("127.0.0.1", 0), _Handler)
        cls.base = f"http://127.0.0.1:{cls.server.server_address[1]}"
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.cache = tempfile.mkdtemp(prefix="ytdlp-cache-")
        ytdlp_bridge.configure(cls.cache)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        shutil.rmtree(cls.cache, ignore_errors=True)

    def setUp(self):
        self.work = tempfile.mkdtemp(prefix="ytdlp-work-")
        self.addCleanup(shutil.rmtree, self.work, ignore_errors=True)

    def test_probe_direct_media_reports_a_format(self):
        payload = json.loads(ytdlp_bridge.probe(f"{self.base}/video.mp4"))
        self.assertTrue(payload["formats"], "expected at least one format")
        self.assertTrue(payload["webpage_url"].endswith("/video.mp4"))

    def test_download_returns_the_path_actually_written(self):
        raw = ytdlp_bridge.download(
            f"{self.base}/video.mp4",
            "mp4",
            self.work,
            json.dumps({"out_template": "video.%(ext)s"}),
        )
        result = json.loads(raw)
        self.assertTrue(
            os.path.isfile(result["filepath"]),
            f"reported path does not exist: {result['filepath']}",
        )
        self.assertEqual(os.path.getsize(result["filepath"]), len(MP4_BYTES))
        self.assertEqual(result["ext"], "mp4")

    def test_provenance_records_source_and_ytdlp_version(self):
        raw = ytdlp_bridge.download(
            f"{self.base}/video.mp4",
            "mp4",
            self.work,
            json.dumps({"out_template": "video.%(ext)s"}),
        )
        prov = json.loads(raw)["provenance"]
        self.assertEqual(prov["original_url"], f"{self.base}/video.mp4")
        self.assertEqual(prov["bytes"], len(MP4_BYTES))
        self.assertEqual(prov["container"], "mp4")
        self.assertTrue(prov["ytdlp_version"])
        self.assertTrue(prov["download_started_utc"].endswith("+00:00"))

    def test_progress_hook_reports_bytes(self):
        seen = []

        class Cb:
            def onProgress(self, text):
                seen.append(json.loads(text))

        ytdlp_bridge.download(
            f"{self.base}/video.mp4",
            "mp4",
            self.work,
            json.dumps({"out_template": "video.%(ext)s"}),
            Cb(),
        )
        self.assertTrue(any(p["status"] == "finished" for p in seen))

    def test_cancellation_aborts_the_download(self):
        class Cancel:
            def isCancelled(self):
                return True

        with self.assertRaises(Exception):
            ytdlp_bridge.download(
                f"{self.base}/stream.m3u8",
                "0",
                self.work,
                json.dumps({"out_template": "video.%(ext)s"}),
                None,
                Cancel(),
            )

    def test_native_hls_download_produces_all_segments(self):
        raw = ytdlp_bridge.download(
            f"{self.base}/stream.m3u8",
            "0",
            self.work,
            json.dumps({"out_template": "video.%(ext)s"}),
        )
        result = json.loads(raw)
        self.assertTrue(os.path.isfile(result["filepath"]))
        # Three segments of 8 fake TS packets each.
        self.assertEqual(os.path.getsize(result["filepath"]), 3 * len(SEGMENT) * 8)

    def test_missing_fragment_fails_instead_of_publishing_a_hole(self):
        with self.assertRaises(Exception):
            ytdlp_bridge.download(
                f"{self.base}/missing-segment.m3u8",
                "0",
                self.work,
                json.dumps({"out_template": "video.%(ext)s"}),
            )

    def test_sidecars_pick_up_subtitles_and_thumbnails_by_stem(self):
        media = os.path.join(self.work, "video.mp4")
        Path(media).write_bytes(MP4_BYTES)
        for name in (
            "video.info.json",
            "video.en.vtt",
            "video.es-419.srt",
            "video.jpg",
            "video.description",
            "video.mp4.part",
        ):
            Path(self.work, name).write_text("x")

        kinds = {}
        for side in ytdlp_bridge._collect_sidecars(self.work, [media]):
            kinds.setdefault(side["kind"], []).append(os.path.basename(side["path"]))

        self.assertEqual(kinds["infojson"], ["video.info.json"])
        self.assertEqual(sorted(kinds["subtitle"]), ["video.en.vtt", "video.es-419.srt"])
        self.assertEqual(kinds["thumbnail"], ["video.jpg"])
        self.assertEqual(kinds["description"], ["video.description"])
        flat = [n for names in kinds.values() for n in names]
        self.assertNotIn("video.mp4.part", flat)

    # --- multi-stream selection (the merge path) ---

    def _master_formats(self):
        payload = json.loads(ytdlp_bridge.probe(f"{self.base}/master.m3u8"))
        video = next(f for f in payload["formats"] if f["has_video"] and not f["has_audio"])
        audio = next(f for f in payload["formats"] if f["has_audio"] and not f["has_video"])
        return video, audio

    def test_master_playlist_exposes_separate_video_and_audio(self):
        video, audio = self._master_formats()
        self.assertTrue(video["height"] in (360, 720))
        self.assertEqual("none", video["acodec"])

    def test_comma_selector_returns_both_streams_from_one_extraction(self):
        video, audio = self._master_formats()
        raw = ytdlp_bridge.download(
            f"{self.base}/master.m3u8",
            f"{video['format_id']},{audio['format_id']}",
            self.work,
            json.dumps({"out_template": "stream_%(format_id)s.%(ext)s"}),
        )
        result = json.loads(raw)
        self.assertEqual(2, len(result["files"]))
        ids = [f["format_id"] for f in result["files"]]
        self.assertEqual([video["format_id"], audio["format_id"]], ids)
        # Distinct paths: a template without %(format_id)s makes them overwrite.
        paths = {f["path"] for f in result["files"]}
        self.assertEqual(2, len(paths))
        for f in result["files"]:
            self.assertTrue(os.path.isfile(f["path"]))
            self.assertGreater(f["bytes"], 0)

    def test_provenance_lists_every_stream_for_a_merge(self):
        video, audio = self._master_formats()
        raw = ytdlp_bridge.download(
            f"{self.base}/master.m3u8",
            f"{video['format_id']},{audio['format_id']}",
            self.work,
            json.dumps({"out_template": "stream_%(format_id)s.%(ext)s"}),
        )
        streams = json.loads(raw)["provenance"]["streams"]
        self.assertEqual(2, len(streams))
        self.assertEqual(
            {video["format_id"], audio["format_id"]},
            {s["format_id"] for s in streams},
        )

    def test_plus_selector_still_fails_without_ffmpeg(self):
        # Documents why the comma selector is used: "+" asks yt-dlp to merge, and there
        # is no merger on device.
        video, audio = self._master_formats()
        with self.assertRaises(Exception):
            ytdlp_bridge.download(
                f"{self.base}/master.m3u8",
                f"{video['format_id']}+{audio['format_id']}",
                self.work,
                json.dumps({"out_template": "stream_%(format_id)s.%(ext)s"}),
            )

    def test_sidecars_are_found_when_streams_are_named_per_format(self):
        for name in ("stream_800.mp4", "stream_aud.mp4"):
            Path(self.work, name).write_bytes(MP4_BYTES)
        for name in ("master.info.json", "master.en.vtt", "master.jpg", "stream_800.mp4.part"):
            Path(self.work, name).write_text("x")

        media = [os.path.join(self.work, "stream_800.mp4"), os.path.join(self.work, "stream_aud.mp4")]
        kinds = {}
        for side in ytdlp_bridge._collect_sidecars(self.work, media):
            kinds.setdefault(side["kind"], []).append(os.path.basename(side["path"]))

        self.assertEqual(["master.info.json"], kinds["infojson"])
        self.assertEqual(["master.en.vtt"], kinds["subtitle"])
        self.assertEqual(["master.jpg"], kinds["thumbnail"])
        flat = [n for names in kinds.values() for n in names]
        self.assertNotIn("stream_800.mp4", flat)
        self.assertNotIn("stream_aud.mp4", flat)
        self.assertNotIn("stream_800.mp4.part", flat)

    def test_strict_fragments_is_on_by_default(self):
        self.assertIs(ytdlp_bridge._base_opts()["skip_unavailable_fragments"], False)

    def test_concurrent_fragment_downloads_is_bounded(self):
        # Parallel HLS/DASH fragment fetch without thrashing Chaquopy/IO.
        self.assertEqual(ytdlp_bridge._base_opts()["concurrent_fragment_downloads"], 4)

    def test_cachedir_is_redirected_to_app_storage(self):
        self.assertEqual(ytdlp_bridge._base_opts()["cachedir"], self.cache)


if __name__ == "__main__":
    unittest.main(verbosity=2)
