package arun.com.chromer.data.website

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Pair
import arun.com.chromer.data.history.HistoryRepository
import arun.com.chromer.data.website.model.WebColor
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.data.website.stores.WebsiteStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.Collections

/**
 * An incognito read leaves no history row and no cache entry; a normal read still writes both.
 */
class DefaultWebsiteRepositoryTest {

  private val url = "https://example.com/page"
  private val site = Website(url).apply { title = "Example page" }

  private val cache = CountingStore()
  private val network = CountingStore()
  private val history = CountingHistory()
  private val repository = DefaultWebsiteRepository(cache, network, history)

  @Test
  fun an_incognito_read_from_the_network_writes_no_history_and_no_cache() = runBlocking<Unit> {
    network.website = site

    val read = repository.getWebsiteWithoutSaving(url).first()

    assertThat(read.title).isEqualTo("Example page")
    delay(SETTLE_MS)
    assertThat(history.inserts).isEmpty()
    assertThat(cache.saves).isEmpty()
  }

  @Test
  fun an_incognito_read_from_the_cache_writes_no_history() = runBlocking<Unit> {
    cache.website = site

    val read = repository.getWebsiteWithoutSaving(url).first()

    assertThat(read.title).isEqualTo("Example page")
    delay(SETTLE_MS)
    assertThat(history.inserts).isEmpty()
    assertThat(cache.saves).isEmpty()
  }

  @Test
  fun an_incognito_read_from_history_writes_no_history() = runBlocking<Unit> {
    history.stored = site

    val read = repository.getWebsiteWithoutSaving(url).first()

    assertThat(read.title).isEqualTo("Example page")
    delay(SETTLE_MS)
    assertThat(history.inserts).isEmpty()
    assertThat(cache.saves).isEmpty()
  }

  @Test
  fun a_normal_read_from_the_network_writes_history_and_the_cache() = runBlocking<Unit> {
    network.website = site

    val read = repository.getWebsite(url).first()

    assertThat(read.title).isEqualTo("Example page")
    withTimeout(5_000) {
      history.firstInsert.await()
      cache.firstSave.await()
    }
    assertThat(history.inserts.map { it.url }).containsExactly(url)
    assertThat(cache.saves.map { it.url }).containsExactly(url)
  }

  @Test
  fun a_normal_read_from_the_cache_writes_history() = runBlocking<Unit> {
    cache.website = site

    val read = repository.getWebsite(url).first()

    assertThat(read.title).isEqualTo("Example page")
    withTimeout(5_000) { history.firstInsert.await() }
    assertThat(history.inserts.map { it.url }).containsExactly(url)
    assertThat(cache.saves).isEmpty()
  }

  private class CountingStore : WebsiteStore {
    @Volatile var website: Website? = null
    val saves: MutableList<Website> = Collections.synchronizedList(mutableListOf())
    val firstSave = CompletableDeferred<Unit>()

    override fun getWebsite(url: String): Flow<Website?> = flowOf(website)

    override suspend fun saveWebsite(website: Website): Website {
      saves += website
      firstSave.complete(Unit)
      return website
    }

    override suspend fun clearCache() = Unit

    override suspend fun getWebsiteColor(url: String): WebColor = throw UnsupportedOperationException()

    override suspend fun saveWebsiteColor(host: String, color: Int): WebColor =
      throw UnsupportedOperationException()

    override fun getWebsiteIconAndColor(website: Website): Pair<Bitmap, Int> =
      throw UnsupportedOperationException()

    override fun getWebsiteRoundIconAndColor(website: Website): Pair<Drawable, Int> =
      throw UnsupportedOperationException()

    override fun getWebsiteIconWithPlaceholderAndColor(website: Website): Pair<Bitmap, Int> =
      throw UnsupportedOperationException()
  }

  private class CountingHistory : HistoryRepository {
    @Volatile var stored: Website? = null
    val inserts: MutableList<Website> = Collections.synchronizedList(mutableListOf())
    val firstInsert = CompletableDeferred<Unit>()

    override suspend fun get(website: Website): Website? = stored

    override suspend fun insert(website: Website): Website {
      inserts += website
      firstInsert.complete(Unit)
      return website
    }

    override suspend fun search(text: String): List<Website> = emptyList()
  }

  private companion object {
    /** The repository writes on its own background scope; this window lets a late write land. */
    const val SETTLE_MS = 300L
  }
}
