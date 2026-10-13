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

package arun.com.chromer.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The one back stack of [arun.com.chromer.MainActivity] with the moves the screens need.
 *
 * Screens take a navigator instead of the back stack, so the helper names the screens used before
 * Navigation 3 (`navigateToHistory()`, `navigateToBrowser(url)`, ...) keep working unchanged.
 */
class LynketNavigator(val backStack: MutableList<NavKey>) {

  val top: NavKey? get() = backStack.lastOrNull()

  /** Pushes [key] unless it is already on top, so a double tap does not stack a screen twice. */
  fun navigate(key: LynketKey) {
    if (backStack.lastOrNull() != key) backStack.add(key)
  }

  /** Pops the top key; returns false when only the root is left (the activity handles that back). */
  fun back(): Boolean {
    if (backStack.size <= 1) return false
    backStack.removeAt(backStack.lastIndex)
    return true
  }

  /** Removes the first occurrence of [key] from the stack, for destinations that close themselves. */
  fun remove(key: LynketKey) {
    val index = backStack.lastIndexOf(key)
    if (index > 0) backStack.removeAt(index)
  }

  fun replaceTop(key: LynketKey) {
    if (backStack.size <= 1) {
      backStack.add(key)
    } else {
      backStack[backStack.lastIndex] = key
    }
  }

  companion object {
    /**
     * The stack a fresh start shows: home, then the changelog when it is due, the intro on a first
     * run (above the changelog, as the old home opened the intro over its changelog dialog), and a
     * link handed to the activity on top.
     */
    fun initialKeys(
      isFirstRun: Boolean,
      showChangelog: Boolean,
      deepLink: LynketKey? = null,
    ): List<LynketKey> = buildList {
      add(LynketKey.Home)
      if (showChangelog) add(LynketKey.Changelog)
      if (isFirstRun) add(LynketKey.Intro)
      if (deepLink != null) add(deepLink)
    }
  }
}

fun LynketNavigator.navigateToHome() {
  while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
  if (backStack.isEmpty()) backStack.add(LynketKey.Home)
}

fun LynketNavigator.navigateToHistory() = navigate(LynketKey.History)
fun LynketNavigator.navigateToTabs() = navigate(LynketKey.Tabs)
fun LynketNavigator.navigateToSettings() = navigate(LynketKey.Settings)
fun LynketNavigator.navigateToBrowser(url: String) = navigate(LynketKey.Browser(url))
fun LynketNavigator.navigateToProviderSelection() = navigate(LynketKey.ProviderSelection)
fun LynketNavigator.navigateToArticle(url: String) = navigate(LynketKey.Article(url))
fun LynketNavigator.navigateToAbout() = navigate(LynketKey.About)
fun LynketNavigator.navigateToTips() = navigate(LynketKey.Tips)
fun LynketNavigator.navigateToIntro() = navigate(LynketKey.Intro)
fun LynketNavigator.navigateToChangelog() = navigate(LynketKey.Changelog)
