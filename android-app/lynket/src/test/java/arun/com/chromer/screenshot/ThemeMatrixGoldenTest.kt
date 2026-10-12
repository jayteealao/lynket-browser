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

package arun.com.chromer.screenshot

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.core.app.ApplicationProvider
import arun.com.chromer.R
import arun.com.chromer.browsing.amp.AmpState
import arun.com.chromer.browsing.article.ReaderState
import arun.com.chromer.browsing.menu.ActionButton
import arun.com.chromer.browsing.menu.BrowsingScreen
import arun.com.chromer.browsing.menu.browsingBottomBarItems
import arun.com.chromer.browsing.menu.browsingMenuItems
import arun.com.chromer.bubbles.webheads.motion.TrashState
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadState
import arun.com.chromer.bubbles.webheads.ui.TrashTarget
import arun.com.chromer.bubbles.webheads.ui.WebHeadBubble
import arun.com.chromer.bubbles.webheads.ui.context.ShareAllFormat
import arun.com.chromer.bubbles.webheads.ui.context.WebHeadContextActions
import arun.com.chromer.bubbles.webheads.ui.context.WebHeadContextContent
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.StringResource
import arun.com.chromer.history.HistoryUiState
import arun.com.chromer.home.CustomTabProviderInfo
import arun.com.chromer.home.ModernHomeViewModel.Suggestions
import arun.com.chromer.search.suggestion.items.SuggestionItem
import arun.com.chromer.shortcuts.ShortcutState
import arun.com.chromer.tabs.ARTICLE
import arun.com.chromer.tabs.CUSTOM_TAB
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tabs.WEB_VIEW
import arun.com.chromer.tabs.ui.TabsUiState
import arun.com.chromer.ui.about.AboutContent
import arun.com.chromer.ui.browsing.AmpResolverDialog
import arun.com.chromer.ui.browsing.NewTabCard
import arun.com.chromer.ui.browsing.OptionsSheetContent
import arun.com.chromer.ui.browsing.ShortcutCreatorDialog
import arun.com.chromer.ui.browsing.optionItems
import arun.com.chromer.ui.changelog.ChangelogDialog
import arun.com.chromer.ui.history.HistoryActions
import arun.com.chromer.ui.history.HistoryContent
import arun.com.chromer.ui.history.TEST_NOW
import arun.com.chromer.ui.history.TEST_ZONE
import arun.com.chromer.ui.history.UiDispatcherResetRule
import arun.com.chromer.ui.history.loaded
import arun.com.chromer.ui.history.testSites
import arun.com.chromer.ui.home.HomeActions
import arun.com.chromer.ui.home.HomeContentState
import arun.com.chromer.ui.home.HomeScreenContent
import arun.com.chromer.ui.intro.IntroContent
import arun.com.chromer.ui.permission.PermissionExplainerDialog
import arun.com.chromer.ui.perapp.PerAppContent
import arun.com.chromer.ui.perapp.PerAppRow
import arun.com.chromer.ui.perapp.PerAppState
import arun.com.chromer.data.apps.model.Provider
import arun.com.chromer.ui.providerselection.ProviderSelectionContent
import arun.com.chromer.browsing.providerselection.ModernProviderSelectionViewModel.ProviderSelectionUiState
import arun.com.chromer.ui.reader.ImageViewerScreen
import arun.com.chromer.ui.reader.ReaderScreen
import arun.com.chromer.ui.reader.readerMenu
import arun.com.chromer.ui.reader.sampleReaderArticle
import arun.com.chromer.ui.settings.AppLabels
import arun.com.chromer.ui.settings.BrowsingModeContent
import arun.com.chromer.ui.settings.BrowsingOptionsContent
import arun.com.chromer.ui.settings.LookAndFeelContent
import arun.com.chromer.ui.settings.SettingsKeyTable
import arun.com.chromer.ui.settings.SettingsRootContent
import arun.com.chromer.ui.settings.SettingsValues
import arun.com.chromer.ui.settings.components.AppPickerSheet
import arun.com.chromer.ui.tabs.TabsActions
import arun.com.chromer.ui.tabs.TabsContent
import arun.com.chromer.ui.theme.ChromerTheme
import arun.com.chromer.ui.theme.ThemeMode
import arun.com.chromer.ui.tips.TipsContent
import arun.com.chromer.ui.webview.WebViewBarsState
import arun.com.chromer.ui.webview.WebViewPage
import arun.com.chromer.ui.webview.WebViewScreen
import arun.com.chromer.util.appicon.ApplicationIcon
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every Compose screen in the light, dark and black themes on a phone (w411dp) and a fold
 * (w841dp): content stays inside the window, nothing sits under the system bars, and dark and
 * black use dark surfaces. Each screen uses the same sample state as its own golden test.
 * Record with `./gradlew :lynket:recordRoborazziDebug --tests '*ThemeMatrixGoldenTest*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ThemeMatrixGoldenTest(
  private val screen: Screen,
  private val theme: ThemeMode,
  private val size: WindowSize,
) {

  enum class WindowSize(val qualifiers: String) {
    Phone("w411dp-h891dp-xxhdpi"),
    Fold("w841dp-h701dp-xxhdpi"),
  }

  /** How a screen is captured: the window root, a dialog window, or after its async image fails. */
  enum class Capture { Root, Dialog, ImageError }

  /** One screen: its capture mode, whether its frames advance by hand, and its content. */
  class Screen(
    val name: String,
    val capture: Capture = Capture.Root,
    val animated: Boolean = false,
    val content: @Composable () -> Unit,
  ) {
    override fun toString() = name
  }

  /** Sets the window size before the compose rule starts its activity. */
  private val windowRule = TestRule { base, _: Description ->
    object : Statement() {
      override fun evaluate() {
        RuntimeEnvironment.setQualifiers(size.qualifiers)
        base.evaluate()
      }
    }
  }

  @get:Rule(order = 0)
  val window: TestRule = windowRule

  @get:Rule(order = 1)
  val uiDispatcherReset = UiDispatcherResetRule()

  @get:Rule(order = 2)
  val composeRule = createComposeRule()

  @Test
  fun capture() {
    if (screen.animated) composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      ChromerTheme(themeMode = theme) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) { screen.content() }
      }
    }
    when {
      screen.animated -> composeRule.mainClock.advanceTimeBy(1_000)
      screen.capture == Capture.ImageError -> composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag("image-error").fetchSemanticsNodes().isNotEmpty()
      }
      else -> composeRule.waitForIdle()
    }
    val file = "src/test/screenshots/arun.com.chromer.screenshot.ThemeMatrixGoldenTest.${screen.name}_${theme.name.lowercase()}_${size.name.lowercase()}.png"
    if (screen.capture == Capture.Dialog) {
      composeRule.onNode(isDialog()).captureRoboImage(file)
    } else {
      composeRule.onRoot().captureRoboImage(file)
    }
  }

  companion object {

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val text = { id: Int -> context.getString(id) }

    // Home (ShellGoldenTest)
    // Android types are built when a case runs, inside the Robolectric sandbox.
    private val provider get() = CustomTabProviderInfo(
      iconUri = ApplicationIcon.createUri("com.android.chrome"),
      providerDescription = StringResource(R.string.tab_provider_status_message_home, listOf("Chrome")),
      providerReason = StringResource(0),
    )
    private val recents = listOf("github", "wikipedia", "reddit", "medium", "youtube", "news", "theverge", "android")
      .map { Website("https://$it.example/").apply { title = it.replaceFirstChar(Char::uppercase) } }

    // Tabs (HistoryTabsGoldenTest)
    private fun tab(url: String, type: Int, title: String) =
      TabsManager.Tab(url, type, Website(title, url, null, null, null, null, false, 0, 1))

    private val tabs = listOf(
      tab("https://developer.android.com/compose", CUSTOM_TAB, "Compose BOM mapping"),
      tab("https://www.reddit.com/r/androiddev", CUSTOM_TAB, "r/androiddev"),
      tab("https://www.theverge.com/android", ARTICLE, "The Verge"),
      tab("https://medium.com/kotlin-coroutines", WEB_VIEW, "Kotlin coroutines guide"),
    )

    // Settings (SettingsGoldenTest)
    private val values: SettingsValues
      get() = SettingsValues.from(emptyPreferences(), context).with(
        SettingsKeyTable.WEB_HEAD_ENABLED to true,
        SettingsKeyTable.TOOLBAR_COLOR_PREF to true,
        SettingsKeyTable.DYNAMIC_COLOR to true,
        SettingsKeyTable.DYNAMIC_COLOR_APP to true,
        SettingsKeyTable.MERGE_TABS to true,
      )

    private val labels = AppLabels(
      providerPackage = "com.android.chrome",
      providerName = "Chrome",
      secondaryName = "Firefox",
      favShareName = "Messages",
    )

    private val apps = listOf(
      "Calendar", "Camera", "Chrome", "Clock", "Contacts", "Drive", "Files", "Gmail", "Keep", "Maps",
      "Messages", "Photos", "Play Store", "Settings", "YouTube",
    ).mapIndexed { n, name ->
      PerAppRow("com.example.${name.lowercase().replace(" ", "")}", name, blacklisted = n % 4 == 1, incognito = n % 5 == 2)
    }

    private val providerIcon get() = Uri.parse("android.resource://com.android.chrome/0")
    private val providers get() = listOf(
      Provider("com.android.chrome", "Chrome", providerIcon, installed = true),
      Provider("com.brave.browser", "Brave", providerIcon, installed = true),
      Provider("org.mozilla.firefox", "Firefox", providerIcon, installed = false),
      Provider("com.microsoft.emmx", "Edge", providerIcon, installed = false),
    )

    // Web heads (WebHeadsGoldenTest)
    private fun head() = WebHeadState("https://example.com").apply {
      isMaster = true
      revealed = true
      badgeCount = 1
      badgeOnLeft = true
    }

    private val noContextActions = object : WebHeadContextActions {
      override fun open(website: Website) = Unit
      override fun copy(website: Website) = Unit
      override fun share(website: Website) = Unit
      override fun close(website: Website) = Unit
      override fun copyAll() = Unit
      override fun shareAll(format: ShareAllFormat) = Unit
      override fun dismiss() = Unit
    }

    private val url = "https://www.theverge.com/2026/10/android-17"

    @Composable
    private fun BottomFrame(content: @Composable () -> Unit) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { content() }
    }

    val screens = listOf(
      Screen("home") { HomeScreenContent(HomeContentState(recents = recents, providerInfo = provider), HomeActions()) },
      Screen("history") {
        HistoryContent(
          state = HistoryUiState(),
          items = flowOf(loaded(testSites)).collectAsLazyPagingItems(),
          providerName = "Chrome",
          actions = HistoryActions(),
          now = TEST_NOW,
          timeZone = TEST_ZONE,
        )
      },
      Screen("tabs") { TabsContent(TabsUiState.Loaded(tabs), closeIcon = true, actions = TabsActions()) },
      Screen("settings") {
        SettingsRootContent(showDefaultBrowserCard = true, version = "2.2.0", onBack = {}, onSetDefault = {}, onOpen = {})
      },
      Screen("browsingMode") {
        BrowsingModeContent(values, showNativeBubbles = true, onBack = {}, onMode = {}, onSet = { _, _ -> })
      },
      Screen("lookAndFeel") { LookAndFeelContent(values, showWallpaperColours = true, onBack = {}, onSet = { _, _ -> }) },
      Screen("browsingOptions") {
        BrowsingOptionsContent(values, labels, onBack = {}, onSet = { _, _ -> }, onOpen = {}, onPickApp = {})
      },
      Screen("perApp") {
        PerAppContent(PerAppState(apps = apps), enabled = true, onBack = {}, onSearch = {}, onRefresh = {}, onMaster = {}, onBlacklist = { _, _ -> }, onIncognito = { _, _ -> })
      },
      Screen("providerSelection") {
        ProviderSelectionContent(
          ProviderSelectionUiState.Success(providers, selectedPackage = "com.android.chrome", usingWebView = false),
          onBack = {}, onUseProvider = {}, onInstall = {}, onUseWebView = {}, onGetChrome = {}, onRetry = {},
        )
      },
      Screen("optionsSheet") {
        BottomFrame {
          Surface(
            shape = BottomSheetDefaults.ExpandedShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.widthIn(max = BottomSheetDefaults.SheetMaxWidth).fillMaxWidth(),
          ) {
            Column {
              BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
              OptionsSheetContent(optionItems(fromArticle = false), urlKnown = true, onPick = {})
            }
          }
        }
      },
      Screen("openWith", capture = Capture.Dialog, animated = true) {
        AppPickerSheet(title = "Open with…", subtitle = url, intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)), onPick = {}, onDismiss = {})
      },
      Screen("newTab") {
        BottomFrame {
          NewTabCard(
            query = "navigation 3",
            suggestions = Suggestions(
              query = "navigation 3",
              google = listOf(
                SuggestionItem.GoogleSuggestionItem("navigation 3 compose"),
                SuggestionItem.GoogleSuggestionItem("navigation 3 compose deep links"),
                SuggestionItem.GoogleSuggestionItem("navigation 3 compose migration"),
              ),
            ),
            onQueryChange = {},
            onSubmit = {},
            onVoiceUnavailable = {},
            onDismiss = {},
          )
        }
      },
      Screen("ampResolver", capture = Capture.Dialog, animated = true) {
        AmpResolverDialog(AmpState.Loading(url), {}, {}, {}, {})
      },
      Screen("shortcutCreator", capture = Capture.Dialog, animated = true) {
        ShortcutCreatorDialog(
          state = ShortcutState.Ready(
            Website(url).apply { title = "Android 17 brings resizable apps everywhere" },
            Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3F51B5.toInt()) },
          ),
          name = "Android 17",
          canCreate = true,
          onNameChange = {},
          onCreate = {},
          onDismiss = {},
        )
      },
      Screen("webHead") {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceDim).padding(16.dp)) {
          Column {
            WebHeadBubble(head(), WebHeadMotion.HEAD_DP.dp, gestures = null)
            TrashTarget(TrashState().apply { scale = 1f; locked = true })
          }
        }
      },
      Screen("webHeadContext") {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
          WebHeadContextContent(
            listOf(
              Website("https://www.android.com").apply { title = "Android" },
              Website("https://en.wikipedia.org/wiki/Web_browser").apply { title = "Web browser - Wikipedia" },
              Website("https://example.com").apply { title = "Example Domain" },
            ),
            noContextActions,
            onShareAll = {},
          )
        }
      },
      Screen("permissionExplainer", capture = Capture.Dialog) {
        PermissionExplainerDialog(
          icon = Icons.Outlined.Layers,
          title = "Allow web heads to float",
          body = "To draw web heads over other apps, Lynket needs the \"Display over other apps\" permission. Android opens the setting next.",
          onGrant = {},
          onDismiss = {},
        )
      },
      Screen("reader") {
        ReaderScreen(
          state = ReaderState.Content(sampleReaderArticle()),
          menuItems = readerMenu(text),
          bottomBarItems = browsingBottomBarItems(BrowsingScreen.Article, true, text),
          textSizeIncrement = 0,
          savedTextSizeIncrement = 0,
          accent = MaterialTheme.colorScheme.primary,
          onClose = {},
          onMenuItem = {},
          onRetry = {},
          onOpenInBrowser = {},
          onImage = {},
          onKeyword = {},
          onTextSizeChange = {},
          onSaveTextSize = {},
        )
      },
      Screen("imageViewer", capture = Capture.ImageError) {
        ImageViewerScreen("file:///nowhere.png", onClose = {}, onShare = {})
      },
      Screen("webView") {
        WebViewScreen(
          page = WebViewPage("Android 17 brings resizable apps everywhere", url, 60),
          toolbarColour = Color(0xFF3F51B5),
          menuItems = browsingMenuItems(BrowsingScreen.WebView, true, ActionButton(text(R.string.share), iconRes = R.drawable.ic_share_24dp), null, text),
          bottomBarItems = browsingBottomBarItems(BrowsingScreen.WebView, true, text),
          bars = WebViewBarsState(),
          onClose = {},
          onMenuItem = {},
        ) { modifier ->
          Box(modifier.background(Color.White)) {
            Column(Modifier.padding(16.dp)) { Text("The page draws here (a WebView on the device).", color = Color.DarkGray) }
          }
        }
      },
      Screen("intro") { IntroContent(onClose = {}, onAction = {}, onSetDefaultBrowser = {}) },
      Screen("changelog") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ChangelogDialog(onOk = {}, onRate = {}) }
      },
      Screen("about") { AboutContent(onBack = {}, onRow = {}) },
      Screen("tips") { TipsContent(onClose = {}) },
    )

    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}_{2}")
    fun cases(): List<Array<Any>> = screens.flatMap { screen ->
      listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Black).flatMap { theme ->
        WindowSize.entries
          // On the phone size the shortcut dialog's text field never lets Compose go idle in a
          // native-graphics window (repro: AppNotIdleException after 60 s, 2026-10-13, as in
          // BrowsingDialogsGoldenTest); BrowsingDialogsTest covers its controls and the browsing
          // device check captures it on a phone.
          .filterNot { size -> screen.name == "shortcutCreator" && size == WindowSize.Phone }
          .map { size -> arrayOf<Any>(screen, theme, size) }
      }
    }
  }
}
