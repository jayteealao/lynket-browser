package arun.com.chromer.bubbles.system

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.shared.Constants
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * On a locked phone a normal bubble notification and the group summary show only the app name and
 * "A page is open in a bubble" when the phone hides sensitive content, and an incognito bubble
 * notification never shows: nothing of the page either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NativeBubbleLockScreenTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val manager by lazy { BubbleNotificationManager(application) }

  private val appName: String get() = application.getString(R.string.app_name)

  private val text: String get() = application.getString(R.string.bubble_lock_screen_text)

  private val siteIcon: Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

  private fun website(url: String) =
    Website(TITLE, url, null, null, null, null, false, 0, 0)

  private fun post(url: String, incognito: Boolean, icon: Bitmap? = siteIcon, color: Int = Color.BLUE) {
    runBlocking {
      manager.showBubbles(BubbleLoadData(website(url), false, false, incognito, icon = icon, color = color))
    }
  }

  private fun notificationOf(url: String): Notification =
    shadowOf(notificationManager).allNotifications.single { it.shortcutId in bubbleIdsOf(url) }

  private fun summary(): Notification =
    shadowOf(notificationManager).allNotifications.single { it.flags and Notification.FLAG_GROUP_SUMMARY != 0 }

  private fun assertGenericPublicVersion(n: Notification, visibility: Int = Notification.VISIBILITY_PRIVATE) {
    assertThat(n.visibility).isEqualTo(visibility)
    val public = n.publicVersion
    assertThat(public).isNotNull()
    public!!
    assertThat(appName).startsWith("Lynket")
    assertThat(public.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo(appName)
    assertThat(public.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo(text)
    assertThat(public.getLargeIcon()).isNull()
    assertThat(public.smallIcon.resId).isEqualTo(R.drawable.ic_chromer_notification)
    assertThat(public.color).isEqualTo(Notification.COLOR_DEFAULT)
    assertThat(public.shortcutId).isNull()
    assertThat(public.extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)).isNull()
    public.extras.keySet().forEach { key ->
      @Suppress("DEPRECATION") // sdlc-debt: the typed getter needs the type of each key; the test reads every value as text.
      val value = public.extras.get(key)?.toString().orEmpty()
      assertThat(value).doesNotContain(TITLE)
      assertThat(value).doesNotContain(HOST)
    }
  }

  @Test
  fun a_normal_bubble_is_private_with_the_generic_public_version() {
    post(URL, incognito = false, icon = null, color = Constants.NO_COLOR)
    assertGenericPublicVersion(notificationOf(URL))

    post(URL, incognito = false)
    assertGenericPublicVersion(notificationOf(URL))
  }

  @Test
  fun an_incognito_bubble_never_shows_on_a_locked_phone() {
    post(URL, incognito = true, icon = null, color = Constants.NO_COLOR)
    assertGenericPublicVersion(notificationOf(URL), Notification.VISIBILITY_SECRET)

    post(URL, incognito = true)
    assertGenericPublicVersion(notificationOf(URL), Notification.VISIBILITY_SECRET)
  }

  @Test
  fun normal_and_incognito_bubbles_have_identical_lock_screen_rows() {
    val normalUrl = "https://$HOST/normal"
    val incognitoUrl = "https://$HOST/incognito"
    post(normalUrl, incognito = false)
    post(incognitoUrl, incognito = true)

    val normal = notificationOf(normalUrl).publicVersion!!.extras
    val incognito = notificationOf(incognitoUrl).publicVersion!!.extras
    assertThat(normal.getCharSequence(Notification.EXTRA_TITLE).toString())
      .isEqualTo(incognito.getCharSequence(Notification.EXTRA_TITLE).toString())
    assertThat(normal.getCharSequence(Notification.EXTRA_TEXT).toString())
      .isEqualTo(incognito.getCharSequence(Notification.EXTRA_TEXT).toString())
  }

  @Test
  fun the_group_summary_is_private_with_the_same_public_version() {
    post(URL, incognito = false)

    assertGenericPublicVersion(summary())
  }

  @Test
  fun an_existing_channel_is_kept_and_each_bubble_is_still_private() {
    notificationManager.createNotificationChannel(
      NotificationChannel(BUBBLE_NOTIFICATION_CHANNEL_ID, "Bubbles notification channel", NotificationManager.IMPORTANCE_HIGH)
    )

    post(URL, incognito = false)

    val channel = notificationManager.getNotificationChannel(BUBBLE_NOTIFICATION_CHANNEL_ID)
    assertThat(channel.lockscreenVisibility).isEqualTo(NO_LOCK_SCREEN_OVERRIDE)
    assertGenericPublicVersion(notificationOf(URL))
  }

  private companion object {
    const val TITLE = "Secret Distinctive Title"
    const val HOST = "secret.example"
    const val URL = "https://$HOST/private/path"

    /** A new channel's lock screen setting before the app sets one (hidden in the SDK as NotificationManager.VISIBILITY_NO_OVERRIDE). */
    const val NO_LOCK_SCREEN_OVERRIDE = -1000
  }
}
