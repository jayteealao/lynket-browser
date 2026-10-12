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

package arun.com.chromer.tabs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tab detection finds Lynket's browsing tasks by the class names of their activities, which also
 * appear in the manifest and in tasks that outlive an update. A rename must fail here, not
 * silently hide open tabs.
 */
class BrowsingActivityNamesTest {

  @Test
  fun everyBrowsingActivityKeepsItsName() {
    assertThat(TabsManager.ALL_BROWSING_ACTIVITIES).containsExactly(
      "arun.com.chromer.browsing.customtabs.CustomTabActivity",
      "arun.com.chromer.browsing.article.ArticleActivity",
      "arun.com.chromer.browsing.webview.WebViewActivity",
      "arun.com.chromer.browsing.webview.EmbeddableWebViewActivity",
    ).inOrder()
  }

  @Test
  fun fullBrowsingActivitiesAreTheThreePageViews() {
    assertThat(TabsManager.FULL_BROWSING_ACTIVITIES).containsExactly(
      "arun.com.chromer.browsing.customtabs.CustomTabActivity",
      "arun.com.chromer.browsing.webview.WebViewActivity",
      "arun.com.chromer.browsing.webview.EmbeddableWebViewActivity",
    ).inOrder()
  }

  @Test
  fun eachTabTypeOpensItsOwnActivity() {
    assertThat(TabsManager.Tab("https://example.com", CUSTOM_TAB).getTargetActivityName()).isEqualTo(TabsManager.CUSTOM_TAB_ACTIVITY)
    assertThat(TabsManager.Tab("https://example.com", WEB_VIEW).getTargetActivityName()).isEqualTo(TabsManager.WEBVIEW_ACTIVITY)
    assertThat(TabsManager.Tab("https://example.com", WEB_VIEW_EMBEDDED).getTargetActivityName()).isEqualTo(TabsManager.EMBEDDABLE_WEBVIEW_ACTIVITY)
    assertThat(TabsManager.Tab("https://example.com", ARTICLE).getTargetActivityName()).isEqualTo(TabsManager.ARTICLE_ACTIVITY)
  }
}
