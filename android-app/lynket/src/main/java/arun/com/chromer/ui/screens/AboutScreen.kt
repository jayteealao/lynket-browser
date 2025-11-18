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

// Phase 4.5: AboutAppActivity migrated to Jetpack Compose

package arun.com.chromer.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import arun.com.chromer.BuildConfig
import arun.com.chromer.R
import arun.com.chromer.about.changelog.Changelog
import arun.com.chromer.shared.Constants
import coil.compose.AsyncImage
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

/**
 * Phase 4.5: About screen migrated to Jetpack Compose
 *
 * Displays app information, developer details, and credits.
 */

sealed class AboutItem {
    abstract val title: String
    abstract val subtitle: String?
    abstract val iconType: IconType
    abstract val onClick: (() -> Unit)?

    data class AppInfo(
        override val title: String,
        override val subtitle: String?,
        override val iconType: IconType,
        override val onClick: (() -> Unit)? = null
    ) : AboutItem()

    data class AuthorInfo(
        override val title: String,
        override val subtitle: String?,
        override val iconType: IconType,
        override val onClick: (() -> Unit)? = null
    ) : AboutItem()

    data class CreditInfo(
        override val title: String,
        override val subtitle: String?,
        override val iconType: IconType,
        override val onClick: (() -> Unit)? = null
    ) : AboutItem()
}

sealed class IconType {
    data class Vector(val icon: CommunityMaterial.Icon, val tint: Color) : IconType()
    data class ImageUrl(val url: String) : IconType()
    data class ImageResource(val resId: Int, val isCircular: Boolean = false) : IconType()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.accessibility_close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // App Section
            item {
                SectionHeader(title = stringResource(R.string.app))
            }
            items(getAppItems(context)) { item ->
                AboutListItem(
                    item = item,
                    onClick = { item.onClick?.invoke() }
                )
            }

            // Author Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = stringResource(R.string.author))
            }
            items(getAuthorItems(context)) { item ->
                AboutListItem(
                    item = item,
                    onClick = { item.onClick?.invoke() }
                )
            }

            // Credits Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(title = stringResource(R.string.credits))
            }
            items(getCreditsItems(context)) { item ->
                AboutListItem(
                    item = item,
                    onClick = { item.onClick?.invoke() }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun AboutListItem(
    item: AboutItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    ListItem(
        headlineContent = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = item.subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        leadingContent = {
            when (val iconType = item.iconType) {
                is IconType.Vector -> {
                    Icon(
                        painter = painterResource(
                            id = IconicsDrawable(context)
                                .icon(iconType.icon)
                                .color(iconType.tint)
                                .sizeDp(24)
                                .toBitmap()
                                .let { R.drawable.ic_launcher } // Fallback - will be replaced
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = iconType.tint
                    )
                }
                is IconType.ImageUrl -> {
                    AsyncImage(
                        model = iconType.url,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                }
                is IconType.ImageResource -> {
                    val bitmap = BitmapFactory.decodeResource(context.resources, iconType.resId)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .then(if (iconType.isCircular) Modifier.clip(CircleShape) else Modifier)
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun getAppItems(context: android.content.Context): List<AboutItem> {
    return listOf(
        AboutItem.AppInfo(
            title = context.getString(R.string.version),
            subtitle = BuildConfig.VERSION_NAME,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_information_outline,
                Color(context.getColor(R.color.colorAccent))
            ),
            onClick = null
        ),
        AboutItem.AppInfo(
            title = context.getString(R.string.changelog),
            subtitle = context.getString(R.string.see_whats_new),
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_chart_line,
                Color(context.getColor(R.color.colorAccent))
            ),
            onClick = { Changelog.show(context as android.app.Activity) }
        ),
        AboutItem.AppInfo(
            title = context.getString(R.string.follow_twitter),
            subtitle = null,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_twitter,
                Color(context.getColor(R.color.colorAccent))
            ),
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/LynketApp")))
            }
        ),
        AboutItem.AppInfo(
            title = context.getString(R.string.discuss_on_reddit),
            subtitle = null,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_reddit,
                Color(context.getColor(R.color.colorAccent))
            ),
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/r/lynket/")))
            }
        ),
        AboutItem.AppInfo(
            title = context.getString(R.string.licenses),
            subtitle = null,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_wallet_membership,
                Color(context.getColor(R.color.colorAccent))
            ),
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://htmlpreview.github.io/?https://github.com/arunkumar9t2/lynket-browser/blob/main/notices.html")
                    )
                )
            }
        ),
        AboutItem.AppInfo(
            title = context.getString(R.string.translations),
            subtitle = context.getString(R.string.help_translations),
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_translate,
                Color(context.getColor(R.color.colorAccent))
            ),
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("http://os0l2aw.oneskyapp.com/collaboration/project/62112")
                    )
                )
            }
        ),
        AboutItem.AppInfo(
            title = context.getString(R.string.source),
            subtitle = context.getString(R.string.contribute_to_chromer),
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_source_branch,
                Color(context.getColor(R.color.colorAccent))
            ),
            onClick = null
        )
    )
}

private fun getAuthorItems(context: android.content.Context): List<AboutItem> {
    return listOf(
        AboutItem.AuthorInfo(
            title = Constants.ME,
            subtitle = Constants.LOCATION,
            iconType = IconType.ImageResource(R.drawable.arun, isCircular = true),
            onClick = null
        ),
        AboutItem.AuthorInfo(
            title = context.getString(R.string.add_to_circles),
            subtitle = null,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_google_circles,
                Color(context.getColor(R.color.google_plus))
            ),
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://google.com/+arunkumar5592")))
            }
        ),
        AboutItem.AuthorInfo(
            title = context.getString(R.string.follow_twitter),
            subtitle = null,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_twitter,
                Color(context.getColor(R.color.twitter))
            ),
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/arunkumar_9t2")))
            }
        ),
        AboutItem.AuthorInfo(
            title = context.getString(R.string.connect_linkedIn),
            subtitle = null,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_linkedin_box,
                Color(context.getColor(R.color.linkedin))
            ),
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://in.linkedin.com/in/arunkumar9t2")))
            }
        ),
        AboutItem.AuthorInfo(
            title = context.getString(R.string.fork_on_github),
            subtitle = null,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_github_circle,
                Color.Black
            ),
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/arunkumar9t2/")))
            }
        ),
        AboutItem.AuthorInfo(
            title = context.getString(R.string.more_apps),
            subtitle = null,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_google_play,
                Color(context.getColor(R.color.play_store_green))
            ),
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/dev?id=9082544673727889961")
                    )
                )
            }
        )
    )
}

private fun getCreditsItems(context: android.content.Context): List<AboutItem> {
    return listOf(
        AboutItem.CreditInfo(
            title = "Patryk Goworowski",
            subtitle = context.getString(R.string.icon_design),
            iconType = IconType.ImageUrl("https://lh3.googleusercontent.com/hZdzG3b5epdGAOtQQgwSwBEeGqbIbQGg68lTD7Nvp2caLJ0CeIRksMII52Q8J6SwZbWcbFRCiNYg2ss=w384-h383-rw-no"),
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://plus.google.com/+PatrykGoworowski")
                    )
                )
            }
        ),
        AboutItem.CreditInfo(
            title = "Max Patchs",
            subtitle = context.getString(R.string.illustrations_and_video),
            iconType = IconType.ImageUrl("https://lh3.googleusercontent.com/lJn5h7sLkNMBlQwbZsyZyPrp0JNv8woEtX0hLg1o1uLmMri1VkVN10DM2XJkI4owV5u5MS5ABPbQ4s4=s1024-rw-no"),
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://plus.google.com/+Windows10-tutorialsBlogspot")
                    )
                )
            }
        ),
        AboutItem.CreditInfo(
            title = context.getString(R.string.beta_testers),
            subtitle = null,
            iconType = IconType.Vector(
                CommunityMaterial.Icon.cmd_google_plus,
                Color(context.getColor(R.color.md_red_700))
            ),
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://plus.google.com/communities/109754631011301174504")
                    )
                )
            }
        )
    )
}
