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

package arun.com.chromer.browsing.webview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The page's own address is told apart from the address of an image, a script or a frame. */
class SameAddressTest {

  @Test
  fun anAddressWithNoPathIsTheRootPage() {
    assertThat(sameAddress("https://expired.example", "https://expired.example/")).isTrue()
  }

  @Test
  fun theFragmentDoesNotCount() {
    assertThat(sameAddress("https://example.com/a#top", "https://example.com/a")).isTrue()
  }

  @Test
  fun anotherHostOrPathIsAnotherAddress() {
    assertThat(sameAddress("https://cdn.example.com/a.png", "https://example.com/")).isFalse()
    assertThat(sameAddress("https://example.com/b", "https://example.com/a")).isFalse()
  }
}
