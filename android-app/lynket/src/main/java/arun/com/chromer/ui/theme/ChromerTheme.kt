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

package arun.com.chromer.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.emptyPreferences
import arun.com.chromer.data.preferences.SettingsSchema
import arun.com.chromer.data.preferences.readBoolean
import arun.com.chromer.data.preferences.readInt
import arun.com.chromer.settings.Preferences
import arun.com.chromer.ui.theme.hct.Hct

/** Light, dark or black (near-black surfaces for OLED screens); [Auto] follows the system. */
enum class ThemeMode { Auto, Light, Dark, Black }

/** The theme choices a surface reads once when it starts. */
@Immutable
data class ThemeSettings(
  val themeMode: ThemeMode = ThemeMode.Auto,
  val useWallpaperColours: Boolean = false,
  val primaryColour: Color? = null,
)

// Roles generated once from the indigo seed #3F51B5 (tonal spot: primary chroma 36, secondary 16,
// neutral 6, neutral variant 8) with the pink #FF4081 hue as tertiary (chroma 48), using the
// Material colour utilities. Pure white and black are tinted toward the seed hue.
internal val LightColours = lightColorScheme(
  primary = Color(0xFF515B92),
  onPrimary = Color(0xFFFEFBFF),
  primaryContainer = Color(0xFFDEE0FF),
  onPrimaryContainer = Color(0xFF0B154B),
  inversePrimary = Color(0xFFBAC3FF),
  secondary = Color(0xFF5B5D72),
  onSecondary = Color(0xFFFEFBFF),
  secondaryContainer = Color(0xFFE0E1F9),
  onSecondaryContainer = Color(0xFF181A2C),
  tertiary = Color(0xFF9A4059),
  onTertiary = Color(0xFFFFFBFF),
  tertiaryContainer = Color(0xFFFFD9DF),
  onTertiaryContainer = Color(0xFF3F0018),
  error = Color(0xFFBA1A1A),
  onError = Color(0xFFFFFBFF),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),
  background = Color(0xFFFBF8FF),
  onBackground = Color(0xFF1B1B21),
  surface = Color(0xFFFBF8FF),
  onSurface = Color(0xFF1B1B21),
  surfaceVariant = Color(0xFFE3E1EC),
  onSurfaceVariant = Color(0xFF46464F),
  surfaceTint = Color(0xFF515B92),
  inverseSurface = Color(0xFF303036),
  inverseOnSurface = Color(0xFFF2EFF7),
  outline = Color(0xFF767680),
  outlineVariant = Color(0xFFC7C5D0),
  scrim = Color(0xFF0D0E13),
  surfaceBright = Color(0xFFFBF8FF),
  surfaceDim = Color(0xFFDBD9E0),
  surfaceContainer = Color(0xFFEFEDF4),
  surfaceContainerHigh = Color(0xFFE9E7EF),
  surfaceContainerHighest = Color(0xFFE4E1E9),
  surfaceContainerLow = Color(0xFFF5F2FA),
  surfaceContainerLowest = Color(0xFFFEFBFF),
)

internal val DarkColours = darkColorScheme(
  primary = Color(0xFFBAC3FF),
  onPrimary = Color(0xFF222C61),
  primaryContainer = Color(0xFF394379),
  onPrimaryContainer = Color(0xFFDEE0FF),
  inversePrimary = Color(0xFF515B92),
  secondary = Color(0xFFC3C5DD),
  onSecondary = Color(0xFF2D2F42),
  secondaryContainer = Color(0xFF434659),
  onSecondaryContainer = Color(0xFFE0E1F9),
  tertiary = Color(0xFFFFB1C1),
  onTertiary = Color(0xFF5F112C),
  tertiaryContainer = Color(0xFF7C2941),
  onTertiaryContainer = Color(0xFFFFD9DF),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
  background = Color(0xFF121318),
  onBackground = Color(0xFFE4E1E9),
  surface = Color(0xFF121318),
  onSurface = Color(0xFFE4E1E9),
  surfaceVariant = Color(0xFF46464F),
  onSurfaceVariant = Color(0xFFC7C5D0),
  surfaceTint = Color(0xFFBAC3FF),
  inverseSurface = Color(0xFFE4E1E9),
  inverseOnSurface = Color(0xFF303036),
  outline = Color(0xFF90909A),
  outlineVariant = Color(0xFF46464F),
  scrim = Color(0xFF0D0E13),
  surfaceBright = Color(0xFF39393F),
  surfaceDim = Color(0xFF121318),
  surfaceContainer = Color(0xFF1F1F25),
  surfaceContainerHigh = Color(0xFF29292F),
  surfaceContainerHighest = Color(0xFF34343A),
  surfaceContainerLow = Color(0xFF1B1B21),
  surfaceContainerLowest = Color(0xFF0D0E13),
)

/** The dark scheme with near-black surfaces; every surface is darker than #20202A. */
internal fun ColorScheme.toBlack(): ColorScheme = copy(
  background = Color(0xFF050508),
  surface = Color(0xFF050508),
  surfaceDim = Color(0xFF050508),
  surfaceBright = Color(0xFF1E1E26),
  surfaceVariant = Color(0xFF1B1B22),
  surfaceContainerLowest = Color(0xFF030305),
  surfaceContainerLow = Color(0xFF0A0A0E),
  surfaceContainer = Color(0xFF0F0F14),
  surfaceContainerHigh = Color(0xFF15151B),
  surfaceContainerHighest = Color(0xFF1B1B22),
)

/** Replaces the primary roles with roles derived from the person's chosen colour. */
internal fun ColorScheme.withPrimary(colour: Color, isLight: Boolean): ColorScheme {
  // The HCT tones Material Components used for these roles: 40/100/90/10 light, 80/20/30/90 dark.
  val argb = colour.toArgb()
  val tones = if (isLight) intArrayOf(40, 100, 90, 10) else intArrayOf(80, 20, 30, 90)
  val (accent, onAccent, accentContainer, onAccentContainer) = tones.map { Color(Hct.tone(argb, it)) }
  return copy(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accentContainer,
    onPrimaryContainer = onAccentContainer,
    surfaceTint = accent,
  )
}

/**
 * The colour scheme for the given choices: wallpaper colours on Android 12 and later when the
 * option is on, else the seed scheme with the person's colour as primary.
 */
fun chromerColorScheme(
  context: Context,
  dark: Boolean,
  black: Boolean,
  useWallpaperColours: Boolean,
  primaryColour: Color?,
): ColorScheme {
  val base = when {
    useWallpaperColours && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
      if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    else -> {
      val seed = if (dark) DarkColours else LightColours
      primaryColour?.let { seed.withPrimary(it, isLight = !dark) } ?: seed
    }
  }
  return if (dark && black) base.toBlack() else base
}

internal val ChromerShapes = Shapes(
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

private val Base = Typography()

/** System font; screen titles and labels at weight 600, row titles 16 sp, summaries 14 sp. */
internal val ChromerTypography = Base.copy(
  displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
  titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
  labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
  bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp),
  bodyMedium = Base.bodyMedium.copy(fontSize = 14.sp),
)

/**
 * Lynket theme for every Compose surface, in activities and in the web head overlay windows.
 * It sets no window colours, so it also works in a window that a service owns.
 */
@Composable
fun ChromerTheme(
  themeMode: ThemeMode = ThemeMode.Auto,
  useWallpaperColours: Boolean = false,
  primaryColour: Color? = null,
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val dark = when (themeMode) {
    ThemeMode.Auto -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark, ThemeMode.Black -> true
  }
  val context = LocalContext.current
  val colorScheme = remember(context, dark, themeMode, useWallpaperColours, primaryColour) {
    chromerColorScheme(context, dark, themeMode == ThemeMode.Black, useWallpaperColours, primaryColour)
  }
  // MaterialExpressiveTheme and MotionScheme.expressive() are internal in the resolved Material 3
  // 1.4.0 (source: javap of material3-android-1.4.0 classes.jar, `expressive$material3`), so the
  // theme uses MaterialTheme with the expressive shapes and type scale and the standard motion.
  MaterialTheme(
    colorScheme = colorScheme,
    shapes = ChromerShapes,
    typography = ChromerTypography,
    content = content,
  )
}

/** The theme mode stored as [code] (0 Auto, 1 Light, 2 Dark, 3 Black); unknown codes are Auto. */
fun themeModeOf(code: Int): ThemeMode = ThemeMode.entries.getOrElse(code) { ThemeMode.Auto }

/** Theme choices from the settings store, read once. */
fun Preferences.themeSettings(): ThemeSettings = ThemeSettings(
  themeMode = themeModeOf(themeMode()),
  useWallpaperColours = useWallpaperColours(),
  primaryColour = Color(toolbarColor()),
)

/** Theme choices from stored settings values. */
internal fun themeSettingsOf(stored: androidx.datastore.preferences.core.Preferences, context: Context): ThemeSettings {
  val schema = { name: String -> SettingsSchema.require(name).default(context) }
  return ThemeSettings(
    themeMode = themeModeOf(stored.readInt(SettingsSchema.THEME_MODE, schema(SettingsSchema.THEME_MODE) as Int)),
    useWallpaperColours = stored.readBoolean(
      SettingsSchema.USE_WALLPAPER_COLOURS,
      schema(SettingsSchema.USE_WALLPAPER_COLOURS) as Boolean,
    ),
    primaryColour = Color(stored.readInt("toolbar_color", schema("toolbar_color") as Int)),
  )
}

/**
 * [ChromerTheme] with the person's theme, colour and wallpaper option. It follows the settings
 * store, so a change on the Look and feel screen applies at once. It never waits for the store:
 * until the first read is in (the app starts that read when it starts), the schema defaults apply.
 */
@Composable
fun ChromerThemeFromSettings(
  /** False for a screen that colours its own system bars, such as the web view's toolbar colour. */
  applySystemBars: Boolean = true,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val repository = remember(context) { Preferences.entryPoint(context).userPreferencesRepository() }
  val defaults = remember(context) { themeSettingsOf(emptyPreferences(), context) }
  val stored by repository.snapshot.collectAsState()
  val settings = stored?.let { themeSettingsOf(it, context) } ?: defaults
  if (applySystemBars) {
    val dark = when (settings.themeMode) {
      ThemeMode.Auto -> isSystemInDarkTheme()
      ThemeMode.Light -> false
      ThemeMode.Dark, ThemeMode.Black -> true
    }
    val activity = remember(context) { context.findComponentActivity() }
    // The bar icons follow the app's theme, not the system's, and again when the theme changes.
    LaunchedEffect(activity, dark) { activity?.applySystemBars(dark) }
  }
  ChromerTheme(
    themeMode = settings.themeMode,
    useWallpaperColours = settings.useWallpaperColours,
    primaryColour = settings.primaryColour,
  ) {
    if (applySystemBars) {
      // The window behind the content (resize, fold, the first frame of the next start) takes the
      // app's background, so a Dark or Black theme never shows a light window.
      val activity = remember(context) { context.findComponentActivity() }
      val background = MaterialTheme.colorScheme.background
      LaunchedEffect(activity, background) { activity?.colourOpaqueWindow(background.toArgb()) }
    }
    content()
  }
}

/** Sets the window background of an activity whose window is opaque; translucent hosts keep theirs. */
private fun ComponentActivity.colourOpaqueWindow(colour: Int) {
  val translucent = obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent)).run {
    try { getBoolean(0, false) } finally { recycle() }
  }
  if (!translucent) window.setBackgroundDrawable(ColorDrawable(colour))
}

/** Edge to edge with status and navigation bar icons readable on the app's light or dark surfaces. */
fun ComponentActivity.applySystemBars(dark: Boolean) {
  val status = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
  val navigation = if (dark) SystemBarStyle.dark(NAV_DARK_SCRIM) else SystemBarStyle.light(NAV_LIGHT_SCRIM, NAV_DARK_SCRIM)
  enableEdgeToEdge(statusBarStyle = status, navigationBarStyle = navigation)
}

// The scrims androidx.activity uses behind three-button navigation.
private val NAV_LIGHT_SCRIM = AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NAV_DARK_SCRIM = AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b)

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
  is ComponentActivity -> this
  is Activity -> null
  is ContextWrapper -> baseContext.findComponentActivity()
  else -> null
}
