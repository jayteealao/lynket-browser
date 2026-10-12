/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinParcelize)
    alias(libs.plugins.kotlin.serialization)
    // Temporary: runs the Butterknife and Epoxy processors, which have no KSP version.
    // Removed together with the last View-based screens.
    alias(libs.plugins.legacyKapt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt) // Phase 1.1: Enabled for modernization
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.room3)
}

android {
    namespace = "arun.com.chromer"
    compileSdk = 37

    defaultConfig {
        applicationId = "arun.com.chromer"
        minSdk = 24 // Android 7.0+ (Decision: Bump from 23)
        targetSdk = 36 // Set explicitly: AGP 9 defaults targetSdk to compileSdk
        versionCode = 56
        versionName = "2.1.3"

        testInstrumentationRunner = "arun.com.chromer.HiltTestRunner" // Phase 1.1: Hilt test runner

        vectorDrawables {
            useSupportLibrary = true
        }

        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        resValues = true // AGP 9 turns resValue off by default; every build type sets app_name
        compose = true
        viewBinding = true // Temporary - will be removed after migration
    }

    buildTypes {
        release {
            resValue("string", "app_name", "Lynket")
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = " - dev"
            resValue("string", "app_name", "Lynket-dev")
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
        }
        // Release-like build used only by the :benchmark module to measure start-up and frame times.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".benchmark"
            resValue("string", "app_name", "Lynket-bench")
            isDebuggable = false
            isProfileable = true
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/licenses/**"
            )
        }
    }

    lint {
        abortOnError = false
        disable += "MissingTranslation"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // PaperDB's Kryo setup reflects into java.util, which JDK 17+ closes to the
                // classpath; Android has no module system, so this is needed on the JVM only.
                it.jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
                // Robolectric's API 36 sandbox sets up shared memory through these JDK internals.
                it.jvmArgs("--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED")
                it.jvmArgs("--add-opens", "java.base/java.io=ALL-UNNAMED")
                // One test JVM holds a Robolectric sandbox per configuration (themes, qualifiers,
                // native graphics); the default 512 MB heap ran out once the dialog tests joined.
                it.maxHeapSize = "3g"
            }
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.multidex)
    implementation(libs.material)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.extensions)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Hilt Dependency Injection - Phase 1.1: Enabled
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Hilt Testing
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Phase 8: RxJava interop removed - migration complete

    // Room 3 (history database)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.room3.paging)
    ksp(libs.androidx.room3.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)
    // One-time read of the per-app rules that release 2.1.3 saved with PaperDB
    implementation(libs.paperdb)

    // Paging 3
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Image Loading - Coil (Modern)
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)

    // =============================================
    // LEGACY DEPENDENCIES - TO BE REMOVED
    // Keep temporarily during migration (Steps 3-9)
    // =============================================

    // Legacy: Image Loading - Glide
    implementation(libs.glide)
    ksp(libs.glide.ksp)

    // Butterknife and Epoxy have no KSP processor; they run on the temporary legacy-kapt bridge
    // and leave with the last View-based screens.
    // Legacy: View Binding - Butterknife
    implementation(libs.butterknife)
    kapt(libs.butterknife.compiler)

    // Legacy: RecyclerView - Epoxy
    implementation(libs.epoxy)
    kapt(libs.epoxy.processor)

    // Phase 8: RxJava dependencies removed - migrated to Kotlin Coroutines/Flows

    // Legacy: UI Libraries
    implementation("com.afollestad.material-dialogs:core:0.9.6.0")
    implementation("com.afollestad.material-dialogs:commons:0.9.6.0")
    // Icon font drawables for the remaining View screens; MaterialDrawer brought this same
    // version before the drawer moved to Compose.
    implementation("com.mikepenz:iconics-core:3.1.0")
    implementation("com.mikepenz:community-material-typeface:2.0.46.1@aar")

    // Legacy: AndroidX (old versions, will be updated)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2") // Updated for viewModels() delegate
    implementation(libs.androidx.activity.ktx)

    // Legacy: Other
    implementation("com.github.duanhong169:drawabletoolbox:1.0.7")

    // Disk LRU cache module
    implementation(project(":disk-cache"))
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // jvmTarget follows android.compileOptions.targetCompatibility (17) with built-in Kotlin.
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }
}

// KAPT Configuration (legacy-kapt bridge: Butterknife and Epoxy only)
kapt {
    correctErrorTypes = true
    javacOptions {
        option("-Xmaxerrs", "500")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-path")
}
