pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Share 2 Archive Today"
include(":app")

// Include FFmpeg submodule
include(":ffmpeg-kit-android-lib")
project(":ffmpeg-kit-android-lib").projectDir = file("ffmpeg-kit-android/android/ffmpeg-kit-android-lib")

// TODO: Re-enable youtubedl-android modules once build issues are resolved
// Include youtubedl-android modules as local dependencies
// include(":youtubedl-common")
// include(":youtubedl-library")
// include(":youtubedl-ffmpeg")
// include(":youtubedl-aria2c")

// project(":youtubedl-common").projectDir = file("youtubedl-android/common")
// project(":youtubedl-library").projectDir = file("youtubedl-android/library")
// project(":youtubedl-ffmpeg").projectDir = file("youtubedl-android/ffmpeg")
// project(":youtubedl-aria2c").projectDir = file("youtubedl-android/aria2c")
 