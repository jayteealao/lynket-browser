package arun.com.chromer.bubbles.system

import android.app.Application
import android.graphics.Bitmap
import arun.com.chromer.browsing.icons.WebsiteIconData
import arun.com.chromer.browsing.icons.WebsiteIconsProvider
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.tabs.TabsManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * An incognito bubble reads its site with the read that saves nothing; a normal bubble keeps the
 * read that records history and the cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NativeBubbleIncognitoReadTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val url = "https://example.com/bubble"

  private val repo = mockk<WebsiteRepository> {
    every { getWebsite(url) } returns flowOf(Website(url))
    every { getWebsiteWithoutSaving(url) } returns flowOf(Website(url))
  }

  /** Every post of the bubble, in order; the posts happen on the IO pool. */
  private val posts: MutableList<BubbleLoadData> = Collections.synchronizedList(mutableListOf())

  private val notifications = mockk<BubbleNotificationManager> {
    coEvery { showBubbles(capture(posts)) } answers { firstArg() }
    every { isShownAsBubble(any()) } returns true
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

  private fun bubble() = NativeFloatingBubble(application, repo, notifications, icons, gate) { tabsManager }

  @Test
  fun an_incognito_bubble_reads_its_site_without_saving() {
    val opened = bubble().openBubble(Website(url), false, false, true, null, 0)

    assertThat(opened).isTrue()
    verify(timeout = 5_000) { repo.getWebsiteWithoutSaving(url) }
    verify(exactly = 0) { repo.getWebsite(any()) }
  }

  @Test
  fun a_normal_bubble_keeps_the_read_that_saves() {
    val opened = bubble().openBubble(Website(url), false, false, false, null, 0)

    assertThat(opened).isTrue()
    verify(timeout = 5_000) { repo.getWebsite(url) }
    verify(exactly = 0) { repo.getWebsiteWithoutSaving(any()) }
  }

  @Test
  fun an_incognito_bubble_never_loads_the_site_icon() {
    bubble().openBubble(Website(url), false, false, true, null, 0)

    awaitPosts(2)
    coVerify(exactly = 0) { icons.getBubbleIconAndColor(any()) }
    assertThat(posts.last().icon).isNull()
  }

  @Test
  fun a_normal_bubble_still_loads_the_site_icon() {
    bubble().openBubble(Website(url), false, false, false, null, 0)

    coVerify(timeout = 5_000) { icons.getBubbleIconAndColor(any()) }
  }

  /** The first post and the post after the read, with a 1 s pause between. */
  private fun awaitPosts(count: Int) {
    val deadline = System.currentTimeMillis() + 5_000
    while (posts.size < count && System.currentTimeMillis() < deadline) Thread.sleep(10)
    assertThat(posts.size).isAtLeast(count)
  }
}
