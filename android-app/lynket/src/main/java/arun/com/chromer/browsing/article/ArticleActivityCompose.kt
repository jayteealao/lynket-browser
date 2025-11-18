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

// Phase 6.3: ArticleActivity migrated to Jetpack Compose

package arun.com.chromer.browsing.article

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.view.WindowCompat
import arun.com.chromer.R
import arun.com.chromer.data.Result
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.screens.ArticleScreen
import arun.com.chromer.ui.screens.ArticleTheme
import arun.com.chromer.ui.theme.ChromerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 6.3: Article Activity migrated to Jetpack Compose
 *
 * Displays web articles in a clean, reader-friendly format with:
 * - Adjustable text size
 * - Theme selection (light/dark/black)
 * - Article parsing and rendering
 * - AMP support
 */
@AndroidEntryPoint
class ArticleActivityCompose : ComponentActivity() {

    @Inject
    lateinit var tabsManager: TabsManager

    @Inject
    lateinit var preferences: Preferences

    private val viewModel: BrowsingArticleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val url = intent.dataString
        if (url == null) {
            Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load article
        if (savedInstanceState == null) {
            viewModel.loadArticle(url)
        }

        // Determine initial theme
        val initialTheme = when (preferences.articleTheme()) {
            Preferences.THEME_LIGHT -> ArticleTheme.LIGHT
            Preferences.THEME_DARK -> ArticleTheme.DARK
            Preferences.THEME_BLACK -> ArticleTheme.BLACK
            else -> ArticleTheme.DARK
        }

        setContent {
            ChromerTheme(
                darkTheme = initialTheme != ArticleTheme.LIGHT
            ) {
                val articleResult by viewModel.articleLiveData.observeAsState()

                ArticleScreen(
                    articleResult = articleResult,
                    onNavigateBack = { finish() },
                    onThemeChange = { theme ->
                        val themeValue = when (theme) {
                            ArticleTheme.LIGHT -> Preferences.THEME_LIGHT
                            ArticleTheme.DARK -> Preferences.THEME_DARK
                            ArticleTheme.BLACK -> Preferences.THEME_BLACK
                        }
                        preferences.articleTheme(themeValue)
                        recreate() // Recreate to apply theme
                    },
                    onTextSizeChange = { size ->
                        preferences.articleTextSizeIncrement(size)
                    },
                    initialTextSize = preferences.articleTextSizeIncrement(),
                    initialTheme = initialTheme
                )

                // Handle loading failure
                if (articleResult is Result.Failure) {
                    handleArticleLoadingFailed(url)
                } else if (articleResult is Result.Success) {
                    val article = (articleResult as Result.Success).data
                    if (article == null || article.elements == null || article.elements!!.size < 1) {
                        handleArticleLoadingFailed(url)
                    }
                }
            }
        }
    }

    private fun handleArticleLoadingFailed(url: String) {
        Toast.makeText(this, R.string.article_loading_failed, Toast.LENGTH_SHORT).show()
        finish()
        tabsManager.openBrowsingTab(
            this,
            Website(url),
            smart = true,
            fromNewTab = false,
            activityNames = TabsManager.FULL_BROWSING_ACTIVITIES
        )
    }
}
