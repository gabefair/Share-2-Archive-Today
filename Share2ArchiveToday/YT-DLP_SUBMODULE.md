# Vendored yt-dlp

yt-dlp lives at [`https://github.com/yt-dlp/yt-dlp`](https://github.com/yt-dlp/yt-dlp) (git submodule).

## Trim for Chaquopy

```bash
python3 tools/trim_ytdlp.py --out ytdlp/build/generated/ytdlp
```

The `:ytdlp` module runs this automatically on `preBuild`.

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
