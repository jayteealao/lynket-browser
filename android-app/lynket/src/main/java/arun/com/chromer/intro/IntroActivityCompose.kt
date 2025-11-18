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

// Phase 8.2: IntroActivity migrated to Jetpack Compose

package arun.com.chromer.intro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import arun.com.chromer.R
import arun.com.chromer.ui.theme.ChromerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Phase 8.2: Intro Activity migrated to Jetpack Compose
 *
 * Onboarding screens introducing app features:
 * - Welcome screen
 * - Custom Tabs explanation
 * - Provider selection
 * - Web Heads feature
 * - AMP mode
 * - Article mode
 * - Merge tabs & apps
 * - Per-app settings
 */
@AndroidEntryPoint
class IntroActivityCompose : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ChromerTheme {
                IntroScreen(
                    onFinish = { finish() }
                )
            }
        }
    }
}

data class IntroSlide(
    val title: String,
    val description: String,
    @DrawableRes val image: Int,
    val backgroundColor: Color = Color(0xFF1E88E5)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroScreen(
    onFinish: () -> Unit
) {
    val slides = listOf(
        IntroSlide(
            title = stringResource(R.string.app_name),
            description = stringResource(R.string.intro_1),
            image = R.drawable.chromer_hd_icon
        ),
        IntroSlide(
            title = stringResource(R.string.custom_tabs),
            description = stringResource(R.string.intro_2_about_custom_tabs),
            image = R.drawable.tutorial_custom_tabs
        ),
        IntroSlide(
            title = stringResource(R.string.choose_secondary_browser),
            description = stringResource(R.string.provider_selection_intro),
            image = R.drawable.tutorial_provider_selection
        ),
        IntroSlide(
            title = stringResource(R.string.web_heads),
            description = stringResource(R.string.web_heads_intro),
            image = R.drawable.tutorial_web_heads
        ),
        IntroSlide(
            title = stringResource(R.string.amp),
            description = stringResource(R.string.tutorial_amp_intro),
            image = R.drawable.tutorial_amp_mode
        ),
        IntroSlide(
            title = stringResource(R.string.article_mode),
            description = stringResource(R.string.article_mode_intro),
            image = R.drawable.tutorial_article_mode
        ),
        IntroSlide(
            title = stringResource(R.string.merge_tabs),
            description = stringResource(R.string.merge_tabs_explanation_intro),
            image = R.drawable.tutorial_merge_tabs_and_apps
        ),
        IntroSlide(
            title = stringResource(R.string.per_app_settings),
            description = stringResource(R.string.per_app_settings_explanation),
            image = R.drawable.tutorial_per_app_settings
        )
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(slides[pagerState.currentPage].backgroundColor)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            IntroSlideContent(slide = slides[page])
        }

        // Navigation controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skip button
            TextButton(
                onClick = onFinish,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.skip))
            }

            // Page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(slides.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Surface(
                        modifier = Modifier
                            .size(if (isSelected) 12.dp else 8.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
                    ) {}
                }
            }

            // Next/Done button
            Button(
                onClick = {
                    scope.launch {
                        if (pagerState.currentPage == slides.size - 1) {
                            onFinish()
                        } else {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = slides[pagerState.currentPage].backgroundColor
                )
            ) {
                if (pagerState.currentPage == slides.size - 1) {
                    Text(stringResource(R.string.done))
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.next)
                    )
                }
            }
        }
    }
}

@Composable
fun IntroSlideContent(
    slide: IntroSlide,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image
        Image(
            painter = painterResource(id = slide.image),
            contentDescription = slide.title,
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 32.dp),
            contentScale = ContentScale.Fit
        )

        // Title
        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Description
        Text(
            text = slide.description,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
