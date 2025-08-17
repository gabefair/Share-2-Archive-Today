#!/bin/bash

# Script to build youtubedl-android locally from source and integrate it
# This approach gives you the latest source code without external dependencies

echo "🔨 Building youtubedl-android locally from source..."

# Check if the source directory exists
if [ ! -d "youtubedl-android" ]; then
    echo "❌ youtubedl-android source directory not found"
    echo "🔄 Cloning the repository..."
    git clone https://github.com/yausername/youtubedl-android.git
fi

cd youtubedl-android

# Pull latest changes
echo "📥 Pulling latest changes..."
git fetch origin
git checkout master
git pull origin master

# Get the latest commit hash
LATEST_COMMIT=$(git rev-parse HEAD)
echo "📝 Building from commit: $LATEST_COMMIT"

# Build the library using the local Gradle wrapper
echo "🔨 Building library..."
./gradlew :library:assembleRelease

if [ $? -eq 0 ]; then
    echo "✅ Library built successfully!"
    
    # Copy the built AAR file to the app's libs directory
    mkdir -p ../app/libs
    cp library/build/outputs/aar/library-release.aar ../app/libs/
    
    echo "📁 AAR file copied to app/libs/"
    
    # Go back to main project
    cd ..
    
    # Update build.gradle.kts to use local AAR
    echo "📝 Updating build.gradle.kts to use local AAR..."
    
    # Create backup
    cp app/build.gradle.kts app/build.gradle.kts.backup
    
    # Replace the dependency with local file
    sed -i.bak 's|implementation("com.github.yausername:youtubedl-android:.*")|implementation(fileTree(dir: "libs", include: ["*.aar"]))|' app/build.gradle.kts
    
    echo "🎉 Local build complete! You now have the latest youtubedl-android source code."
    echo "📱 The library is built locally and integrated into your project."
    echo "🔄 You can now build your app with: ./gradlew build"
    
else
    echo "❌ Failed to build library"
    cd ..
    exit 1
fi
