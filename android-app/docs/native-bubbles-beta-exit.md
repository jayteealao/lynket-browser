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

## Privacy and lifecycle

| Criterion | Check | Where |
|---|---|---|
| An incognito link opened as a native bubble adds no row to History | `NativeBubbleIncognitoReadTest` (`an_incognito_bubble_reads_nothing_about_its_site`: the bubble uses the read that saves nothing) | JVM |
| An incognito bubble writes no website cache entry and loads no site icon | `NativeBubbleIncognitoReadTest` (`an_incognito_bubble_reads_nothing_about_its_site`, `an_incognito_bubble_never_loads_the_site_icon`) | JVM |
| No launcher shortcut shows the title or address of an incognito bubble page | `NativeBubbleIdentityTest` (`an_incognito_shortcut_carries_only_the_neutral_label_and_the_lynket_icon`, `an_incognito_bubble_id_cannot_be_matched_against_its_address`) | JVM |
| On a locked phone an incognito bubble never shows, and a normal bubble and the group summary show only the generic public text when the phone hides sensitive content | `NativeBubbleLockScreenTest` | JVM |
| Normal bubbles keep their History row, site icon and shortcut title | `NativeBubbleIncognitoReadTest` (`a_normal_bubble_keeps_the_read_that_saves`), `NativeBubbleIdentityTest` (`a_normal_bubble_keeps_its_title_address_and_site_icon`) | JVM |
| Dismissing a bubble removes its notification and its shortcut | `NativeBubbleDismissTest` (`dismissing_a_bubble_cancels_its_notification_and_removes_its_shortcut`) | JVM |
| The group summary goes with the last bubble, and not before | `NativeBubbleDismissTest` (`the_summary_stays_until_the_last_bubble_is_dismissed`) | JVM |
| A shared link shows its bubble with no fixed 1 second wait: a slow site icon does not hold the first post past 300 ms | `NativeBubblePostingTest` (`a_slow_icon_does_not_hold_the_first_post_past_300_ms`) | JVM |
| Three links shared quickly post three bubbles without each waiting for the one before | `NativeBubblePostingTest` (`three_links_post_without_waiting_for_each_other`) | JVM |
| The bubble notification is a message (`CATEGORY_MESSAGE`) with the locus id of its shortcut | `NativeBubbleConversationTest` (`a_bubble_is_a_message_with_the_locus_id_of_its_shortcut`) | JVM |
| The expanded bubble height follows the window, so it is right in split screen | `NativeBubbleConversationTest` (`the_desired_height_follows_the_window_not_the_display`, `the_desired_height_follows_the_window_on_android_11`) | JVM |
| A favicon for a bubble or a web head decodes at no more than the pixel size of its largest use | `FaviconDecodeSizeTest` | JVM |
| The image loader limits its memory cache while the app is in the background | `ImageLoaderBackgroundLimitTest` | JVM |
| The message of a refused bubble draws edge to edge, with bar icons readable in the light, dark and black themes | `BubbleRefusedEdgeToEdgeTest` | JVM |
| On Android 15 and later, a screen share does not show an incognito page | `WebViewActivitySensitiveContentTest` (API 35 and 36) | JVM |

The bubble flag itself, which the JVM tests can only fake, is the emulator check of
[The bubble flow on an emulator](#the-bubble-flow-on-an-emulator) and the phone check of
[A real phone](#a-real-phone). `NativeBubbleTest` covers what the app does after Android answers:
a bubble Android shows stays, and a plain notification is removed and its page opens in a tab.

## Web heads, the other bubble mode

A person who leaves native bubbles, or whose phone cannot show them, falls back to a tab or to web
heads, so web heads must hold too. Device checks run as
`bash maestro/webheads/webheads-check.sh <serial> <dir> <mode>`, one mode per run.

| Criterion | Check | Where |
|---|---|---|
| With "Display over other apps" off, a shared link opens in a tab and a message says why | `OverlayRefusalTest`, `WebHeadServiceOverlayTest`; `maestro/webheads/webheads-check.sh` `refused`, `maestro/webheads/bubble-guidance-check.sh` `overlay` and `service` | JVM, emulator |
| With the permission off, the overlay settings page opens only when the person taps Open Settings | `OverlayRefusalTest` (`refused_tab_shows_the_overlay_message_and_no_settings_page_before_a_tap`); `webheads-check.sh` `refused` | JVM, emulator |
| After Android restarts the service with no heads, no web heads notification stays | `WebHeadServiceOverlayTest` (`a_restart_with_no_head_stops_and_removes_the_notification`); `webheads-check.sh` `restart` | JVM, emulator |
| After a rotation, every open head rests on a screen edge inside the new bounds | `WebHeadsControllerTest` (`open_heads_move_to_the_stack_edge_after_a_rotation`, `six_heads_low_on_the_left_stay_inside_after_a_rotation`); `webheads-check.sh` `resnap` | JVM, emulator |
| After a rotation, a new head spawns where the stack was | `WebHeadsControllerTest` (`a_new_head_after_a_rotation_spawns_on_the_stack_side_at_its_height_ratio`); `webheads-check.sh` `rotation` | JVM, emulator |
| A normal fling on a 1280 dp wide window snaps a head to an edge | `WebHeadsControllerTest` (`a_fling_ends_at_an_edge`); `webheads-check.sh` `fling` | JVM, emulator |
| TalkBack says the number of heads and whether the trash is locked, and a person hears "Release to close" | `WebHeadBubbleSemanticsTest`, `TrashTargetSemanticsTest`; `webheads-check.sh` `speech` and `trash`; listening with TalkBack on a real phone | JVM, emulator, real phone |
| A head that locks onto the trash or leaves it ticks once, and a person signs off the feel | `WebHeadServiceOverlayTest` (`locking_and_leaving_the_trash_ticks_on_the_head_window`); `webheads-check.sh` `trash`; `bash maestro/webheads/drag-check.sh <serial> <dir>` and rows 11-13 of `maestro/webheads/feel-checklist.md`, signed on a real phone | JVM, emulator, real phone |
