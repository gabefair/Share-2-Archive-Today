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
            // Don't exclude Jackson META-INF files
            pickFirsts += "META-INF/services/com.fasterxml.jackson.core.JsonFactory"
            pickFirsts += "META-INF/services/com.fasterxml.jackson.core.ObjectCodec"
        }
    }

    dependenciesInfo { // The name of these variables are misleading, they need to be false in order to make the app more transparent.
        includeInApk = false
        includeInBundle = false
    }
}

// Custom task to build youtubedl-android library locally
tasks.register("buildYoutubeDl") {
    group = "build"
    description = "Builds the youtubedl-android library locally"
    
    doLast {
        println("🔨 Building youtubedl-android components...")
        
        // Check if the libraries need to be rebuilt
        val commonAar = file("libs/common-release.aar")
        val libraryAar = file("libs/library-release.aar")
        val sourceDir = file("youtubedl-android")
        
        val needsRebuild = !commonAar.exists() || !libraryAar.exists() || 
                          commonAar.lastModified() < sourceDir.lastModified() ||
                          libraryAar.lastModified() < sourceDir.lastModified()
        
        if (needsRebuild) {
            println("📦 Libraries need to be rebuilt, building now...")
            
            try {
                // Build all components using the main project's gradle
                exec {
                    workingDir = project.projectDir
                    commandLine("${project.gradle.gradleHomeDir}/bin/gradle", "-p", "../youtubedl-android", "assembleRelease")
                }
                
                // Copy the built AARs to the appropriate locations
                copy {
                    from("youtubedl-android/common/build/outputs/aar/common-release.aar")
                    into("libs")
                }
                copy {
                    from("youtubedl-android/library/build/outputs/aar/library-release.aar")
                    into("libs")
                }
                
                println("✅ youtubedl-android components (common + library) built successfully!")
            } catch (e: Exception) {
                println("⚠️  Warning: Failed to build youtubedl-android components automatically")
                println("💡 This usually happens due to Java version compatibility issues")
                println("💡 The app will use the existing AAR files if available")
                println("💡 Make sure both common-release.aar and library-release.aar are present")
                println("💡 To build manually, run: ./build-youtubedl-local.sh --build-only")
                
                // Don't fail the build, just warn
                if (!commonAar.exists() || !libraryAar.exists()) {
                    throw GradleException("youtubedl-android AAR files not found. Please build them manually using ./build-youtubedl-local.sh --build-only")
                }
            }
        } else {
            println("✅ Libraries (common + library) are up-to-date, skipping build")
        }
    }
}

// Make the preBuild task depend on building the library
tasks.named("preBuild") {
    dependsOn("buildYoutubeDl")
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")

    // ML Kit for barcode scanning (optional dependency)
    compileOnly("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    compileOnly("com.google.android.gms:play-services-tasks:18.2.0")
    
    // Video downloading capabilities (using locally built youtubedl-android AARs)
    // Note: When using AAR files directly, transitive dependencies are not automatically included
    // We must explicitly declare all dependencies that the library requires
    // This is why we were getting NoClassDefFoundError for Jackson classes
    // The library depends on the common module, so we need both AARs
    // The order matters: common must be declared before library
    // This is why we were getting NoClassDefFoundError for ZipUtils
    implementation(files("libs/common-release.aar"))
    implementation(files("libs/library-release.aar"))
    
    // Jackson dependencies required by youtubedl-android library
    // These are needed because the AAR doesn't include its transitive dependencies
    // The crash log shows NoClassDefFoundError for ObjectMapper, which means these classes
    // are not available at runtime even though they're found at compile time
    // Using version 2.11.1 to match the version used by youtubedl-android library
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.11.1")
    
    // Commons IO dependency required by youtubedl-android library
    implementation("commons-io:commons-io:2.5")
    
    // Commons Compress dependency required by youtubedl-android common module
    implementation("org.apache.commons:commons-compress:1.12")
    
    // FFmpeg and Aria2c support (optional - these will be built when needed)
    // implementation(files("youtubedl-android/ffmpeg/build/outputs/aar/ffmpeg-release.aar"))
    // implementation(files("youtubedl-android/aria2c/build/outputs/aar/aria2c-release.aar"))
    
    // Coroutines support
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // AndroidX dependencies for notifications
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
}