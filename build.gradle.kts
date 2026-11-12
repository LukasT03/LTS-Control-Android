plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
}

// The project lives in iCloud Drive, which syncs build outputs and leaves conflict copies
// ("… 2.dex") that break the build. iCloud skips folders ending in ".nosync".
allprojects {
    layout.buildDirectory = layout.projectDirectory.dir("build.nosync")
}
