# How Lynket fits together

Lynket opens links from other apps in Custom Tabs, its own web view, an article reader or floating
web heads, and remembers how each app's links should open. This page explains the parts of the
app and how they connect. Build and test commands are in the repository `README.md` and in
`android-app/CLAUDE.md`.

Every screen is Jetpack Compose with Material 3. The only XML layout left is the Custom Tab
bottom bar (`res/layout/widget_bottom_bar_layout.xml`), because Chrome draws it as RemoteViews.
`scripts/check-compose-only.sh` fails the build check if a layout, a View screen, a Fragment or
kapt comes back.

## One back stack, thin entry activities

`MainActivity` holds every in-app screen on a single Navigation 3 back stack:

- Each screen is a `@Serializable` key in `ui/navigation/LynketKeys.kt`. `LynketNavDisplay`
  (`ui/navigation/ChromerNavigation.kt`) is the only `NavDisplay`, and screens move with a
  `LynketNavigator` (`navigate`, `back`, `remove`).
- A fresh start builds the first stack once: home, then the changelog after an update, the intro
  on a first run, and a web link handed to the launcher. A restored process keeps its saved stack.
- Another activity, a notification or a device check opens a settings screen by name with the
  `arun.com.chromer.extra.DESTINATION` extra (`Destination` in `LynketKeys.kt`).

The other activities exist because Android or other apps start them by name: the link intercept
(`BrowserInterceptActivity`), the browsing hosts (`CustomTabActivity`, `WebViewActivity`,
`EmbeddableWebViewActivity`, `ArticleActivity`, `ImageViewActivity`), the translucent dialog hosts
(the Lynket options sheet, Open with, New tab, the AMP lookup, the shortcut creator) and the
History and Tabs shortcut targets. Each one hosts a single Compose screen and keeps no navigation
of its own; `BaseActivity`, their usual base, is a plain `AppCompatActivity` with no layout.

A link travels like this: `BrowserInterceptActivity` reads the link and the app it came from (the
referrer, or usage stats when the person allowed them), applies that app's per-app rule
(incognito, a blacklisted app's link goes to the secondary browser), and `browsing/` routes it to
the chosen browsing mode. When a blacklisted app's link cannot reach a secondary browser, the
intercept activity shows a Compose error dialog and stays open until it closes.

## Settings and per-app rules: DataStore

- Settings live in one Preferences DataStore, `user_preferences`. `data/preferences/SettingsSchema.kt`
  lists every key with its type and default. Screens read through `UserPreferencesRepository`
  flows; older code reads synchronously through the `settings/Preferences` facade, which serves
  the store's latest snapshot.
- Per-app rules live in a second DataStore, `per_app_rules`.
- Both stores migrate once from what earlier releases saved. `LegacySettingsMigration` copies the
  old SharedPreferences file before reading it and converts the six list settings that release
  2.1.3 kept as text. `PaperPerAppMigration` copies release 2.1.3's PaperDB book (`files/APPS`)
  and reads the copy. Neither ever deletes or changes the old data; PaperDB stays a dependency
  only for that one read.

## History: Room

History is a Room 3 database, `chromer.db`, with its schemas in `lynket/schemas/`.
`data/database/HistoryDatabase` opens it on first use; a broken file is moved to
`files/history-broken/` and the app starts with empty history. `LegacyHistoryImport` merges the
history file of release 2.1.3 (`databases/History`) by URL, once, and moves the old file to
`files/history-migration/` only after every old URL is found in Room.

## Dependency injection: Hilt

Hilt is the only injection system. Every activity, service and receiver is an
`@AndroidEntryPoint`, view models are `@HiltViewModel`, and the modules are in `di/`. Hilt's
compile-time check that every module is installed in a component is on.

## Web heads: a Compose overlay service

`WebHeadService` is a special-use foreground service. Each web head and the trash target is its
own `TYPE_APPLICATION_OVERLAY` window holding a `ComposeView` (`bubbles/webheads/overlay/OverlayWindow.kt`).
Because a service has no lifecycle, saved state or view model store of its own,
`OverlayLifecycleOwner` gives every window one, and the service moves them through their states
as heads appear and close.

`motion/WebHeadsController.kt` moves the windows: drag, fling, edge snap, the trash lock, the
follower chain and the queue beyond five heads. `motion/WebHeadMotion.kt` holds the springs,
converted from the earlier spring values so the heads feel the same. When the system refuses the
foreground start, the link opens in a browsing tab with a message.

Native bubbles (`bubbles/system/`) are the alternative on newer Android: a conversation shortcut
per site and a bubble notification that opens the page in `EmbeddableWebViewActivity`.

## Images: Coil 3

`Lynket` is Coil's `SingletonImageLoader.Factory`, so Compose images, favicons and app icons share
one loader. Network loading comes from `coil-network-okhttp`. Two helpers sit on top:

- `util/image/ApplicationIconFetcher` loads an installed app's icon from an
  `application-icon:<package>` address, for the home screen, the per-app list and the browsing
  options screen.
- `util/image/WebsiteIconLoader` returns a website's favicon as a software bitmap for bubbles,
  task descriptions and home-screen shortcuts, and draws a 56 dp letter placeholder in the site's
  colour when there is no usable favicon.

## Start-up: the Baseline Profile

The `baselineprofile` module drives two journeys on a device: a link opened through the intercept
activity into a Custom Tab, and a launch to the home screen with its drawer. The plugin writes the
result to `lynket/src/main/generated/baselineProfiles/` (`baseline-prof.txt` and
`startup-prof.txt`), and every non-debug build (benchmark and release) ships it.
`profileinstaller` installs the profile on devices that do not get one from Play. Regenerate it
with `./gradlew :lynket:generateBaselineProfile` after a large change to start-up or the home
screen, and commit both files. The `benchmark` module measures link-open start-up and web head
drag frame times against the minified `benchmark` build.

## Tests

- JVM tests run on Robolectric (`sdk = [34]`): view models, stores and migrations, routing, and
  Compose screens through the Compose test rule.
- Screenshot goldens (Roborazzi) are in `lynket/src/test/screenshots`. Each screen has its own
  golden test, and `ThemeMatrixGoldenTest` draws every screen in the light, dark and black themes
  at a phone and a fold window size.
- Device checks are bash scripts under `maestro/` that drive an emulator with adb and Maestro,
  including the whole path of a user who updates from release 2.1.3
  (`maestro/charter/charter-check.sh`).
