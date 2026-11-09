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

package arun.com.chromer.browsing.article

import android.app.Activity
import android.content.Intent
import android.net.Uri
import arun.com.chromer.LynketRobolectricSuite
import arun.com.chromer.R
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The reader opens with a slide up from the bottom edge and closes with a slide down, and the
 * screen behind it does not move: on Android 14 and later through the activity's open and close
 * overrides, and before that through the pending transition of the start and of the finish.
 */
@HiltAndroidTest
class ArticleActivityTransitionTest : LynketRobolectricSuite() {

  @Before
  fun reset() {
    clearPreferences()
  }

  private fun reader() = Robolectric.buildActivity(
    ArticleActivity::class.java,
    Intent(application, ArticleActivity::class.java).setData(Uri.parse("http://127.0.0.1/article.html")),
  )

  @Test
  fun onAndroid14AndLaterTheReaderSlidesUpOnOpenAndDownOnClose() {
    val activity = reader().setup().get()
    val shadow = shadowOf(activity)

    val open = shadow.getOverriddenActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN)!!
    assertThat(open.enterAnim).isEqualTo(R.anim.reader_slide_up)
    assertThat(open.exitAnim).isEqualTo(0)

    val close = shadow.getOverriddenActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE)!!
    assertThat(close.enterAnim).isEqualTo(0)
    assertThat(close.exitAnim).isEqualTo(R.anim.reader_slide_down)
  }

  @Test
  @Config(sdk = [33])
  fun beforeAndroid14TheStartAndTheFinishCarryTheSlide() {
    val controller = reader().create()
    val shadow = shadowOf(controller.get())
    assertThat(shadow.pendingTransitionEnterAnimationResourceId).isEqualTo(R.anim.reader_slide_up)
    assertThat(shadow.pendingTransitionExitAnimationResourceId).isEqualTo(0)

    controller.get().finish()

    assertThat(shadow.pendingTransitionEnterAnimationResourceId).isEqualTo(0)
    assertThat(shadow.pendingTransitionExitAnimationResourceId).isEqualTo(R.anim.reader_slide_down)
  }
}
