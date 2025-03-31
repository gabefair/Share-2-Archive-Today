plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "org.gnosco.share2archivetoday"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.gnosco.share2archivetoday"
        minSdk = 3
        targetSdk = 34
        versionCode = 41
        versionName = "4.1"
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
        debug {
            isTestCoverageEnabled = true
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
}
