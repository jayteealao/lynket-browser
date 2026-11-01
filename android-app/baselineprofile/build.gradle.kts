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

// Produces the app's Baseline Profile from the link-open and home journeys on a Gradle Managed
// Device (API 36, Google Play image, created by Gradle): ./gradlew :lynket:generateBaselineProfile.
// The host needs the x86_64 emulator with hardware acceleration; no adb device is used.
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

    testOptions {
        managedDevices {
            localDevices {
                // The emulator the Baseline Profile is generated on. A Google Play image, because the
                // link-open journey hands the link to Chrome's Custom Tab; API 36 is the app's targetSdk.
                create("pixel6Api36") {
                    device = "Pixel 6"
                    sdkVersion = 36
                    systemImageSource = "google_apis_playstore"
                }
            }
        }
    }
}

// Generates on the managed device above; Gradle creates and boots it, so no adb device is needed.
baselineProfile {
    managedDevices += "pixel6Api36"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)

    constraints {
        // The benchmark APK resolves coroutines at the app's catalog version; the Macrobenchmark
        // libraries ask for older ones.
        implementation(libs.kotlinx.coroutines.core)
    }
}

// Resolved versions are pinned in gradle.lockfile; refresh with --write-locks after a dependency change.
dependencyLocking {
    lockAllConfigurations()
}
