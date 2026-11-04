# Native bubbles past the stack limit

What Android does with Lynket's native bubbles when more are open than the stack shows. These are measurements on emulators, taken with `maestro/webheads/overflow-probe.sh` and the debug-only `BubbleProbeReceiver` on 2026-11-03. Android 12 and 13 sit between the two versions and were not measured, and neither was a phone maker's own build of Android.

## Android 14

Build `UE1A.230829.050` (`sdk_gphone64_x86_64`), 1080 x 2400, density 420.

| Measure | Result |
|---|---|
| Stack limit | 5 |
| Overflow limit | 16 |
| Re-post with nothing changed (`same`) | Returns the bubble. No alert |
| Re-post with changed text (`text`) | Returns the bubble. No alert |
| Cancel, then post (`fresh`) | Returns the bubble. No sound, heads-up row, flyout or expansion, but Android marks the notification interruptive |
| Delete intent on a bubble that the person drags away | Fires once |
| Delete intent on a bubble that ages out of the stack | Does not fire within 10 s |
| Delete intent on a bubble that the overflow drops | Does not fire |

## Android 11

Build `RSR1.240422.006` (`sdk_gphone_x86_64`), 1080 x 2400, density 420.

| Measure | Result |
|---|---|
| Stack limit | 5 |
| Overflow limit | 16, read from the app's own notifications, because the shell's dump does not list the overflow. The overflow panel with 3 bubbles agrees with that count |
| Re-post with nothing changed (`same`) | Returns the bubble. No alert |
| Re-post with changed text (`text`) | Returns the bubble. No sound, heads-up row, flyout or expansion, but Android marks the notification interruptive |
| Cancel, then post (`fresh`) | Returns the bubble. No sound, heads-up row, flyout or expansion, but Android marks the notification interruptive |
| Delete intent on a bubble that the person drags away | Fires once |
| Delete intent on a bubble that ages out of the stack | Does not fire within 10 s |
| Delete intent on a bubble that the overflow drops | Does not fire |

`same` is the only re-post that Android leaves uninterruptive on both versions.

"No alert" means all of these held: no sound or vibrate key, `showFlyout` false, the stack stayed collapsed, and four screenshots in the next 3 s show no heads-up row. `mIsInterruptive` false is added where the table says so. An emulator cannot show a felt vibration, so that check reads the key.

## What the app can read

Both versions gave the same result from `NotificationManager.getActiveNotifications()`.

| Where the bubble is | The app reads |
|---|---|
| In the stack | Active, with `FLAG_BUBBLE` |
| In the overflow | Active, without `FLAG_BUBBLE` |
| Dropped by the overflow | Not active |
| Returned by a re-post | Active, with `FLAG_BUBBLE` again |

So a bubble that aged out differs from one the person closed in two ways: no delete intent arrives, and its notification stays active.

- Each new bubble past 21 drops the oldest overflow bubble. The app's count of active bubble notifications stops at 21 and gets no signal for the bubble that left.
- The shortcut of a dropped bubble stays behind. After 24 links the app had 21 notifications and 24 bubble shortcuts, 12 of them dynamic.
- No Lynket row shows in the shade while a bubble is in the overflow, on either version.
- The newest overflow bubble was read from the order the bubbles left the stack. Android 14 lists the overflow newest first; Android 11 does not list it.

## What the app does with this

`BubbleReconciler` brings the list of open bubbles (`BubbleRegistry`) in line with this read when a bubble opens, when one is dismissed, and at each activity start. An opening or a dismissal can reach it before the flag of an aged bubble clears, so the read at an activity start is the one that a bubble page can rely on.

- A bubble with `FLAG_BUBBLE` reads as in the stack, and an active one without it as in the overflow. A bubble that has just posted reads as in the overflow until its flag shows.
- A bubble that is no longer active leaves the list with no mark, because the person did not close it. It is not offered for a return.
- A failed read changes nothing, so a read that throws never loses an open page.
- The read never adds an entry, so the bubbles that outlive the app's process stay unlisted.
- The shortcut of a dropped bubble still stays behind.

On the Android 14 emulator, `overflow-probe.sh registry` read 7 open with 2 in the overflow, 8 with 3, and 21 with 16 at 21 links. From link 22 the total stayed at 21, and each dropped bubble left the list with no `Bubble dismissed` line.

A close in the stack posts the newest overflow bubble again, from the data that bubble last posted.

- Newest means last in open order. A link sent again keeps its first place, although Android moves it to the front, so the two can differ for a link sent twice.
- Every update of a posted bubble carries `FLAG_ONLY_ALERT_ONCE`, so the post is the silent `same` re-post of the tables above, rebuilt rather than cloned.
- One return runs at a time, and each waits up to 1.5 s for the flag, so the next return reads where this bubble went.
- A close in the overflow returns nothing, and neither does a second delete intent for one close.
- A failed return changes nothing and says nothing: the bubble stays in the overflow, and the next close tries it again.
- A returned bubble with no icon shows its colour disc, then loads the icon and updates in place, keeping the posted title and text.
- A drag of the stack is one close per bubble, so with 7 open it closes 5 and brings the 2 overflow bubbles back.

A drag needs care, because Android takes `FLAG_BUBBLE` off the whole stack at once and sends the delete intents one by one. Until an intent arrives, its closed bubble reads as an overflow bubble. Android also delivers the next intent only when the receiver has finished the last one. So the receiver does not keep the broadcast open for the return, a close in the stack leaves its read to the return, and the return waits 500 ms for the rest of the drag. A delete intent later than that wait would post its closed bubble for a moment.

| `overflow-probe.sh` | Android 11 | Android 14 |
|---|---|---|
| `close`: closes 1 and 2 each return the newest overflow bubble; close 3 returns none | Yes | Yes |
| The return is interruptive, or has a sound key or a flyout | No | No |
| `close-stack`: 5 closes, 2 returns, newest first, total 2 | Yes | Yes |
| `close-stack`: the stack expands, or a heads-up row shows | No | No |
| `close-icon`: disc, then the icon in place | Not run | Yes |

The rebuilt post behaves as the probe's clone did on both versions, so the fallbacks to a clone or a changed text were not needed.

## What clears the stack and the overflow

Measured on Android 14 only.

| Action | Stack and overflow |
|---|---|
| The app cancels all its notifications | Both empty |
| A force stop with bubbles open | Both stay, although the app now has no notifications |
| The app's data cleared after that | Both stay |
| An uninstall | Both empty |

A later cancel by the app does not clear what a force stop left behind, so the app has no read of those bubbles and no way to remove them.

## Android source, as read

These lines describe the `android11-release` and `android14-release` branches, which are not the emulator builds. They are what the code says, not behaviour. Where they disagree with the tables above, the tables win.

- `BubbleController.updateBubble` on `android14-release` (lines 1292-1302) keeps a same-text update of an overflow bubble in the overflow: "Update the bubble but don't promote it out of overflow". **The measurement disagrees: `same` returned the bubble on Android 14.** The cause was not traced. By then the aged notification has lost `FLAG_BUBBLE`, which may make the re-post a new bubble request rather than an update; that is a guess, and it should be checked before the app relies on it on Android 12 or 13.
- `BubbleController.updateBubble` on `android11-release` (line 1113) takes the bubble out of the overflow on any update.
- `BubbleData` sends the delete intent only for a user gesture (`android11-release` line 692, `android14-release` line 1027).
- `BubblePositioner.calculateMaxBubbles` (line 223) on `android14-release` lowers the stack limit below 5 on a screen with a short edge, so the app must not hard-code 5. `bubbles_max_overflow` is 16 on both branches.
