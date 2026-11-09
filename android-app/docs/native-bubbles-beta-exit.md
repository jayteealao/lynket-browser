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
