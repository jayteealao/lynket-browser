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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.Collections

/**
 * An incognito read leaves no history row and no cache entry; a normal read still writes both.
 * A lookup also tells a fetched page from one that no store and no network fetch gave.
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

  @Test
  fun a_read_without_history_from_the_network_writes_only_the_cache() = runBlocking<Unit> {
    network.website = site

    val read = repository.getWebsiteWithoutHistory(url).first()

    assertThat(read.title).isEqualTo("Example page")
    withTimeout(5_000) { cache.firstSave.await() }
    assertThat(cache.saves.map { it.url }).containsExactly(url)
    delay(SETTLE_MS)
    assertThat(history.inserts).isEmpty()
  }

  @Test
  fun a_lookup_from_the_network_is_fetched() = runBlocking<Unit> {
    network.website = site

    val lookup = repository.lookUpWebsite(url, incognito = false).first()

    assertThat(lookup.fetchFailed).isFalse()
    assertThat(lookup.website.title).isEqualTo("Example page")
  }

  @Test
  fun a_lookup_that_nothing_answers_is_failed_with_the_bare_address() = runBlocking<Unit> {
    val lookup = repository.lookUpWebsite(url, incognito = false).first()

    assertThat(lookup.fetchFailed).isTrue()
    assertThat(lookup.website.url).isEqualTo(url)
    assertThat(lookup.website.title).isNull()
  }

  @Test
  fun a_failed_normal_lookup_keeps_the_visit_in_history_but_caches_nothing() = runBlocking<Unit> {
    val lookup = repository.lookUpWebsite(url, incognito = false).first()

    assertThat(lookup.fetchFailed).isTrue()
    withTimeout(5_000) { history.firstInsert.await() }
    assertThat(history.inserts.map { it.url }).containsExactly(url)
    assertThat(cache.saves).isEmpty()
  }

  @Test
  fun a_failed_incognito_lookup_writes_no_history() = runBlocking<Unit> {
    val lookup = repository.lookUpWebsite(url, incognito = true).first()

    assertThat(lookup.fetchFailed).isTrue()
    delay(SETTLE_MS)
    assertThat(history.inserts).isEmpty()
    assertThat(cache.saves).isEmpty()
  }

  @Test
  fun a_lookup_from_the_cache_says_it_did_not_read_the_network() = runBlocking<Unit> {
    cache.website = site

    assertThat(repository.lookUpWebsite(url, incognito = true).first().fromNetwork).isFalse()
  }

  @Test
  fun a_fetch_skips_the_cache_and_reads_the_network() = runBlocking<Unit> {
    cache.website = site

    val fetch = repository.fetchWebsite(url, incognito = true).first()

    assertThat(fetch.fromNetwork).isTrue()
    assertThat(fetch.fetchFailed).isTrue()
  }

  @Test
  fun a_lookup_whose_network_store_throws_is_failed() = runBlocking<Unit> {
    network.failure = IllegalStateException("offline")

    val lookup = repository.lookUpWebsite(url, incognito = true).first()

    assertThat(lookup.fetchFailed).isTrue()
    assertThat(lookup.website.url).isEqualTo(url)
  }

  @Test
  fun a_lookup_from_the_cache_or_history_is_fetched() = runBlocking<Unit> {
    cache.website = site
    assertThat(repository.lookUpWebsite(url, incognito = false).first().fetchFailed).isFalse()

    cache.website = null
    history.stored = site
    assertThat(repository.lookUpWebsite(url, incognito = false).first().fetchFailed).isFalse()
  }

  @Test
  fun an_incognito_lookup_from_the_network_writes_no_history_and_no_cache() = runBlocking<Unit> {
    network.website = site

    val lookup = repository.lookUpWebsite(url, incognito = true).first()

    assertThat(lookup.fetchFailed).isFalse()
    delay(SETTLE_MS)
    assertThat(history.inserts).isEmpty()
    assertThat(cache.saves).isEmpty()
  }

  @Test
  fun a_normal_lookup_from_the_network_writes_history_and_the_cache() = runBlocking<Unit> {
    network.website = site

    val lookup = repository.lookUpWebsite(url, incognito = false).first()

    assertThat(lookup.fetchFailed).isFalse()
    withTimeout(5_000) {
      history.firstInsert.await()
      cache.firstSave.await()
    }
    assertThat(history.inserts.map { it.url }).containsExactly(url)
    assertThat(cache.saves.map { it.url }).containsExactly(url)
  }

  private class CountingStore : WebsiteStore {
    @Volatile var website: Website? = null
    @Volatile var failure: Throwable? = null
    val saves: MutableList<Website> = Collections.synchronizedList(mutableListOf())
    val firstSave = CompletableDeferred<Unit>()

    override fun getWebsite(url: String): Flow<Website?> = failure?.let { error -> flow { throw error } } ?: flowOf(website)

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

    override suspend fun insert(website: Website) {
      inserts += website
      firstInsert.complete(Unit)
    }

    override suspend fun search(text: String): List<Website> = emptyList()
  }

  private companion object {
    /** The repository writes on its own background scope; this window lets a late write land. */
    const val SETTLE_MS = 300L
  }
}
