plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.chaquopy)
}

import java.io.File

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
            // FOSS ships arm64-v8a only (Chaquopy + yt-dlp size/compat).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

/**
 * Host Python for trim + Chaquopy. Prefer a project venv (no system Python required).
 *
 * Resolution order:
 * 1. S2A_BUILD_PYTHON / CHAQUOPY_BUILD_PYTHON
 * 2. Share2ArchiveToday/.venv/bin/python3, then python
 * 3. On PATH: python3.14, python3, then python
 */
fun findExecutable(vararg candidates: String): String? {
    val pathDirs = System.getenv("PATH")
        ?.split(File.pathSeparator)
        ?.filter { it.isNotBlank() }
        .orEmpty()
    for (name in candidates) {
        if (name.contains('/') || name.contains('\\')) {
            val direct = File(name)
            if (direct.canExecute()) return direct.absolutePath
            continue
        }
        for (dir in pathDirs) {
            val file = File(dir, name)
            if (file.canExecute()) return file.absolutePath
        }
    }
    return null
}

val hostPython: String = run {
    val env = sequenceOf("S2A_BUILD_PYTHON", "CHAQUOPY_BUILD_PYTHON")
        .mapNotNull { System.getenv(it)?.takeIf(String::isNotBlank) }
        .firstOrNull()
    if (env != null) return@run env

    val venv = sequenceOf("python3", "python")
        .map { rootProject.file(".venv/bin/$it") }
        .firstOrNull { it.canExecute() }
    if (venv != null) return@run venv.absolutePath

    findExecutable("python3.14", "python3", "python")
        ?: "python3" // last resort; Exec/Chaquopy will surface a clear failure
}

/**
 * Checkout yt-dlp's latest GitHub *release* tag into third_party/yt-dlp before trim.
 *
 * Override with S2A_YTDLP_TAG=YYYY.MM.DD for reproducible / offline builds.
 * Needs network unless the desired tag is already checked out.
 */
val fetchYtdlpLatest by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("tools/fetch_ytdlp_latest.py"))
    inputs.file(rootProject.file("ytdlp/YTDLP_PIN"))
    outputs.file(rootProject.file("third_party/yt-dlp/yt_dlp/version.py"))
    // When a pin is committed, Gradle can skip the fetch if the checkout already matches.
    // Force latest with S2A_YTDLP_LATEST=1 (then the pin file alone is not enough).
    val forceLatest = System.getenv("S2A_YTDLP_LATEST").orEmpty() in setOf("1", "true", "yes")
    outputs.upToDateWhen { !forceLatest }
    workingDir = rootProject.projectDir
    commandLine(hostPython, "tools/fetch_ytdlp_latest.py")
}

val trimYtdlp by tasks.registering(Exec::class) {
    dependsOn(fetchYtdlpLatest)
    val outDir = layout.buildDirectory.dir("generated/ytdlp")
    inputs.dir(rootProject.file("third_party/yt-dlp/yt_dlp"))
    inputs.file(rootProject.file("tools/trim_ytdlp.py"))
    outputs.dir(outDir)
    workingDir = rootProject.projectDir
    commandLine(
        hostPython,
        "tools/trim_ytdlp.py",
        "--out",
        outDir.get().asFile.absolutePath,
    )
}

/**
 * Host-side tests for ytdlp_bridge.py against a local HTTP server.
 *
 * Covers the parts no JVM test can reach: real yt-dlp extraction, the native HLS
 * downloader, progress hooks, cancellation and sidecar collection. No network needed.
 */
val pythonBridgeTest by tasks.registering(Exec::class) {
    dependsOn(trimYtdlp)
    inputs.dir(layout.projectDirectory.dir("src/main/python"))
    inputs.dir(layout.projectDirectory.dir("src/test/python"))
    inputs.dir(layout.buildDirectory.dir("generated/ytdlp"))
    outputs.file(layout.buildDirectory.file("python-bridge-test.ok"))
    workingDir = rootProject.projectDir
    commandLine(hostPython, "ytdlp/src/test/python/test_ytdlp_bridge.py")
    doLast {
        layout.buildDirectory.file("python-bridge-test.ok").get().asFile.writeText("ok")
    }
}

tasks.named("check").configure { dependsOn(pythonBridgeTest) }

chaquopy {
    defaultConfig {
        version = "3.14"
        buildPython(hostPython)
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
    // Real org.json on the unit-test classpath; the android.jar stub throws instead.
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("junit:junit:4.13.2")
}
