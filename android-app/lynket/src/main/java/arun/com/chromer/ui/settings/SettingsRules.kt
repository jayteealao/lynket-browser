/*
 *  Lynket
 *
 *  Copyright (C) 2025 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package arun.com.chromer.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import arun.com.chromer.R
import arun.com.chromer.bubbles.system.BubbleSetting
import arun.com.chromer.data.preferences.BrowsingMode
import arun.com.chromer.data.preferences.SettingType
import arun.com.chromer.data.preferences.SettingsSchema
import arun.com.chromer.data.preferences.readBoolean
import arun.com.chromer.data.preferences.readInt
import arun.com.chromer.data.preferences.readString
import arun.com.chromer.ui.settings.SettingsKeyTable.AGGRESSIVE_LOADING
import arun.com.chromer.ui.settings.SettingsKeyTable.AMP_MODE
import arun.com.chromer.ui.settings.SettingsKeyTable.ANIMATION_SPEED
import arun.com.chromer.ui.settings.SettingsKeyTable.ANIMATION_TYPE
import arun.com.chromer.ui.settings.SettingsKeyTable.ARTICLE_MODE
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR_APP
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR_WEB
import arun.com.chromer.ui.settings.SettingsKeyTable.MERGE_TABS
import arun.com.chromer.ui.settings.SettingsKeyTable.NATIVE_BUBBLES
import arun.com.chromer.ui.settings.SettingsKeyTable.TOOLBAR_COLOR_PREF
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_ENABLED

/** Every settings value with its default filled in, keyed by the stored name. */
data class SettingsValues(val values: Map<String, Any?>) {
  fun bool(key: String): Boolean = values[key] as? Boolean ?: false
  fun int(key: String): Int = values[key] as? Int ?: 0
  fun string(key: String): String? = values[key] as? String

  /** These values with [changes] applied. */
  fun with(changes: Map<String, Any?>): SettingsValues = SettingsValues(values + changes)
  fun with(vararg changes: Pair<String, Any?>): SettingsValues = with(changes.toMap())

  companion object {
    /** Reads every schema key from [stored], with the schema default where a key is missing. */
    fun from(stored: Preferences, context: Context): SettingsValues = SettingsValues(
      SettingsSchema.keys.associate { key ->
        val default = key.default(context)
        key.name to when (key.type) {
          SettingType.BOOLEAN -> stored.readBoolean(key.name, default as Boolean)
          SettingType.INT -> stored.readInt(key.name, default as Int)
          SettingType.STRING -> stored.readString(key.name, default as String?)
        }
      },
    )
  }
}

/** A dialog a settings change asks for, in the order the old screens showed them. */
sealed interface SettingsDialog {
  data object DynamicColourHelp : SettingsDialog
  data object UsageAccessForAppColour : SettingsDialog
  data object MergeTabsOff : SettingsDialog
  data object AggressiveLoadingNeedsMergeTabs : SettingsDialog
  data object AmpOutsideWebHeads : SettingsDialog
  data object AmpAndArticle : SettingsDialog
  data object OverlayPermission : SettingsDialog
  data class NotificationPermission(val forBubbles: Boolean) : SettingsDialog
  data object NativeBubblesWarning : SettingsDialog

  /** Native bubbles chosen while the app's bubble setting is not "All conversations can bubble". */
  data class BubbleSettingExplainer(val setting: BubbleSetting) : SettingsDialog
}

/** What a change writes (in one store edit) and which dialogs it shows. */
data class RuleResult(val writes: Map<String, Any?>, val dialogs: List<SettingsDialog> = emptyList())

/**
 * The dependency rules of the old preference screens, as pure functions. Each rule was read from the
 * old fragment code: personalization (coloured toolbar, dynamic colour, animation), web head
 * options (aggressive loading), behaviour (merge tabs) and browse faster (AMP, article).
 */
object SettingsRules {

  /** Aggressive loading is in effect only in web heads mode. */
  private fun aggressiveLoading(values: SettingsValues) =
    selectedMode(values) == BrowsingMode.WebHeads && values.bool(AGGRESSIVE_LOADING)

  /** The writes and dialogs for setting [key] to [value]. */
  fun change(key: String, value: Any?, current: SettingsValues, canReadUsageStats: Boolean): RuleResult {
    val writes = linkedMapOf(key to value)
    val dialogs = mutableListOf<SettingsDialog>()
    when (key) {
      TOOLBAR_COLOR_PREF -> if (value == false) {
        // Coloured toolbar off unchecks dynamic colour, which in turn clears its two boxes.
        writes[DYNAMIC_COLOR] = false
        writes[DYNAMIC_COLOR_APP] = false
        writes[DYNAMIC_COLOR_WEB] = false
      }
      DYNAMIC_COLOR -> if (value == true) {
        dialogs += SettingsDialog.DynamicColourHelp
      } else {
        writes[DYNAMIC_COLOR_APP] = false
        writes[DYNAMIC_COLOR_WEB] = false
      }
      DYNAMIC_COLOR_APP -> if (value == true && !canReadUsageStats) {
        dialogs += SettingsDialog.UsageAccessForAppColour
      }
      MERGE_TABS -> if (value == false && aggressiveLoading(current)) {
        writes[AGGRESSIVE_LOADING] = false
        dialogs += SettingsDialog.MergeTabsOff
      }
      AGGRESSIVE_LOADING -> if (value == true && !current.bool(MERGE_TABS)) {
        writes[MERGE_TABS] = true
        dialogs += SettingsDialog.AggressiveLoadingNeedsMergeTabs
      }
      AMP_MODE -> if (value == true) {
        // AMP warns in every mode but web heads.
        if (selectedMode(current) != BrowsingMode.WebHeads) dialogs += SettingsDialog.AmpOutsideWebHeads
        if (current.bool(ARTICLE_MODE)) dialogs += SettingsDialog.AmpAndArticle
      }
      ARTICLE_MODE -> if (value == true && current.bool(AMP_MODE)) {
        dialogs += SettingsDialog.AmpAndArticle
      }
    }
    return RuleResult(writes, dialogs)
  }

  /**
   * Choosing a browsing mode. Web heads turn on only with the overlay permission; without it the
   * explainer shows and web heads stay off. Native bubbles show their beta warning first.
   */
  fun chooseMode(mode: BrowsingMode, overlayGranted: Boolean, notificationsAllowed: Boolean): RuleResult =
    when (mode) {
      BrowsingMode.SlideOver -> RuleResult(BrowsingMode.SlideOver.switches())
      BrowsingMode.NativeBubbles -> RuleResult(
        BrowsingMode.NativeBubbles.switches(),
        listOf(SettingsDialog.NativeBubblesWarning),
      )
      BrowsingMode.WebHeads -> if (overlayGranted) {
        overlayGranted(notificationsAllowed)
      } else {
        // Without the permission web heads stay off: the stored mode is slide over.
        RuleResult(BrowsingMode.SlideOver.switches(), listOf(SettingsDialog.OverlayPermission))
      }
    }

  /** Back from the overlay setting with the permission: web heads on, native bubbles off. */
  fun overlayGranted(notificationsAllowed: Boolean): RuleResult = RuleResult(
    BrowsingMode.WebHeads.switches(),
    if (notificationsAllowed) emptyList() else listOf(SettingsDialog.NotificationPermission(forBubbles = false)),
  )

  /**
   * After the native bubbles warning closes, notifications are asked for when not allowed, then
   * the bubble setting is explained when it does not let every conversation bubble.
   */
  fun afterBubblesWarning(notificationsAllowed: Boolean, bubbleSetting: BubbleSetting = BubbleSetting.ALL): List<SettingsDialog> =
    buildList {
      if (!notificationsAllowed) add(SettingsDialog.NotificationPermission(forBubbles = true))
      if (bubbleSetting in BUBBLE_SETTINGS_TO_EXPLAIN) add(SettingsDialog.BubbleSettingExplainer(bubbleSetting))
    }

  private val BUBBLE_SETTINGS_TO_EXPLAIN = setOf(BubbleSetting.SELECTED, BubbleSetting.NONE, BubbleSetting.DEVICE_OFF)

  fun selectedMode(values: SettingsValues): BrowsingMode =
    BrowsingMode.resolve(webHeads = values.bool(WEB_HEAD_ENABLED), nativeBubbles = values.bool(NATIVE_BUBBLES))

  /**
   * Whether the row of [key] takes input. The web head rows take input only in web heads mode.
   * Minimize behavior takes none in native bubble mode, where Minimize always opens a bubble.
   */
  fun isEnabled(key: String, values: SettingsValues): Boolean = when (key) {
    SettingsKeyTable.TOOLBAR_COLOR, DYNAMIC_COLOR -> values.bool(TOOLBAR_COLOR_PREF)
    ANIMATION_SPEED -> values.int(ANIMATION_TYPE) != 0
    SettingsKeyTable.WEB_HEADS_COLOR,
    SettingsKeyTable.WEB_HEAD_SPAWN,
    SettingsKeyTable.WEB_HEAD_SIZE,
    AGGRESSIVE_LOADING,
    SettingsKeyTable.WEB_HEAD_CLOSE_ON_OPEN -> selectedMode(values) == BrowsingMode.WebHeads
    SettingsKeyTable.MINIMIZE_BEHAVIOR -> selectedMode(values) != BrowsingMode.NativeBubbles
    else -> true
  }

  /**
   * The summary a row shows in place of its chosen value: Minimize behavior in native bubble mode,
   * where Minimize always opens a bubble.
   */
  @StringRes
  fun summaryOverride(key: String, values: SettingsValues): Int? = when {
    key == SettingsKeyTable.MINIMIZE_BEHAVIOR && selectedMode(values) == BrowsingMode.NativeBubbles ->
      R.string.minimize_always_opens_bubble
    else -> null
  }

  /** Whether the row of [key] shows at all: the dynamic colour boxes only while dynamic colour is on. */
  fun isVisible(key: String, values: SettingsValues): Boolean = when (key) {
    DYNAMIC_COLOR_APP, DYNAMIC_COLOR_WEB -> values.bool(DYNAMIC_COLOR)
    else -> true
  }

  /** The web head groups carry a note while the browsing mode is not web heads (slide over or native bubbles). */
  fun showsOutsideWebHeadsNotice(values: SettingsValues): Boolean = selectedMode(values) != BrowsingMode.WebHeads

  /** The dynamic colour summary of the old screen. */
  @StringRes
  fun dynamicColourSummary(values: SettingsValues): Int {
    val app = values.bool(DYNAMIC_COLOR_APP)
    val web = values.bool(DYNAMIC_COLOR_WEB)
    return when {
      app && web -> R.string.dynamic_summary_appweb
      app -> R.string.dynamic_summary_app
      web -> R.string.dynamic_summary_web
      else -> R.string.no_option_selected
    }
  }
}
