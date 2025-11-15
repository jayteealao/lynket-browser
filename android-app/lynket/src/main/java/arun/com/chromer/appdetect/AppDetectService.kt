/*
 * Phase 7: Converted from Java to Kotlin
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

package arun.com.chromer.appdetect

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import androidx.core.content.ContextCompat
import arun.com.chromer.R
import arun.com.chromer.di.service.ServiceComponent
import arun.com.chromer.shared.Constants.EXTRA_KEY_CLEAR_LAST_TOP_APP
import arun.com.chromer.shared.base.service.BaseService
import arun.com.chromer.util.Utils
import timber.log.Timber
import java.util.TreeMap
import javax.inject.Inject

class AppDetectService : BaseService() {

    @Inject
    lateinit var appDetectionManager: AppDetectionManager

    // Needed to turn off polling when screen is turned off.
    private var screenStateReceiver: BroadcastReceiver? = null

    // Flag to control polling.
    private var stopPolling = false

    // Detector to get current foreground app.
    private var appDetector: AppDetector = AppDetector { "" }

    // Handler to run our polling.
    private val detectorHandler = Handler()

    // The runnable which runs out detector.
    private val appDetectorRunnable = object : Runnable {
        override fun run() {
            try {
                val packageName = appDetector.getForegroundPackage()
                appDetectionManager.logPackage(packageName)
            } catch (e: Exception) {
                Timber.e(e.toString())
            }
            if (!stopPolling) {
                detectorHandler.postDelayed(this, POLLING_INTERVAL.toLong())
            }
        }
    }

    private fun clearLastAppIfNeeded(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_KEY_CLEAR_LAST_TOP_APP, false) == true) {
            appDetectionManager.clear()
            Timber.d("Last app cleared")
        }
    }

    override fun onCreate() {
        initChannels()
        super.onCreate()

        if (Utils.ANDROID_OREO) {
            startForeground(1, NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chromer_notification)
                .setPriority(PRIORITY_MIN)
                .setContentText(getString(R.string.app_detection_service_explanation))
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.app_detection_service_explanation))
                    .setBigContentTitle(getString(R.string.app_detection_service)))
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setContentTitle(getString(R.string.app_detection_service))
                .setAutoCancel(false)
                .setLocalOnly(true)
                .build())
        }

        if (!Utils.canReadUsageStats(this)) {
            Timber.e("Attempted to poll without usage permission")
            stopSelf()
        }

        registerScreenReceiver()
        appDetector = if (Utils.isLollipopAbove()) {
            LollipopDetector()
        } else {
            PreLollipopDetector()
        }
    }

    private fun initChannels() {
        if (Utils.ANDROID_OREO) {
            val channel = NotificationChannel(CHANNEL_ID, "App Detection Service", NotificationManager.IMPORTANCE_MIN)
            channel.description = getString(R.string.app_detection_notification_channel_description)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun inject(serviceComponent: ServiceComponent) {
        serviceComponent.inject(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        clearLastAppIfNeeded(intent)
        startDetection()
        Timber.d("Started")
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopDetection()
        try {
            screenStateReceiver?.let { unregisterReceiver(it) }
        } catch (e: IllegalStateException) {
            Timber.e(e)
        }
        appDetectionManager.clear()
        Timber.d("Destroying")
        super.onDestroy()
    }

    private fun startDetection() {
        stopPolling = false
        kickStartDetection()
    }

    private fun kickStartDetection() {
        Timber.d("Kick starting polling")
        detectorHandler.post(appDetectorRunnable)
    }

    private fun stopDetection() {
        stopPolling = true
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        screenStateReceiver = ScreenStateReceiver()
        registerReceiver(screenStateReceiver, filter)
    }

    private fun interface AppDetector {
        fun getForegroundPackage(): String
    }

    private inner class ScreenStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    stopDetection()
                    Timber.d("Turned off polling")
                }
                Intent.ACTION_SCREEN_ON -> {
                    startDetection()
                    Timber.d("Turned on polling")
                }
            }
        }
    }

    private inner class PreLollipopDetector : AppDetector {
        override fun getForegroundPackage(): String {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val runningTaskInfo = am.getRunningTasks(1)[0]
            return runningTaskInfo?.topActivity?.packageName ?: ""
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private inner class LollipopDetector : AppDetector {
        override fun getForegroundPackage(): String {
            val time = System.currentTimeMillis()
            val usageMan = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = usageMan.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 3, time)

            val sortedMap = TreeMap<Long, android.app.usage.UsageStats>()
            for (usageStats in stats) {
                usageStats?.let {
                    sortedMap[it.lastTimeUsed] = it
                }
            }
            if (sortedMap.isNotEmpty()) {
                val usageStats = sortedMap[sortedMap.lastKey()]
                return usageStats?.packageName ?: ""
            }
            sortedMap.clear()
            return ""
        }
    }

    companion object {
        // Gap at which we polling the system for current foreground app.
        private const val POLLING_INTERVAL = 400
        private const val CHANNEL_ID = "App detection service"
    }
}
