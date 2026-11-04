# Native bubbles past the stack limit

What Android does with Lynket's native bubbles when more are open than the stack shows. These are measurements on emulators, taken with `maestro/webheads/overflow-probe.sh` and the debug-only `BubbleProbeReceiver` on 2026-11-03 and 2026-11-04. Android 12 and 13 sit between the two versions and were not measured, and neither was a phone maker's own build of Android.

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

## The shade

Read with `overflow-probe.sh shade` on 2026-11-04, with the summary and every bubble notification active. The seventh link has a long address.

| Open | Android 14 | Android 11 |
|---|---|---|
| 1 bubble | No Lynket row. 2 notifications of the app are active | No Lynket row. 2 active |
| 7 bubbles, 2 of them in the overflow | No Lynket row. 8 active | No Lynket row. 8 active |
| 7 bubbles, with a page on screen that reads 3 of 7 | No Lynket row. 8 active | No Lynket row. 8 active |

- No notification, summary or shortcut text holds the count, at any of the three points, on either version. The count is drawn in the page and nowhere else.
- The summary keeps its text, "Active bubbles" and "Showing bubble notifications, swipe to expand". It stays active, and Android hides it while a bubble is open.
- With a page on screen, the screenshot is the evidence for the row: the dump of the screen can read the page and not the shade. The shade is see-through on Android 11, so the page or the stack can show behind it.
- A phone maker's own build was not measured, so a row there is not ruled out.

`NativeBubbleShadeTest` holds the text on the JVM: no count in what the manager posts, the summary's words, and no alert on a returned bubble.

## What the app does with this

`BubbleReconciler` brings the list of open bubbles (`BubbleRegistry`) in line with this read when a bubble opens, when one is dismissed, and at each activity start. An opening or a dismissal can reach it before the flag of an aged bubble clears, so the read at an activity start is the one that a bubble page can rely on.

- A bubble with `FLAG_BUBBLE` reads as in the stack, and an active one without it as in the overflow. A bubble that has just posted reads as in the overflow until its flag shows.
- A bubble that is no longer active leaves the list with no mark, because the person did not close it. It is not offered for a return.
- A failed read changes nothing, so a read that throws never loses an open page.
- The read never adds an entry, so the bubbles that outlive the app's process stay unlisted.
- The shortcut of a dropped bubble still stays behind.

On the Android 14 emulator, `overflow-probe.sh registry` read 7 open with 2 in the overflow, 8 with 3, and 21 with 16 at 21 links. From link 22 the total stayed at 21, and each dropped bubble left the list with no `Bubble dismissed` line. On the Android 11 emulator the mode read the same at every point, with the app's own notifications standing in for the overflow that the shell does not list.

A page opened by its bubble reads its place and the total from the list, and shows them in its top bar, for example 3 of 7.

- Android hides the group summary while a bubble is open, so the count is drawn in the page and goes to no system surface.
- The place is the open order, not the order of the stack, which Android changes as bubbles are read.
- The total counts the overflow. A bubble that ages out stays listed, so the total does not move; a bubble dropped from a full overflow leaves the list at the next read, and a bubble page start is one.
- Only the bubble intent names a bubble. A page in a tab, and a page opened from the conversation shortcut, show no count, and one bubble alone shows none.
- Bubbles that outlive the app's process stay out of it: a page with no entry shows no count, and a page with an entry shows a total that leaves out the unlisted ones.

On the Android 14 emulator, `overflow-probe.sh count` read 3 of 7 with 2 in the overflow, 5 of 8 with 3, and 5 of 7 after a close with that page on screen. `overflow-probe.sh count-long` read 12 of 12 whole beside a shortened address at the largest font. Both modes read the same on the Android 11 emulator.

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
| `close-icon`: disc, then the icon in place | Yes | Yes |

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

## Android 11 and Android 14 compared

Read with `overflow-probe.sh story` on 2026-11-04, one run through on one set of bubbles: 8 links, a close in the stack with a page on screen, a close of that page from its Manage menu, then two more closes. The two runs gave the same rows, with the ids left out.

| Outcome | Android 14 | Android 11 | Cause of a difference |
|---|---|---|---|
| The count before and after a close, with the page on screen | 4 of 8, then 4 of 7 in place | The same | None |
| One return per close, newest first, in the stack with its flag | Each of the first three closes returned one bubble, the newest in the overflow. The fourth returned none | The same | None |
| No sound key, flyout or interruptive mark on a return | None on any of the three returns | The same | None |
| A close from the Manage menu | Dismiss bubble sends one delete intent, closes the page's bubble and returns the next overflow bubble. The list reads 6 and the stack stays open | The same | None |
| A closed bubble stays gone | Neither closed bubble has a notification, a place in the list or a return, through to the last close | The same | None |
| No Lynket row, and no count in any text | No row in the shade. 5 notification records, 7 shortcut labels and no line with a count | The same | None |
| How the overflow can be read | The shell lists it | The shell does not list it; the app's own read stands in | The shell's dump on Android 11 prints the stack only, and the two versions dump different services |
| A `text` re-post | Not interruptive | Interruptive; the app does not use it | Not traced. The measurement stands |
| The shade over a page | Opaque | See-through, so the page or the stack shows behind it | Android draws it |
| A dump of the shade with a page open | Reads the shade | Reads the page | The screenshot is the evidence |

The whole behaviour holds on both versions, and each difference above lies in what Android shows or lets a tool read, or in a re-post the app does not use.

One screen was measured, 1080 x 2400 at density 420. Android 12 and 13 and a phone maker's build, Samsung's included, were not measured. An emulator shows no felt vibration, so the sound and vibrate check reads the key.

## A bubble turned off from its menu

Read with `overflow-probe.sh dont-bubble` on 2026-11-04: 7 links, then Manage and Don't bubble conversation on the page on screen. Both versions read the same.

- The bubble and its notification go, and no delete intent arrives.
- The list goes from 7 to 6 at the next read, and the top bar of the page on screen reads 3 of 6.
- The stack holds 4 and the overflow 2, and no bubble returns, because a return needs a delete intent.

What a later close does in that state was not measured, nor was a bubble turned off in Android's settings with the app closed.

## Android source, as read

These lines describe the `android11-release` and `android14-release` branches, which are not the emulator builds. They are what the code says, not behaviour. Where they disagree with the tables above, the tables win.

- `BubbleController.updateBubble` on `android14-release` (lines 1292-1302) keeps a same-text update of an overflow bubble in the overflow: "Update the bubble but don't promote it out of overflow". **The measurement disagrees: `same` returned the bubble on Android 14.** The cause was not traced. By then the aged notification has lost `FLAG_BUBBLE`, which may make the re-post a new bubble request rather than an update; that is a guess, and it should be checked before the app relies on it on Android 12 or 13.
- `BubbleController.updateBubble` on `android11-release` (line 1113) takes the bubble out of the overflow on any update.
- `BubbleData` sends the delete intent only for a user gesture (`android11-release` line 692, `android14-release` line 1027).
- `BubblePositioner.calculateMaxBubbles` (line 223) on `android14-release` lowers the stack limit below 5 on a screen with a short edge, so the app must not hard-code 5. `bubbles_max_overflow` is 16 on both branches.
