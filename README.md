
# Lynket
**Android browser app based on Custom Tabs protocol.**

<img src="android-app/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" align="left"
width="200"
    hspace="10" vspace="10">

Lynket utilizes Chrome Custom Tab API to create a customized browsing experience while adding innovative features like background loading with floating bubbles, article mode and multitasking using Android's recent menu.

Lynket is available for free on the Google Play Store.


<a href="https://play.google.com/store/apps/details?id=arun.com.chromer">
    <img alt="Get it on Google Play"
        height="80"
        src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" />
</a>

If you have got some time, read the introduction post on [my blog](https://arunkumar.dev/Lynket-browser-2nd-anniversary-update/) or watch intro [Video](https://www.youtube.com/watch?v=Hcd2R2Lh5ks&t=2s)

![enter image description here](https://raw.githubusercontent.com/arunkumar9t2/chromer/master/art/chromer_screenshots.png)

## Features
| Features| Demo|
|--|--|
| **Web heads** - Floating bubbles to load multiple links and launch them.  | ![enter image description here](https://raw.githubusercontent.com/arunkumar9t2/chromer/master/art/Web%20heads%20intro.gif) |
| **Web heads** - Intelligently manages background loading of Custom Tabs using Android's overview screen. Uses Lollipop Document API.| ![enter image description here](https://raw.githubusercontent.com/arunkumar9t2/chromer/master/art/Background%20Loading.gif) |
| **Provider** - Lynket works with any custom tab compatible browser. This means features like adblock, Google Account sync, data saver are inherited.  | ![enter image description here](https://raw.githubusercontent.com/arunkumar9t2/chromer/master/art/Provider%20Selection.gif) |
| **Article mode** - Filters all unnecessary content and renders web page on a RecyclerView efficiently. Uses Crux library.  | ![enter image description here](https://raw.githubusercontent.com/arunkumar9t2/chromer/master/art/Article%20Mode.gif) |
| **Minimize and Tabs** - Lynket can minimize the tab and also show all active tabs for you to quickly switch to.  |  ![enter image description here](https://raw.githubusercontent.com/arunkumar9t2/chromer/master/art/Multitasking.gif)|


## Architecture and Code Structure
Lynket is an all-Compose app in Kotlin. One activity holds every in-app screen on a single back stack; the browsing, dialog and web head entry points are thin activities and a service that each host one Compose screen. Hilt injects every Android component, settings and per-app rules live in DataStore, history in Room, and Coil loads images. The source is packaged by feature. [android-app/docs/architecture.md](android-app/docs/architecture.md) explains the parts and how they fit together.

## Building

The Gradle project lives in `android-app/`. You need JDK 17 or later and the Android SDK with platform 37 (`compileSdk`; the app targets SDK 36). Run these from `android-app/`:

| Task | Command |
|---|---|
| Debug APK | `./gradlew :lynket:assembleDebug` |
| Release APK (minified, with the Baseline Profile) | `./gradlew :lynket:assembleRelease` |
| Unit tests (JVM, Robolectric) | `./gradlew :lynket:testDebugUnitTest` |
| Compare the screenshot goldens | `./gradlew :lynket:verifyRoborazziDebug` |
| Record the screenshot goldens again after a deliberate UI change | `./gradlew :lynket:recordRoborazziDebug` |
| Check that no XML layout, View screen or kapt came back | `bash scripts/check-compose-only.sh` |
| Build the benchmark app and the macrobenchmarks | `./gradlew :lynket:assembleBenchmark :benchmark:assembleBenchmark` |
| Run the macrobenchmarks on a connected device | `./gradlew :benchmark:connectedBenchmarkAndroidTest` |
| Generate the Baseline Profile on a connected device (API 33 or later) | `./gradlew :lynket:generateBaselineProfile` |

The screenshot goldens are in `android-app/lynket/src/test/screenshots`; every screen has one in the light, dark and black themes at a phone and a fold window size. Device checks are bash scripts under `android-app/maestro/` that drive an emulator with adb and [Maestro](https://maestro.mobile.dev); each script's header says what it checks and what it needs. `maestro/charter/charter-check.sh <phone> <fold> <dir>` runs the whole path of a user who updates from release 2.1.3.

### Code Style

 - [mNo Hungarion notation.](http://jakewharton.com/just-say-no-to-hungarian-notation/)
 - No `Impl` suffixes for interface implementations. Instead name based on what it does. Ex: `AppStore` and implementations `AppDiskStore` and `AppSystemStore`.
 - All new modules are preferred to be written in `Kotlin`
 - Formatting - Android Studio Default.

## Contributions
You are more than welcome to contribute to Lynket's development. New features are branched off of `develop` . PRs are welcome against the `develop` branch. 
How you can help:

 - Grab an issue from [issues](https://github.com/arunkumar9t2/chromer/issues) section that interests you.
 - Unit testing using Roboelectric and Dagger 2 is already setup but coverage is rather low. Adding tests to existing features would greatly help! Refer [src/test/](https://github.com/arunkumar9t2/chromer/tree/master/app/src/test/java/arun/com/chromer).
 - Break the ice. Feel free to create an issue to discuss your ideas.

## License

Lynket is licensed under the [GNU v3 Public License.](LICENSE)

