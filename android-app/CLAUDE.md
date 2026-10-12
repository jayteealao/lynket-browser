# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Lynket is an Android browser application that provides custom tabs functionality, web heads (bubble-style browsing), and article reading modes. The project is a multi-module Gradle build:

- **lynket**: The application module. Hilt is the only dependency injection system.
- **disk-cache**: A Kotlin disk LRU cache library for Parcelable values (website and article caches).
- **benchmark**: Macrobenchmarks for link-open start-up and web head drag (runs against the `benchmark` build type of `:lynket`).
- **baselineprofile**: Generates the app's Baseline Profile (a link open and the home screen); the profile ships in every non-debug build.

## Build Configuration

### Prerequisites

- JDK 17 or later. `gradle.properties` points `org.gradle.java.home` at the Android Studio JBR 21.
- Android SDK with platform `android-37` (compileSdk) and build-tools 36.0.0 or later.

### Toolchain

- **Android Gradle Plugin**: 9.4.1, with AGP's built-in Kotlin (no `org.jetbrains.kotlin.android` plugin).
- **Gradle**: 9.8.1 (wrapper).
- **Kotlin**: 2.4.21. AGP 9 brings KGP 2.2.10, so the root `build.gradle.kts` puts KGP 2.4.21 and the KSP Gradle plugin on the `buildscript` classpath.
- **KSP**: 2.3.12. Hilt 2.60.1, Room 3 (`androidx.room3` 3.0.3, Gradle plugin `androidx.room3`) and Moshi run on KSP. There is no kapt; `scripts/check-compose-only.sh` fails if kapt, the Kotlin Android plugin or an AGP opt-out flag comes back.
- **gradle.properties**: AGP 9 defaults (non-transitive and final R class, optimized resource shrinking, Hilt's install-in check). Jetifier is off; no dependency needs it.
- **SDK levels**: compileSdk 37 (Compose BOM 2026.09.00 / Compose UI 1.12 needs it), targetSdk 36, minSdk 24. Do not raise targetSdk to 37.
- **Libraries**: Compose BOM 2026.09.00, Coil 3.6.3, Robolectric 4.17 (tests pin `sdk = [34]`), Roborazzi 1.76.0, Baseline Profile plugin and Macrobenchmark 1.5.0, ProfileInstaller 1.4.1.
- **Back handling**: targetSdk 36 turns on predictive back, and the system no longer calls `onBackPressed`. Use `OnBackPressedDispatcher` callbacks.

### Gradle Commands

Run from `android-app/`:
- `./gradlew :lynket:assembleDebug` - Build the debug APK
- `./gradlew :lynket:testDebugUnitTest` - Run the JVM unit tests
- `./gradlew :lynket:verifyRoborazziDebug` - Compare the screenshot goldens (`lynket/src/test/screenshots`); `recordRoborazziDebug` records them again. `ThemeMatrixGoldenTest` draws every screen in the light, dark and black themes at a phone and a fold size
- `bash scripts/check-compose-only.sh` - Fails on an XML layout other than the Custom Tab bottom bar's RemoteViews layout, a menu or preference XML, a View, Fragment or Adapter subclass, `setContentView` with a layout, kapt, or an AGP opt-out flag
- `./gradlew :disk-cache:testDebugUnitTest` - Run the disk cache journal tests
- `./gradlew :lynket:assembleBenchmark :benchmark:assembleBenchmark` - Build the minified benchmark app and the benchmarks
- `./gradlew :lynket:installDebug` - Install the debug build on a device
- `./gradlew :lynket:generateBaselineProfile` - Regenerate the Baseline Profile on a connected device or emulator (API 33 or later) into `lynket/src/main/generated/baselineProfiles/`; commit both files
- `bash maestro/charter/charter-check.sh <phone> <fold> <dir>` - The whole path of a user who updates from release 2.1.3: the update on the phone, a link through the intercept activity, a Chrome Custom Tab with the bottom bar, the Lynket options sheet, web heads through the permission explainer, the drag recording, a native bubble, History from the drawer (read against the database) and the 2.1.3 pinned shortcut; then the update, the drag recording and History on the fold. Each step writes PASS or FAIL to `charter.txt`
- `bash maestro/upgrade/upgrade-check.sh <serial> <dir>` / `maestro/upgrade/tile-check.sh` - Device checks that settings and per-app rules survive an update from release 2.1.3 and that the web heads tile reaches every reader (debug build; it dumps both stores with the debug-only `StoreDumpReceiver`)
- `bash maestro/upgrade/link-source-check.sh <serial> <dir>` - After the upgrade check: no app-detection service or notification, and a link from a blacklisted app follows its per-app rule
- `bash maestro/upgrade/history-check.sh <serial> <dir>` - Device check that 500 history rows of release 2.1.3 (5 written by the app, 495 appended in its format) survive an update and show in the History screen
- `bash maestro/webheads/webheads-check.sh <serial> <dir> <ac19|ac22|ac23>` - Web heads on a device: a head and the trash window and their removal (ac19); the permission explainer, the special-use foreground start and the tab fallback when the start is refused (ac22); a native bubble that opens its page (ac23)
- `bash maestro/webheads/drag-check.sh <serial> <dir>` - Records drag, fling, edge snap, trash, queue, list and tap; a person watches `drag.mp4` and signs `feel-checklist.md`
- `bash maestro/webheads/charter-check.sh <serial> <dir>` - From a 2.1.3 install: upgrade, a link in a tab, web heads turned on through the explainer, the drag check and a native bubble
- `bash maestro/shell/shell-check.sh <serial> <dir> <intro|seed-recents|captures|offline|back-gesture|deeplink>` - Home and first run on a device: the intro once on a fresh install, captures of every shell screen, the offline message, a held back swipe from About, Tips and the drawer, and a web link handed to the launcher (intro, captures and offline reinstall the debug build)
- `bash maestro/settings/settings-check.sh <serial> <dir>` - Every settings screen at every scroll position, the rows and dialogs, the per-app loading list and search, and the no-provider state with Chrome disabled (enabled again on exit), then a link in a Custom Tab
- `bash maestro/shell/legacy-2.1.3-captures.sh <serial> <dir>` - Reference captures of release 2.1.3's intro, changelog, home, menu, About and Tips (installs the existing 2.1.3 APK, then removes it)
- `bash maestro/browsing/browsing-check.sh <serial> <dir> <legacy|captures|entry-points|amp-error|bottom-bar|recreate>` - The browsing dialogs are Compose (`ui/browsing/`: the Lynket options sheet, New tab, the AMP lookup with its offline error, the shortcut creator; Open with uses `AppPickerSheet`); `ChromerOptionsActivity`, `OpenIntentWithActivity`, `NewTabDialogActivity`, `AmpResolverActivity` and `HomeScreenShortcutCreatorActivity` stay and each host one of them on `Theme.Lynket.Translucent`. Custom Tab menu, action button and bottom-bar callbacks are mutable, explicit PendingIntents (`browsing/customtabs/CallbackPendingIntents.kt`) so Chrome can add the page address and the clicked view; receivers read it with `browsingUrl()`. Chrome's bottom-bar views have no resource ids in a UI dump, so the check taps them by label. A tile click only registers with Quick Settings open (`cmd statusbar expand-settings` first)
- `bash maestro/reader/reader-check.sh <serial> <dir> <legacy|captures|error|image|webview|bubble|recreate>` - The article reader, the image viewer and the web view are Compose (`ui/reader/`, `ui/webview/`); `ArticleActivity`, `ImageViewActivity`, `WebViewActivity` and `EmbeddableWebViewActivity` keep their names and each host one screen. The reader and the web view share one menu model (`browsing/menu/MenuDelegate`: `menuItems()`, `bottomBarItems()`, `handleItemSelected`) and one top-bar and bottom-bar component (`ui/browsing/BrowsingBars.kt`). The web view's WebView is a View inside an `AndroidView`, wrapped by a `SwipeRefreshLayout`; the manifest handles rotation, fold, bubble resize and night mode for both web view activities, so the page and its history stay, and `saveState` covers process death (one `WebView created` log line per instance). The check serves `maestro/reader/fixtures/` from this host with `python -m http.server` on a free port and reaches it through `adb reverse`, so every run reads the same article (legacy and captures reinstall the debug build)
- `bash maestro/history-tabs/history-tabs-check.sh <serial> <dir> <captures|clear-all|tabs-reorder|back-gesture>` - History and Tabs are Compose screens (`ui/history/`, `ui/tabs/`); `HistoryActivity` and `TabsActivity` stay as shortcut targets and each host one of them. The check captures both (500 rows; `SEED=direct` writes them straight into the database), deletes all and compares the count with the store, taps a tab and checks its own task comes forward with no new one, and holds a back swipe from History (reinstalls the debug build)

### Build System

- Gradle version catalog (`gradle/libs.versions.toml`) for dependency management.
- KSP for annotation processing; no kapt.

## Module Structure

### lynket

The application. Every screen is Jetpack Compose with Material 3; the only XML layout is the Custom Tab bottom bar, which Chrome draws as RemoteViews.
- **Dependency Injection**: Hilt only
- **Async**: Kotlin coroutines and Flow (no RxJava, no LiveData)
- **Image Loading**: Coil 3. `Lynket` is the `SingletonImageLoader.Factory`; `util/image/ApplicationIconFetcher` loads app icons from `application-icon:<package>` addresses, and `util/image/WebsiteIconLoader` gives favicons (or the 56 dp letter placeholder) as bitmaps for bubbles, task descriptions and shortcuts
- **Storage**: settings in one Preferences DataStore (`user_preferences`, every key listed in `data/preferences/SettingsSchema.kt`; read synchronously through the `settings/Preferences` facade); per-app rules in a second DataStore (`per_app_rules`); history in Room 3 (`chromer.db`, schemas in `lynket/schemas/`, opened on first use by `data/database/HistoryDatabase`, which moves a broken file to `files/history-broken/` and starts empty). The 2.1.3 history file (`databases/History`) is read once, merged by URL, and kept in `files/history-migration/`. Both DataStores migrate once from the old stores (the settings SharedPreferences file, release 2.1.3's PaperDB `files/APPS` book), back them up and never delete them. PaperDB 2.6 is a dependency only for that one-time read.

Key packages:
- `browsing/` - Core browsing functionality including custom tabs, webview, article mode, AMP support
- `bubbles/webheads/` - Floating bubble-style browser windows
- `home/` - The home's view model (`ModernHomeViewModel`) and provider info
- `ui/navigation/` - The Navigation 3 shell: keys, the navigator and the one `NavDisplay`
- `ui/home/`, `ui/intro/`, `ui/about/`, `ui/tips/`, `ui/changelog/` - The Compose home (search bar, drawer, feed), first run, About, Tips and the changelog dialog
- `settings/` - App settings and preferences
- `di/` - Hilt modules
- `data/` - Data layer and repositories
- `tabs/` - Tab management
- `history/` - Browsing history

### Dependency Injection

Every activity, service and receiver is injected by Hilt with `@AndroidEntryPoint`; there is no other component.

## Key Application Components

### Main Entry Points

1. **MainActivity** (`MainActivity.kt`) - The launcher activity and the only host of in-app navigation
2. **BrowserInterceptActivity** (`browsing/browserintercept/BrowserInterceptActivity.kt`) - Handles VIEW intents for http/https URLs
3. **Lynket** Application class initializes:
   - Hilt
   - the settings and per-app rule stores (DataStore)
   - Timber logging (debug builds)
   - the Coil image loader
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
- Debug builds can slow the per-app list to show its loading state: `adb shell "run-as arun.com.chromer.debug sh -c 'mkdir -p files/debug-faults && echo 4000 > files/debug-faults/per-app-slow-load'"` (see `PerAppLoadFaults`).

### Browsing Modes

The app supports multiple browsing modes:
- **Custom Tabs** (`browsing/customtabs/`) - Chrome Custom Tabs integration
- **Web View** (`browsing/webview/`) - Built-in WebView in a Compose screen (`ui/webview/WebViewScreen.kt`)
- **Article Mode** (`browsing/article/`) - Reader-friendly article view in Compose (`ui/reader/`); a page with no article shows the error state with Retry and Open in browser
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

`settings.gradle` includes `:lynket`, `:disk-cache`, `:benchmark` and `:baselineprofile`.

## Dependencies of Note

- **AndroidX**: Core KTX, Browser (Custom Tabs), Lifecycle, Paging, Navigation 3, DataStore, Room 3, SwipeRefreshLayout (pull to refresh around the web view), Palette (website and app colours), ProfileInstaller
- **Compose**: BOM 2026.09.00, Material 3
- **Network**: OkHttp, Retrofit, Moshi
- **Images**: Coil 3 (`coil`, `coil-compose`, `coil-network-okhttp`)
- **Storage migration**: PaperDB 2.6, only for the one-time read of release 2.1.3's per-app rules

## Permissions

Required permissions include:
- Internet and network access
- System alert window (for web heads)
- Package usage stats (read when a link opens, to find the app it came from; no background service)
- Foreground service
- Query all packages

## Further Reading

- `docs/architecture.md` - How the app fits together: the back stack and the entry activities, the stores and their migrations, history, Hilt, the web head overlay service, image loading and the Baseline Profile
