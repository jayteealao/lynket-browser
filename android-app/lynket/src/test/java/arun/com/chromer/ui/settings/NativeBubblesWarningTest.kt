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

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import arun.com.chromer.R
import arun.com.chromer.ui.accessibility.AccessibilityChecksRule
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Native Bubbles (Beta) warning: its lead line names Android's bubble setting, a "Known issues"
 * heading lists the four known issues one by one, and Guide and OK keep their actions.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = WARNING_PHONE)
class NativeBubblesWarningTest {

  @get:Rule
  val composeRule = createComposeRule()

  @get:Rule(order = 99)
  val accessibilityChecks = AccessibilityChecksRule(composeRule)

  private val context: Application get() = ApplicationProvider.getApplicationContext()
  private fun string(id: Int) = context.getString(id)

  private val issues = listOf(
    R.string.browsing_mode_native_bubbles_issue_web_view,
    R.string.browsing_mode_native_bubbles_issue_limit,
    R.string.browsing_mode_native_bubbles_issue_menu,
    R.string.browsing_mode_native_bubbles_issue_refused,
  )

  private var guides = 0
  private var dismissals = 0

  private val actions = SettingsDialogActions(
    onDismiss = { dismissals++ },
    onOpenOverlaySettings = {},
    onRequestNotifications = {},
    onOpenUsageAccess = {},
    onUsageAccessDeclined = {},
    onBubblesGuide = { guides++ },
  )

  private fun show(theme: ThemeMode = ThemeMode.Light) {
    composeRule.setContent {
      ChromerTheme(themeMode = theme) { SettingsDialogContent(SettingsDialog.NativeBubblesWarning, actions) }
    }
    composeRule.waitForIdle()
  }

  @Test
  fun theLeadNamesTheBubbleSettingInQuotes() {
    show()
    composeRule.onNodeWithText(string(R.string.browsing_mode_native_bubbles)).assertExists()
    composeRule.onNodeWithText(string(R.string.browsing_mode_native_bubbles_warning))
      .assertTextContains("\"All conversations can bubble\"", substring = true)
  }

  @Test
  fun knownIssuesIsAHeadingOverFourSeparateIssues() {
    show()
    composeRule.onNodeWithText(string(R.string.browsing_mode_native_bubbles_known_issues))
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    issues.forEach { composeRule.onAllNodesWithText(string(it)).assertCountEquals(1) }
  }

  @Test
  fun theOldDisclaimerAndProviderIssueAreGone() {
    show()
    composeRule.onAllNodesWithText("not final", substring = true).assertCountEquals(0)
    composeRule.onAllNodesWithText("Web View provider", substring = true).assertCountEquals(0)
  }

  @Test
  fun guideOpensTheGuideAndOkCloses() {
    show()
    composeRule.onNodeWithText(string(R.string.browsing_mode_native_bubbles_guide)).performClick()
    assertThat(guides).isEqualTo(1)
    composeRule.onNodeWithText(string(android.R.string.ok)).performClick()
    assertThat(dismissals).isEqualTo(1)
  }

  @Test
  fun guideSitsBeforeOk() {
    show()
    val guide = composeRule.onNodeWithText(string(R.string.browsing_mode_native_bubbles_guide)).getUnclippedBoundsInRoot()
    val ok = composeRule.onNodeWithText(string(android.R.string.ok)).getUnclippedBoundsInRoot()
    assertThat(guide.left).isLessThan(ok.left)
  }
}

private const val WARNING_PHONE = "w411dp-h891dp-xxhdpi"
