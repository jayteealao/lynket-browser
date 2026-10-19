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

package arun.com.chromer.bubbles.webheads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import arun.com.chromer.R
import arun.com.chromer.browsing.article.ArticlePreloader
import arun.com.chromer.browsing.customtabs.CustomTabManager
import arun.com.chromer.browsing.newtab.NewTabDialogActivity
import arun.com.chromer.bubbles.webheads.motion.WebHeadHost
import arun.com.chromer.bubbles.webheads.motion.WebHeadMotion
import arun.com.chromer.bubbles.webheads.motion.WebHeadState
import arun.com.chromer.bubbles.webheads.motion.WebHeadsController
import arun.com.chromer.bubbles.webheads.overlay.OverlayWindow
import arun.com.chromer.bubbles.webheads.ui.TrashTarget
import arun.com.chromer.bubbles.webheads.ui.WebHeadBubble
import arun.com.chromer.bubbles.webheads.ui.WebHeadGestures
import arun.com.chromer.bubbles.webheads.ui.context.WebHeadContextActivity
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants
import arun.com.chromer.shared.Constants.ACTION_OPEN_CONTEXT_ACTIVITY
import arun.com.chromer.shared.Constants.ACTION_OPEN_NEW_TAB
import arun.com.chromer.shared.Constants.ACTION_STOP_WEBHEAD_SERVICE
import arun.com.chromer.shared.Constants.EXTRA_KEY_FROM_AMP
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.shared.Constants.EXTRA_KEY_MINIMIZE
import arun.com.chromer.shared.Constants.EXTRA_KEY_WEBSITE
import arun.com.chromer.shared.Constants.NO_COLOR
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.ui.theme.ChromerThemeFromSettings
import arun.com.chromer.util.Utils
import arun.com.chromer.util.events.Event
import arun.com.chromer.util.events.EventBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Floating web heads. Each head and the trash is a small overlay window with Compose content;
 * [WebHeadsController] moves them with the same springs as the earlier View web heads.
 */
@AndroidEntryPoint
class WebHeadService : OverlayService(), CustomTabManager.ConnectionCallback, WebHeadHost, WebHeadGestures {

  @Inject
  lateinit var websiteRepository: WebsiteRepository

  @Inject
  lateinit var tabsManager: TabsManager

  @Inject
  lateinit var articlePreloader: ArticlePreloader

  @Inject
  lateinit var eventBus: EventBus

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var motionScope: CoroutineScope? = null

  private val headWindows = HashMap<String, OverlayWindow>()
  private var trashWindow: OverlayWindow? = null

  @VisibleForTesting
  internal var controller: WebHeadsController? = null
    private set

  /** Every overlay window this service shows now (heads, then the trash). */
  @VisibleForTesting
  internal val overlayWindows: List<OverlayWindow>
    get() = headWindows.values.toList() + listOfNotNull(trashWindow)

  private val notificationActionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        ACTION_STOP_WEBHEAD_SERVICE -> stopService()
        ACTION_OPEN_CONTEXT_ACTIVITY -> openContextActivity()
        ACTION_OPEN_NEW_TAB -> {
          val newTabIntent = Intent(context, NewTabDialogActivity::class.java)
          newTabIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
          context.startActivity(newTabIntent)
        }
      }
    }
  }

  private var customTabConnected = false

  override fun onBind(intent: Intent): IBinder? = null

  override fun getNotificationId(): Int = 1

  override fun getNotification(): Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        WebHeadService::class.java.name,
        getString(R.string.web_heads_service),
        NotificationManager.IMPORTANCE_MIN
      )
      channel.description = getString(R.string.web_heads_channel_description)
      (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)?.createNotificationChannel(channel)
    }

    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      flags = flags or PendingIntent.FLAG_IMMUTABLE
    }
    // Package-scoped, so only this app's not-exported receiver gets them.
    val contentIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_STOP_WEBHEAD_SERVICE).setPackage(packageName), flags)
    val contextActivity = PendingIntent.getBroadcast(this, 0, Intent(ACTION_OPEN_CONTEXT_ACTIVITY).setPackage(packageName), flags)
    val newTab = PendingIntent.getBroadcast(this, 0, Intent(ACTION_OPEN_NEW_TAB).setPackage(packageName), flags)

    val notification = NotificationCompat.Builder(this, WebHeadService::class.java.name)
      .setSmallIcon(R.drawable.ic_chromer_notification)
      .setPriority(NotificationCompat.PRIORITY_MIN)
      .setContentText(getString(R.string.tap_close_all))
      .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
      .addAction(R.drawable.ic_add, getText(R.string.open_new_tab), newTab)
      .addAction(R.drawable.ic_list, getText(R.string.manage), contextActivity)
      .setContentTitle(getString(R.string.web_heads_service))
      .setContentIntent(contentIntent)
      .setAutoCancel(false)
      .setLocalOnly(true)
      .build()

    notification.flags = notification.flags or Notification.FLAG_FOREGROUND_SERVICE
    return notification
  }

  override fun onCreate() {
    super.onCreate()
    if (!Settings.canDrawOverlays(this) || !foregroundStarted) {
      // onStartCommand opens the link in a tab when the foreground start was refused.
      return
    }
    createController()
    bindToCustomTabSession()
    collectEvents()
    registerReceivers()
  }

  /**
   * Handles close requests from the web head list and colour changes from settings until the
   * service stops. The collector subscribes before [onCreate] returns, so an event sent right after
   * the service starts is not lost.
   */
  private fun collectEvents() {
    serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
      eventBus.observe<Event.WebHeadEvent>().collect { event ->
        when (event) {
          is Event.WebHeadEvent.CloseRequested -> controller?.close(event.url)
          is Event.WebHeadEvent.ColourChanged ->
            if (event.colour != NO_COLOR) controller?.all?.forEach { it.colour = Color(event.colour) }
          else -> Unit
        }
      }
    }
  }

  private fun createController() {
    val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val scope = CoroutineScope(
      SupervisorJob() + AndroidUiDispatcher.Main + object : MotionDurationScale {
        // "Remove animations" settles heads at once.
        override val scaleFactor: Float = scale
      }
    )
    motionScope = scope
    val metrics = resources.displayMetrics
    val density = metrics.density
    val viewConfiguration = ViewConfiguration.get(this)
    val headDp = headSizeDp()
    val controller = WebHeadsController(
      host = this,
      scope = scope,
      dispWidth = metrics.widthPixels,
      dispHeight = metrics.heightPixels,
      density = density,
      screenWidthDp = resources.configuration.screenWidthDp,
      headWindowPx = ((headDp + WebHeadMotion.HEAD_WINDOW_INSET_DP * 2) * density + 0.5f).toInt(),
      trashWindowPx = (WebHeadMotion.TRASH_WINDOW_DP * density + 0.5f).toInt(),
      touchSlop = viewConfiguration.scaledTouchSlop.toFloat(),
      minFlingVelocity = viewConfiguration.scaledMinimumFlingVelocity.toFloat(),
      spawnOnRight = Preferences.get(this).webHeadsSpawnLocation() == 1,
      closeOnOpen = { Preferences.get(this).webHeadsCloseOnOpen() },
    )
    this.controller = controller
    trashWindow = OverlayWindow(this, overlayOwner, touchable = false, controller.trashX, controller.trashY) {
      ChromerThemeFromSettings { TrashTarget(controller.trash) }
    }.also { it.add() }
  }

  private fun headSizeDp(): Float =
    if (Preferences.get(this).webHeadsSize() == 2) WebHeadMotion.SMALL_HEAD_DP else WebHeadMotion.HEAD_DP

  override fun onDestroy() {
    Timber.d("Exiting webhead service")
    serviceScope.cancel()
    removeOverlay()
    customTabManager?.unbindCustomTabsService(this)
    unregisterReceivers()
    super.onDestroy()
  }

  private fun removeOverlay() {
    controller?.removeAll()
    headWindows.values.forEach { it.remove() }
    headWindows.clear()
    trashWindow?.remove()
    trashWindow = null
    motionScope?.cancel()
    motionScope = null
    controller = null
  }

  override fun onOverlayPermissionRevoked() {
    removeOverlay()
    super.onOverlayPermissionRevoked()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    val metrics = resources.displayMetrics
    controller?.updateDisplay(metrics.widthPixels, metrics.heightPixels, newConfig.screenWidthDp)
    controller?.let { trashWindow?.move(it.trashX, it.trashY) }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!foregroundStarted) {
      openInTabInstead(intent)
      stopSelf()
      return START_NOT_STICKY
    }
    checkForOverlayPermission()
    processIntent(intent)
    return START_STICKY
  }

  /** The system refused the foreground start: the link opens in a browsing tab with a message. */
  private fun openInTabInstead(intent: Intent?) {
    val url = intent?.dataString ?: return
    Timber.e("Web heads could not start; opening %s in a tab", url)
    Toast.makeText(this, R.string.web_heads_start_failed, Toast.LENGTH_LONG).show()
    tabsManager.openBrowsingTab(
      this,
      Website(url),
      smart = true,
      fromNewTab = false,
      incognito = intent.getBooleanExtra(EXTRA_KEY_INCOGNITO, false),
    )
  }

  private fun processIntent(intent: Intent?) {
    if (intent == null || intent.dataString == null) return

    val isForMinimized = intent.getBooleanExtra(EXTRA_KEY_MINIMIZE, false)
    val isFromAmp = intent.getBooleanExtra(EXTRA_KEY_FROM_AMP, false)
    val isIncognito = intent.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)

    val urlToLoad = intent.dataString.orEmpty()
    if (TextUtils.isEmpty(urlToLoad)) {
      Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show()
      return
    }
    val controller = controller ?: return
    if (urlToLoad !in controller) {
      addWebHead(controller, urlToLoad, isFromAmp, isIncognito)
    } else if (!isForMinimized) {
      Toast.makeText(this, R.string.already_loaded, Toast.LENGTH_SHORT).show()
    }
  }

  private fun addWebHead(controller: WebHeadsController, url: String, isFromAmp: Boolean, isIncognito: Boolean) {
    val head = controller.add(url, isFromAmp, isIncognito)
    val userColour = Preferences.get(this).webHeadColor()
    if (userColour != ContextCompat.getColor(this, R.color.web_head_color)) {
      head.colour = Color(userColour)
    }
    preLoadForArticle(url)
    doExtraction(url, isIncognito)
  }

  // region Windows

  override fun addHeadWindow(head: WebHeadState, x: Int, y: Int) {
    val size = headSizeDp().dp
    headWindows[head.url] = OverlayWindow(this, overlayOwner, touchable = true, x, y) {
      ChromerThemeFromSettings { WebHeadBubble(head, size, this) }
    }.also { it.add() }
  }

  override fun moveHeadWindow(head: WebHeadState, x: Int, y: Int) {
    headWindows[head.url]?.move(x, y)
  }

  override fun setHeadWindowVisible(head: WebHeadState, visible: Boolean) {
    headWindows[head.url]?.visible = visible
  }

  override fun removeHeadWindow(head: WebHeadState) {
    headWindows.remove(head.url)?.remove()
  }

  // endregion

  // region Gestures

  override fun onDown(head: WebHeadState, raw: Offset) {
    controller?.onTouchDown(head, raw.x, raw.y)
  }

  override fun onMove(head: WebHeadState, raw: Offset) {
    controller?.onTouchMove(head, raw.x, raw.y)
  }

  override fun onUp(head: WebHeadState, velocity: Offset, raw: Offset, tap: Boolean) {
    val controller = controller ?: return
    controller.onTouchUp(head, velocity.x, velocity.y, raw.x, raw.y)
    if (tap) controller.onTap(head)
  }

  override fun onLongPress(head: WebHeadState) {
    controller?.onLongPress(head)
  }

  override fun onOpen(head: WebHeadState) {
    controller?.onTap(head)
  }

  override fun onClose(head: WebHeadState) {
    controller?.destroy(head, receiveCallback = true)
  }

  override fun onHeadClick(head: WebHeadState) {
    tabsManager.openUrl(this, head.website, true, true, false, head.fromAmp, head.incognito)
    if (Preferences.get(this).webHeadsCloseOnOpen()) {
      controller?.destroy(head, receiveCallback = true)
    }
    controller?.hideTrash()
  }

  override fun onHeadsLongPress() {
    openContextActivity()
  }

  override fun onHeadDestroyed(head: WebHeadState, isLastHead: Boolean) {
    if (isLastHead) {
      controller?.exitTrash { stopService() } ?: stopService()
    } else if (!Preferences.get(this).articleMode()) {
      preLoadUrl("")
    }
    eventBus.tryEmit(Event.WebHeadEvent.Closed(head.website.url))
  }

  // endregion

  private fun doExtraction(webHeadUrl: String, isIncognito: Boolean) {
    val websiteFlow = if (!isIncognito) {
      websiteRepository.getWebsite(webHeadUrl)
    } else {
      websiteRepository.getWebsiteReadOnly(webHeadUrl)
    }
    serviceScope.launch {
      try {
        websiteFlow
          .filter { it != null }
          .flowOn(Dispatchers.IO)
          // The heads and their map belong to the main thread.
          .onEach { website -> withContext(Dispatchers.Main) { onSiteLoaded(webHeadUrl, website) } }
          .map { website -> websiteRepository.getWebsiteRoundIconAndColor(website) }
          .flowOn(Dispatchers.IO)
          .catch { error -> Timber.e(error) }
          .collect { iconAndColor ->
            controller?.get(webHeadUrl)?.let { head ->
              // A site without an icon gives no drawable; the head keeps the first letter.
              iconAndColor.first?.let { head.favicon = it.toBitmap().asImageBitmap() }
              if (iconAndColor.second != Constants.NO_COLOR) head.colour = Color(iconAndColor.second)
            }
          }
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
  }

  /** The details of the site of [webHeadUrl] arrived: its head and the web head list take them. */
  @VisibleForTesting
  internal fun onSiteLoaded(webHeadUrl: String, website: Website) {
    controller?.get(webHeadUrl)?.let { head ->
      head.website = website
      warmUp(head)
      eventBus.tryEmit(Event.WebHeadEvent.WebsiteUpdated(website))
    }
  }

  private fun bindToCustomTabSession() {
    customTabManager?.let {
      Timber.d("Severing existing connection")
      it.unbindCustomTabsService(this)
    }
    customTabManager = CustomTabManager()
    customTabManager?.setConnectionCallback(this)
    customTabManager?.setNavigationCallback(WebHeadNavigationCallback())
    if (customTabManager?.bindCustomTabsService(this) == true) {
      Timber.d("Binding successful")
    }
  }

  private fun warmUp(head: WebHeadState) {
    if (!Preferences.get(this).aggressiveLoading()) {
      val url = head.website.preferredUrl()
      if (customTabConnected) preLoadUrl(url) else deferPreload(url)
    }
  }

  private fun preLoadUrl(url: String) {
    if (!Preferences.get(this).articleMode()) {
      customTabManager?.mayLaunchUrl(Uri.parse(url))
    }
  }

  private fun preLoadForArticle(url: String) {
    if (Preferences.get(this).articleMode()) {
      articlePreloader.preloadArticle(Uri.parse(url)) { success ->
        Timber.d("Url %s preloaded, result: %b", url, success)
      }
    }
  }

  /** Preloads [urlToLoad] after a short wait; the wait ends with the service, which cancels [serviceScope]. */
  @VisibleForTesting
  internal fun deferPreload(urlToLoad: String): Job = serviceScope.launch {
    delay(PRELOAD_DELAY_MS)
    preLoadUrl(urlToLoad)
  }

  private fun openContextActivity() {
    val websites = ArrayList(controller?.all.orEmpty().asReversed().map { it.website })
    ContextActivityHelper.open(this, websites)
  }

  override fun onCustomTabsConnected() {
    customTabConnected = true
    Timber.d("Connected to custom tabs successfully")
  }

  override fun onCustomTabsDisconnected() {
    customTabConnected = false
  }

  private fun registerReceivers() {
    val notificationFilter = IntentFilter()
    notificationFilter.addAction(ACTION_STOP_WEBHEAD_SERVICE)
    notificationFilter.addAction(ACTION_OPEN_CONTEXT_ACTIVITY)
    notificationFilter.addAction(ACTION_OPEN_NEW_TAB)
    ContextCompat.registerReceiver(
      this,
      notificationActionReceiver,
      notificationFilter,
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
  }

  private fun unregisterReceivers() {
    try {
      unregisterReceiver(notificationActionReceiver)
    } catch (ignored: IllegalArgumentException) {
      Timber.e(ignored)
    }
  }

  private object ContextActivityHelper {
    fun open(context: Context, websites: ArrayList<Website>) {
      val intent = Intent(context, WebHeadContextActivity::class.java)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
      intent.putParcelableArrayListExtra(EXTRA_KEY_WEBSITE, websites)
      context.startActivity(intent)
    }
  }

  private class WebHeadNavigationCallback : CustomTabManager.NavigationCallback() {
    override fun onNavigationEvent(navigationEvent: Int, extras: Bundle?) {
      when (navigationEvent) {
        TAB_SHOWN -> {}
        TAB_HIDDEN -> {}
      }
    }
  }

  companion object {
    const val MAX_VISIBLE_WEB_HEADS = WebHeadMotion.MAX_VISIBLE_WEB_HEADS
    private const val PRELOAD_DELAY_MS = 300L
    private var customTabManager: CustomTabManager? = null

    @JvmStatic
    fun getTabSession(): CustomTabsSession? {
      return customTabManager?.getSession()
    }
  }
}
