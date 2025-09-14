#!/bin/bash

# Script to update youtubedl-android to the latest source code
# This script fetches the latest commit hash from the master branch and updates the dependency

echo "🔄 Updating youtubedl-android to latest source code..."

# Get the latest commit hash from the master branch using git ls-remote
LATEST_COMMIT=$(git ls-remote https://github.com/yausername/youtubedl-android.git refs/heads/master | cut -f1)

if [ -z "$LATEST_COMMIT" ]; then
    echo "❌ Failed to fetch latest commit hash"
    echo "🔄 Trying alternative method..."
    
    # Alternative method using curl with better error handling
    LATEST_COMMIT=$(curl -s -H "Accept: application/vnd.github.v3+json" \
        https://api.github.com/repos/yausername/youtubedl-android/commits/master | \
        grep -o '"sha":"[^"]*"' | head -1 | cut -d'"' -f4)
    
    if [ -z "$LATEST_COMMIT" ]; then
        echo "❌ All methods failed to fetch latest commit hash"
        echo "💡 You can manually check the latest commit at: https://github.com/yausername/youtubedl-android/commits/master"
        exit 1
    fi
fi

echo "📝 Latest commit: $LATEST_COMMIT"

# Update the build.gradle.kts file with the latest commit
sed -i.bak "s|implementation(\"com.github.yausername:youtubedl-android:.*\")|implementation(\"com.github.yausername:youtubedl-android:$LATEST_COMMIT\")|" app/build.gradle.kts

if [ $? -eq 0 ]; then
    echo "✅ Successfully updated youtubedl-android to commit $LATEST_COMMIT"
    echo "🔄 Cleaning and rebuilding project..."
    
    # Clean and rebuild the project
    ./gradlew clean
    ./gradlew build
    
    echo "🎉 Update complete! You now have the latest youtubedl-android source code."
    echo "📱 You can now run your app with the latest features and bug fixes."
else
    echo "❌ Failed to update build.gradle.kts"
    exit 1
fi
