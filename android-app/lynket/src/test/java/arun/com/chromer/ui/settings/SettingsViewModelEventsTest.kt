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

package arun.com.chromer.ui.settings

import android.os.Looper
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.bubbles.system.BubbleGate
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.events.Event
import arun.com.chromer.util.events.EventBus
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import javax.inject.Inject

/** A change of the web head colour tells the heads on screen; no other setting does. */
@HiltAndroidTest
class SettingsViewModelEventsTest : LynketRobolectricSuite() {

  @Inject
  lateinit var eventBus: EventBus

  private val collectorScope = CoroutineScope(Dispatchers.Unconfined)

  @After
  fun cancelCollectors() = collectorScope.cancel()

  private fun viewModel() =
    SettingsViewModel(application, userPreferencesRepository, preferences, mockk<TabsManager>(relaxed = true), eventBus, BubbleGate(application))

  private fun webHeadEvents(): List<Event.WebHeadEvent> {
    val events = mutableListOf<Event.WebHeadEvent>()
    collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
      eventBus.observe<Event.WebHeadEvent>().collect { events += it }
    }
    return events
  }

  @Test
  fun changingTheWebHeadColourSendsColourChanged() {
    val events = webHeadEvents()

    viewModel().set(SettingsKeyTable.WEB_HEADS_COLOR, 0xFF00C853.toInt(), canReadUsageStats = false)
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(events).containsExactly(Event.WebHeadEvent.ColourChanged(0xFF00C853.toInt()))
  }

  @Test
  fun anotherKeySendsNoColourEvent() {
    val events = webHeadEvents()

    viewModel().set(SettingsKeyTable.TOOLBAR_COLOR, 0xFF00C853.toInt(), canReadUsageStats = false)
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(events).isEmpty()
  }
}
