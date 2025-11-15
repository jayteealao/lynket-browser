# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Lynket is an Android browser application that provides custom tabs functionality, web heads (bubble-style browsing), and article reading modes. The project is structured as a multi-module Gradle build with two primary modules:

- **lynket**: The legacy main application module using traditional Dagger 2 for dependency injection
- **lynket-playground**: A modern experimental module using Whetstone (Anvil-based DI) and Jetpack Compose
- **disk-cache**: A disk LRU cache library module

## Build Configuration

### Prerequisites

**CRITICAL**: This project requires Java/JDK to be installed and JAVA_HOME properly configured before building:
1. Install JDK 11 or later
2. Set JAVA_HOME environment variable to JDK installation path
3. Ensure `java` command is available in PATH

Without proper Java setup, Gradle commands will fail with: "JAVA_HOME is not set and no 'java' command could be found in your PATH."

### Current Build State (Updated 2025-01-XX)

**Active Configuration**:
- **Kotlin**: 1.8.22 (temporary, for kotlin-android-extensions support)
- **Android Gradle Plugin**: 8.3.2
- **Dagger**: 2.50 (upgraded for Kotlin compatibility)
- **Compose Compiler**: 1.4.8 (matched to Kotlin 1.8.22)

**Active Module**: Only `:lynket-playground` is currently built. The main `:lynket` module is being modernized.

**Critical Blockers**:
1. **6 Missing Utility Libraries** - Blocks all compilation
   - `dev.arunkumar.android.common.Extensions.kt` (dpToPx, etc.)
   - `dev.arunkumar.android.common.Resource.kt` (sealed class wrapper)
   - `dev.arunkumar.android.rxschedulers.RxExtensions.kt` (asResource, ioToUi, poolToUi)
   - `dev.arunkumar.android.epoxy.EpoxyExtensions.kt` (span, TotalSpanOverride)
   - `arun.com.chromer.tabs.DocumentUtils.kt` (task management)
   - `arun.com.chromer.shared.ServiceManager.kt` (service lifecycle)

These utilities were previously provided by unpublished SNAPSHOT dependencies from `dev.arunkumar.android` packages that are no longer available.

### Gradle Commands

The project uses Gradle with custom build-logic plugins defined in `build-logic/`. Key constants are defined in `build-logic/src/main/kotlin/constants/Constants.kt`:
- Compile SDK: 31 (legacy lynket module)
- Compile SDK: 34 (lynket-playground module)
- Min SDK: 23 (legacy) / 28 (playground)
- Target SDK: 31 (legacy) / 34 (playground)
- Package name: `arun.com.chromer`

Common commands (requires JAVA_HOME):
- `./gradlew assembleDebug` - Build debug APK (lynket-playground only)
- `./gradlew assembleRelease` - Build release APK
- `./gradlew test` - Run unit tests
- `./gradlew installDebug` - Install debug build to device

### Build System

The project uses:
- Gradle version catalog (`gradle/libs.versions.toml`) for dependency management
- Custom Gradle plugins in `build-logic/` directory
- `android-binary-plugin` custom plugin for main app configuration
- KAPT for annotation processing (Dagger, Butterknife, Epoxy, Glide)
- Scabbard for Dagger graph visualization (currently disabled)

## Module Structure

### lynket-playground

Modern module serving as experimentation ground:
- **Dependency Injection**: Whetstone (Anvil + Dagger) with `@ContributesAppInjector`
- **UI**: Jetpack Compose with Material 3
- **State Management**: Molecule for Compose-based state
- **Architecture**: Activity injection via lifecycle callbacks
- Main packages: `di/`, `home/`, `halo/`, `theme/`

Key files:
- `Lynket.kt`: Application class using `ApplicationComponentOwner` interface
- `di/AppComponent.kt`: Whetstone-based app component with `@MergeComponent`

### lynket (Legacy Module)

Production module with extensive feature set:
- **Dependency Injection**: Traditional Dagger 2 with manual component setup
- **UI**: Mix of XML layouts and Jetpack Compose
- **Reactive Programming**: RxJava 1.x and RxJava 2.x with RxKotlin
- **View Binding**: Butterknife for view injection
- **Lists**: Airbnb Epoxy for RecyclerViews
- **Image Loading**: Glide
- **Storage**: PaperDB for NoSQL persistence

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

Component factories are accessed via `appComponent()` extension function on Application.

**lynket-playground module** uses Whetstone with compile-time code generation via Anvil's `@MergeComponent`.

## Key Application Components

### Main Entry Points

1. **HomeActivity** (`home/HomeActivity.kt`) - Main launcher activity
2. **BrowserInterceptActivity** (`browsing/browserintercept/BrowserInterceptActivity.kt`) - Handles VIEW intents for http/https URLs
3. **Lynket** Application class initializes:
   - Dagger/Whetstone DI
   - PaperDB storage
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

- `AppDetectService` - Detects installed browser apps
- `AppColorExtractorJob` - Extracts app colors for theming (JobService)
- `WebHeadService` - Manages floating web head bubbles
- `KeepAliveService` - Keeps custom tabs connection alive

### Quick Settings Tiles

- `WebHeadTile` - Toggle web heads from quick settings
- `IncognitoTile` - Launch incognito browsing
- `AmpTile` - Toggle AMP mode
- `ArticleTile` - Toggle article reading mode

## Version Configuration

Current module settings are in `settings.gradle`:
- Commented out: `:lynket` and `:disk-cache`
- Active: `:lynket-playground`

This suggests the project is transitioning from legacy to modern architecture.

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
- Package usage stats
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
