plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

// TODO: Re-enable submodule checking once youtubedl-android integration is working
// Task to ensure submodules are initialized
// tasks.register("checkSubmodules") {
//     group = "verification"
//     description = "Check if Git submodules are properly initialized"
//     
//     doLast {
//         val submoduleDir = file("youtubedl-android")
//         if (!submoduleDir.exists() || !file("youtubedl-android/.git").exists()) {
//             println("Submodules not initialized. Running git submodule update --init --recursive...")
//             exec {
//                 commandLine("git", "submodule", "update", "--init", "--recursive")
//                 workingDir = projectDir
//             }
//             println("Submodules initialized successfully!")
//         } else {
//             println("Submodules already initialized")
//         }
//     }
// }

// Make the checkSubmodules task run before any build tasks
// tasks.matching { it.name.contains("build") || it.name.contains("assemble") || it.name.contains("compile") }.configureEach {
//     dependsOn("checkSubmodules")
// }