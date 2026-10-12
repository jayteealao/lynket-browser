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

package arun.com.chromer.ui.navigation

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A link handed to the app keeps its exact text through the deep-link matcher and the Browser key;
 * anything that is not a web address is refused without an exception.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class WebUrlDeepLinkMatcherTest {

  private val urls = listOf(
    "https://example.com/a%20b?q=ü&x=1#frag",
    "https://example.com/path?query=a&b=c#section",
    "https://example.com/100%25/done",
    "https://example.com/a%2Fb/c",
    "https://example.com/search?q=hello+world&lang=en",
    "https://bücher.example/straße?ä=ö",
    "https://xn--bcher-kva.example/",
    "https://例え.テスト/パス?クエリ=値#断片",
    "http://example.com/?only=query",
    "https://example.com/#only-fragment",
    "HTTPS://Example.COM/Mixed?Case=Yes",
  )

  private fun viewIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

  @Test
  fun everyLinkKeepsItsExactText() {
    urls.forEach { url ->
      val key = WebUrlDeepLinkMatcher.keyFor(viewIntent(url))
      assertThat(key).isEqualTo(LynketKey.Browser(url))
      assertThat(WebUrl.parseOrNull(key!!.url)?.value).isEqualTo(url)
    }
  }

  @Test
  fun aBrowserKeyHoldsTheUrlUnchanged() {
    urls.forEach { url -> assertThat(LynketKey.Browser(url).url).isEqualTo(url) }
  }

  @Test
  fun malformedWebLinksMatchButAreNotValidAddresses() {
    listOf("https://", "http://", "https:///path-only").forEach { raw ->
      val key = WebUrlDeepLinkMatcher.keyFor(viewIntent(raw))
      assertThat(key).isEqualTo(LynketKey.Browser(raw))
      assertThat(WebUrl.parseOrNull(raw)).isNull()
    }
  }

  @Test
  fun otherSchemesAndActionsDoNotMatch() {
    assertThat(WebUrlDeepLinkMatcher.keyFor(viewIntent("javascript:alert(1)"))).isNull()
    assertThat(WebUrlDeepLinkMatcher.keyFor(viewIntent("ftp://x"))).isNull()
    assertThat(WebUrlDeepLinkMatcher.keyFor(viewIntent("mailto:a@b.c"))).isNull()
    assertThat(WebUrlDeepLinkMatcher.keyFor(Intent(Intent.ACTION_MAIN))).isNull()
    assertThat(WebUrlDeepLinkMatcher.keyFor(Intent(Intent.ACTION_SEND, Uri.parse("https://example.com")))).isNull()
  }

  @Test
  fun parseOrNullRefusesNonAddressesWithoutThrowing() {
    listOf(
      null, "", " ", "https://", "http://exa mple.com", "javascript:alert(1)", "ftp://x",
      "example.com", "://", "https//example.com", "%%%", "\u0000",
    ).forEach { raw -> assertThat(WebUrl.parseOrNull(raw)).isNull() }
  }
}
