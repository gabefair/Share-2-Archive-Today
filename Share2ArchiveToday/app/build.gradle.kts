plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.chaquo.python")
}

android {
    namespace = "org.gnosco.share2archivetoday"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.gnosco.share2archivetoday"
        minSdk = 28
        targetSdk = 36
        versionCode = 71
        versionName = "7.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        // Feature flags for different architectures
        buildConfigField("boolean", "SUPPORTS_VIDEO_DOWNLOAD", "true")
    }

    // Create product flavors for different feature sets
    flavorDimensions += "features"
    productFlavors {
        create("full") {
            dimension = "features"
            buildConfigField("boolean", "SUPPORTS_VIDEO_DOWNLOAD", "true")
            buildConfigField("boolean", "SUPPORTS_ARCHIVE_FEATURES", "true")
            versionNameSuffix = "-full"
        }
        create("archive") {
            dimension = "features"
            buildConfigField("boolean", "SUPPORTS_VIDEO_DOWNLOAD", "false")
            buildConfigField("boolean", "SUPPORTS_ARCHIVE_FEATURES", "true")
            versionNameSuffix = "-archive"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    bundle {
        abi {
            enableSplit = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../debug.keystore")  // Using debug keystore for testing
            storePassword = "android"
            keyAlias = "debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-release.pro",  // Release-specific rules (strips logs)
                "build/python/proguard-rules.pro"
            )
            // Remove debug activities from release builds
            buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "false")
            buildConfigField("boolean", "ENABLE_DEBUG_TESTING", "false")
        }
        getByName("debug") {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
            // Keep debug activities in debug builds
            buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "true")
            buildConfigField("boolean", "ENABLE_DEBUG_TESTING", "true")
            // Debug builds keep all logging - no log stripping
            proguardFiles(
                "proguard-rules.pro",
                "build/python/proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    dependenciesInfo { // The name of these variables are misleading, they need to be false in order to make the app more transparent.
        includeInApk = false
        includeInBundle = false
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"

        pip {
            // yt-dlp dependencies (yt-dlp itself is now included from source)
            install("mutagen")
            install("websockets")
            install("brotli")
            install("pycryptodomex")
        }
    }

    // Include local yt-dlp source from git submodule
    sourceSets {
        getByName("main") {
            srcDir("src/main/python")
            srcDir("src/main/python/yt-dlp")
        }
    }
}

dependencies {
    // AndroidX Core (required for FileProvider and other core functionality)
    implementation(libs.androidx.core.ktx)

    implementation(libs.core)

    // Coroutines for async operations
    implementation(libs.kotlinx.coroutines.android)

    // ML Kit for barcode scanning (optional dependency)
    compileOnly(libs.play.services.mlkit.barcode.scanning)
    compileOnly(libs.play.services.tasks)

    // WebSocket support (OkHttp includes WebSocket support)
    implementation(libs.okhttp)

    // Google's official Brotli library
    implementation(libs.dec)

    // Additional crypto support for AES-128 HLS
    implementation(libs.bcprov.jdk15on)
    implementation(libs.bcpkix.jdk15on)

    // Media3 for video processing (Transformer API)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.effect)

    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
