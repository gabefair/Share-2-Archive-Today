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
        minSdk = 30
        targetSdk = 36
        versionCode = 70
        versionName = "7.0"
        
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures { // ask what this is
        viewBinding = true
    }

    bundle {
        abi {
            enableSplit = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "build/python/proguard-rules.pro"
            )
        }
        getByName("debug") {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
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
            install("yt-dlp")
            install("mutagen")
            install("websockets")
            install("brotli")
            install("pycryptodomex")
        }
    }
}

dependencies {
    // AndroidX Core (required for FileProvider and other core functionality)
    implementation("androidx.core:core-ktx:1.12.0")
    
    implementation("com.google.zxing:core:3.5.3")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ML Kit for barcode scanning (optional dependency)
    compileOnly("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    compileOnly("com.google.android.gms:play-services-tasks:18.2.0")
    
    // WebSocket support (OkHttp includes WebSocket support)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Google's official Brotli library
    implementation("org.brotli:dec:0.1.2")
    
    // Additional crypto support for AES-128 HLS
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
