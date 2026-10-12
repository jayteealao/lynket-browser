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

import androidx.lifecycle.ViewModelProvider
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.browsing.amp.AmpResolverActivity
import arun.com.chromer.browsing.newtab.NewTabDialogActivity
import arun.com.chromer.browsing.newtab.NewTabViewModel
import arun.com.chromer.browsing.openwith.OpenIntentWithActivity
import arun.com.chromer.shortcuts.HomeScreenShortcutCreatorActivity
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowToast

/**
 * The dialog activities survive a configuration change while they show (the dialog stays, with
 * what was typed), and close with the old message when started without a page address. The
 * options sheet activity is not started here: an open ModalBottomSheet never goes idle in
 * Robolectric (repro 2026-10-12: optionsSheetSurvivesRecreation timed out after 240 s, and a plain
 * setup() of the options activity looped in Choreographer frames for 20 minutes in the full run).
 * The sheet activities (options, Open with) are recreated on a device instead (night-mode toggle
 * in maestro/browsing/browsing-check.sh recreate); BrowsingDialogsTest covers the sheet without an
 * address (the address items are disabled, the rest still work).
 */
@HiltAndroidTest
class DialogActivitiesRecreateTest : LynketRobolectricSuite() {

  @Test
  fun newTabKeepsTheTypedTextAcrossRecreation() {
    val controller = Robolectric.buildActivity(NewTabDialogActivity::class.java).setup()
    ViewModelProvider(controller.get())[NewTabViewModel::class.java].onQueryChange("exam")
    controller.recreate()
    assertThat(controller.get().isFinishing).isFalse()
    assertThat(ViewModelProvider(controller.get())[NewTabViewModel::class.java].query.value).isEqualTo("exam")
  }

  @Test
  fun openWithWithoutAnAddressClosesWithTheOldMessage() {
    val activity = Robolectric.buildActivity(OpenIntentWithActivity::class.java).setup().get()
    assertThat(activity.isFinishing).isTrue()
    assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Invalid link")
  }

  @Test
  fun ampAndShortcutWithoutAnAddressCloseWithTheOldMessage() {
    listOf(AmpResolverActivity::class.java, HomeScreenShortcutCreatorActivity::class.java).forEach { type ->
      val activity = Robolectric.buildActivity(type).setup().get()
      assertThat(activity.isFinishing).isTrue()
      assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Not a supported link, try again")
    }
  }
}
