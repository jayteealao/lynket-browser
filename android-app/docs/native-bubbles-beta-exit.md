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

## One browsing mode

| Criterion | Check | Where |
|---|---|---|
| No code outside the one mode reader and the migration reads the old web heads and native bubbles switches | `PreferencesFacadeTest` (`eachValidStoredModeReadsBackUnchanged`, `writingAModeStoresExactlyThatMode`); and `git grep -nE "fun (webHeads\|nativeBubbles)\(" -- lynket/src/main` prints only the two private readers of `BrowsingModeMigration.kt` | JVM, source search |
| A restored settings set with both switches on reads as one mode (web heads), and the settings screen agrees | `BrowsingModeMigrationTest` (`aRestoredSetWithBothSwitchesOnIsStoredAsWebHeadsAndTheSettingsScreenAgrees`), `PreferencesFacadeTest` (`bothModesOnReadsAsWebHeadsAndIsStoredAsWebHeadsWithoutWaitingOnTheMainThread`) | JVM |
| In native bubble mode no web head setting row is enabled that has no effect | `SettingsRulesTest` (`webHeadRowsMinimizeAndTheNoteFollowTheBrowsingMode`), `SettingsScreensUiTest` (`lookAndFeelWebHeadRowsAreGreyUnderTheNoteInNativeBubbleMode`, `browsingOptionsWebHeadRowsAreGreyUnderTheNoteInNativeBubbleMode`) | JVM |
| In native bubble mode the minimize option's label names a bubble, not a web head | `SettingsRulesTest` (`webHeadRowsMinimizeAndTheNoteFollowTheBrowsingMode`), `SettingsScreensUiTest` (`minimizeIsGreyAndSaysItOpensABubbleInNativeBubbleMode`); `maestro/webheads/webheads-check.sh` `bubble-minimize` | JVM, emulator |
| Aggressive loading still works in web heads mode | `PreferencesFacadeTest` (`aggressiveLoadingIsOnOnlyInWebHeadsModeWithItsSettingOn`); `webheads-check.sh` `aggressive` | JVM, emulator |

## Open bubbles and the overflow

Android shows five bubbles in its stack and moves older ones to an overflow that it does not report
to the app. [native-bubble-overflow.md](native-bubble-overflow.md) holds the measured behaviour.
Device checks run as `bash maestro/webheads/overflow-probe.sh <serial> <dir> <mode>`, which refuses
any serial that is not an emulator, on an Android 11 and an Android 14 emulator each.

| Criterion | Check | Where |
|---|---|---|
| The app's list holds every open bubble of the active mode, in open order, overflow included | `BubbleRegistryTest`, `BubbleReconcilerTest`; `overflow-probe.sh` `registry` | JVM, emulators on Android 11 and 14 |
| With 7 bubbles open, a page in a bubble reads its place and the total ("3 of 7"), and no notification, shortcut or intent holds the count | `NativeBubbleShadeTest` (`no_notification_shortcut_or_intent_of_seven_bubbles_holds_the_count`); `overflow-probe.sh` `count` | JVM, emulators on Android 11 and 14 |
| When the person closes a bubble in the stack and the overflow holds one, the newest overflow bubble returns with no alert: at once in a folded stack, when the stack folds in an open one, and at once when the X closes the last bubble of an open stack | `NativeBubblePostingTest` (`a_fold_fills_every_free_place_newest_first`), `NativeBubbleDismissTest` (`a_close_in_the_stack_posts_the_newest_overflow_bubble_again`), `NativeBubbleShadeTest` (`a_returned_bubble_carries_no_alert`); `overflow-probe.sh` `return`, `close` and `close-last` | JVM, emulators on Android 11 and 14 |
| A bubble that the person closed on purpose, or turned off from its Manage menu, does not come back | `BubbleRegistryTest` (`a_marked_entry_stays_listed_and_is_never_offered_for_a_return`), `NativeBubbleDismissTest` (`a_close_in_the_overflow_posts_nothing_and_stays_closed`); `overflow-probe.sh` `close` and `dont-bubble` | JVM, emulators on Android 11 and 14 |
| The behaviour above is measured on Android 11 and Android 14, and the results in [native-bubble-overflow.md](native-bubble-overflow.md) match the current build | `overflow-probe.sh` `registry`, `count`, `return`, `close` and `dont-bubble` on both versions | Emulators on Android 11 and 14 |

## Page state

Device checks: `bash maestro/browsing/error-page-check.sh <serial> <dir> <mode>`,
`bash maestro/browsing/bubble-menu-check.sh <serial> <dir> <mode>` and
`maestro/webheads/webheads-check.sh` page state modes. The `offline-bubble`, `retry-bubble` and
`full-window` modes start after `webheads-check.sh <serial> <dir> bubble`; the header comment of each
script gives the setup of the other modes.

| Criterion | Check | Where |
|---|---|---|
| With the phone offline, a page in a bubble shows the error page with Retry, under a top bar that keeps Close and the count | `WebViewErrorPageTest` (`anOfflineBubblePageShowsItsCauseAndRetryUnderCloseAndTheCount`); `maestro/browsing/error-page-check.sh` `offline-bubble` | JVM, emulator |
| Retry loads the page once the connection is back, in the same bubble | `WebViewRetryTest` (`retryInABubbleLoadsTheFailedAddressAndThePageShowsInTheSameBubble`); `error-page-check.sh` `retry-bubble` and `retry-offline` | JVM, emulator |
| A certificate error shows the error page and gives no way past it | `WebViewErrorPageTest` (`aCertificateErrorCancelsTheLoadAndShowsNoWayPastIt`), `WebViewRetryTest` (`aRetryOfACertificateErrorIsCancelledAgainWithNoWayPastIt`); `error-page-check.sh` `certificate` and `retry-certificate` | JVM, emulator |
| Open in full window shows the same page in a full window and closes the bubble | `OpenInFullWindowTest` (`theSamePageOpensInAFullWindowOfItsOwnAndTheBubbleCloses`); `maestro/browsing/bubble-menu-check.sh` `full-window` | JVM, emulator |
| The bars of a page in a bubble take the site's colour when the site has one, with a readable title | `BubbleSiteColourPageTest`, `BubbleSiteColourIntentTest`; `bubble-menu-check.sh` `site-colour` | JVM, emulator |
| A web head shows the loading ring from the open until its page finishes, and only the front head turns | `WebHeadRingTest` (`aLoadingFrontHeadTurnsItsArcAndFadesItsFill`); `webheads-check.sh` `page-state`; `webheads-check.sh` `page-state-worst` on a real phone for the frame time | JVM, emulator, real phone |
| A web head shows its loaded state after the page finishes | `WebHeadRingTest` (`aLoadedPageShowsTheHeadOfToday`); `webheads-check.sh` `page-state` | JVM, emulator |
| TalkBack says "loading" or "loaded" for a head, and a person hears it | `WebHeadBubbleSemanticsTest` (`a_loading_head_says_loading`, `a_loaded_head_says_loaded`); `webheads-check.sh` `page-state-speech`; listening with TalkBack on a real phone | JVM, emulator, real phone |
| The web view uses only stable androidx.webkit calls, and a link opens no slower than before | `WebViewWarmUpTest`; `./gradlew :lynket:lintDebug` (no opt-in or experimental call); `git grep -ln "androidx.webkit" -- lynket/src/main` lists only `WebViewWarmUp.kt`; `bash maestro/browsing/link-open-timing.sh <serial> <dir> bubble` on the build before and after, then `... compare <before.tsv> <after.tsv>` prints PASS | JVM, lint, emulator |

`BubbleMenuAuditTest` keeps the items a bubble hides in line with
`lynket/src/test/resources/bubble-menu-audit.tsv`; see
[The known issues of the warning](#the-known-issues-of-the-warning).

## The known issues of the warning

The warning shown when a person picks Native Bubbles (Beta) lists four known issues
(`browsing_mode_native_bubbles_issue_*` in `lynket/src/main/res/values/strings.xml`):

1. Pages in a bubble open in Lynket's web view, not in your browser.
2. Android shows five bubbles at a time; more go to its overflow.
3. Some menu items are hidden in a bubble.
4. When Android does not show a bubble, the page opens in a tab.

| Criterion | Check | Where |
|---|---|---|
| Each of the four issues is either fixed, and its line leaves the warning, or kept as how native bubbles work and reworded as such outside a "Known issues" list | A read of the four strings against the code; `NativeBubblesWarningTest` updated to the new text and green | JVM, review |
| What a bubble hides from its menu and bottom bar is the list the device audit found, and no more | `BubbleMenuAuditTest`; `maestro/browsing/bubble-menu-check.sh` `audit-menu`, `audit-action`, `audit-bar` and `hidden` | JVM, emulator |
| The warning keeps naming the bubble setting "All conversations can bubble" for as long as Android needs it | `NativeBubblesWarningTest` (`theLeadNamesTheBubbleSettingInQuotes`) | JVM |

## A real phone

Emulator bubbles can differ from a phone's: phone makers change the bubble stack, its overflow and
its settings. The emulator tests run on Google's API 34 image only.

| Criterion | Check | Where |
|---|---|---|
| The bubble flow of [The bubble flow on an emulator](#the-bubble-flow-on-an-emulator) holds on a real phone on Android 11 or later that is named as the test phone | `NativeBubbleSystemTest` with `ANDROID_SERIAL` set to the phone (it changes the phone's bubble settings, so use a phone set aside for testing); or by hand: a link in native bubble mode shows a bubble, and `dumpsys notification` shows the bubble flag (`0x1000`) on its notification | Real phone |
| A refused bubble opens its link in a tab with the right message, and Open Settings opens the matching Android page, on that phone | `bash maestro/webheads/bubble-guidance-check.sh <serial> <dir> <mode>` with `all`, `selected` and `none` | Real phone |
| The same flow holds on one phone maker's build on the newest Android version available | The two rows above on that phone; the Samsung phone on Android 17 in [native-bubble-overflow.md](native-bubble-overflow.md#a-samsung-phone-on-android-17) is the example so far | Real phone |
| The phone checks of the earlier sections pass: TalkBack heard saying "Release to close", "loading" and "loaded", the trash ticks signed in `maestro/webheads/feel-checklist.md`, and the frame time of loading heads within one refresh interval | See [Web heads, the other bubble mode](#web-heads-the-other-bubble-mode) and [Page state](#page-state) | Real phone |

## CI

Today CI runs no check: every job in `.github/workflows/android.yml` is commented out, and the
emulator tests run by hand. An emulator test can fail for reasons of the emulator, such as a slow
boot or a busy host, so a CI gate on it can block work that is fine.

| Criterion | Check | Where |
|---|---|---|
| This document records how the emulator bubble tests run before each release, chosen from the two options below | This section names the option taken and links its evidence | This document |

The two options, and the evidence each needs before the label goes:

- **A CI job runs the emulator tests.** Evidence: a CI job that boots an emulator and runs
  `NativeBubbleSystemTest` on each change to the bubble code, with a record of repeated green runs
  (for example 20 runs in a row with no failure that the code did not cause).
- **The release checklist runs them.** Evidence: a release checklist step that runs the command of
  [The bubble flow on an emulator](#the-bubble-flow-on-an-emulator) before each release, with its
  result kept with the release.

The choice is open.
