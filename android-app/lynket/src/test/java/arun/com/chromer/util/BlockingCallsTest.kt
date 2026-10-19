package arun.com.chromer.util

import android.app.Application
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.concurrent.thread

/** A blocking wait on the main thread is logged with its site; one off the main thread is not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlockingCallsTest {

  @Before
  fun clearLog() {
    ShadowLog.clear()
  }

  @Test
  fun aMainThreadWaitReturnsTheValueAndLogsItsSite() {
    val value = blockingWait("Test.site", Dispatchers.IO) { 42 }

    assertThat(value).isEqualTo(42)
    val waits = ShadowLog.getLogsForTag(MAIN_THREAD_WAIT_TAG)
    assertThat(waits).hasSize(1)
    assertThat(waits.single().msg).isEqualTo("Main thread waits: Test.site")
    assertThat(waits.single().throwable).hasMessageThat().isEqualTo("Test.site")
  }

  @Test
  fun aBackgroundWaitLogsNothing() {
    var value = 0
    thread { value = blockingWait("Test.background") { 7 } }.join()

    assertThat(value).isEqualTo(7)
    assertThat(ShadowLog.getLogsForTag(MAIN_THREAD_WAIT_TAG)).isEmpty()
  }
}
