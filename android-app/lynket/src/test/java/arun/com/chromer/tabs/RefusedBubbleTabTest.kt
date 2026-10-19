/*
 *  Lynket
 *
 *  Copyright (C) 2026 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.tabs

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.bubbles.system.BubbleGate
import arun.com.chromer.bubbles.system.BubbleRefusal
import arun.com.chromer.bubbles.system.BubbleRefusedActivity
import arun.com.chromer.bubbles.system.bubbleConversationId
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.RxPreferences
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppTask
import javax.inject.Inject

/**
 * Native bubbles mode: a bubble Android would refuse opens its page in a tab at once, the tab
 * carries the cause, and no bubble notification posts. Web heads mode never asks.
 */
@HiltAndroidTest
@Config(sdk = [36])
class RefusedBubbleTabTest : LynketRobolectricSuite() {

  @BindValue
  @JvmField
  val bubbleGate: BubbleGate = mockk(relaxed = true)

  @Inject
  lateinit var tabsManager: TabsManager

  @Inject
  lateinit var rxPreferences: RxPreferences

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val url = "https://example.com/refused"

  @Test
  fun eachCauseOpensATabThatCarriesIt() {
    rxPreferences.nativeBubbles.set(true)
    for (refusal in listOf(BubbleRefusal.NOTIFICATIONS_OFF, BubbleRefusal.BUBBLES_OFF)) {
      every { bubbleGate.refusal(bubbleConversationId(url)) } returns refusal

      tabsManager.openWebHeads(application, Website(url))

      val started = shadowOf(application).nextStartedActivity
      assertThat(started.component?.className).isEqualTo(WebViewActivity::class.java.name)
      assertThat(started.dataString).isEqualTo(url)
      assertThat(BubbleRefusedActivity.refusalOf(started)).isEqualTo(refusal)
      assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }
  }

  @Test
  fun noCauseShowsTheBubble() {
    rxPreferences.nativeBubbles.set(true)
    every { bubbleGate.refusal(any()) } returns null

    tabsManager.openWebHeads(application, Website(url))

    assertThat(shadowOf(application).nextStartedActivity).isNull()
    awaitNotification()
  }

  @Test
  fun webHeadsModeDoesNotAsk() {
    rxPreferences.nativeBubbles.set(false)

    tabsManager.openWebHeads(application, Website(url))

    verify(exactly = 0) { bubbleGate.refusal(any()) }
  }

  @Test
  fun anOpenTabOfThePageComesForwardAndTheMessageGetsATaskOfItsOwn() {
    val task = ShadowAppTask.newInstance()
    shadowOf(task).setTaskInfo(
      ActivityManager.RecentTaskInfo().apply {
        baseIntent = Intent(application, CustomTabActivity::class.java).setData(Uri.parse(url))
      },
    )
    shadowOf(application.getSystemService(ActivityManager::class.java)).setAppTasks(listOf(task))

    tabsManager.openRefusedBubble(application, Website(url), BubbleRefusal.BUBBLES_OFF)

    val message = shadowOf(application).nextStartedActivity
    assertThat(message.component?.className).isEqualTo(BubbleRefusedActivity::class.java.name)
    assertThat(BubbleRefusedActivity.refusalOf(message)).isEqualTo(BubbleRefusal.BUBBLES_OFF)
    assertThat(message.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    assertThat(message.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK).isNotEqualTo(0)
    assertThat(shadowOf(application).nextStartedActivity).isNull()
  }

  /** The bubble posts on a background thread after the link opens. */
  private fun awaitNotification() {
    repeat(300) {
      shadowOf(android.os.Looper.getMainLooper()).idle()
      if (shadowOf(notificationManager).allNotifications.any { it.shortcutId == bubbleConversationId(url) }) return
      Thread.sleep(10)
    }
    error("No bubble notification was posted")
  }
}
