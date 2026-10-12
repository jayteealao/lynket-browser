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

package arun.com.chromer.browsing

import android.app.Application
import androidx.core.content.ContextCompat
import app.cash.turbine.test
import arun.com.chromer.R
import arun.com.chromer.data.Result
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.home.MainDispatcherRule
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The browsing activities read the website, the toolbar colour and the task description from
 * these streams. Like the LiveData fields they replace, each stream gives its latest value to a
 * late collector and delivers equal values again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrowsingViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val url = "https://example.com/page"

  private val preferences = mockk<Preferences> {
    every { dynamiceToolbarEnabledAndWebEnabled() } returns false
  }

  private fun repository(website: Website = Website(url).apply { title = "Example" }) =
    mockk<WebsiteRepository> {
      every { getWebsite(url) } returns flowOf(website)
      every { getWebsiteReadOnly(url) } returns flowOf(website)
      every { getWebsiteIconWithPlaceholderAndColor(any()) } returns android.util.Pair(null, Constants.NO_COLOR)
    }

  private fun vm(repo: WebsiteRepository = repository()) = BrowsingViewModel(application, preferences, repo)

  @Test
  fun theToolbarColourStartsAtThePrimaryColour() = runTest {
    vm().toolbarColor.test {
      assertThat(awaitItem()).isEqualTo(ContextCompat.getColor(application, R.color.colorPrimary))
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun aLoadedWebsiteReachesALateCollector() = runTest {
    val vm = vm()
    vm.loadWebSiteDetails(url)

    vm.website.test {
      val result = awaitItem()
      assertThat(result).isInstanceOf(Result.Success::class.java)
      assertThat((result as Result.Success).data!!.url).isEqualTo(url)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun equalResultsAreBothDelivered() = runTest {
    val vm = vm()
    vm.website.test {
      val same = Result.Success(Website(url))
      vm.website.tryEmit(same)
      vm.website.tryEmit(same)
      assertThat(awaitItem()).isSameInstanceAs(same)
      assertThat(awaitItem()).isSameInstanceAs(same)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun aLoadedWebsiteSetsTheTaskDescription() = runTest {
    val vm = vm()
    vm.activityDescription.test {
      vm.loadWebSiteDetails(url)
      assertThat(awaitItem().label).isEqualTo("Example")
      cancelAndIgnoreRemainingEvents()
    }
  }
}
