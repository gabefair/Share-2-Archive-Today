#!/bin/bash

# Comprehensive script to manage youtubedl-android submodules and build locally
# This script handles: submodule initialization, updates, and local building
# Usage: ./build-youtubedl-local.sh [--init-only] [--update-only] [--build-only]

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}🔍 $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_build() {
    echo -e "${BLUE}🔨 $1${NC}"
}

# Parse command line arguments
INIT_ONLY=false
UPDATE_ONLY=false
BUILD_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --init-only)
            INIT_ONLY=true
            shift
            ;;
        --update-only)
            UPDATE_ONLY=true
            shift
            ;;
        --build-only)
            BUILD_ONLY=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --init-only     Only initialize submodules (don't build)"
            echo "  --update-only   Only update submodules (don't build)"
            echo "  --build-only    Only build (assume submodules are ready)"
            echo "  --help, -h      Show this help message"
            echo ""
            echo "Default behavior: Initialize/update submodules and then build"
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo "🚀 youtubedl-android Management Script"
echo "======================================"

# Function to initialize submodules
init_submodules() {
    print_status "Checking Git repository status..."
    
    if [ ! -d ".git" ]; then
        print_error "Not in a Git repository. Cannot initialize submodules."
        echo "💡 Please run this script from within your Git repository."
        exit 1
    fi
    
    if [ -d "youtubedl-android" ] && [ -f "youtubedl-android/.git" ]; then
        print_success "Submodules already initialized"
        return 0
    fi
    
    print_status "Submodules not initialized, initializing now..."
    if git submodule update --init --recursive; then
        print_success "Submodules initialized successfully!"
        return 0
    else
        print_error "Failed to initialize submodules"
        echo "💡 Try running: git submodule update --init --recursive"
        return 1
    fi
}

# Function to update submodules
update_submodules() {
    print_status "Checking for submodule updates..."
    
    if [ ! -d "youtubedl-android" ] || [ ! -f "youtubedl-android/.git" ]; then
        print_warning "Submodules not initialized, initializing first..."
        init_submodules
    fi
    
    if git submodule update --remote --recursive; then
        print_success "Submodules updated successfully!"
        return 0
    else
        print_warning "Submodule update failed, continuing with current version"
        return 1
    fi
}

# Function to build the library
build_library() {
    print_status "Ensuring youtubedl-android directory exists..."
    
    if [ ! -d "youtubedl-android" ]; then
        print_error "youtubedl-android directory not found"
        exit 1
    fi
    
    cd youtubedl-android
    
    # Get the current commit hash
    CURRENT_COMMIT=$(git rev-parse HEAD)
    print_status "Building from commit: $CURRENT_COMMIT"
    
    # Build all required modules using the local Gradle wrapper
    print_build "Building common module..."
    if ! ./gradlew :common:assembleRelease; then
        print_error "Failed to build common module"
        cd ..
        return 1
    fi
    
    print_build "Building library module..."
    if ! ./gradlew :library:assembleRelease; then
        print_error "Failed to build library module"
        cd ..
        return 1
    fi
    
    print_success "All modules built successfully!"
    
    # Copy the built AAR files to the app's libs directory
    mkdir -p ../app/libs
    cp common/build/outputs/aar/common-release.aar ../app/libs/
    cp library/build/outputs/aar/library-release.aar ../app/libs/
    
    print_success "AAR files copied to app/libs/"
    
    # Go back to main project
    cd ..
    
    echo ""
    print_success "Local build complete! You now have the latest youtubedl-android source code."
    echo "📱 The library is built locally and integrated into your project."
    echo "🔄 You can now build your app with: ./gradlew build"
    
    return 0
}

# Main execution logic
main() {
    if [ "$INIT_ONLY" = true ]; then
        print_status "Initialization only mode"
        init_submodules
        print_success "Ready to build!"
        return 0
    fi
    
    if [ "$UPDATE_ONLY" = true ]; then
        print_status "Update only mode"
        update_submodules
        print_success "Submodules updated!"
        return 0
    fi
    
    if [ "$BUILD_ONLY" = true ]; then
        print_status "Build only mode"
        build_library
        return $?
    fi
    
    # Default behavior: initialize/update and build
    print_status "Full mode: Initializing/updating submodules and building"
    
    if ! init_submodules; then
        exit 1
    fi
    
    if ! update_submodules; then
        print_warning "Continuing with build despite update issues"
    fi
    
    if ! build_library; then
        exit 1
    fi
}

# Run the main function
main "$@"
