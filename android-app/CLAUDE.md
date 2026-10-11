# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Lynket is an Android browser application that provides custom tabs functionality, web heads (bubble-style browsing), and article reading modes. The project is a multi-module Gradle build:

- **lynket**: The application module. Hilt is the only dependency injection system.
- **disk-cache**: A Kotlin disk LRU cache library for Parcelable values (website and article caches).
- **benchmark**: Macrobenchmarks for link-open start-up and web head drag (runs against the `benchmark` build type of `:lynket`).

## Build Configuration

### Prerequisites

- JDK 17 or later. `gradle.properties` points `org.gradle.java.home` at the Android Studio JBR 21.
- Android SDK with platform `android-37` (compileSdk) and build-tools 36.0.0 or later.

### Toolchain

- **Android Gradle Plugin**: 9.4.1, with AGP's built-in Kotlin (no `org.jetbrains.kotlin.android` plugin).
- **Gradle**: 9.8.1 (wrapper).
- **Kotlin**: 2.4.21. AGP 9 brings KGP 2.2.10, so the root `build.gradle.kts` puts KGP 2.4.21 and the KSP Gradle plugin on the `buildscript` classpath.
- **KSP**: 2.3.12. Hilt 2.60.1, Glide (KSP artifact), Room 2.8.5 and Moshi run on KSP. Glide's KSP processor generates no `GlideApp`; load images with `Glide.with`.
- **Temporary legacy-kapt bridge**: `com.android.legacy-kapt` runs only the Butterknife and Epoxy processors, which have no KSP version. It leaves with the last View-based screens, together with `android.enableAppCompileTimeRClass=false`, `android.nonFinalResIds=false` and `android.r8.optimizedResourceShrinking=false` in `gradle.properties`.
- **SDK levels**: compileSdk 37 (Compose BOM 2026.09.00 / Compose UI 1.12 needs it), targetSdk 36, minSdk 24. Do not raise targetSdk to 37.
- **Libraries**: Compose BOM 2026.09.00, Robolectric 4.17 (tests pin `sdk = [34]`), Roborazzi 1.76.0.
- **Back handling**: targetSdk 36 turns on predictive back, and the system no longer calls `onBackPressed`. Use `OnBackPressedDispatcher` callbacks.

### Gradle Commands

Run from `android-app/`:
- `./gradlew :lynket:assembleDebug` - Build the debug APK
- `./gradlew :lynket:testDebugUnitTest` - Run the JVM unit tests
- `./gradlew :lynket:verifyRoborazziDebug` - Compare the screenshot goldens
- `./gradlew :disk-cache:testDebugUnitTest` - Run the disk cache journal tests
- `./gradlew :lynket:assembleBenchmark :benchmark:assembleBenchmark` - Build the minified benchmark app and the benchmarks
- `./gradlew :lynket:installDebug` - Install the debug build on a device
- `bash maestro/upgrade/upgrade-check.sh <serial> <dir>` / `maestro/upgrade/tile-check.sh` - Device checks that settings and per-app rules survive an update from release 2.1.3 and that the web heads tile reaches every reader (debug build; it dumps both stores with the debug-only `StoreDumpReceiver`)
- `bash maestro/upgrade/link-source-check.sh <serial> <dir>` - After the upgrade check: no app-detection service or notification, and a link from a blacklisted app follows its per-app rule

### Build System

- Gradle version catalog (`gradle/libs.versions.toml`) for dependency management.
- KSP for annotation processing; legacy-kapt only for Butterknife and Epoxy (see above).

## Module Structure

### lynket (Legacy Module)

Production module with extensive feature set:
- **Dependency Injection**: Traditional Dagger 2 with manual component setup
- **UI**: Mix of XML layouts and Jetpack Compose
- **Reactive Programming**: RxJava 1.x and RxJava 2.x with RxKotlin
- **View Binding**: Butterknife for view injection
- **Lists**: Airbnb Epoxy for RecyclerViews
- **Image Loading**: Glide
- **Storage**: settings in one Preferences DataStore (`user_preferences`, every key listed in `data/preferences/SettingsSchema.kt`; read synchronously through the `settings/Preferences` facade); per-app rules in a second DataStore (`per_app_rules`); history in Room. Both DataStores migrate once from the old stores (the settings SharedPreferences file, release 2.1.3's PaperDB `files/APPS` book), back them up and never delete them. PaperDB 2.6 is a dependency only for that one-time read.

Key packages:
- `browsing/` - Core browsing functionality including custom tabs, webview, article mode, AMP support
- `bubbles/webheads/` - Floating bubble-style browser windows
- `home/` - Main launcher activity
- `settings/` - App settings and preferences
- `di/` - Dependency injection components (AppComponent, ActivityComponent, ServiceComponent, FragmentComponent, ViewComponent)
- `data/` - Data layer and repositories
- `tabs/` - Tab management
- `history/` - Browsing history

### Dependency Injection Architecture

**lynket module** uses hierarchical Dagger components:
- `AppComponent` (@Singleton) - Application-scoped dependencies
- `ActivityComponent` - Activity-scoped dependencies
- `ServiceComponent` - Service-scoped dependencies
- `FragmentComponent` - Fragment-scoped dependencies
- `ViewComponent` - View-scoped dependencies

Every activity, fragment, view, service and receiver is injected by Hilt with `@AndroidEntryPoint`; there is no other component.

## Key Application Components

### Main Entry Points

1. **HomeActivity** (`home/HomeActivity.kt`) - Main launcher activity
2. **BrowserInterceptActivity** (`browsing/browserintercept/BrowserInterceptActivity.kt`) - Handles VIEW intents for http/https URLs
3. **Lynket** Application class initializes:
   - Dagger/Whetstone DI
   - the settings and per-app rule stores (DataStore)
   - RxDogTag (debug builds)
   - Timber logging (debug builds)
   - Material Drawer with Glide
   - Epoxy
   - MultiDex

### Browsing Modes

The app supports multiple browsing modes:
- **Custom Tabs** (`browsing/customtabs/`) - Chrome Custom Tabs integration
- **Web View** (`browsing/webview/`) - Built-in WebView
- **Article Mode** (`browsing/article/`) - Reader-friendly article view
- **AMP Mode** (`browsing/amp/`) - Accelerated Mobile Pages
- **Web Heads** (`bubbles/webheads/`) - Floating bubble browser windows

### Services

- `AppColorExtractorJob` - Extracts app colors for theming (JobService)
- `WebHeadService` - Manages floating web head bubbles
- `KeepAliveService` - Keeps custom tabs connection alive

### Quick Settings Tiles

- `WebHeadTile` - Toggle web heads from quick settings
- `IncognitoTile` - Launch incognito browsing
- `AmpTile` - Toggle AMP mode
- `ArticleTile` - Toggle article reading mode

## Version Configuration

`settings.gradle` includes `:lynket`, `:disk-cache` and `:benchmark`.

## Dependencies of Note

- **AndroidX**: Core KTX, Browser, Lifecycle, Paging, ConstraintLayout
- **Compose**: Material, UI, Tooling (in both modules, different versions)
- **Reactive**: RxJava, RxAndroid, RxBinding, RxKotlin, RxRelay, RxKPrefs
- **DI**: Dagger 2.42 (lynket), Dagger 2.50 + Whetstone 0.6.0 (playground)
- **UI Libraries**: Material Drawer, MaterialDialogs, AppIntro, PhotoView
- **Personal Libraries**: Custom utils from `dev.arunkumar.android` (snapshots)

## Permissions

Required permissions include:
- Internet and network access
- System alert window (for web heads)
- Package usage stats (read when a link opens, to find the app it came from; no background service)
- Foreground service
- Query all packages

---

## Modernization Effort

> **📋 SINGLE SOURCE OF TRUTH**: See **`REWRITE.md`** for the complete, detailed modernization plan.

### Current Status (Quick Reference)

**Phase**: Phase 0 Complete ✅ → Starting Phase 1
**App Status**: ✅ Building successfully
**Timeline**: 22-24 weeks total (started Week 1)

### Phase 0 Achievements ✅
- Upgraded Dagger from 2.42 to 2.50
- Created ViewModelKey annotation
- App builds successfully
- Foundation ready for modernization

### Architectural Goals
1. **100% Compose UI** - Eliminate all XML layouts (89 files)
2. **Kotlin Flow** - Remove all RxJava (342 usages in 52 files)
3. **MVI with Molecule** - Modern state management pattern
4. **Clean Architecture** - Proper layering (UI → Domain → Data)
5. **70%+ Test Coverage** - Comprehensive testing
6. **Performance** - <2s startup, 60fps scrolling

### Key Migrations
- **UI**: XML + Butterknife → Jetpack Compose + Material 3
- **Reactive**: RxJava → Kotlin Flow + Coroutines
- **DI**: Manual Dagger → Whetstone (Anvil + Dagger)
- **State**: LiveData + RxRelay → StateFlow + Molecule
- **Lists**: Epoxy → Compose LazyColumn/Grid
- **Images**: Glide → Coil
- **Storage**: PaperDB/SharedPrefs → Room + DataStore
- **Architecture**: Mixed → Clean Architecture + MVI

### Documentation

- **`REWRITE.md`** ⭐ - Complete migration plan with 8 phases, weekly tracking, feature inventory
- **`CLAUDE.md`** - This file, project context for Claude Code

All other markdown files have been archived/removed to maintain a single source of truth.

### Quick Start for Development

1. **Check Current Phase**: See `REWRITE.md` → Weekly Tracking section
2. **Find Next Task**: See current phase's task list
3. **Follow Migration Workflow**: See `REWRITE.md` → Migration Strategy
4. **Update Progress**: Update weekly tracking section
5. **Run Tests**: Maintain >70% coverage

### Common Build Issues

**"JAVA_HOME not set"**: Install JDK 11+ and set JAVA_HOME environment variable

**Compilation errors**: Most likely related to current migration work. Check `REWRITE.md` for current phase progress.

**Dependency issues**: See `REWRITE.md` → Technical Specifications → Dependency Changes

For detailed troubleshooting, migration patterns, and architectural guidance, see **`REWRITE.md`**.
