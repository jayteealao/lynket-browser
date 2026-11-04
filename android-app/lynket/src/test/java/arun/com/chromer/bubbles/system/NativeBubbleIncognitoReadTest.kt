package arun.com.chromer.bubbles.system

import android.app.Application
import android.graphics.Bitmap
import arun.com.chromer.browsing.icons.WebsiteIconData
import arun.com.chromer.browsing.icons.WebsiteIconsProvider
import arun.com.chromer.bubbles.BubbleRegistry
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An incognito bubble reads its site with the read that saves nothing; a normal bubble keeps the
 * read that records history and the cache. The bubble runs on a test dispatcher, so each case
 * runs it to the end on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NativeBubbleIncognitoReadTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val url = "https://example.com/bubble"

  private val repo = mockk<WebsiteRepository> {
    every { getWebsite(url) } returns flowOf(Website(url))
    every { getWebsiteWithoutSaving(url) } returns flowOf(Website(url))
  }

  private val dispatcher = StandardTestDispatcher()

  /** Every post of the bubble, in order. */
  private val posts = mutableListOf<BubbleLoadData>()

  private val notifications = mockk<BubbleNotificationManager> {
    coEvery { showBubbles(capture(posts), any()) } answers { firstArg() }
    every { isShownAsBubble(any(), any()) } returns true
    every { bubblePlaces() } returns null
  }

  private val icons = mockk<WebsiteIconsProvider> {
    coEvery { getBubbleIconAndColor(any()) } answers {
      WebsiteIconData(firstArg(), Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 0)
    }
  }

  private val gate = mockk<BubbleGate> {
    every { refusal(any()) } returns null
  }

  private val tabsManager = mockk<TabsManager>(relaxed = true)

  private val registry = BubbleRegistry()

  private fun bubble() = NativeFloatingBubble(
    application,
    repo,
    notifications,
    icons,
    gate,
    registry,
    BubbleReconciler(notifications, registry, dispatcher),
    dispatcher,
  ) { tabsManager }

  @Test
  fun an_incognito_bubble_reads_nothing_about_its_site() {
    val opened = bubble().openBubble(Website(url), false, false, true, null, 0)
    dispatcher.scheduler.advanceUntilIdle()

    assertThat(opened).isTrue()
    verify(exactly = 0) { repo.getWebsiteWithoutSaving(any()) }
    verify(exactly = 0) { repo.getWebsite(any()) }
  }

  @Test
  fun a_normal_bubble_keeps_the_read_that_saves() {
    val opened = bubble().openBubble(Website(url), false, false, false, null, 0)
    dispatcher.scheduler.advanceUntilIdle()

    assertThat(opened).isTrue()
    verify { repo.getWebsite(url) }
    verify(exactly = 0) { repo.getWebsiteWithoutSaving(any()) }
  }

  @Test
  fun an_incognito_bubble_never_loads_the_site_icon() {
    bubble().openBubble(Website(url), false, false, true, null, 0)
    dispatcher.scheduler.advanceUntilIdle()

    // One post, with the Lynket icon: nothing an incognito bubble shows comes from the site.
    assertThat(posts).hasSize(1)
    assertThat(posts.single().icon).isNull()
    coVerify(exactly = 0) { icons.getBubbleIconAndColor(any()) }
  }

  @Test
  fun a_normal_bubble_still_loads_the_site_icon() {
    bubble().openBubble(Website(url), false, false, false, null, 0)
    dispatcher.scheduler.advanceUntilIdle()

    coVerify { icons.getBubbleIconAndColor(any()) }
  }

  @Test
  fun the_list_entry_of_an_incognito_bubble_prints_no_address() {
    bubble().openBubble(Website(url), false, false, true, null, 0)
    dispatcher.scheduler.advanceUntilIdle()

    val entry = registry.list(BrowsingMode.NativeBubbles).single()
    assertThat(entry.incognito).isTrue()
    assertThat(entry.toString()).doesNotContain("example.com")
  }

}
