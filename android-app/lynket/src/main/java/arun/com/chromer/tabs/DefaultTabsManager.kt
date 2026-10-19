// Phase 8: Converted from RxJava to Kotlin Coroutines
/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.tabs

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.content.Intent.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat.getColor
import arun.com.chromer.BuildConfig
import arun.com.chromer.R
import arun.com.chromer.appdetect.AppDetectionManager
import arun.com.chromer.appdetect.LinkSource
import arun.com.chromer.browsing.amp.AmpResolverActivity
import arun.com.chromer.browsing.article.ArticleActivity
import arun.com.chromer.browsing.backgroundloading.BackgroundLoadingStrategyFactory
import arun.com.chromer.browsing.customtabs.CustomTabActivity
import arun.com.chromer.browsing.customtabs.CustomTabs
import arun.com.chromer.browsing.newtab.NewTabDialogActivity
import arun.com.chromer.browsing.webview.WebViewActivity
import arun.com.chromer.bubbles.BubbleType.NATIVE
import arun.com.chromer.bubbles.BubbleType.WEB_HEADS
import arun.com.chromer.bubbles.FloatingBubbleFactory
import arun.com.chromer.bubbles.system.BubbleGate
import arun.com.chromer.bubbles.system.BubbleRefusal
import arun.com.chromer.bubbles.system.BubbleRefusedActivity
import arun.com.chromer.bubbles.system.bubbleConversationId
import arun.com.chromer.data.apps.AppRepository
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.extenstions.isPackageInstalled
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants.EXTRA_KEY_BUBBLE_REFUSAL
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.shared.Constants.EXTRA_KEY_LINK_SOURCE_APP
import arun.com.chromer.shared.Constants.EXTRA_KEY_TOOLBAR_COLOR
import arun.com.chromer.shared.Constants.EXTRA_KEY_WEBSITE
import arun.com.chromer.shared.Constants.NO_COLOR
import arun.com.chromer.tabs.ui.TabsActivity
import arun.com.chromer.ui.browsing.showSecondaryBrowserError
import arun.com.chromer.util.SafeIntent
import arun.com.chromer.util.Utils
import arun.com.chromer.util.events.EventBus
import arun.com.chromer.util.events.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTabsManager
@Inject
constructor(
  private val application: Application,
  private val preferences: Preferences,
  private val appDetectionManager: AppDetectionManager,
  private val appRepository: AppRepository,
  private val websiteRepository: WebsiteRepository,
  private val backgroundLoadingStrategyFactory: BackgroundLoadingStrategyFactory,
  private val eventBus: EventBus,
  private val floatingBubbleFactory: FloatingBubbleFactory,
  private val bubbleGate: BubbleGate,
) : TabsManager {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  override fun openUrl(
    context: Context,
    website: Website,
    fromApp: Boolean, // TODO Move to sealed classes
    fromWebHeads: Boolean,
    fromNewTab: Boolean,
    fromAmp: Boolean,
    incognito: Boolean,
    linkSource: LinkSource,
  ) {
    scope.launch(Dispatchers.IO) {
      openUrlInternal(
        context,
        website,
        fromApp,
        fromWebHeads,
        fromNewTab,
        fromAmp,
        incognito,
        linkSource,
      )
    }
  }

  private suspend fun openUrlInternal(
    context: Context,
    website: Website,
    fromApp: Boolean = true,
    fromWebHeads: Boolean = false,
    fromNewTab: Boolean = false,
    fromAmp: Boolean = false,
    incognito: Boolean = false,
    linkSource: LinkSource = LinkSource.NONE,
  ) {
    // Clear non browsing activities if it was external intent.
    if (!fromApp) {
      clearNonBrowsingActivities()
    }
    // Open in web heads mode if we this command did not come from web heads.
    val webHeads = preferences.webHeads()
    val nativeBubbles = preferences.nativeBubbles()
    Timber.d("Open %s: webHeads=%b nativeBubbles=%b", website.url, webHeads, nativeBubbles)
    if ((webHeads || nativeBubbles) && !fromWebHeads) {
      openWebHeads(context, website, fromMinimize = fromAmp, fromAmp = false, incognito = incognito, linkSource = linkSource)
      return
    }

    // Check if already an instance for this URL is there in our tasks
    if (reOrderTabByUrl(context, website)) {
      // Just bring it to front
      return
    }

    // Check if we should try to find AMP version of incoming url.
    if (preferences.ampMode() && !fromAmp) {
      if (website.hasAmp()) {
        // We already got the amp url, so open it in a browsing tab.
        openBrowsingTab(
          context,
          Website.Ampify(website),
          smart = true,
          fromNewTab = fromNewTab,
          activityNames = null,
          incognito = incognito,
          linkSource = linkSource,
        )
        return
      } else if (!fromWebHeads) {
        // Open a proxy activity, attempt an extraction then open the AMP url if exists.
        val ampResolver = Intent(context, AmpResolverActivity::class.java).apply {
          data = website.preferredUri()
          // Its own task: a link from outside reaches BrowserInterceptActivity as the root of a
          // task, which it removes when it finishes (finishAndRemoveTask), and the resolver would
          // go with it before it can open the page.
          addFlags(FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
          if (incognito) {
            putExtra(EXTRA_KEY_INCOGNITO, true)
          }
          // The resolver opens the page later; it passes the app colour source on.
          if (linkSource.filtered.isNotEmpty()) {
            putExtra(EXTRA_KEY_LINK_SOURCE_APP, linkSource.filtered)
          }
        }
        context.startActivity(ampResolver)
        return
      }
    }

    if (preferences.articleMode()) {
      // Launch article mode
      openArticle(context, website, newTab = false, incognito = incognito, linkSource = linkSource)
      return
    }
    // If everything failed then launch normally in browsing activity.
    openBrowsingTab(context, website, smart = true, fromNewTab = fromNewTab, activityNames = null, incognito = incognito, linkSource = linkSource)
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  override fun reOrderTabByUrl(
    context: Context,
    website: Website,
    activityNames: List<String>?
  ): Boolean {
    return findTaskAndExecuteAction(context, website, activityNames) { task ->
      Timber.d("Moved tab to front $website")
      task.moveToFront()
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  override fun finishTabByUrl(
    context: Context,
    website: Website,
    activityNames: List<String>?
  ): Boolean {
    return findTaskAndExecuteAction(context, website, activityNames) { task ->
      Timber.d("Finishing task $website")
      task.finishAndRemoveTask()
    }
  }

  /**
   * Talks to activity manager and finds all active task. Then find a task that matches the input
   * criteria which is base url and optionally and preferred activity name the url belongs to.
   * Upon finding the task, executes {@param foundAction}
   */
  private fun findTaskAndExecuteAction(
    context: Context,
    website: Website,
    activityNames: List<String>?,
    foundAction: (task: ActivityManager.AppTask) -> Unit
  ): Boolean {
    try {
      val am = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
      if (Utils.isLollipopAbove()) {
        val appTasks = am.appTasks
        Timber.d("No of app tasks ${appTasks?.size}")
        for (task in appTasks) {
          val info = DocumentUtils.getTaskInfoFromTask(task)
          info?.let {
            try {
              val intent = info.baseIntent
              val url = intent.dataString
              val componentClassName = intent.component!!.className

              val urlMatches = url != null && website.matches(url)

              val taskComponentMatches = activityNames?.contains(componentClassName)
                ?: TabsManager.ALL_BROWSING_ACTIVITIES.contains(componentClassName)

              if (taskComponentMatches && urlMatches) {
                foundAction(task)
                return true
              }
            } catch (e: Exception) {
              Timber.e(e)
            }
          }
        }
      }
    } catch (e: Exception) {
      Timber.e(e)
    }
    return false
  }

  override fun minimizeTabByUrl(url: String, fromClass: String, incognito: Boolean) {
    eventBus.tryEmit(Event.TabEvent.Minimized(url, getTabType(fromClass)))
    if (preferences.webHeads() || preferences.nativeBubbles() || preferences.minimizeToWebHead()) {
      // When minimizing, don't try to handle aggressive loading cases.
      openWebHeads(application, website = Website(url), fromMinimize = true, incognito = incognito)
    }
  }

  override suspend fun processIncomingIntent(
    activity: Activity,
    intent: Intent
  ) {
    try {
      withContext(Dispatchers.IO) {
        // Safety check against malicious intents
        val safeIntent = SafeIntent(intent)
        val url = safeIntent.dataString ?: return@withContext
        var proceed = true
        // Find the app this link came from, for per-app rules and the app-based toolbar colour.
        val linkSource = if (preferences.perAppSettings() || preferences.isAppBasedToolbar()) {
          appDetectionManager.detectLinkSource()
        } else {
          LinkSource.NONE
        }
        // The first thing to check is if we should blacklist.
        if (preferences.perAppSettings()) {
          val lastApp = linkSource.nonFiltered
          if (lastApp.isNotEmpty()) {
            val blacklisted = appRepository.isPackageBlacklisted(lastApp)
            val incognito = !blacklisted && appRepository.isPackageIncognito(lastApp)
            Timber.d("Per-app rule for %s: blacklisted=%b incognito=%b", lastApp, blacklisted, incognito)
            if (blacklisted) {
              withContext(Dispatchers.Main) {
                doBlacklistAction(activity, safeIntent)
              }
              proceed = false
            } else if (incognito) {
              withContext(Dispatchers.Main) {
                doIncognitoAction(activity, url, linkSource)
              }
              proceed = false
            }
          }
        }

        if (proceed) {
          openUrlInternal(activity, Website(url), fromApp = false, linkSource = linkSource)
        }
      }
    } catch (e: Exception) {
      Timber.e(e, "Critical error when processing incoming intent")
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  override fun openArticle(
    context: Context,
    website: Website,
    newTab: Boolean,
    incognito: Boolean
  ) = openArticle(context, website, newTab, incognito, LinkSource.NONE)

  private fun openArticle(
    context: Context,
    website: Website,
    newTab: Boolean,
    incognito: Boolean,
    linkSource: LinkSource,
  ) {
    if (!reOrderTabByUrl(context, website, listOf(ArticleActivity::class.java.name))) {
      val intent = Intent(context, ArticleActivity::class.java).apply {
        data = website.preferredUri()
        if (context !is Activity) {
          addFlags(FLAG_ACTIVITY_NEW_TASK)
        }
        if (newTab || Preferences.get(context).mergeTabs()) {
          addFlags(FLAG_ACTIVITY_NEW_DOCUMENT)
          addFlags(FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        if (incognito) {
          putExtra(EXTRA_KEY_INCOGNITO, true)
        }
        putExtra(EXTRA_KEY_TOOLBAR_COLOR, customizedWebsiteColor(website, linkSource))
      }
      context.startActivity(intent)
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  override fun openBrowsingTab(
    context: Context,
    website: Website,
    smart: Boolean,
    fromNewTab: Boolean,
    activityNames: List<String>?,
    incognito: Boolean,
    linkSource: LinkSource,
  ) {
    openBrowsingTab(context, website, smart, fromNewTab, activityNames, incognito, linkSource, extras = null)
  }

  /** Returns false when an existing tab of [website] came to the front instead of a new one. */
  private fun openBrowsingTab(
    context: Context,
    website: Website,
    smart: Boolean,
    fromNewTab: Boolean,
    activityNames: List<String>?,
    incognito: Boolean,
    linkSource: LinkSource,
    extras: Bundle? = null,
  ): Boolean {
    val reordered = smart && reOrderTabByUrl(context, website, activityNames)

    if (!reordered) {
      val isIncognito = preferences.fullIncognitoMode() || incognito

      val browsingActivity = if (shouldUseWebView(incognito)) {
        Intent(context, WebViewActivity::class.java)
      } else {
        Intent(context, CustomTabActivity::class.java)
      }.apply {
        data = website.preferredUri()
        putExtra(EXTRA_KEY_WEBSITE, website)
        putExtra(EXTRA_KEY_TOOLBAR_COLOR, customizedWebsiteColor(website, linkSource))
        if (isIncognito) {
          putExtra(EXTRA_KEY_INCOGNITO, true)
        }
        extras?.let(::putExtras)
      }

      if (preferences.mergeTabs() || fromNewTab) {
        browsingActivity.addFlags(FLAG_ACTIVITY_NEW_DOCUMENT)
        browsingActivity.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK)
      }
      if (context !is Activity) {
        browsingActivity.addFlags(FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(browsingActivity)
    }
    return !reordered
  }

  override fun openRefusedBubble(context: Context, website: Website, refusal: BubbleRefusal, incognito: Boolean) =
    openRefusedBubble(context, website, refusal, incognito, LinkSource.NONE)

  /**
   * The page opens in a tab, which then shows the message (see BrowsingActivity). When the tab of
   * this page already exists and only comes to the front, the message starts in a task of its own.
   */
  private fun openRefusedBubble(
    context: Context,
    website: Website,
    refusal: BubbleRefusal,
    incognito: Boolean,
    linkSource: LinkSource,
  ) {
    val extras = Bundle().apply { putString(EXTRA_KEY_BUBBLE_REFUSAL, refusal.name) }
    val opened = openBrowsingTab(
      context,
      website,
      smart = true,
      fromNewTab = false,
      activityNames = null,
      incognito = incognito,
      linkSource = linkSource,
      extras = extras,
    )
    if (!opened) {
      context.startActivity(BubbleRefusedActivity.intent(context, refusal, newTask = true))
    }
  }

  override fun shouldUseWebView(incognito: Boolean): Boolean {
    val canSafelyOpenCCT =
      !preferences.useWebView() && CustomTabs.getCustomTabSupportingPackages(application)
        .isNotEmpty()
    val isIncognito = preferences.fullIncognitoMode() || incognito
    return !(!isIncognito && canSafelyOpenCCT)
  }

  override fun openWebHeads(
    context: Context,
    website: Website,
    fromMinimize: Boolean,
    fromAmp: Boolean,
    incognito: Boolean
  ) = openWebHeads(context, website, fromMinimize, fromAmp, incognito, LinkSource.NONE)

  private fun openWebHeads(
    context: Context,
    website: Website,
    fromMinimize: Boolean,
    fromAmp: Boolean,
    incognito: Boolean,
    linkSource: LinkSource,
  ) {
    val url = website.preferredUrl()
    val bubbles = preferences.nativeBubbles()
    val webHeads = preferences.webHeads()

    if (bubbles && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Android would not show the bubble: open the page in a tab now and say why.
      val refusal = bubbleGate.refusal(bubbleConversationId(website.url))
      if (refusal != null) {
        Timber.w("Bubble refused (%s); opening %s in a tab", refusal, website.url)
        openRefusedBubble(context, website, refusal, incognito, linkSource)
        return
      }
    }

    val opened = floatingBubbleFactory[if (bubbles) NATIVE else WEB_HEADS].openBubble(
      website,
      fromMinimize,
      fromAmp,
      incognito,
      context,
      customizedWebsiteColor(website, linkSource)
    )
    if (!opened) {
      // The bubble could not show; the page opens in a tab instead of being lost.
      openBrowsingTab(context, website, smart = true, fromNewTab = false, activityNames = null, incognito = incognito, linkSource = linkSource)
      return
    }

    val shouldUseWebView = shouldUseWebView(incognito)

    // If this command was not issued for minimizing, then attempt aggressive loading.
    if (preferences.aggressiveLoading() && !fromMinimize) {
      when {
        preferences.articleMode() -> backgroundLoadingStrategyFactory[ARTICLE].prepare(url)
        else -> {
          if (shouldUseWebView) {
            if (!bubbles) {
              backgroundLoadingStrategyFactory[WEB_VIEW].prepare(url)
            }
          } else {
            backgroundLoadingStrategyFactory[CUSTOM_TAB].prepare(url)
          }
          openBrowsingTab(
            context,
            website,
            smart = true,
            fromNewTab = false,
            activityNames = null,
            incognito = incognito,
            linkSource = linkSource,
          )
        }
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  override fun openNewTab(context: Context, url: String) {
    val newTabIntent = Intent(context, NewTabDialogActivity::class.java).apply {
      data = Uri.parse(url)
      addFlags(FLAG_ACTIVITY_NEW_TASK)
      addFlags(FLAG_ACTIVITY_NEW_DOCUMENT)
      addFlags(FLAG_ACTIVITY_MULTIPLE_TASK)
    }
    context.startActivity(newTabIntent)
  }

  override fun clearNonBrowsingActivities() {
    eventBus.tryEmit(Event.TabEvent.FinishNonBrowsingActivities)
  }

  override fun showTabsActivity() {
    application.startActivity(Intent(application, TabsActivity::class.java).apply {
      addFlags(FLAG_ACTIVITY_NEW_TASK)
    })
  }


  @SuppressLint("NewApi")
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  override suspend fun getActiveTabs(): List<TabsManager.Tab> {
    return withContext(Dispatchers.IO) {
      try {
        val am = application.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        (am.appTasks ?: emptyList<ActivityManager.AppTask>())
          .asSequence()
          .map(DocumentUtils::getTaskInfoFromTask)
          .filterNotNull()
          .filter { it.baseIntent.dataString != null && it.baseIntent.component != null }
          .map {
            val url = it.baseIntent.dataString!!

            @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            val type = getTabType(it.baseIntent.component!!.className)
            TabsManager.Tab(url, type)
          }.filter { it.type != OTHER }
          .toMutableList()
      } catch (e: Exception) {
        Timber.e(e, "Error getting active tabs")
        emptyList()
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  override suspend fun closeAllTabs(): List<TabsManager.Tab> {
    return withContext(Dispatchers.IO) {
      val tabs = getActiveTabs()
      tabs.forEach { tab ->
        finishTabByUrl(application, Website(tab.url), listOf(tab.getTargetActivityName()))
      }
      tabs
    }
  }

  /**
   * The toolbar colour from the person's settings; the app colour comes from [linkSource], the app
   * this link came from, so a link opened inside Lynket never takes an earlier link's app colour.
   */
  @ColorInt
  private fun customizedWebsiteColor(website: Website, linkSource: LinkSource): Int {
    if (preferences.isColoredToolbar()) {
      if (preferences.dynamicToolbar()) {
        var appColor = NO_COLOR
        var websiteColor = NO_COLOR

        if (preferences.dynamicToolbarOnApp()) {
          val lastApp = linkSource.filtered
          if (lastApp.isNotEmpty()) {
            appColor = appRepository.getPackageColorSync(lastApp)
          }
        }
        if (preferences.dynamicToolbarOnWeb()) {
          websiteColor = if (website.themeColor() != NO_COLOR) {
            website.themeColor()
          } else {
            websiteRepository.getWebsiteColorSync(website.url)
          }
        }
        return when {
          appColor != NO_COLOR -> appColor
          websiteColor != NO_COLOR -> websiteColor
          else -> preferences.toolbarColor()
        }
      } else {
        return preferences.toolbarColor()
      }
    } else return getColor(application, R.color.primary)
  }


  private fun doIncognitoAction(activity: Activity, url: String, linkSource: LinkSource) {
    openUrl(activity, Website(url), fromApp = false, incognito = true, linkSource = linkSource)
    if (BuildConfig.DEBUG) {
      Handler(Looper.getMainLooper()).post {
        Toast.makeText(activity, "Incognito", Toast.LENGTH_SHORT).show()
      }
    }
  }

  /**
   * Performs the blacklist action which is opening the given url in user's secondary browser.
   */
  private fun doBlacklistAction(activity: Activity, safeIntent: SafeIntent) {
    // Perform a safe copy of this intent
    // A VIEW intent for the page, as a browser receives it. Without an action every activity of the
    // browser that takes the address matched, and Android showed a chooser of them.
    val intentCopy = Intent(Intent.ACTION_VIEW).apply {
      data = safeIntent.data
      addCategory(Intent.CATEGORY_BROWSABLE)
      safeIntent.unsafe.extras?.let {
        putExtras(it)
      }
    }
    val secondaryBrowser = preferences.secondaryBrowserPackage()
    if (secondaryBrowser == null) {
      showSecondaryBrowserHandlingError(
        activity,
        activity.getText(R.string.secondary_browser_not_error)
      )
      return
    }
    if (activity.packageManager.isPackageInstalled(secondaryBrowser)) {
      intentCopy.`package` = secondaryBrowser
      // When the browser has exactly one activity for it, name that activity.
      activity.packageManager.queryIntentActivities(intentCopy, 0).singleOrNull()?.activityInfo?.let {
        intentCopy.setClassName(it.packageName, it.name)
      }
      try {
        activity.startActivity(intentCopy)
        if (BuildConfig.DEBUG) {
          Toast.makeText(activity, "Blacklisted", Toast.LENGTH_SHORT).show()
        }
      } catch (e: Exception) {
        showSecondaryBrowserHandlingError(
          activity,
          activity.getText(R.string.secondary_browser_launch_error)
        )
      }
    } else {
      showSecondaryBrowserHandlingError(
        activity,
        activity.getText(R.string.secondary_browser_not_installed)
      )
    }
  }


  /**
   * Shows the error dialog for the blacklist errors over [activity], which stays open until the
   * dialog closes. Runs on the main thread (the blacklist action is called there).
   */
  private fun showSecondaryBrowserHandlingError(activity: Activity, message: CharSequence) {
    showSecondaryBrowserError(activity, message)
  }
}
