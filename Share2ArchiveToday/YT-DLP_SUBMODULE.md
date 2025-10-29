# yt-dlp Integration via Git Submodule

## Overview

This project now uses yt-dlp as a git submodule rather than installing it via pip. This allows us to:
- Build from source
- Customize and remove unused features
- Control the exact version being used
- Reduce the final APK size

## Submodule Location

The yt-dlp submodule is located at:
```
app/src/main/python/yt-dlp/
```

## Configuration


**Important**: Adding `src/main/python/yt-dlp` as a source directory allows Chaquopy to include the `yt_dlp` package at the root level, making `import yt_dlp` work correctly.

### Dependencies

Only yt-dlp's runtime dependencies are installed via pip:
- mutagen
- websockets
- brotli
- pycryptodomex

## Build Process

The yt-dlp source is automatically included during the build process:
1. Chaquopy processes both source directories:
   - `src/main/python/` (your app code)
   - `src/main/python/yt-dlp/` (yt-dlp source)
2. The `yt_dlp` package is packaged at the root level in the `app.imy` file
3. Your Python code can import yt_dlp normally: `import yt_dlp`
4. No build or compilation step is needed - Python source is used directly

## Updating yt-dlp

To update yt-dlp to a newer version:

```bash
cd app/src/main/python/yt-dlp
git pull origin master
cd ../../../../
git add app/src/main/python/yt-dlp
git commit -m "Update yt-dlp submodule to latest version"
```

Or to update to a specific version:

```bash
cd app/src/main/python/yt-dlp
git checkout <version-tag>
cd ../../../../
git add app/src/main/python/yt-dlp
git commit -m "Update yt-dlp submodule to <version-tag>"
```

## Customization

Since we're building from source, you can customize yt-dlp by:

1. **Removing unused extractors**: Edit `yt-dlp/yt_dlp/extractor/__init__.py` to remove extractors you don't need
2. **Removing unused features**: Modify the yt-dlp source code as needed
3. **Optimization**: Remove test files, documentation, and other non-runtime files

Example - removing unused files before build:

```bash
# Remove test files
rm -rf app/src/main/python/yt-dlp/test/
rm -rf app/src/main/python/yt-dlp/devscripts/

# Remove documentation
rm -rf app/src/main/python/yt-dlp/*.md
rm -rf app/src/main/python/yt-dlp/Changelog.md
```

## Cloning This Project

When cloning this project, you need to initialize the submodule:

```bash
git clone <repository-url>
cd Share2ArchiveToday
git submodule init
git submodule update
```

Or clone with submodules in one command:

```bash
git clone --recursive <repository-url>
```

## Verification

You can verify the yt-dlp source is included in the build by checking:

```bash
# Check the Python sources in the build
ls app/build/python/sources/<flavor>Debug/yt-dlp/yt_dlp/

# Check the packaged assets
unzip -l app/build/intermediates/assets/<flavor>Debug/mergeArchiveDebugAssets/chaquopy/app.imy | grep yt_dlp
```

## Benefits

1. **Size control**: Remove unused features to reduce APK size
2. **Version control**: Lock to a specific version or commit
3. **Customization**: Modify source code as needed
4. **Transparency**: Full visibility into the code being shipped
5. **Build from source**: No dependency on pre-built packages

