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

// Produces the app's Baseline Profile from the link-open and home journeys.
// Run on an API 33+ emulator or device (no root needed):
//   ANDROID_SERIAL=<serial> ./gradlew :lynket:generateBaselineProfile
plugins {
    alias(libs.plugins.androidTest)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "arun.com.chromer.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36 // Set explicitly: AGP 9 defaults targetSdk to compileSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":lynket"
}

// Uses the devices connected to adb (pick one with ANDROID_SERIAL).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// Resolved versions are pinned in gradle.lockfile; refresh with --write-locks after a dependency change.
dependencyLocking {
    lockAllConfigurations()
}
