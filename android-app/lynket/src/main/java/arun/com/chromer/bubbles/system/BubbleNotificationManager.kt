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

package arun.com.chromer.bubbles.system

import android.app.*
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.LocusId
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import arun.com.chromer.MainActivity
import arun.com.chromer.R
import arun.com.chromer.browsing.webview.EmbeddableWebViewActivity
import arun.com.chromer.shared.Constants
import arun.com.chromer.util.Utils
import arun.com.chromer.util.image.WebsiteIconLoader
import dev.arunkumar.android.common.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

internal const val BUBBLE_NOTIFICATION_CHANNEL_ID = "BUBBLE_NOTIFICATION_CHANNEL_ID"
private const val BUBBLE_NOTIFICATION_GROUP = "bubbles"

@Singleton
@RequiresApi(Build.VERSION_CODES.Q)
class BubbleNotificationManager
@Inject
constructor(
  private val application: Application
) {

  /** Guards every post and removal, so the summary and the check for other bubbles agree. */
  private val postLock = Any()

  private val notificationManager by lazy {
    application.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  }

  private fun createNotificationChannel() {
    var channel = notificationManager.getNotificationChannel(BUBBLE_NOTIFICATION_CHANNEL_ID)
    if (channel == null) {
      channel = NotificationChannel(
        BUBBLE_NOTIFICATION_CHANNEL_ID,
        "Bubbles notification channel",
        IMPORTANCE_HIGH
      ).apply {
        description = "Channel for showing bubbles"
        setAllowBubbles(true)
        setSound(null, null)
        enableLights(false)
        enableVibration(false)
        setBypassDnd(true)
        importance = IMPORTANCE_HIGH
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun updateGroupSummaryNotification(lastNotificationColor: Int) {
    val bubblesSummaryNotification = Notification.Builder(
      application,
      BUBBLE_NOTIFICATION_CHANNEL_ID
    ).run {
      setContentTitle(application.getString(R.string.bubble_notification_group_title))
      setContentText(application.getString(R.string.bubble_notification_group_description))
      setGroup(BUBBLE_NOTIFICATION_GROUP)
      setGroupSummary(true)
      setAllowSystemGeneratedContextualActions(true)
      if (lastNotificationColor != Constants.NO_COLOR) {
        setColor(lastNotificationColor)
        setColorized(true)
      }
      setLocalOnly(true)
      setOngoing(false)
      setSmallIcon(Icon.createWithResource(application, R.drawable.ic_chromer_notification))
      setLargeIcon(Icon.createWithResource(application, R.mipmap.ic_launcher_round))
      privateOnLockScreen()
      build()
    }
    notificationManager.notify(BUBBLE_NOTIFICATION_GROUP.hashCode(), bubblesSummaryNotification)
  }

  /**
   * An update of a posted bubble passes withSummary = false: the summary is already there, and the
   * update is dropped when the bubble is no longer posted.
   */
  suspend fun showBubbles(bubbleData: BubbleLoadData, withSummary: Boolean = true): BubbleLoadData = try {
    // Posting a bubble and its summary, and removing a bubble and the summary it leaves alone, each
    // run whole: bubbles of different links post from different threads.
    withContext(Dispatchers.IO) { synchronized(postLock) { post(bubbleData, withSummary) } }
  } catch (e: Exception) {
    Timber.e(e)
    bubbleData
  }

  private fun post(bubbleData: BubbleLoadData, withSummary: Boolean): BubbleLoadData {
    val website = bubbleData.website
    val notificationId = bubbleNotificationId(website.url, bubbleData.incognito)
    // A bubble the person dismissed while its icon loaded stays dismissed: an update of it would
    // post it, and its shortcut, again.
    if (!withSummary && !isPosted(notificationId)) return bubbleData
    createNotificationChannel()
    val context = bubbleData.contextRef.get() ?: application

    // Bubbles require FLAG_MUTABLE on Android 12+ because they need to update the intent
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
    val bubbleIntent = PendingIntent.getActivity(
      context,
      notificationId,
      Intent(context, EmbeddableWebViewActivity::class.java).apply {
        data = Uri.parse(website.url)
        putExtra(Constants.EXTRA_KEY_INCOGNITO, bubbleData.incognito)
      },
      flags
    )
    // Holds only the notification id, so an incognito bubble leaves no address in it.
    val dismissIntent = PendingIntent.getBroadcast(
      application,
      notificationId,
      Intent(application, BubbleDismissReceiver::class.java)
        .putExtra(EXTRA_BUBBLE_NOTIFICATION_ID, notificationId),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val identity = bubbleData.identity()

    // Android 11 and later only show a bubble for a conversation, which needs a long-lived
    // shortcut for it.
    val shortcutId = pushConversationShortcut(website.url, identity, bubbleData.incognito)

    val bubbleNotification = notification(context, BUBBLE_NOTIFICATION_CHANNEL_ID) {
      shortcutId?.let {
        setShortcutId(it)
        // The same id as the shortcut, so Android ties the notification, the shortcut and the bubble together.
        setLocusId(LocusId(it))
      }
      setContentTitle(identity.label)
      identity.text?.let(::setContentText)
      setGroup(BUBBLE_NOTIFICATION_GROUP)

      setAllowSystemGeneratedContextualActions(true)
      // An incognito bubble takes no colour from the page or from the app that sent it.
      if (!bubbleData.incognito) {
        bubbleData.color.takeIf { it != Constants.NO_COLOR }?.let(::setColor)
        setColorized(true)
      }
      setLocalOnly(true)

      setOngoing(false) // A dismissed bubble cleans up through its delete intent.
      setSmallIcon(Icon.createWithResource(context, R.drawable.ic_chromer_notification))
      setLargeIcon(identity.icon)
      if (bubbleData.incognito) secretOnLockScreen() else privateOnLockScreen()

      bubbleMetadata {
        setIcon(identity.icon)
        setIntent(bubbleIntent)
        setDeleteIntent(dismissIntent)
        setAutoExpandBubble(false)
        setSuppressNotification(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setSuppressableBubble(true)
        setDesiredHeight(desiredHeightDp(context))
      }

      // A bubble is a conversation: the messaging style with an important person qualifies it on
      // Android 10, and the shortcut on Android 11 and later.
      setCategory(Notification.CATEGORY_MESSAGE)
      style = Notification.MessagingStyle(addPerson {
        // A conversation whose only person is a bot is not a conversation, and only
        // conversations bubble.
        setIcon(identity.icon)
        setName(identity.label)
        setImportant(true)
      })
    }

    if (withSummary) updateGroupSummaryNotification(if (bubbleData.incognito) Constants.NO_COLOR else bubbleData.color)

    notificationManager.notify(notificationId, bubbleNotification)
    return bubbleData
  }

  /** 80 % of the height of the window that posts the bubble, so split screen gets a bubble that fits. */
  private fun desiredHeightDp(context: Context): Int {
    val windowManager = context.getSystemService(WindowManager::class.java)
    val windowHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      windowManager.currentWindowMetrics.bounds.height()
    } else {
      @Suppress("DEPRECATION") // sdlc-debt: Android 10 has no window metrics; its display size is the old source.
      Point().apply(windowManager.defaultDisplay::getSize).y
    }
    return Utils.pxToDp((windowHeight * 0.8).toInt())
  }

  /**
   * Whether Android shows the posted notification of [url] as a bubble: the system sets
   * [Notification.FLAG_BUBBLE] on it when it honours the bubble request.
   */
  fun isShownAsBubble(url: String, incognito: Boolean): Boolean = try {
    notificationManager.activeNotifications.any {
      it.id == bubbleNotificationId(url, incognito) && it.notification.flags and Notification.FLAG_BUBBLE != 0
    }
  } catch (e: RuntimeException) {
    Timber.e(e, "Could not read the active notifications")
    // Unknown: keep the notification rather than remove a bubble that may be showing.
    true
  }

  private fun isPosted(notificationId: Int): Boolean = try {
    notificationManager.activeNotifications.any { it.id == notificationId }
  } catch (e: RuntimeException) {
    Timber.e(e, "Could not read the active notifications")
    // Unknown: update the bubble rather than leave a stale one showing.
    true
  }

  /**
   * Removes the bubble of [url] that Android did not show: its notification, the group summary
   * when no other bubble is left, and its shortcut, the same as a dismissal.
   */
  fun cancel(url: String, incognito: Boolean) = dismissed(bubbleNotificationId(url, incognito))

  /**
   * Called when the person dismisses the bubble posted as [notificationId]: its notification, the
   * group summary when it was the last bubble, and its shortcut go.
   */
  fun dismissed(notificationId: Int) = synchronized(postLock) {
    cancel(notificationId)
    try {
      ShortcutManagerCompat.removeLongLivedShortcuts(application, listOf(conversationIdOfNotification(notificationId)))
    } catch (e: Exception) {
      Timber.e(e, "Could not remove the bubble shortcut")
    }
  }

  private fun cancel(notificationId: Int) = synchronized(postLock) {
    notificationManager.cancel(notificationId)
    val othersLeft = try {
      notificationManager.activeNotifications.any {
        it.id != notificationId && it.id != BUBBLE_NOTIFICATION_GROUP.hashCode() &&
          it.notification.group == BUBBLE_NOTIFICATION_GROUP
      }
    } catch (e: RuntimeException) {
      Timber.e(e, "Could not read the active notifications")
      true
    }
    if (!othersLeft) notificationManager.cancel(BUBBLE_NOTIFICATION_GROUP.hashCode())
  }

  /** Publishes the long-lived shortcut a bubble is tied to; returns its id, or null when refused. */
  private fun pushConversationShortcut(
    url: String,
    identity: BubbleIdentity,
    incognito: Boolean,
  ): String? = try {
    val id = bubbleConversationId(url, incognito)
    val iconCompat = IconCompat.createFromIcon(application, identity.icon)
    val label = if (incognito) identity.label else identity.label.ifBlank { url }
    val person = Person.Builder()
      .setName(label)
      .setKey(id)
      .setImportant(true)
      .apply { iconCompat?.let(::setIcon) }
      .build()
    // The system keeps the shortcut, so an incognito one opens the Lynket home and holds no address.
    val intent = if (incognito) {
      Intent(application, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .putExtra(Constants.EXTRA_KEY_INCOGNITO, true)
    } else {
      Intent(application, EmbeddableWebViewActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse(url))
        .putExtra(Constants.EXTRA_KEY_INCOGNITO, false)
    }
    val shortcut = ShortcutInfoCompat.Builder(application, id)
      .setShortLabel(label)
      .setLongLived(true)
      .setLocusId(LocusIdCompat(id))
      .setPerson(person)
      .setIntent(intent)
      .apply { iconCompat?.let(::setIcon) }
      .build()
    ShortcutManagerCompat.pushDynamicShortcut(application, shortcut)
    id
  } catch (e: Exception) {
    Timber.e(e, "Could not publish the bubble shortcut")
    null
  }

  /** What a locked phone shows for every bubble and the summary: the app name and one generic line. */
  private fun lockScreenVersion(): Notification = notification(application, BUBBLE_NOTIFICATION_CHANNEL_ID) {
    setContentTitle(application.getString(R.string.app_name))
    setContentText(application.getString(R.string.bubble_lock_screen_text))
    setSmallIcon(Icon.createWithResource(application, R.drawable.ic_chromer_notification))
  }

  /**
   * Hides the page on a secure lock screen when the phone hides sensitive content, whatever the
   * channel holds: Android keeps no lock screen setting that an app puts on its channel.
   */
  private fun Notification.Builder.privateOnLockScreen(): Notification.Builder =
    setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(lockScreenVersion())

  /** An incognito bubble never shows on a secure lock screen, whatever the phone setting. */
  private fun Notification.Builder.secretOnLockScreen(): Notification.Builder =
    setVisibility(Notification.VISIBILITY_SECRET).setPublicVersion(lockScreenVersion())

  /** What a bubble shows outside itself: its notification, its person and its shortcut. */
  private class BubbleIdentity(val label: String, val text: String?, val icon: Icon)

  /** An incognito bubble shows only the neutral label and the Lynket icon, never its page. */
  private fun BubbleLoadData.identity(): BubbleIdentity = if (incognito) {
    BubbleIdentity(
      application.getString(R.string.bubble_incognito_label),
      null,
      Icon.createWithResource(application, R.mipmap.ic_launcher),
    )
  } else {
    BubbleIdentity(
      website.safeLabel(),
      website.preferredUrl(),
      icon?.let(Icon::createWithAdaptiveBitmap) ?: fallbackIcon(),
    )
  }

  private fun BubbleLoadData.fallbackIcon(): Icon = if (color != Constants.NO_COLOR) {
    // The same size the bubble's favicon decodes at.
    val iconSize = WebsiteIconLoader.LARGEST_ICON_DP.dpToPx()
    Icon.createWithAdaptiveBitmap(
      ColorDrawable(color).toBitmap(
        width = iconSize,
        height = iconSize
      )
    )
  } else {
    Icon.createWithResource(application, R.mipmap.ic_launcher)
  }
}

