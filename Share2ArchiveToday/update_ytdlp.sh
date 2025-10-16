#!/bin/bash
#
# yt-dlp Update Script for Share2ArchiveToday
# Updates yt-dlp to a specified version
#
# Usage: ./update_ytdlp.sh [VERSION]
#   VERSION: yt-dlp version (e.g., 2025.10.15)
#            If not provided, fetches the latest version from GitHub
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Paths
REQUIREMENTS_FILE="app/src/main/python/requirements.txt"
BUILD_GRADLE="app/build.gradle.kts"

echo -e "${GREEN}=== yt-dlp Update Script ===${NC}"
echo ""

# Function to get latest yt-dlp version from GitHub
get_latest_version() {
    echo -e "${YELLOW}Fetching latest yt-dlp version from GitHub...${NC}"
    
    # Try using curl first
    if command -v curl &> /dev/null; then
        LATEST=$(curl -s https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')
    # Fallback to wget
    elif command -v wget &> /dev/null; then
        LATEST=$(wget -qO- https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')
    else
        echo -e "${RED}Error: Neither curl nor wget is available${NC}"
        exit 1
    fi
    
    if [ -z "$LATEST" ]; then
        echo -e "${RED}Error: Failed to fetch latest version${NC}"
        exit 1
    fi
    
    echo "$LATEST"
}

# Get version from argument or fetch latest
if [ -z "$1" ]; then
    VERSION=$(get_latest_version)
    echo -e "${GREEN}Latest version: $VERSION${NC}"
else
    VERSION=$1
    echo -e "${GREEN}Using specified version: $VERSION${NC}"
fi

# Validate version format (should be YYYY.MM.DD or similar)
if ! [[ $VERSION =~ ^[0-9]{4}\.[0-9]{2}\.[0-9]{2}$ ]]; then
    echo -e "${YELLOW}Warning: Version format doesn't match YYYY.MM.DD${NC}"
    echo -e "${YELLOW}Proceeding anyway...${NC}"
fi

echo ""
echo -e "${YELLOW}Checking files...${NC}"

# Check if files exist
if [ ! -f "$REQUIREMENTS_FILE" ]; then
    echo -e "${RED}Error: $REQUIREMENTS_FILE not found${NC}"
    exit 1
fi

if [ ! -f "$BUILD_GRADLE" ]; then
    echo -e "${RED}Error: $BUILD_GRADLE not found${NC}"
    exit 1
fi

# Backup files
echo -e "${YELLOW}Creating backups...${NC}"
cp "$REQUIREMENTS_FILE" "${REQUIREMENTS_FILE}.backup"
cp "$BUILD_GRADLE" "${BUILD_GRADLE}.backup"

# Update requirements.txt
echo -e "${YELLOW}Updating $REQUIREMENTS_FILE...${NC}"
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/yt-dlp\[default\]==.*/yt-dlp[default]==$VERSION/" "$REQUIREMENTS_FILE"
else
    # Linux
    sed -i "s/yt-dlp\[default\]==.*/yt-dlp[default]==$VERSION/" "$REQUIREMENTS_FILE"
fi

# Update build.gradle.kts
echo -e "${YELLOW}Updating $BUILD_GRADLE...${NC}"

# Check if build.gradle.kts has a pinned version
if grep -q 'install("yt-dlp==.*")' "$BUILD_GRADLE"; then
    # Update pinned version
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s/install(\"yt-dlp==.*\")/install(\"yt-dlp==$VERSION\")/" "$BUILD_GRADLE"
    else
        sed -i "s/install(\"yt-dlp==.*\")/install(\"yt-dlp==$VERSION\")/" "$BUILD_GRADLE"
    fi
    echo -e "${GREEN}✓ Updated pinned version in build.gradle.kts${NC}"
elif grep -q 'install("yt-dlp")' "$BUILD_GRADLE"; then
    # Has unpinned version - optionally pin it
    echo -e "${YELLOW}build.gradle.kts has unpinned yt-dlp version${NC}"
    read -p "Pin to version $VERSION? [y/N] " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s/install(\"yt-dlp\")/install(\"yt-dlp==$VERSION\")/" "$BUILD_GRADLE"
        else
            sed -i "s/install(\"yt-dlp\")/install(\"yt-dlp==$VERSION\")/" "$BUILD_GRADLE"
        fi
        echo -e "${GREEN}✓ Pinned version in build.gradle.kts${NC}"
    else
        echo -e "${YELLOW}⊘ Keeping unpinned version in build.gradle.kts${NC}"
    fi
else
    echo -e "${YELLOW}⊘ No yt-dlp pip install found in build.gradle.kts${NC}"
fi

echo ""
echo -e "${GREEN}=== Update Summary ===${NC}"
echo -e "Version: ${GREEN}$VERSION${NC}"
echo -e "Updated: ${GREEN}$REQUIREMENTS_FILE${NC}"
echo -e "Updated: ${GREEN}$BUILD_GRADLE${NC}"
echo ""

# Show what changed
echo -e "${YELLOW}Changes in $REQUIREMENTS_FILE:${NC}"
grep "yt-dlp" "$REQUIREMENTS_FILE"

echo ""
echo -e "${YELLOW}Changes in $BUILD_GRADLE (yt-dlp lines):${NC}"
grep "yt-dlp" "$BUILD_GRADLE" || echo "No yt-dlp pip install in build.gradle.kts"

echo ""
echo -e "${GREEN}✓ Update complete!${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  1. Review changes: git diff"
echo "  2. Clean build: ./gradlew clean"
echo "  3. Build app: ./gradlew build"
echo "  4. Test downloads with various video sites"
echo "  5. If successful, commit changes"
echo ""
echo -e "${YELLOW}To revert changes:${NC}"
echo "  mv ${REQUIREMENTS_FILE}.backup $REQUIREMENTS_FILE"
echo "  mv ${BUILD_GRADLE}.backup $BUILD_GRADLE"
echo ""
echo -e "${YELLOW}Check yt-dlp changelog:${NC}"
echo "  https://github.com/yt-dlp/yt-dlp/releases/tag/$VERSION"
echo ""

# Optional: Clean build automatically
read -p "Run './gradlew clean' now? [y/N] " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Running clean build...${NC}"
    ./gradlew clean
    echo -e "${GREEN}✓ Clean complete${NC}"
else
    echo -e "${YELLOW}Remember to run './gradlew clean build' before testing${NC}"
fi

echo ""
echo -e "${GREEN}Done!${NC}"
