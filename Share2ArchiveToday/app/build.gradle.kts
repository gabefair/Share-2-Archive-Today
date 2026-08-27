plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

android {
    namespace = "org.gnosco.share2archivetoday"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.gnosco.share2archivetoday"
        minSdk = 23
        targetSdk = 37
        versionCode = 61
        versionName = "6.1"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            isDefault = true
            minSdk = 24
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
        create("play") {
            dimension = "distribution"
            minSdk = 23
        }
        create("dev") {
            dimension = "distribution"
            minSdk = 24
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        // Version and flavor are recorded in each download's provenance manifest.
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    // Share FOSS download sources with the emulator (dev) flavor.
    sourceSets {
        getByName("dev") {
            kotlin.srcDir("src/foss/java")
            res.srcDir("src/foss/res")
            manifest.srcFile("src/foss/AndroidManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("com.google.zxing:core:3.5.3")

    compileOnly("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    compileOnly("com.google.android.gms:play-services-tasks:18.2.0")

    "fossImplementation"(project(":ytdlp"))
    "devImplementation"(project(":ytdlp"))
    "fossImplementation"(libs.kotlinx.coroutines.android)
    "devImplementation"(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("org.robolectric:robolectric:4.13")
}
