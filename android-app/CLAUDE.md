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
- **KSP**: 2.3.12. Hilt 2.60.1, Glide (KSP artifact), Room 3 (`androidx.room3` 3.0.3, Gradle plugin `androidx.room3`) and Moshi run on KSP. Glide's KSP processor generates no `GlideApp`; load images with `Glide.with`.
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
- `bash maestro/upgrade/history-check.sh <serial> <dir>` - Device check that 500 history rows of release 2.1.3 (5 written by the app, 495 appended in its format) survive an update and show in the History screen
- `bash maestro/webheads/webheads-check.sh <serial> <dir> <ac19|ac22|ac23>` - Web heads on a device: a head and the trash window and their removal (ac19); the permission explainer, the special-use foreground start and the tab fallback when the start is refused (ac22); a native bubble that opens its page (ac23)
- `bash maestro/webheads/drag-check.sh <serial> <dir>` - Records drag, fling, edge snap, trash, queue, list and tap; a person watches `drag.mp4` and signs `feel-checklist.md`
- `bash maestro/webheads/charter-check.sh <serial> <dir>` - From a 2.1.3 install: upgrade, a link in a tab, web heads turned on through the explainer, the drag check and a native bubble
- `bash maestro/shell/shell-check.sh <serial> <dir> <intro|seed-recents|captures|offline|back-gesture|deeplink>` - Home and first run on a device: the intro once on a fresh install, captures of every shell screen, the offline message, a held back swipe from About, Tips and the drawer, and a web link handed to the launcher (intro, captures and offline reinstall the debug build)
- `bash maestro/settings/settings-check.sh <serial> <dir>` - Every settings screen at every scroll position, the rows and dialogs, the per-app loading list and search, and the no-provider state with Chrome disabled (enabled again on exit), then a link in a Custom Tab
- `bash maestro/shell/legacy-2.1.3-captures.sh <serial> <dir>` - Reference captures of release 2.1.3's intro, changelog, home, menu, About and Tips (installs the existing 2.1.3 APK, then removes it)

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
- **Storage**: settings in one Preferences DataStore (`user_preferences`, every key listed in `data/preferences/SettingsSchema.kt`; read synchronously through the `settings/Preferences` facade); per-app rules in a second DataStore (`per_app_rules`); history in Room 3 (`chromer.db`, schemas in `lynket/schemas/`, opened on first use by `data/database/HistoryDatabase`, which moves a broken file to `files/history-broken/` and starts empty). The 2.1.3 history file (`databases/History`) is read once, merged by URL, and kept in `files/history-migration/`. Both DataStores migrate once from the old stores (the settings SharedPreferences file, release 2.1.3's PaperDB `files/APPS` book), back them up and never delete them. PaperDB 2.6 is a dependency only for that one-time read.

Key packages:
- `browsing/` - Core browsing functionality including custom tabs, webview, article mode, AMP support
- `bubbles/webheads/` - Floating bubble-style browser windows
- `home/` - The home's view model (`ModernHomeViewModel`) and provider info
- `ui/navigation/` - The Navigation 3 shell: keys, the navigator and the one `NavDisplay`
- `ui/home/`, `ui/intro/`, `ui/about/`, `ui/tips/`, `ui/changelog/` - The Compose home (search bar, drawer, feed), first run, About, Tips and the changelog dialog
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

1. **MainActivity** (`MainActivity.kt`) - The launcher activity and the only host of in-app navigation
2. **BrowserInterceptActivity** (`browsing/browserintercept/BrowserInterceptActivity.kt`) - Handles VIEW intents for http/https URLs
3. **Lynket** Application class initializes:
   - Dagger/Whetstone DI
   - the settings and per-app rule stores (DataStore)
   - RxDogTag (debug builds)
   - Timber logging (debug builds)
   - Epoxy
   - MultiDex

### In-app navigation (Navigation 3)

- Every in-app screen is a `@Serializable` `LynketKey` (`ui/navigation/LynketKeys.kt`) on one back stack, `rememberNavBackStack` in `MainActivity`, shown by `LynketNavDisplay` (`ui/navigation/ChromerNavigation.kt`), the only `NavDisplay`. Screens take a `LynketNavigator` (`navigate`, `back`, `remove`); there is no NavHost or NavController.
- A fresh start (no saved state) builds the first stack once with `LynketNavigator.initialKeys`: home, the changelog when the version changed (`Changelog.shouldShow`), the intro on a first run (`Preferences.isFirstRun`) and a web link handed to the activity. A restored process keeps its saved stack.
- The changelog is a dialog destination (`DialogSceneStrategy`). Back pops one key; at the root the activity finishes. Predictive back previews the previous destination.
- Links reach the activity through `WebUrlDeepLinkMatcher` (http and https only); a malformed one shows the link error state.
- Shared states live in `ui/components/`: `EmptyState`, `ErrorState`, `LoadingList`/`LoadingTiles` and `showTransientError` for snackbars. `NetworkMonitor` (`util/network/`) reports connectivity for the offline message.
- `MainActivity` exposes Compose test tags as resource ids for the device checks. Tests swap screens through `LynketEntries`; text with links never goes idle inside a Robolectric dialog window, so back stack tests fake the changelog content and `ShellGoldenTest` covers the real one.
- Settings, Browsing mode, Look and feel, Browsing options, Per app settings and Custom Tab provider selection are Compose screens on the same back stack (`ui/settings/`, `ui/perapp/`, `ui/providerselection/`). `ui/settings/SettingsKeyTable.kt` lists every stored key with its screen, group, order and control (or the code that reads it), and `SettingsRules.kt` holds the dependent rows and dialogs of the old preference screens; `SettingsViewModel` writes each change and its dependent values in one store edit. There is no androidx.preference.
- Another activity or a device check opens a settings screen by name: `adb shell am start -n arun.com.chromer.debug/arun.com.chromer.MainActivity -f 0x24000000 --es arun.com.chromer.extra.DESTINATION BrowsingMode` (`Destination` in `LynketKeys.kt`; allowed: Settings, BrowsingMode, LookAndFeel, BrowsingOptions, PerApp, ProviderSelection; anything else is ignored).
- Debug builds can slow the per-app list to show its loading state: `adb shell run-as arun.com.chromer.debug sh -c 'mkdir -p files/debug-faults && echo 4000 > files/debug-faults/per-app-slow-load'` (see `PerAppLoadFaults`).

### Browsing Modes

The app supports multiple browsing modes:
- **Custom Tabs** (`browsing/customtabs/`) - Chrome Custom Tabs integration
- **Web View** (`browsing/webview/`) - Built-in WebView
- **Article Mode** (`browsing/article/`) - Reader-friendly article view
- **AMP Mode** (`browsing/amp/`) - Accelerated Mobile Pages
- **Web Heads** (`bubbles/webheads/`) - Floating bubble browser windows
  - Each head and the trash is its own `TYPE_APPLICATION_OVERLAY` window holding a `ComposeView` (`overlay/OverlayWindow.kt`); the service owns their lifecycle, saved state and view model store (`overlay/OverlayLifecycleOwner.kt`).
  - `motion/WebHeadsController.kt` moves the windows: drag, fling, edge snap, trash lock, the follower chain, the queue beyond five heads and coasting. `motion/WebHeadMotion.kt` holds the springs, converted from the earlier Rebound values with Rebound's own formulas so the heads feel the same; `WebHeadMotionTest` pins the numbers.
  - The service starts as a special-use foreground service (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` in the manifest). When the system refuses the start, the link opens in a browsing tab with a message. Debug builds can force the refusal: `adb shell run-as arun.com.chromer.debug touch files/debug-faults/webhead-start-fail` (see `WebHeadStartFaults`).
  - Native bubbles (`bubbles/system/`) need a long-lived conversation shortcut per site and a person that is not a bot; with notifications or bubbles off for the app, the link opens in a tab.

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
- **UI Libraries**: MaterialDialogs, PhotoView, Iconics core (icons of the remaining View screens), Navigation 3
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
