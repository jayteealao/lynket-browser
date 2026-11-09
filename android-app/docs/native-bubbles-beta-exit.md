# When native bubbles leave Beta

Browsing mode offers native bubbles as "Native Bubbles (Beta)", and its warning lists the known
issues. The "(Beta)" label and the warning stay until every criterion below holds. This document
does not remove them; it says what has to be true first.

Each section is a table with three columns:

- **Criterion**: what must be true for a person who uses native bubbles.
- **Check**: the test, script or sign-off that shows it. Test classes live under
  `lynket/src/test` (JVM tests on Robolectric, `./gradlew :lynket:testDebugUnitTest`) or
  `lynket/src/androidTest` (instrumented tests on an emulator). Scripts live under `maestro/`, and
  the header comment of each script says what its modes do.
- **Where**: a JVM test, an emulator, or a real phone.

A criterion holds only when its check has passed on the current code. A pass on an older build, or
on a check that was renamed since, does not count: run it again. Paths and commands below are
relative to `android-app/`.

## The bubble flow on an emulator

Only Android's own notification service sets the bubble flag on a notification, so these two
checks run as instrumented tests on an emulator, not on the JVM.

| Criterion | Check | Where |
|---|---|---|
| A bubble that the app posts with the bubble setting at "All conversations can bubble" gets Android's bubble flag on its notification within 10 s | `NativeBubbleSystemTest`, `postedBubbleCarriesTheBubbleFlag` | Emulator, API 34 |
| With the bubble setting at none, a link in native bubble mode opens once, in a tab that carries the refusal cause for its message, and no bubble of it gets the flag | `NativeBubbleSystemTest`, `refusedBubbleOpensTheLinkInATab` | Emulator, API 34 |

Boot an API 34 emulator headless with the host GPU, then run `NativeBubbleSystemTest` with
`connectedDebugAndroidTest`:

```bash
emulator -avd <name> -no-window -no-audio -no-boot-anim -gpu host -no-snapshot-load
ANDROID_SERIAL=<emulator serial> ./gradlew :lynket:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=arun.com.chromer.bubbles.system.NativeBubbleSystemTest
```

Fall back to `-gpu swiftshader_indirect` only when a host GPU boot fails. The tests need Android 11
or later (`@SdkSuppress`). They change the device's bubble switch and the app's bubble setting
through the shell, so run them on an emulator, not on a phone someone uses. Stop the emulator when
the run ends.
