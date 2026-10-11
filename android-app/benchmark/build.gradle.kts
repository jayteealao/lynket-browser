/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

// Macrobenchmarks for link-open start-up and web head drag smoothness.
// Run: ./gradlew :benchmark:connectedBenchmarkAndroidTest
plugins {
    alias(libs.plugins.androidTest)
}

android {
    // Must differ from the target app id (arun.com.chromer.benchmark), or the two APKs collide on install.
    namespace = "arun.com.chromer.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        targetSdk = 36 // Set explicitly: AGP 9 defaults targetSdk to compileSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Baselines are recorded on the Pixel_9_Pro emulator; compare runs use the same AVD.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":lynket"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
