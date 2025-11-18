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

package arun.com.chromer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arun.com.chromer.R
import arun.com.chromer.data.Result
import arun.com.chromer.data.webarticle.model.WebArticle
import arun.com.chromer.data.webarticle.model.WebArticleElement
import coil.compose.AsyncImage

/**
 * Phase 6.3: Article screen migrated to Jetpack Compose
 *
 * Displays web articles in a reader-friendly format with:
 * - Text size adjustment
 * - Theme selection (light/dark/black)
 * - Clean reading layout
 * - AMP support
 */

enum class ArticleTheme {
    LIGHT, DARK, BLACK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    articleResult: Result<WebArticle?>?,
    onNavigateBack: () -> Unit = {},
    onThemeChange: (ArticleTheme) -> Unit = {},
    onTextSizeChange: (Int) -> Unit = {},
    initialTextSize: Int = 0,
    initialTheme: ArticleTheme = if (isSystemInDarkTheme()) ArticleTheme.DARK else ArticleTheme.LIGHT
) {
    var showTextSizeDialog by remember { mutableStateOf(false) }
    var textSize by remember { mutableStateOf(initialTextSize) }
    var currentTheme by remember { mutableStateOf(initialTheme) }

    // Determine colors based on theme
    val backgroundColor = when (currentTheme) {
        ArticleTheme.LIGHT -> MaterialTheme.colorScheme.surface
        ArticleTheme.DARK -> Color(0xFF121212)
        ArticleTheme.BLACK -> Color.Black
    }

    val contentColor = when (currentTheme) {
        ArticleTheme.LIGHT -> MaterialTheme.colorScheme.onSurface
        ArticleTheme.DARK -> Color(0xFFE0E0E0)
        ArticleTheme.BLACK -> Color.White
    }

    if (showTextSizeDialog) {
        TextSizeDialog(
            currentSize = textSize,
            onDismiss = { showTextSizeDialog = false },
            onSizeChange = { newSize ->
                textSize = newSize
                onTextSizeChange(newSize)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.accessibility_close),
                            tint = contentColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTextSizeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.text_size),
                            tint = contentColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    navigationIconContentColor = contentColor
                )
            )
        },
        containerColor = backgroundColor
    ) { paddingValues ->
        when (articleResult) {
            is Result.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = contentColor)
                }
            }
            is Result.Success -> {
                val article = articleResult.data
                if (article != null && article.elements != null) {
                    ArticleContent(
                        article = article,
                        textSizeIncrement = textSize,
                        contentColor = contentColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                } else {
                    ArticleError(
                        message = stringResource(R.string.article_loading_failed),
                        contentColor = contentColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
            is Result.Failure -> {
                ArticleError(
                    message = stringResource(R.string.article_loading_failed),
                    contentColor = contentColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = contentColor)
                }
            }
        }
    }
}

@Composable
private fun ArticleContent(
    article: WebArticle,
    textSizeIncrement: Int,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        item {
            Text(
                text = article.title ?: "",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = (28 + textSizeIncrement).sp,
                color = contentColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Author and date
        if (!article.author.isNullOrEmpty() || !article.datePublished.isNullOrEmpty()) {
            item {
                Column {
                    article.author?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = (14 + textSizeIncrement).sp,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                    article.datePublished?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = (12 + textSizeIncrement).sp,
                            color = contentColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Article elements
        article.elements?.let { elements ->
            items(elements) { element ->
                ArticleElementItem(
                    element = element,
                    textSizeIncrement = textSizeIncrement,
                    contentColor = contentColor
                )
            }
        }
    }
}

@Composable
private fun ArticleElementItem(
    element: WebArticleElement,
    textSizeIncrement: Int,
    contentColor: Color
) {
    when (element.type) {
        "text" -> {
            Text(
                text = element.content ?: "",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = (16 + textSizeIncrement).sp,
                color = contentColor,
                lineHeight = (24 + textSizeIncrement).sp
            )
        }
        "image" -> {
            element.content?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                )
            }
        }
        "heading" -> {
            Text(
                text = element.content ?: "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontSize = (20 + textSizeIncrement).sp,
                color = contentColor,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
        "quote" -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = contentColor.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = element.content ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = (16 + textSizeIncrement).sp,
                    color = contentColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        else -> {
            Text(
                text = element.content ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = (16 + textSizeIncrement).sp,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ArticleError(
    message: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun TextSizeDialog(
    currentSize: Int,
    onDismiss: () -> Unit,
    onSizeChange: (Int) -> Unit
) {
    var sliderValue by remember { mutableStateOf(currentSize.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_size)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.text_size_preview, sliderValue.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = -4f..8f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSizeChange(sliderValue.toInt())
                    onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
