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

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import arun.com.chromer.R
import arun.com.chromer.ui.navigation.LynketNavigator
import arun.com.chromer.ui.settings.SettingsKeyTable.ANIMATION_SPEED
import arun.com.chromer.ui.settings.SettingsKeyTable.ANIMATION_TYPE
import arun.com.chromer.ui.settings.SettingsKeyTable.ARTICLE_THEME
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR_APP
import arun.com.chromer.ui.settings.SettingsKeyTable.DYNAMIC_COLOR_WEB
import arun.com.chromer.ui.settings.SettingsKeyTable.PREFERRED_ACTION
import arun.com.chromer.ui.settings.SettingsKeyTable.THEME_MODE
import arun.com.chromer.ui.settings.SettingsKeyTable.TOOLBAR_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.TOOLBAR_COLOR_PREF
import arun.com.chromer.ui.settings.SettingsKeyTable.USE_WALLPAPER_COLOURS
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEADS_COLOR
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_SIZE
import arun.com.chromer.ui.settings.SettingsKeyTable.WEB_HEAD_SPAWN
import arun.com.chromer.ui.settings.components.ColorPickerSheet
import arun.com.chromer.ui.settings.components.ListChoiceDialog
import arun.com.chromer.ui.settings.components.RowControl
import arun.com.chromer.ui.settings.components.RowIcon
import arun.com.chromer.ui.settings.components.SettingsGroup
import arun.com.chromer.ui.settings.components.SettingsRow
import arun.com.chromer.ui.settings.components.SettingsScaffold
import arun.com.chromer.ui.settings.components.hexOf
import arun.com.chromer.ui.settings.components.settingsColumn
import arun.com.chromer.ui.theme.ThemeMode

/** Look and feel: theme first, then personalization, web heads and the article theme as before. */
@Composable
fun LookAndFeelScreen(navigator: LynketNavigator, viewModel: SettingsViewModel = hiltViewModel()) {
  val values by viewModel.values.collectAsStateWithLifecycle()
  val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
  LookAndFeelContent(
    values = values,
    showWallpaperColours = wallpaperColoursAvailable(),
    onBack = { navigator.back() },
    onSet = viewModel::set,
  )
  SettingsDialogHost(dialogs.firstOrNull(), viewModel)
}

/** Colours from the wallpaper exist from Android 12; earlier versions show no switch for them. */
internal fun wallpaperColoursAvailable(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= Build.VERSION_CODES.S

private val ThemeChips = listOf(
  ThemeMode.Auto to R.string.theme_auto,
  ThemeMode.Light to R.string.theme_light,
  ThemeMode.Dark to R.string.theme_dark,
  ThemeMode.Black to R.string.theme_black,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookAndFeelContent(
  values: SettingsValues,
  showWallpaperColours: Boolean,
  onBack: () -> Unit,
  onSet: (String, Any?) -> Unit,
  openList: String? = null,
  openColour: String? = null,
) {
  var listKey by rememberSaveable { mutableStateOf(openList) }
  var colourKey by rememberSaveable { mutableStateOf(openColour) }
  val webHeadsNotice = if (SettingsRules.showsSlideOverNotice(values)) stringResource(R.string.not_available_in_slide_mode) else null

  val actions = listChoices(PREFERRED_ACTION, R.array.actionValues, R.array.actionKeys)
  val animations = listChoices(ANIMATION_TYPE, R.array.animationValues, R.array.animationKeys)
  val speeds = listChoices(ANIMATION_SPEED, R.array.animationSpeedValues, R.array.animationSpeedKeys)
  val spawns = listChoices(WEB_HEAD_SPAWN, R.array.webHeadsSpawnValues, R.array.webHeadsSpawnKeys)
  val sizes = listChoices(WEB_HEAD_SIZE, R.array.webHeadsSizeValues, R.array.webHeadsSizeKeys)
  val themes = listChoices(ARTICLE_THEME, R.array.articleThemeValues, R.array.articleThemeKeys)
  val lists = mapOf(
    PREFERRED_ACTION to (R.string.preferred_action to actions),
    ANIMATION_TYPE to (R.string.choose_animation to animations),
    ANIMATION_SPEED to (R.string.animation_speed to speeds),
    WEB_HEAD_SPAWN to (R.string.webhead_spawn_title to spawns),
    WEB_HEAD_SIZE to (R.string.webhead_size_dialog to sizes),
    ARTICLE_THEME to (R.string.article_theme to themes),
  )

  SettingsScaffold(
    title = stringResource(R.string.settings_look_and_feel),
    onBack = onBack,
    modifier = Modifier.testTag("look-and-feel"),
  ) {
    item(key = "theme") {
      SettingsGroup(label = stringResource(R.string.settings_theme), modifier = Modifier.settingsColumn()) {
        row {
          val selected = values.int(THEME_MODE)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 16.dp, vertical = 8.dp).testTag("row-$THEME_MODE"),
          ) {
            ThemeChips.forEach { (mode, label) ->
              val on = selected == mode.ordinal
              FilterChip(
                selected = on,
                onClick = { onSet(THEME_MODE, mode.ordinal) },
                label = { Text(stringResource(label)) },
                leadingIcon = if (on) {
                  { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(0.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(),
                modifier = Modifier.testTag("theme-${mode.name.lowercase()}").semantics { role = Role.RadioButton },
              )
            }
          }
        }
        if (showWallpaperColours) {
          row {
            SwitchRow(
              USE_WALLPAPER_COLOURS,
              R.string.use_wallpaper_colors,
              R.string.use_wallpaper_colors_summary,
              values,
              onSet,
              Icons.Outlined.Wallpaper,
            )
          }
        }
      }
    }
    item(key = "personalization") {
      SettingsGroup(label = stringResource(R.string.personalization), modifier = Modifier.settingsColumn()) {
        row { SwitchRow(TOOLBAR_COLOR_PREF, R.string.colored_toolbar, null, values, onSet, Icons.Outlined.Palette) }
        row { ColourRow(TOOLBAR_COLOR, R.string.default_toolbar_color, values) { colourKey = TOOLBAR_COLOR } }
        row {
          val checked = values.bool(DYNAMIC_COLOR)
          SettingsRow(
            title = stringResource(R.string.dynamic_toolbar_color),
            summary = AnnotatedString(stringResource(SettingsRules.dynamicColourSummary(values))),
            icon = RowIcon(Icons.Outlined.FormatColorFill),
            control = RowControl.Switch(checked),
            enabled = SettingsRules.isEnabled(DYNAMIC_COLOR, values),
            modifier = Modifier.testTag("row-$DYNAMIC_COLOR"),
            onClick = { onSet(DYNAMIC_COLOR, !checked) },
          )
        }
        if (SettingsRules.isVisible(DYNAMIC_COLOR_APP, values)) {
          row { CheckboxRow(DYNAMIC_COLOR_APP, R.string.based_on_app, null, values, onSet, indent = true) }
          row { CheckboxRow(DYNAMIC_COLOR_WEB, R.string.based_on_web, null, values, onSet, indent = true) }
        }
        row { ListRow(PREFERRED_ACTION, R.string.preferred_action, actions, values, Icons.Outlined.FavoriteBorder) { listKey = PREFERRED_ACTION } }
        row { ListRow(ANIMATION_TYPE, R.string.choose_animation, animations, values, Icons.Outlined.FilterNone) { listKey = ANIMATION_TYPE } }
        row { ListRow(ANIMATION_SPEED, R.string.animation_speed, speeds, values, Icons.Outlined.Speed) { listKey = ANIMATION_SPEED } }
      }
    }
    item(key = "web-heads") {
      SettingsGroup(label = stringResource(R.string.web_heads), notice = webHeadsNotice, modifier = Modifier.settingsColumn()) {
        row { ColourRow(WEB_HEADS_COLOR, R.string.web_heads_color, values) { colourKey = WEB_HEADS_COLOR } }
        row { ListRow(WEB_HEAD_SPAWN, R.string.webhead_spawn_summary, spawns, values, Icons.Outlined.Code) { listKey = WEB_HEAD_SPAWN } }
        row { ListRow(WEB_HEAD_SIZE, R.string.webhead_size_title, sizes, values, Icons.Outlined.CropFree) { listKey = WEB_HEAD_SIZE } }
      }
    }
    item(key = "article") {
      SettingsGroup(label = stringResource(R.string.article_mode), modifier = Modifier.settingsColumn()) {
        row { ListRow(ARTICLE_THEME, R.string.article_theme, themes, values, Icons.Outlined.FormatColorFill) { listKey = ARTICLE_THEME } }
      }
    }
  }

  listKey?.let { key ->
    val (title, choices) = lists.getValue(key)
    ListChoiceDialog(
      title = stringResource(title),
      choices = choices,
      selected = values.values[key] ?: Unit,
      onSelect = { onSet(key, it); listKey = null },
      onDismiss = { listKey = null },
    )
  }
  colourKey?.let { key ->
    ColorPickerSheet(
      title = stringResource(if (key == TOOLBAR_COLOR) R.string.default_toolbar_color else R.string.web_heads_color),
      initial = values.int(key),
      onDone = { onSet(key, it); colourKey = null },
      onDismiss = { colourKey = null },
    )
  }
}

@Composable
private fun ColourRow(key: String, title: Int, values: SettingsValues, onClick: () -> Unit) {
  val colour = values.int(key)
  SettingsRow(
    title = stringResource(title),
    summary = AnnotatedString(hexOf(colour)),
    icon = RowIcon(Icons.Outlined.Palette),
    control = RowControl.Colour(Color(colour), hexOf(colour)),
    enabled = SettingsRules.isEnabled(key, values),
    modifier = Modifier.testTag("row-$key"),
    onClick = onClick,
  )
}
