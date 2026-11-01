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

package arun.com.chromer.ui.accessibility

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeAccessibilityValidator
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Accessibility checks for Compose content in the JVM screen tests.
 *
 * It reads the merged semantics tree of every Compose root in the view it is given, and fails on
 * each element a person can click, long-press or set a value on (a slider) that has nothing a
 * screen reader can speak (no content description, text or editable text), and on each element a
 * person can click or long-press that has a touch target under 48 x 48 dp. A slider is dragged
 * along its length and set with a screen reader's adjust gestures, so its height is not judged.
 *
 * Elements with only other actions (scrolling, dragging, custom actions) are not judged, nor is an
 * element with an empty size (scrolled out of view). An element its container cuts is judged at its
 * full layout size, so cutting it cannot hide a small target. Colour contrast is not checked: it
 * needs pixels, and these checks read semantics only.
 *
 * The Compose library's own `enableAccessibilityChecks()` is not used: under Robolectric the
 * Accessibility Test Framework reports "Composable content is not evaluated in this environment"
 * and passes every Compose element, so an unlabelled icon button would pass.
 */
class LynketAccessibilityValidator : ComposeAccessibilityValidator {

  override fun check(view: View) {
    val density = view.resources.displayMetrics.density
    val failures = mutableListOf<String>()
    composeRoots(view).forEach { root ->
      walk(root.semanticsOwner.rootSemanticsNode) { node -> failures += check(node, density) }
    }
    if (failures.isNotEmpty()) {
      throw AssertionError("Accessibility checks failed:\n" + failures.joinToString("\n"))
    }
  }

  private fun check(node: SemanticsNode, density: Float): List<String> {
    val config = node.config
    val pressable = SemanticsActions.OnClick in config || SemanticsActions.OnLongClick in config
    val actionable = pressable || SemanticsActions.SetProgress in config
    if (!actionable || isHidden(config) || isExempt(node)) return emptyList()
    val bounds = node.boundsInRoot
    if (bounds.width <= 0f || bounds.height <= 0f) return emptyList()

    val failures = mutableListOf<String>()
    val label = speakableLabel(config)
    if (label.isBlank()) failures += describe("SpeakableLabel", node, label, density)

    if (pressable) {
      val (width, height) = fullTouchSize(node)
      if (width / density < MIN_TOUCH_DP || height / density < MIN_TOUCH_DP) {
        failures += describe("TouchTarget", node, label, density)
      }
    }
    return failures
  }

  /**
   * The touch target in pixels at the element's full layout size: the visible bounds a container
   * cuts are replaced by the layout size, and the touch area Compose adds around the element is
   * kept. So an element that a container cuts is judged by the size it has, not skipped.
   */
  private fun fullTouchSize(node: SemanticsNode): Pair<Float, Float> {
    val bounds = node.boundsInRoot
    val touch = node.touchBoundsInRoot
    val width = maxOf(bounds.width, node.size.width.toFloat()) + (touch.width - bounds.width)
    val height = maxOf(bounds.height, node.size.height.toFloat()) + (touch.height - bounds.height)
    return width to height
  }

  private fun describe(check: String, node: SemanticsNode, label: String, density: Float): String {
    val config = node.config
    val touch = node.touchBoundsInRoot
    val (width, height) = fullTouchSize(node)
    val tag = config.getOrNull(SemanticsProperties.TestTag) ?: "-"
    val role = config.getOrNull(SemanticsProperties.Role)?.toString() ?: "-"
    val size = "%.1f x %.1f dp".format(width / density, height / density)
    val position = "(%.1f, %.1f) dp".format(touch.left / density, touch.top / density)
    return "$check: tag=$tag role=$role label='${label.take(40)}' size=$size at $position"
  }

  private fun speakableLabel(config: SemanticsConfiguration): String =
    (config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "") +
      (config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: "") +
      (config.getOrNull(SemanticsProperties.EditableText)?.text ?: "")

  @Suppress("DEPRECATION") // sdlc-debt: InvisibleToUser is deprecated for HideFromAccessibility; still read so older call sites stay hidden. Drop it when Compose removes the property.
  private fun isHidden(config: SemanticsConfiguration): Boolean =
    SemanticsProperties.HideFromAccessibility in config || SemanticsProperties.InvisibleToUser in config

  private fun isExempt(node: SemanticsNode): Boolean = isTextLink(node) || isSheetDragHandleAnchor(node)

  /**
   * E1, text links. An inline link in a `Text` (`LinkAnnotation`) gets its own clickable node that
   * carries no text; the link text is on the parent `Text` node, through which screen readers read
   * and activate the link. The Accessibility Test Framework's own link check does not run on
   * API 26 and later.
   */
  private fun isTextLink(node: SemanticsNode): Boolean = SemanticsProperties.LinkTestMarker in node.config

  /**
   * E2, the Material 3 bottom-sheet drag handle. `ModalBottomSheet` wraps its default drag handle
   * in a tooltip anchor that has only a long-press action, no label and a 32 dp width. Its single
   * child is the handle itself, which carries the "Drag handle" description and the dismiss
   * action, and is what a screen reader focuses. The sheets also close by back and by a scrim
   * tap. The anchor is drawn by the library; widening it means a custom drag handle in every sheet,
   * a visible layout change.
   */
  private fun isSheetDragHandleAnchor(node: SemanticsNode): Boolean {
    val config = node.config
    if (SemanticsActions.OnClick in config || SemanticsActions.OnLongClick !in config) return false
    val child = node.children.singleOrNull() ?: return false
    return !child.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
      SemanticsActions.Dismiss in child.config
  }

  private fun composeRoots(view: View): List<ViewRootForTest> {
    val roots = mutableListOf<ViewRootForTest>()
    fun visit(v: View) {
      if (v is ViewRootForTest) roots += v
      if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
    }
    visit(view)
    return roots
  }

  private fun walk(node: SemanticsNode, visit: (SemanticsNode) -> Unit) {
    visit(node)
    node.children.forEach { walk(it, visit) }
  }

  private companion object {
    /** 48 dp, with a 0.5 dp tolerance for rounding to pixels. */
    const val MIN_TOUCH_DP = 47.5f
  }
}

/**
 * Turns the accessibility checks on for a Compose UI test class. They run on every test action
 * (`performClick`, `performTouchInput`, ...) and once after the test, on the content left on
 * screen. Declare it inside the Compose rule, so it runs after the content is set and before the
 * Compose rule disposes it:
 *
 * ```
 * @get:Rule(order = 99)
 * val accessibilityChecks = AccessibilityChecksRule(composeRule)
 * ```
 *
 * A test that ends with no Compose content (its activity closed, or no `setContent`) keeps only
 * the checks of its actions.
 */
class AccessibilityChecksRule(private val composeRule: ComposeTestRule) : TestRule {

  override fun apply(base: Statement, description: Description): Statement = object : Statement() {
    override fun evaluate() {
      val androidRule = composeRule as? AndroidComposeTestRule<*, *>
        ?: error("AccessibilityChecksRule needs an AndroidComposeTestRule, got ${composeRule::class.java.name}")
      androidRule.setComposeAccessibilityValidator(LynketAccessibilityValidator())
      base.evaluate()
      if (hasComposeRoot()) composeRule.onAllNodes(isRoot()).onFirst().tryPerformAccessibilityChecks()
    }
  }

  private fun hasComposeRoot(): Boolean = try {
    composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()
  } catch (_: IllegalStateException) {
    false // "No compose hierarchies found in the app": the test left no Compose content.
  }
}
