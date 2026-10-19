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

package arun.com.chromer.bubbles.system

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.setContent
import arun.com.chromer.shared.Constants.EXTRA_KEY_BUBBLE_REFUSAL
import arun.com.chromer.shared.base.activity.BaseActivity
import arun.com.chromer.ui.browsing.BubbleRefusedMessage
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * A small window at the bottom of the screen, over the tab a refused bubble opened in. It shows the
 * cause and an "Open Settings" action, takes no touch outside itself, so the page under it stays
 * usable, and closes on Back, on the action or when the message times out.
 */
@AndroidEntryPoint
class BubbleRefusedActivity : BaseActivity() {

  @Inject
  lateinit var bubbleGate: BubbleGate

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val refusal = refusalOf(intent)
    if (refusal == null) {
      finish()
      return
    }
    window.apply {
      setGravity(Gravity.BOTTOM)
      setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
      clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }
    setContent {
      ChromerThemeFromSettings(applySystemBars = false) {
        BubbleRefusedMessage(
          refusal = refusal,
          onOpenSettings = { openSettings(refusal) },
          onDone = ::finishQuietly,
        )
      }
    }
  }

  private fun openSettings(refusal: BubbleRefusal) {
    try {
      startActivity(bubbleGate.settingsIntent(refusal))
    } catch (e: ActivityNotFoundException) {
      Timber.e(e, "No settings page for %s", refusal)
      try {
        startActivity(bubbleGate.appDetailsIntent())
      } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No app details page")
      }
    }
    finishQuietly()
  }

  private fun finishQuietly() {
    if (isFinishing) return
    finish()
    @Suppress("DEPRECATION") // sdlc-debt: overrideActivityTransition needs API 34; minSdk is 24; switch to it when minSdk reaches 34
    overridePendingTransition(0, 0)
  }

  companion object {
    /** The cause carried by [intent], or null when it has none or an unknown one. */
    fun refusalOf(intent: Intent?): BubbleRefusal? {
      val name = intent?.getStringExtra(EXTRA_KEY_BUBBLE_REFUSAL) ?: return null
      return BubbleRefusal.entries.firstOrNull { it.name == name }
    }

    /** The intent that shows the message for [refusal]. */
    fun intent(context: Context, refusal: BubbleRefusal, newTask: Boolean): Intent =
      Intent(context, BubbleRefusedActivity::class.java)
        .putExtra(EXTRA_KEY_BUBBLE_REFUSAL, refusal.name)
        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        .apply {
          // A task of its own when no tab of this link was started, so it does not join the
          // task of another Lynket screen.
          if (newTask || context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
          }
        }

    /**
     * Started by a browsing activity after it opened its page, when its intent carries a cause;
     * it then opens above the page, in the same task.
     */
    fun showIfRefused(activity: Activity) {
      val refusal = refusalOf(activity.intent) ?: return
      activity.startActivity(intent(activity, refusal, newTask = false))
    }
  }
}
