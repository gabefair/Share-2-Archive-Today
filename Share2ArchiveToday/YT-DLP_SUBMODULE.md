# Vendored yt-dlp

yt-dlp lives at [`https://github.com/yt-dlp/yt-dlp`](https://github.com/yt-dlp/yt-dlp) under `third_party/yt-dlp`.

## Always build against the latest *release*

On every `:ytdlp` / app build, Gradle runs:

```bash
python3 tools/fetch_ytdlp_latest.py
python3 tools/trim_ytdlp.py --out ytdlp/build/generated/ytdlp
```

`fetch_ytdlp_latest.py` queries GitHub for the **latest release tag** (not `master`) and checks it out. That is what fixes YouTube “probe OK / download 403” when extractors age out.

Because the version is not pinned in the source tree, every download records the yt-dlp release that produced it in its provenance manifest (read at runtime from the packaged `yt_dlp.version.__version__`, so it always matches the code that actually ran).

### Pin / offline (optional)

```bash
export S2A_YTDLP_TAG=2026.08.19   # skip “latest” lookup; use this tag
./gradlew :app:assembleFossDebug
```

Use a pin for reproducible CI / F-Droid builds. If the tag is already checked out, no network is required.

## Trim for Chaquopy

The trim stubs `external.py` (no ffmpeg/aria2c on Android). The stub is **generated from the upstream module's public surface** rather than hand-written, so a new external-downloader class in yt-dlp cannot turn into an `ImportError` or `AttributeError` on a user's device. Every class becomes an `ExternalFD` subclass reporting `available() == False`, and `FFmpegFD.can_merge_formats()` returns `False` so the native downloaders are used.

**No site / adult filtering.** The FOSS download build keeps the full extractor set (including adult sites). `ytdlp_bridge` sets `age_limit: None` so yt-dlp does not skip age-restricted videos. The Play flavor has no download feature at all — it does not ship a reduced extractor list.

## Android / no JS runtime

Modern yt-dlp prefers a JS runtime (Deno/Node) for some YouTube clients. On-device we usually have none, so yt-dlp falls back to its **JS-less** client set (e.g. `visionos`) and mostly HLS (`m3u8_native`) formats. That is expected — do **not** invent custom `player_client` overrides unless comparing against current upstream docs.

## Download behaviour

### Archive integrity

- `skip_unavailable_fragments` is **off**. A fragmented download that loses a segment fails loudly instead of publishing a file with silent gaps.
- The output path comes from `requested_downloads[0].filepath` (falling back to the `finished` progress hook), not `prepare_filename()`, which recomputes a name and can diverge from what was written.
- Container and MIME type are derived from the file yt-dlp actually produced, so a WebM or MKV is never labelled `.mp4`.
- Every download writes a `.manifest.json` next to it: shared and resolved URL, UTC timestamps, app and yt-dlp versions, every stream fetched, a SHA-256 of every saved file, and whether Media3 re-containered the bytes on device.
- The manifest also carries a `page_archive_url` — the archive.today submission link for the source page — and both the completion notification and the history long-press menu offer **Archive page**, so the media stays connected to an archived rendering of the page it came from. The app never submits to archive.today itself; it opens the link in the browser, because automated submissions are gated and would risk getting the user's address blocked.

### One extraction per download

A merged download uses a **comma** format selector (`"137,140"`), which fetches both streams in a single `extract_info`. Requesting `"137+140"` would ask yt-dlp to merge and abort with *"you have requested merging of multiple formats but ffmpeg is not installed"*, and running a separate download call per stream tripled the requests to the site for every merged download — which matters for a tool whose users care about not being rate-limited or blocked.

The output template must contain `%(format_id)s` when the selector has a comma, or the streams overwrite each other. If an extractor collapses the selector to one stream, the executor falls back to fetching them one at a time.

Playlist and channel shares are resolved to a canonical single-video URL during the probe, and the download uses that URL, so the resolution happens once rather than on every call.

### Sidecars

With “Save archivist metadata” on, yt-dlp writes `.info.json`, the description, the thumbnail, **subtitles and auto-captions** (`subtitleslangs: ["all"]`, no ffmpeg needed), and a `.ytdlp.log` of the run's warnings. Comments are a **separate** opt-in because scraping them can add many minutes and hundreds of megabytes.

A download that produces more than one file gets its own folder under `Download/Share2Archive/` so media and metadata cannot be separated or mismatched by MediaStore's de-duplication.

### Quality selection

The picker prefers `m3u8_native` over progressive `https`, soft-caps at 1080p, and prefers a combined A/V format to avoid a merge.

**Archive quality** (checkbox) removes the cap and prefers the highest-bitrate stream even when that requires a merge — on YouTube the adaptive streams are a much higher bitrate than the progressive one at the same height.

When a merge is required, codecs the platform MP4 muxer can actually contain (`avc1`/`hev1`/`av01` + `mp4a`) are preferred over VP9/Opus, which are only containerable in WebM. If the merge still fails, **both streams are published side by side** rather than discarding a completed transfer; the manifest records the failure and the `ffmpeg -c copy` command to merge them off-device.

### Cancellation and limits

- The ongoing notification has a **Cancel** action; the flag is polled from the Python progress hook, which aborts yt-dlp mid-download.
- `Service.onTimeout` is handled for Android 15+, where a `dataSync` foreground service has a capped daily runtime. Partial files are kept.
- Download ids are derived from URL + format, so re-sharing a link resumes the existing partial instead of starting over. Abandoned scratch directories are swept after 7 days.

## Tests

```bash
./gradlew :ytdlp:check          # JVM unit tests + the Python bridge tests
./gradlew :app:testFossDebugUnitTest
python3 ytdlp/src/test/python/test_ytdlp_bridge.py   # directly, for iteration
```

`ytdlp/src/test/python/test_ytdlp_bridge.py` runs the real vendored yt-dlp against a throwaway local HTTP server, so it needs no network and no device. It serves a direct MP4, a media playlist, and an HLS master playlist with separate video renditions and an audio group, which lets it cover the Generic extractor, the native HLS downloader, progress hooks, cancellation, sidecar collection, provenance, multi-stream (comma) selection, and that a missing fragment fails rather than producing a file with holes.

The JVM tests cover the pure logic: quality/codec selection, failure classification, naming and truncation, MIME mapping, and manifest construction.

## NixOS builds

AGP’s bundled `aapt2` is a generic Linux binary. On NixOS run Gradle under `steam-run` (or enable `nix-ld`):

```bash
./gradlew --stop          # a daemon started outside steam-run will keep failing
steam-run ./gradlew :app:assembleFossDebug :app:assemblePlayDebug
```

## Flavors

| Flavor | Contents |
|--------|----------|
| `play` | Archive + clipboard only (no Chaquopy) |
| `foss` | Full download feature (default, arm64) |
| `dev` | Full download + `x86_64` for emulators |
