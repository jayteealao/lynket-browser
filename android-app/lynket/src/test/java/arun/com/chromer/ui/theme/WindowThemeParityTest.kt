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

package arun.com.chromer.ui.theme

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every activity's window theme resolves to the same window and colour attributes: each activity
 * of the merged manifest, the link alias and the Compose test host, at API 30 and 36, in day and
 * night. The kept tables were recorded on the earlier themes; an activity's window must look the
 * same on its new theme.
 *
 * "actionBar" and "noTitle" are what the window shows: an activity whose class extends the
 * support library's activity draws the bar from the library's own theme attributes, every other
 * activity from the platform's.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30, 36], application = Application::class)
class WindowThemeParityTest {

  private val context: Application get() = ApplicationProvider.getApplicationContext()

  @Test
  @Config(qualifiers = "notnight")
  fun day_window_attributes_match_the_recorded_table() = check("day")

  @Test
  @Config(qualifiers = "night")
  fun night_window_attributes_match_the_recorded_table() = check("night")

  private fun check(mode: String) {
    val rows = activities().flatMap { info -> rowsOf(info) }
    RecordedTable.check("window/api${Build.VERSION.SDK_INT}-$mode.txt", rows)
  }

  private fun activities(): List<ActivityInfo> {
    val pm = context.packageManager
    val declared = pm.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty().toList()
    val alias = runCatching {
      pm.getActivityInfo(ComponentName(context.packageName, "arun.com.chromer.activities.BrowserInterceptActivity"), 0)
    }.getOrNull()
    return (declared + listOfNotNull(alias)).distinctBy { it.name }.sortedBy { it.name }
  }

  private fun rowsOf(info: ActivityInfo): List<String> {
    val themed = ContextThemeWrapper(context, info.themeResource)
    val values = ATTRIBUTES.map { (name, attr) -> name to describe(themed, attr) } +
      listOf(
        "actionBar" to effectiveFlag(themed, info, "windowActionBar", android.R.attr.windowActionBar),
        "noTitle" to effectiveFlag(themed, info, "windowNoTitle", android.R.attr.windowNoTitle),
      )
    return values.map { (name, value) -> "${info.name} $name=$value" }
  }

  /** The library's attribute for a library activity, the platform's for any other. */
  private fun effectiveFlag(themed: Context, info: ActivityInfo, libraryName: String, platformAttr: Int): String {
    val libraryBase = runCatching { Class.forName("androidx.appcompat.app.AppCompatActivity") }.getOrNull()
    val activityClass = runCatching { Class.forName(info.targetActivity ?: info.name) }.getOrNull()
    val libraryAttr = context.resources.getIdentifier(libraryName, "attr", resourcePackage())
    val usesLibrary = libraryBase != null && activityClass != null && libraryBase.isAssignableFrom(activityClass)
    return describe(themed, if (usesLibrary && libraryAttr != 0) libraryAttr else platformAttr)
  }

  private fun resourcePackage(): String = context.resources.getResourcePackageName(arun.com.chromer.R.string.app_name)

  private fun describe(themed: Context, attr: Int): String {
    val value = TypedValue()
    if (!themed.theme.resolveAttribute(attr, value, true)) return "unset"
    return when (value.type) {
      TypedValue.TYPE_NULL -> "@null"
      TypedValue.TYPE_INT_BOOLEAN -> (value.data != 0).toString()
      TypedValue.TYPE_FLOAT -> value.float.toString()
      TypedValue.TYPE_DIMENSION -> TypedValue.coerceToString(value.type, value.data)
      TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> value.data.toString()
      in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> "#%08X".format(value.data)
      TypedValue.TYPE_REFERENCE -> "ref ${nameOf(themed, value.data)}"
      TypedValue.TYPE_STRING -> describeFile(themed, value)
      else -> "type ${value.type} ${value.coerceToString()}"
    }
  }

  /** A colour state list by its default colour; a drawable by its class and colour, if it has one. */
  private fun describeFile(themed: Context, value: TypedValue): String {
    val type = runCatching { themed.resources.getResourceTypeName(value.resourceId) }.getOrNull()
    return when (type) {
      "color" -> "colors #%08X".format(themed.getColorStateList(value.resourceId).defaultColor)
      "drawable" -> {
        val drawable = runCatching { themed.getDrawable(value.resourceId) }.getOrNull()
        val colour = (drawable as? android.graphics.drawable.ColorDrawable)?.color?.let { " #%08X".format(it) }.orEmpty()
        "drawable ${drawable?.javaClass?.simpleName}$colour"
      }
      else -> "file ${nameOf(themed, value.resourceId)}"
    }
  }

  private fun nameOf(themed: Context, id: Int): String =
    runCatching { themed.resources.getResourceName(id) }.getOrElse { "#$id" }

  private companion object {
    val ATTRIBUTES: List<Pair<String, Int>> = listOf(
      "windowIsTranslucent" to android.R.attr.windowIsTranslucent,
      "windowIsFloating" to android.R.attr.windowIsFloating,
      "windowFullscreen" to android.R.attr.windowFullscreen,
      "windowBackground" to android.R.attr.windowBackground,
      "windowContentOverlay" to android.R.attr.windowContentOverlay,
      "windowAnimationStyle" to android.R.attr.windowAnimationStyle,
      "windowActivityTransitions" to android.R.attr.windowActivityTransitions,
      "windowContentTransitions" to android.R.attr.windowContentTransitions,
      "windowDrawsSystemBarBackgrounds" to android.R.attr.windowDrawsSystemBarBackgrounds,
      "backgroundDimEnabled" to android.R.attr.backgroundDimEnabled,
      "backgroundDimAmount" to android.R.attr.backgroundDimAmount,
      "statusBarColor" to android.R.attr.statusBarColor,
      "navigationBarColor" to android.R.attr.navigationBarColor,
      "navigationBarDividerColor" to android.R.attr.navigationBarDividerColor,
      "windowLightStatusBar" to android.R.attr.windowLightStatusBar,
      "windowLightNavigationBar" to android.R.attr.windowLightNavigationBar,
      "enforceStatusBarContrast" to android.R.attr.enforceStatusBarContrast,
      "enforceNavigationBarContrast" to android.R.attr.enforceNavigationBarContrast,
      "windowLayoutInDisplayCutoutMode" to android.R.attr.windowLayoutInDisplayCutoutMode,
      "isLightTheme" to android.R.attr.isLightTheme,
      "forceDarkAllowed" to android.R.attr.forceDarkAllowed,
      "colorPrimary" to android.R.attr.colorPrimary,
      "colorPrimaryDark" to android.R.attr.colorPrimaryDark,
      "colorAccent" to android.R.attr.colorAccent,
      "colorControlNormal" to android.R.attr.colorControlNormal,
      "colorControlActivated" to android.R.attr.colorControlActivated,
      "colorControlHighlight" to android.R.attr.colorControlHighlight,
      "colorButtonNormal" to android.R.attr.colorButtonNormal,
      "colorEdgeEffect" to android.R.attr.colorEdgeEffect,
      "colorBackground" to android.R.attr.colorBackground,
      "colorForeground" to android.R.attr.colorForeground,
      "textColorPrimary" to android.R.attr.textColorPrimary,
      "textColorSecondary" to android.R.attr.textColorSecondary,
      "textColorHighlight" to android.R.attr.textColorHighlight,
      "textColorHint" to android.R.attr.textColorHint,
      "textColorLink" to android.R.attr.textColorLink,
    )
  }
}
