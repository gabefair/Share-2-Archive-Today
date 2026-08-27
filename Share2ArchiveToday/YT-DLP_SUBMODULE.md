# Vendored yt-dlp

yt-dlp lives at [`https://github.com/yt-dlp/yt-dlp`](https://github.com/yt-dlp/yt-dlp) under `third_party/yt-dlp`.

## Always build against the latest *release*

On every `:ytdlp` / app build, Gradle runs:

```bash
python3 tools/fetch_ytdlp_latest.py
python3 tools/trim_ytdlp.py --out ytdlp/build/generated/ytdlp
```

`fetch_ytdlp_latest.py` queries GitHub for the **latest release tag** (not `master`) and checks it out. That is what fixes YouTube “probe OK / download 403” when extractors age out.

### Pin / offline (optional)

```bash
export S2A_YTDLP_TAG=2026.08.19   # skip “latest” lookup; use this tag
./gradlew :app:assembleFossDebug
```

Use a pin for reproducible CI / F-Droid builds. If the tag is already checked out, no network is required.

## Trim for Chaquopy

The trim stubs `external.py` (no ffmpeg/aria2c on Android) but keeps the symbols yt-dlp imports (`FFmpegFD.available`, etc.).

**No site / adult filtering.** The FOSS download build keeps the full extractor set (including adult sites). `ytdlp_bridge` sets `age_limit: None` so yt-dlp does not skip age-restricted videos. The Play flavor has no download feature at all — it does not ship a reduced extractor list.

## Android / no JS runtime

Modern yt-dlp prefers a JS runtime (Deno/Node) for some YouTube clients. On-device we usually have none, so yt-dlp falls back to its **JS-less** client set (e.g. `visionos`) and mostly HLS (`m3u8_native`) formats. That is expected — do **not** invent custom `player_client` overrides unless comparing against current upstream docs.

The quality picker prefers `m3u8_native` over progressive `https` when both exist.

## NixOS builds

AGP’s bundled `aapt2` is a generic Linux binary. On NixOS run Gradle under `steam-run` (or enable `nix-ld`):

```bash
steam-run ./gradlew :app:assembleFossDebug :app:assemblePlayDebug
```

## Flavors

| Flavor | Contents |
|--------|----------|
| `play` | Archive + clipboard only (no Chaquopy) |
| `foss` | Full download feature (default, arm64) |
| `dev` | Full download + `x86_64` for emulators |
