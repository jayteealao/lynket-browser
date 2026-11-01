package arun.com.chromer.bubbles.system

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import arun.com.chromer.MainActivity
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * An incognito bubble shows only "Incognito page" and the Lynket icon in its notification and
 * its launcher shortcut; a normal bubble keeps its title, address and site icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NativeBubbleIdentityTest {

  private val application: Application get() = RuntimeEnvironment.getApplication()

  private val notificationManager: NotificationManager
    get() = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val manager by lazy { BubbleNotificationManager(application) }

  private val label: String get() = application.getString(R.string.bubble_incognito_label)

  private val siteIcon: Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

  private fun website(url: String) =
    Website(TITLE, url, null, null, null, null, false, 0, 0)

  private fun post(url: String, incognito: Boolean) {
    runBlocking {
      manager.showBubbles(BubbleLoadData(website(url), false, false, incognito, icon = siteIcon, color = Color.BLUE))
    }
  }

  private fun notificationOf(url: String): Notification =
    shadowOf(notificationManager).allNotifications.single { it.shortcutId in bubbleIdsOf(url) }

  private fun shortcutOf(url: String): ShortcutInfo =
    application.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single { it.id in bubbleIdsOf(url) }

  private fun ShortcutInfo.persons(): Array<android.app.Person> =
    ReflectionHelpers.callInstanceMethod(this, "getPersons")

  private fun ShortcutInfo.icon(): Icon = ReflectionHelpers.callInstanceMethod(this, "getIcon")

  private fun Icon.isLynketIcon(): Boolean = type == Icon.TYPE_RESOURCE && resId == R.mipmap.ic_launcher

  @Test
  fun an_incognito_notification_shows_only_the_neutral_label_and_the_lynket_icon() {
    post(URL, incognito = true)

    val notification = notificationOf(URL)
    val extras = notification.extras
    assertThat(extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo(label)
    assertThat(extras.getCharSequence(Notification.EXTRA_TEXT)).isNull()
    assertThat(notification.getLargeIcon().isLynketIcon()).isTrue()
    assertThat(notification.bubbleMetadata!!.icon!!.isLynketIcon()).isTrue()
    val person = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)!!
    assertThat(person.name.toString()).isEqualTo(label)
    extras.keySet().forEach { key ->
      @Suppress("DEPRECATION") // sdlc-debt: the typed getter needs the type of each key; the test reads every value as text.
      val value = extras.get(key)?.toString().orEmpty()
      assertThat(value).doesNotContain(TITLE)
      assertThat(value).doesNotContain(HOST)
    }
  }

  @Test
  fun an_incognito_notification_and_its_summary_take_no_page_colour() {
    post(URL, incognito = true)

    val all = shadowOf(notificationManager).allNotifications
    val summary = all.single { it.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
    listOf(notificationOf(URL), summary).forEach { notification ->
      assertThat(notification.color).isEqualTo(Notification.COLOR_DEFAULT)
      assertThat(notification.extras.getBoolean(Notification.EXTRA_COLORIZED)).isFalse()
    }
  }

  @Test
  fun an_incognito_shortcut_carries_only_the_neutral_label_and_the_lynket_icon() {
    post(URL, incognito = true)

    val shortcut = shortcutOf(URL)
    assertThat(shortcut.shortLabel.toString()).isEqualTo(label)
    assertThat(shortcut.longLabel).isNull()
    assertThat(shortcut.persons().single().name.toString()).isEqualTo(label)
    assertThat(shortcut.icon().isLynketIcon()).isTrue()
    val intent = shortcut.intent!!
    assertThat(intent.data).isNull()
    assertThat(intent.component!!.className).isEqualTo(MainActivity::class.java.name)
    assertThat(intent.toUri(0)).doesNotContain(TITLE)
    assertThat(intent.toUri(0)).doesNotContain(HOST)
  }

  @Test
  fun an_incognito_bubble_id_cannot_be_matched_against_its_address() {
    post(URL, incognito = true)

    val notification = notificationOf(URL)
    assertThat(notification.shortcutId).isNotEqualTo(bubbleConversationId(URL))
    assertThat(shadowOf(notificationManager).allNotifications.none { it.shortcutId == bubbleConversationId(URL) }).isTrue()
    // The same link keeps one id for as long as Lynket runs, so a resend updates the same bubble.
    assertThat(bubbleConversationId(URL, incognito = true)).isEqualTo(notification.shortcutId)
  }

  @Test
  fun three_incognito_bubbles_keep_three_shortcuts_with_the_same_label() {
    val urls = (1..3).map { "https://$HOST/private/$it" }
    urls.forEach { post(it, incognito = true) }

    val shortcuts = urls.map(::shortcutOf)
    assertThat(shortcuts.map { it.id }.toSet()).hasSize(3)
    shortcuts.forEach { assertThat(it.shortLabel.toString()).isEqualTo(label) }
  }

  @Test
  fun a_normal_bubble_keeps_its_title_address_and_site_icon() {
    val url = "https://normal.example/page"
    post(url, incognito = false)

    val notification = notificationOf(url)
    assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo(TITLE)
    assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo(url)
    assertThat(notification.getLargeIcon().type).isEqualTo(Icon.TYPE_ADAPTIVE_BITMAP)
    assertThat(notification.color).isEqualTo(Color.BLUE)
    val shortcut = shortcutOf(url)
    assertThat(shortcut.shortLabel.toString()).isEqualTo(TITLE)
    assertThat(shortcut.persons().single().name.toString()).isEqualTo(TITLE)
    assertThat(shortcut.intent!!.dataString).isEqualTo(url)
  }

  private companion object {
    const val TITLE = "Secret Distinctive Title"
    const val HOST = "secret.example"
    const val URL = "https://$HOST/private/path"
  }
}
