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

// Phase 7: Converted from Java to Kotlin
// Phase 8.7: Converted from RxJava to Kotlin Flows/Coroutines

package arun.com.chromer.bubbles.webheads

import android.animation.Animator
import android.animation.AnimatorSet
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import arun.com.chromer.R
import arun.com.chromer.browsing.article.ArticlePreloader
import arun.com.chromer.browsing.customtabs.CustomTabManager
import arun.com.chromer.browsing.newtab.NewTabDialogActivity
import arun.com.chromer.bubbles.webheads.physics.SpringChain2D
import arun.com.chromer.bubbles.webheads.ui.WebHeadContract
import arun.com.chromer.bubbles.webheads.ui.context.WebHeadContextActivity
import arun.com.chromer.bubbles.webheads.ui.views.Trashy
import arun.com.chromer.bubbles.webheads.ui.views.WebHead
import arun.com.chromer.data.website.WebsiteRepository
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.di.service.ServiceComponent
import arun.com.chromer.settings.Preferences
import arun.com.chromer.shared.Constants
import arun.com.chromer.shared.Constants.ACTION_CLOSE_WEBHEAD_BY_URL
import arun.com.chromer.shared.Constants.ACTION_EVENT_WEBHEAD_DELETED
import arun.com.chromer.shared.Constants.ACTION_EVENT_WEBSITE_UPDATED
import arun.com.chromer.shared.Constants.ACTION_OPEN_CONTEXT_ACTIVITY
import arun.com.chromer.shared.Constants.ACTION_OPEN_NEW_TAB
import arun.com.chromer.shared.Constants.ACTION_REBIND_WEBHEAD_TAB_CONNECTION
import arun.com.chromer.shared.Constants.ACTION_STOP_WEBHEAD_SERVICE
import arun.com.chromer.shared.Constants.ACTION_WEBHEAD_COLOR_SET
import arun.com.chromer.shared.Constants.EXTRA_KEY_FROM_AMP
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.shared.Constants.EXTRA_KEY_MINIMIZE
import arun.com.chromer.shared.Constants.EXTRA_KEY_REBIND_WEBHEAD_CXN
import arun.com.chromer.shared.Constants.EXTRA_KEY_WEBHEAD_COLOR
import arun.com.chromer.shared.Constants.EXTRA_KEY_WEBSITE
import arun.com.chromer.shared.Constants.NO_COLOR
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.util.Utils
import com.facebook.rebound.Spring
import com.facebook.rebound.SpringConfig
import com.facebook.rebound.SpringSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import timber.log.Timber
import java.util.LinkedList
import javax.inject.Inject

class WebHeadService : OverlayService(), WebHeadContract, CustomTabManager.ConnectionCallback {

    @Inject
    lateinit var websiteRepository: WebsiteRepository

    @Inject
    lateinit var tabsManager: TabsManager

    @Inject
    lateinit var articlePreloader: ArticlePreloader

    private val webHeads = LinkedHashMap<String, WebHead>()
    private val springSystem = SpringSystem.create()

    // Service-scoped coroutine scope for lifecycle-aware operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val localReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_REBIND_WEBHEAD_TAB_CONNECTION -> {
                    val shouldRebind = intent.getBooleanExtra(EXTRA_KEY_REBIND_WEBHEAD_CXN, false)
                    if (shouldRebind) {
                        bindToCustomTabSession()
                    }
                }
                ACTION_WEBHEAD_COLOR_SET -> {
                    val webHeadColor = intent.getIntExtra(EXTRA_KEY_WEBHEAD_COLOR, NO_COLOR)
                    if (webHeadColor != NO_COLOR) {
                        updateWebHeadColors(webHeadColor)
                    }
                }
                ACTION_CLOSE_WEBHEAD_BY_URL -> {
                    val website = intent.getParcelableExtra<Website>(EXTRA_KEY_WEBSITE)
                    website?.let { closeWebHeadByUrl(it.url) }
                }
            }
        }
    }

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

    private var springChain2D: SpringChain2D? = null
    private var customTabConnected = false

    override fun onBind(intent: Intent): IBinder? = null

    override fun getNotificationId(): Int = 1

    override fun getNotification(): Notification {
        if (Utils.ANDROID_OREO) {
            val channel = NotificationChannel(
                WebHeadService::class.java.name,
                getString(R.string.web_heads_service),
                NotificationManager.IMPORTANCE_MIN
            )
            channel.description = getString(R.string.app_detection_notification_channel_description)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.createNotificationChannel(channel)
        }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }

        val contentIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_STOP_WEBHEAD_SERVICE), flags)
        val contextActivity = PendingIntent.getBroadcast(this, 0, Intent(ACTION_OPEN_CONTEXT_ACTIVITY), flags)
        val newTab = PendingIntent.getBroadcast(this, 0, Intent(ACTION_OPEN_NEW_TAB), flags)

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                stopService()
                return
            }
        }
        springChain2D = SpringChain2D.create(this)
        Trashy.init(this)
        bindToCustomTabSession()
        registerReceivers()
    }

    override fun inject(serviceComponent: ServiceComponent) {
        serviceComponent.inject(this)
    }

    override fun onDestroy() {
        Timber.d("Exiting webhead service")
        serviceScope.cancel() // Cancel all running coroutines
        WebHead.clearMasterPosition()
        removeWebHeads()
        customTabManager?.unbindCustomTabsService(this)
        Trashy.destroy()
        unregisterReceivers()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkForOverlayPermission()
        processIntent(intent)
        return START_STICKY
    }

    private fun processIntent(intent: Intent?) {
        if (intent == null || intent.dataString == null) return

        val isForMinimized = intent.getBooleanExtra(EXTRA_KEY_MINIMIZE, false)
        val isFromAmp = intent.getBooleanExtra(EXTRA_KEY_FROM_AMP, false)
        val isIncognito = intent.getBooleanExtra(EXTRA_KEY_INCOGNITO, false)

        val urlToLoad = intent.dataString
        if (TextUtils.isEmpty(urlToLoad)) {
            Toast.makeText(this, R.string.invalid_link, Toast.LENGTH_SHORT).show()
            return
        }

        if (!isLinkAlreadyLoaded(urlToLoad)) {
            addWebHead(urlToLoad!!, isFromAmp, isIncognito)
        } else if (!isForMinimized) {
            Toast.makeText(this, R.string.already_loaded, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLinkAlreadyLoaded(urlToLoad: String?): Boolean {
        return urlToLoad == null || webHeads.containsKey(urlToLoad)
    }

    private fun addWebHead(webHeadUrl: String, isFromAmp: Boolean, isIncognito: Boolean) {
        if (springChain2D == null) {
            springChain2D = SpringChain2D.create(this)
        }
        springChain2D?.clear()

        val newWebHead = WebHead(this, webHeadUrl, this)
        for (oldWebHead in webHeads.values) {
            oldWebHead.setMaster(false)
        }
        newWebHead.setMaster(true)
        newWebHead.setFromAmp(isFromAmp)
        newWebHead.setIncognito(isIncognito)

        webHeads[webHeadUrl] = newWebHead

        reveal(newWebHead)
        preLoadForArticle(webHeadUrl)
        doExtraction(webHeadUrl, isIncognito)
    }

    private fun reveal(newWebHead: WebHead): Boolean {
        return newWebHead.post {
            newWebHead.reveal {
                updateSpringChain()
                onMasterWebHeadMoved(newWebHead.windowParams.x, newWebHead.windowParams.y)
            }
        }
    }

    private fun doExtraction(webHeadUrl: String, isIncognito: Boolean) {
        val websiteObservable = if (!isIncognito) {
            websiteRepository.getWebsite(webHeadUrl)
        } else {
            websiteRepository.getWebsiteReadOnly(webHeadUrl)
        }

        // Convert RxJava Observable to Flow and collect in coroutine
        serviceScope.launch {
            try {
                kotlinx.coroutines.rx2.asFlow(websiteObservable)
                    .filter { it != null }
                    .flowOn(Dispatchers.IO)
                    .onEach { website ->
                        val webHead = webHeads[webHeadUrl]
                        if (webHead != null) {
                            warmUp(webHead)
                            webHead.setWebsite(website)
                            ContextActivityHelper.signalUpdated(application, webHead.getWebsite())
                        }
                    }
                    .map { website -> websiteRepository.getWebsiteRoundIconAndColor(website) }
                    .flowOn(Dispatchers.IO)
                    .catch { error ->
                        Timber.e(error)
                    }
                    .collect { faviconColor ->
                        val webHead = webHeads[webHeadUrl]
                        if (webHead != null) {
                            faviconColor.first?.let { webHead.setFaviconDrawable(it) }
                            if (faviconColor.second != Constants.NO_COLOR) {
                                webHead.setWebHeadColor(faviconColor.second)
                            }
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e)
            }
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

    private fun warmUp(webHead: WebHead) {
        if (!Preferences.get(this).aggressiveLoading()) {
            if (customTabConnected) {
                preLoadUrl(webHead.getUnShortenedUrl())
            } else {
                deferPreload(webHead.getUnShortenedUrl())
            }
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

    private fun deferPreload(urlToLoad: String) {
        Handler().postDelayed({ preLoadUrl(urlToLoad) }, 300)
    }

    private fun removeWebHeads() {
        for (webhead in webHeads.values) {
            webhead?.destroySelf(false)
        }
        webHeads.clear()
        springChain2D?.clear()
        Timber.d("WebHeads: %d", webHeads.size)
    }

    private fun shouldQueue(index: Int): Boolean {
        return index > MAX_VISIBLE_WEB_HEADS
    }

    private fun updateWebHeadColors(@ColorInt webHeadColor: Int) {
        val animatorSet = AnimatorSet()
        val animators = LinkedList<Animator>()
        for (webhead in webHeads.values) {
            animators.add(webhead.getRevealAnimator(webHeadColor))
        }
        animatorSet.playTogether(animators)
        animatorSet.start()
    }

    private fun selectNextMaster() {
        val it = ArrayList(webHeads.keys).listIterator(webHeads.size)
        while (it.hasPrevious()) {
            val key = it.previous()
            val toBeMaster = webHeads[key]
            if (toBeMaster != null) {
                toBeMaster.setMaster(true)
                updateSpringChain()
                toBeMaster.goToMasterTouchDownPoint()
            }
            break
        }
    }

    private fun updateSpringChain() {
        springChain2D?.rest()
        springChain2D?.clear()
        springChain2D?.disableDisplacement()

        var springChainIndex = webHeads.values.size
        var index = webHeads.values.size

        for (webHead in webHeads.values) {
            if (webHead != null) {
                if (webHead.isMaster()) {
                    springChain2D?.setMasterSprings(webHead.getXSpring(), webHead.getYSpring())
                } else {
                    if (shouldQueue(index)) {
                        webHead.setInQueue(true)
                    } else {
                        webHead.setInQueue(false)
                        webHead.setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(90.0, (9 + springChainIndex * 5).toDouble()))
                        springChain2D?.addSlaveSprings(webHead.getXSpring(), webHead.getYSpring())
                    }
                    springChainIndex--
                }
                index--
            }
        }
        springChain2D?.enableDisplacement()
    }

    override fun onWebHeadClick(webHead: WebHead) {
        tabsManager.openUrl(this, webHead.getWebsite(), true, true, false, webHead.isFromAmp(), webHead.isIncognito())

        if (Preferences.get(this).webHeadsCloseOnOpen()) {
            webHead.destroySelf(true)
        }
        hideTrashy()
    }

    override fun onWebHeadDestroyed(webHead: WebHead, isLastWebHead: Boolean) {
        webHead.setMaster(false)
        webHeads.remove(webHead.getUrl())
        if (isLastWebHead) {
            Trashy.get(this).destroyAnimator { stopService() }
        } else {
            selectNextMaster()
            if (!Preferences.get(this).articleMode()) {
                preLoadUrl("")
            }
        }
        ContextActivityHelper.signalDeleted(this, webHead.getWebsite())
    }

    override fun onMasterWebHeadMoved(x: Int, y: Int) {
        springChain2D?.performGroupMove(x, y)
    }

    override fun newSpring(): Spring {
        return springSystem.createSpring()
    }

    override fun onMasterLockedToTrashy() {
        springChain2D?.disableDisplacement()
    }

    override fun onMasterReleasedFromTrashy() {
        springChain2D?.enableDisplacement()
    }

    override fun closeAll() {
        stopService()
    }

    override fun onMasterLongClick() {
        openContextActivity()
    }

    private fun openContextActivity() {
        val it = ArrayList(webHeads.keys).listIterator(webHeads.size)
        val websites = ArrayList<Website>()
        while (it.hasPrevious()) {
            val key = it.previous()
            val webHead = webHeads[key]
            if (webHead != null) {
                websites.add(webHead.getWebsite())
            }
        }
        ContextActivityHelper.open(this, websites)
    }

    override fun onCustomTabsConnected() {
        customTabConnected = true
        Timber.d("Connected to custom tabs successfully")
    }

    override fun onCustomTabsDisconnected() {
        customTabConnected = false
    }

    private fun closeWebHeadByUrl(url: String) {
        val webHead = webHeads[url]
        webHead?.destroySelf(true)
    }

    private fun hideTrashy() {
        Trashy.disappear()
    }

    private fun registerReceivers() {
        val localEvents = IntentFilter()
        localEvents.addAction(ACTION_WEBHEAD_COLOR_SET)
        localEvents.addAction(ACTION_REBIND_WEBHEAD_TAB_CONNECTION)
        localEvents.addAction(ACTION_CLOSE_WEBHEAD_BY_URL)
        localEvents.addAction(ACTION_OPEN_CONTEXT_ACTIVITY)
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver, localEvents)

        val notificationFilter = IntentFilter()
        notificationFilter.addAction(ACTION_STOP_WEBHEAD_SERVICE)
        notificationFilter.addAction(ACTION_OPEN_CONTEXT_ACTIVITY)
        notificationFilter.addAction(ACTION_OPEN_NEW_TAB)
        registerReceiver(notificationActionReceiver, notificationFilter)
    }

    private fun unregisterReceivers() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver)
            unregisterReceiver(notificationActionReceiver)
        } catch (ignored: IllegalArgumentException) {
            Timber.e(ignored)
        }
    }

    private object ContextActivityHelper {
        fun signalUpdated(context: Context, website: Website) {
            val intent = Intent(ACTION_EVENT_WEBSITE_UPDATED)
            intent.putExtra(EXTRA_KEY_WEBSITE, website)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

        fun signalDeleted(context: Context, website: Website) {
            val intent = Intent(ACTION_EVENT_WEBHEAD_DELETED)
            intent.putExtra(EXTRA_KEY_WEBSITE, website)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

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
        const val MAX_VISIBLE_WEB_HEADS = 5
        private var customTabManager: CustomTabManager? = null

        @JvmStatic
        fun getTabSession(): CustomTabsSession? {
            return customTabManager?.getSession()
        }
    }
}
