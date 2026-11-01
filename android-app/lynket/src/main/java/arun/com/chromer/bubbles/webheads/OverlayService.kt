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

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.annotation.IntRange
import androidx.core.app.ServiceCompat
import arun.com.chromer.bubbles.webheads.overlay.OverlayLifecycleOwner
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants.EXTRA_KEY_INCOGNITO
import arun.com.chromer.shared.base.service.BaseService
import arun.com.chromer.util.Utils
import timber.log.Timber
import javax.inject.Inject

/**
 * A foreground service that draws over other apps. It owns the lifecycle, saved state and view
 * model store of its Compose overlay windows, and stops when the overlay permission goes away.
 *
 * A subclass asks [checkForOverlayPermission] first in `onStartCommand`, with the start's intent,
 * so a link that arrives after the permission went away still opens in a tab.
 */
abstract class OverlayService : BaseService() {
  @IntRange(from = 1)
  abstract fun getNotificationId(): Int

  abstract fun getNotification(): Notification

  abstract override fun onBind(intent: Intent): IBinder?

  @Inject
  lateinit var overlayRefusal: OverlayRefusal

  /** Owner of every Compose view this service puts in a window. */
  protected val overlayOwner = OverlayLifecycleOwner()

  /** False when the system refused to run this service in the foreground. */
  protected var foregroundStarted = false
    private set

  private val mainHandler = Handler(Looper.getMainLooper())
  private var appOpsWatcher: AppOpsManager.OnOpChangedListener? = null

  override fun onCreate() {
    super.onCreate()
    overlayOwner.create()
    foregroundStarted = startInForeground()
    watchOverlayPermission()
  }

  override fun onDestroy() {
    appOpsWatcher?.let {
      (getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager).stopWatchingMode(it)
    }
    appOpsWatcher = null
    overlayOwner.destroy()
    super.onDestroy()
  }

  /**
   * Starts the foreground state as a special-use service. Android 12 and later refuse a start
   * from the background, and Android 15 and later refuse it for an overlay app with no visible
   * overlay window; the refusal is logged and the caller falls back.
   */
  private fun startInForeground(): Boolean = try {
    ServiceCompat.startForeground(
      this,
      getNotificationId(),
      getNotification(),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
    )
    true
  } catch (e: IllegalStateException) {
    // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException.
    Timber.e(e, "Web heads could not start in the foreground")
    false
  } catch (e: IllegalArgumentException) {
    // InvalidForegroundServiceTypeException (API 34+) is an IllegalArgumentException.
    Timber.e(e, "Web heads could not start in the foreground")
    false
  } catch (e: SecurityException) {
    Timber.e(e, "Web heads could not start in the foreground")
    false
  }

  /** When the overlay permission is revoked while heads show, the windows go and the service stops. */
  private fun watchOverlayPermission() {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val watcher = AppOpsManager.OnOpChangedListener { op, packageName ->
      if (packageName == this.packageName) {
        mainHandler.post {
          if (!Settings.canDrawOverlays(this)) {
            Timber.d("Overlay permission revoked: %s", op)
            onOverlayPermissionRevoked()
          }
        }
      }
    }
    appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, watcher)
    appOpsWatcher = watcher
  }

  /** Removes every overlay window and stops. */
  protected open fun onOverlayPermissionRevoked() {
    stopService()
  }

  protected fun stopService() {
    Timber.d("Stopping service.")
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(getNotificationId())
    stopSelf()
  }

  protected fun updateNotification(notification: Notification) {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(getNotificationId(), notification)
  }

  /**
   * True when the overlay permission is on and the start may go on. Otherwise the link of [intent],
   * if it has one, opens in a tab with a message that names the setting, the service stops, and
   * this returns false. It never opens the overlay settings page: only a tap on Open Settings does.
   */
  protected fun checkForOverlayPermission(intent: Intent?): Boolean {
    if (Utils.isOverlayGranted(this)) return true
    intent?.dataString?.let { url ->
      overlayRefusal.refuse(this, Website(url), intent.getBooleanExtra(EXTRA_KEY_INCOGNITO, false))
    }
    Timber.d("Overlay permission is off; stopping the overlay service")
    stopService()
    return false
  }
}
