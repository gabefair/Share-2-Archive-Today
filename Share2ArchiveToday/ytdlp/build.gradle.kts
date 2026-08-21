plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.chaquopy)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

android {
    namespace = "org.gnosco.share2archivetoday.ytdlp"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        ndk {
            // App flavors filter further; include emulator ABI for :dev builds.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

val trimYtdlp by tasks.registering(Exec::class) {
    val outDir = layout.buildDirectory.dir("generated/ytdlp")
    inputs.dir(rootProject.file("third_party/yt-dlp/yt_dlp"))
    inputs.file(rootProject.file("tools/trim_ytdlp.py"))
    outputs.dir(outDir)
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "tools/trim_ytdlp.py",
        "--out",
        outDir.get().asFile.absolutePath,
    )
}

chaquopy {
    defaultConfig {
        version = "3.14"
    }
    sourceSets {
        getByName("main") {
            srcDir(layout.buildDirectory.dir("generated/ytdlp"))
        }
    }
}

// Ensure trim runs before Chaquopy packages Python sources.
tasks.matching { it.name.startsWith("extractPython") || it.name.contains("Python", ignoreCase = false) }.configureEach {
    dependsOn(trimYtdlp)
}
tasks.named("preBuild").configure { dependsOn(trimYtdlp) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.common)

    testImplementation("junit:junit:4.13.2")
}
