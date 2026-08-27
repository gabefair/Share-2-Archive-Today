# Vendored yt-dlp

yt-dlp lives at [`https://github.com/yt-dlp/yt-dlp`](https://github.com/yt-dlp/yt-dlp) under `third_party/yt-dlp`.

## Pinned release (default)

On every `:ytdlp` / app build, Gradle runs:

```bash
python3 tools/fetch_ytdlp_latest.py
python3 tools/trim_ytdlp.py --out ytdlp/build/generated/ytdlp
```

`fetch_ytdlp_latest.py` checks out the tag in [`ytdlp/YTDLP_PIN`](ytdlp/YTDLP_PIN) so FOSS/F-Droid builds are reproducible. Every download still records the packaged `yt_dlp.version.__version__` in its provenance manifest.

| Env | Effect |
|-----|--------|
| *(none)* | Use `ytdlp/YTDLP_PIN` |
| `S2A_YTDLP_TAG=YYYY.MM.DD` | Force that tag |
| `S2A_YTDLP_LATEST=1` | Query GitHub for the newest release tag |

If the desired tag is already checked out, no network is required.

## Trim for Chaquopy

The trim stubs `external.py` (no ffmpeg/aria2c on Android). The stub is **generated from the upstream module's public surface** rather than hand-written, so a new external-downloader class in yt-dlp cannot turn into an `ImportError` or `AttributeError` on a user's device. Every class becomes an `ExternalFD` subclass reporting `available() == False`, and `FFmpegFD.can_merge_formats()` returns `False` so the native downloaders are used. If upstream drops a symbol the stub must export, trim **fails the build** instead of warning.

**No site / adult filtering.** The FOSS download build keeps the full extractor set (including adult sites). `ytdlp_bridge` sets `age_limit: None` so yt-dlp does not skip age-restricted videos. The Play flavor has no download feature at all — it does not ship a reduced extractor list.

## Android / no JS runtime

Modern yt-dlp prefers a JS runtime (Deno/Node) for some YouTube clients. On-device we usually have none, so yt-dlp falls back to its **JS-less** client set (e.g. `visionos`) and mostly HLS (`m3u8_native`) formats. That is expected — do **not** invent custom `player_client` overrides unless comparing against current upstream docs.

## ABI

FOSS is **arm64-v8a only** (Chaquopy + yt-dlp size and native-wheel limits). The `dev` flavor also includes `x86_64` for emulators. There is no `armeabi-v7a` build.

## Download behaviour

Downloads run as a **WorkManager** long-running / expedited worker (`VideoDownloadWorker`) with a foreground notification (cancel + history). That replaces a hand-rolled `dataSync` foreground service and avoids Android 15 `Service.onTimeout` daily caps for the download path. Partial files are kept; the same `downloadId` resumes work under the scratch dir. History **Retry** re-enqueues with `ExistingWorkPolicy.REPLACE` and the stored format selectors.

### Archive integrity

- `skip_unavailable_fragments` is **off**. A fragmented download that loses a segment fails loudly instead of publishing a file with silent gaps.
- `concurrent_fragment_downloads` is **4** (bounded parallelism for HLS/DASH).
- The output path comes from `requested_downloads[0].filepath` (falling back to the `finished` progress hook), not `prepare_filename()`, which recomputes a name and can diverge from what was written.
- Container and MIME type are derived from the file yt-dlp actually produced, so a WebM or MKV is never labelled `.mp4`.
- Every download writes a `.manifest.json` next to it: shared and resolved URL, UTC timestamps, app and yt-dlp versions, every stream fetched, a SHA-256 of every saved file, and whether Media3 re-containered the bytes on device.
- The manifest also carries a `page_archive_url` — the archive.today submission link for the source page — and both the completion notification and the history long-press menu offer **Archive page**, so the media stays connected to an archived rendering of the page it came from. The app never submits to archive.today itself; it opens the link in the browser, because automated submissions are gated and would risk getting the user's address blocked.

### One extraction per download

A merged download uses a **comma** format selector (`"137,140"`), which fetches both streams in a single `extract_info`. Requesting `"137+140"` would ask yt-dlp to merge and abort with *"you have requested merging of multiple formats but ffmpeg is not installed"*, and running a separate download call per stream tripled the requests to the site for every merged download — which matters for a tool whose users care about not being rate-limited or blocked.

The output template must contain `%(format_id)s` when the selector has a comma, or the streams overwrite each other. If an extractor collapses the selector to one stream, the executor falls back to fetching them one at a time.

Playlist and channel shares are resolved to a canonical single-video URL during the probe, and the download uses that URL, so the resolution happens once rather than on every call. The UI toasts when a playlist/channel URL was narrowed to the first entry, and duplicate detection also matches `webpage_url` / video id.

### Sidecars

With “Save archivist metadata” on, yt-dlp writes `.info.json`, the description, the thumbnail, **subtitles and auto-captions** (device locales + `en` — not `all`, which 429s YouTube), and a `.ytdlp.log` of the run's warnings. Caption/side-file download errors use `ignoreerrors: only_download` so a finished media file is kept. Comments are a **separate** opt-in because scraping them can add many minutes and hundreds of megabytes.

Repeated “no impersonate target” warnings are suppressed after the first (Android has no `curl_cffi`). Missing JS runtime is expected on-device; yt-dlp falls back to JS-less YouTube clients.

A download that produces more than one file gets its own folder under `Download/Share2Archive/` so media and metadata cannot be separated or mismatched by MediaStore's de-duplication.

### Quality selection

The picker prefers `m3u8_native` over progressive `https`, soft-caps at 1080p, and prefers a combined A/V format to avoid a merge.

**Archive quality** (checkbox) removes the cap and prefers the highest-bitrate stream even when that requires a merge — on YouTube the adaptive streams are a much higher bitrate than the progressive one at the same height.

When a merge is required, only **AVC** (`avc1`/`avc3`/`h264`/`mp4v`) + AAC-family audio are treated as safe Media3 transmux targets. HEVC and AV1 still download but are marked mux-risk (prefer an AVC sibling when present). VP9/Opus need WebM, which Media3 cannot write. If the merge still fails, **both streams are published side by side** rather than discarding a completed transfer; the manifest records the failure and the `ffmpeg -c copy` command to merge them off-device.

When **Save archivist metadata** is on and a merge succeeds, title / description / uploader / date / source URL / cover art are also **embedded into the MP4** (iTunes/QuickTime tags) so players see them without opening the sidecars. Sidecar files are still written for a lossless archival copy.

Space preflight uses a **4×** occupancy multiplier when mux or audio-extract will keep intermediate streams on disk (2× for progressive).

### Cancellation and limits

- The ongoing notification has a **Cancel** action; WorkManager stop + a flag polled from the Python progress hook (and from Media3 export) abort mid-work.
- Download ids are derived from URL + format, so re-sharing a link resumes the existing partial instead of starting over. Abandoned scratch directories are swept after 7 days.

## Tests

```bash
./gradlew :ytdlp:check          # JVM unit tests + the Python bridge tests
./gradlew :ytdlp:connectedDebugAndroidTest   # Media3 mux/cancel on a device/emulator
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
| `foss` | Full download feature (default, arm64 only) |
| `dev` | Full download + `x86_64` for emulators |
