plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "org.gnosco.share2archivetoday"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.gnosco.share2archivetoday"
        minSdk = 24
        targetSdk = 36
        versionCode = 53
        versionName = "5.3"
        
        // Required for youtubedl-android - specify NDK version instead of deprecated ndk block
        ndkVersion = "25.2.9519653"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

dependencies {
    implementation("com.google.zxing:core:3.5.3")

    // ML Kit for barcode scanning (optional dependency)
    compileOnly("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    compileOnly("com.google.android.gms:play-services-tasks:18.2.0")
    
    // Video downloading capabilities (using latest youtubedl-android source)
    implementation(project(":youtubedl-library"))
    implementation(project(":youtubedl-ffmpeg"))
    implementation(project(":youtubedl-aria2c"))
    
    // Coroutines support
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // AndroidX dependencies for notifications
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
}