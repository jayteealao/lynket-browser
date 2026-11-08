package arun.com.chromer.bubbles.webheads.motion

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timber.log.Timber

/** The page state of each head: loading from the open, then the first signal, or unknown after the limit. */
@OptIn(ExperimentalCoroutinesApi::class)
class HeadPageTrackerTest {

  private fun TestScope.tracker() = HeadPageTracker(backgroundScope)

  @Test
  fun a_new_head_has_an_unknown_page() {
    assertThat(WebHeadState("https://a.example").pageState).isEqualTo(PageState.Unknown)
  }

  @Test
  fun the_log_of_an_incognito_head_names_no_address() = runTest {
    val lines = mutableListOf<String>()
    val tree = object : Timber.Tree() {
      override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        lines += message
      }
    }
    Timber.plant(tree)
    try {
      val tracker = tracker()
      val secret = WebHeadState("https://secret.example/page", incognito = true)
      val open = WebHeadState("https://open.example/page")
      tracker.start(secret)
      tracker.start(open)
      tracker.finish(secret, PageSignal.SiteDetails, failed = false)
      tracker.finish(open, PageSignal.SiteDetails, failed = false)
    } finally {
      Timber.uproot(tree)
    }
    assertThat(lines.joinToString(" ")).doesNotContain("secret.example")
    assertThat(lines.joinToString(" ")).contains("open.example")
  }

  @Test
  fun a_started_head_is_loading() = runTest {
    val head = WebHeadState("https://a.example")
    tracker().start(head)
    assertThat(head.pageState).isEqualTo(PageState.Loading)
  }

  @Test
  fun each_signal_finishes_a_loading_head_as_loaded() = runTest {
    val tracker = tracker()
    PageSignal.entries.forEach { signal ->
      val head = WebHeadState("https://${signal.name}.example")
      tracker.start(head)
      tracker.finish(head, signal, failed = false)
      assertThat(head.pageState).isEqualTo(PageState.Loaded)
    }
  }

  @Test
  fun a_failed_signal_finishes_a_loading_head_as_failed() = runTest {
    val tracker = tracker()
    PageSignal.entries.forEach { signal ->
      val head = WebHeadState("https://${signal.name}.example")
      tracker.start(head)
      tracker.finish(head, signal, failed = true)
      assertThat(head.pageState).isEqualTo(PageState.Failed)
    }
  }

  @Test
  fun with_no_signal_the_head_turns_unknown_at_the_limit() = runTest {
    val head = WebHeadState("https://a.example")
    tracker().start(head)

    advanceTimeBy(PAGE_STATE_LIMIT_MS - 1)
    runCurrent()
    assertThat(head.pageState).isEqualTo(PageState.Loading)

    advanceTimeBy(1)
    runCurrent()
    assertThat(head.pageState).isEqualTo(PageState.Unknown)
  }

  @Test
  fun a_finished_head_ignores_a_later_signal_and_the_limit() = runTest {
    val tracker = tracker()
    val head = WebHeadState("https://a.example")
    tracker.start(head)
    tracker.finish(head, PageSignal.SiteDetails, failed = false)

    tracker.finish(head, PageSignal.Navigation, failed = true)
    advanceTimeBy(PAGE_STATE_LIMIT_MS * 2)
    runCurrent()

    assertThat(head.pageState).isEqualTo(PageState.Loaded)
  }

  @Test
  fun a_head_that_turned_unknown_ignores_a_late_signal() = runTest {
    val tracker = tracker()
    val head = WebHeadState("https://a.example")
    tracker.start(head)
    advanceTimeBy(PAGE_STATE_LIMIT_MS)
    runCurrent()

    tracker.finish(head, PageSignal.SiteDetails, failed = false)

    assertThat(head.pageState).isEqualTo(PageState.Unknown)
  }

  @Test
  fun of_two_heads_only_the_finished_one_turns_loaded() = runTest {
    val tracker = tracker()
    val first = WebHeadState("https://a.example")
    val second = WebHeadState("https://b.example")
    tracker.start(first)
    tracker.start(second)

    tracker.finish(second, PageSignal.SiteDetails, failed = false)

    assertThat(first.pageState).isEqualTo(PageState.Loading)
    assertThat(second.pageState).isEqualTo(PageState.Loaded)
  }

  @Test
  fun a_dropped_head_ignores_the_limit_and_a_later_signal() = runTest {
    val tracker = tracker()
    val head = WebHeadState("https://a.example")
    tracker.start(head)

    tracker.drop(head)
    advanceTimeBy(PAGE_STATE_LIMIT_MS)
    runCurrent()
    tracker.finish(head, PageSignal.SiteDetails, failed = false)

    assertThat(head.pageState).isEqualTo(PageState.Loading)
  }

  @Test
  fun a_dropped_head_does_not_touch_a_new_head_of_the_same_address() = runTest {
    val tracker = tracker()
    val closed = WebHeadState("https://a.example")
    tracker.start(closed)
    tracker.drop(closed)
    val reopened = WebHeadState("https://a.example")
    tracker.start(reopened)

    tracker.finish(closed, PageSignal.SiteDetails, failed = true)

    assertThat(reopened.pageState).isEqualTo(PageState.Loading)
  }
}
