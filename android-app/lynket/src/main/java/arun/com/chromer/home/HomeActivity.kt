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

package arun.com.chromer.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.postDelayed
import androidx.recyclerview.widget.SimpleItemAnimator
import arun.com.chromer.R
import arun.com.chromer.about.changelog.Changelog
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.databinding.ActivityMainBinding
import arun.com.chromer.di.activity.ActivityComponent
import arun.com.chromer.extenstions.gone
import arun.com.chromer.extenstions.show
import arun.com.chromer.extenstions.watch
import arun.com.chromer.home.bottomsheet.HomeBottomSheet
import arun.com.chromer.home.epoxycontroller.HomeFeedController
import arun.com.chromer.intro.ChromerIntroActivity
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.SettingsGroupActivity
import arun.com.chromer.shared.base.Snackable
import arun.com.chromer.shared.base.activity.BaseActivity
import arun.com.chromer.tabs.TabsManager
import arun.com.chromer.tips.TipsActivity
import arun.com.chromer.util.events.EventBus
import arun.com.chromer.util.events.Event
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : BaseActivity(), Snackable {
  private lateinit var binding: ActivityMainBinding

  @Inject
  lateinit var eventBus: EventBus

  @Inject
  lateinit var tabsManager: TabsManager

  private val homeActivityViewModel: HomeActivityViewModel by viewModels()

  override fun inject(activityComponent: ActivityComponent) = activityComponent.inject(this)


  @Inject
  lateinit var homeFeedController: HomeFeedController

  @Inject
  lateinit var tabsLifecycleObserver: TabsLifecycleObserver

  override val layoutRes: Int get() = R.layout.activity_main

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.AppTheme_NoActionBar)
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    if (Preferences.get(this).isFirstRun) {
      startActivity(Intent(this, ChromerIntroActivity::class.java))
    }

    Changelog.conditionalShow(this)

    setupToolbar()
    setupSearchBar()
    setupFeed()
    setupEventListeners()
  }

  override fun snack(textToSnack: String) {
    Snackbar.make(binding.coordinatorLayout, textToSnack, Snackbar.LENGTH_SHORT).show()
  }

  override fun snackLong(textToSnack: String) {
    Snackbar.make(binding.coordinatorLayout, textToSnack, Snackbar.LENGTH_LONG).show()
  }

  private fun setupToolbar() {
    binding.tipsIcon.setImageDrawable(
      IconicsDrawable(this)
        .icon(CommunityMaterial.Icon.cmd_lightbulb_on)
        .colorRes(R.color.md_yellow_700)
        .sizeDp(24)
    )
  }

  private fun setupEventListeners() {
    // Phase 8: Migrated from RxJava to Kotlin Flow
    lifecycleScope.launch {
      eventBus.observe<Event.TabEvent.FinishNonBrowsingActivities>()
        .collect {
          finish()
        }
    }

    binding.settingsIcon.setOnClickListener {
      startActivity(Intent(this, SettingsGroupActivity::class.java))
    }
    binding.tipsIcon.setOnClickListener {
      startActivity(Intent(this, TipsActivity::class.java))
    }
  }

  override fun onStart() {
    super.onStart()
    // TODO: Phase 8 Migration - Convert activeTabs() Observable to Flow
    // Use lifecycleScope.launch { tabsLifecycleObserver.activeTabsFlow().collect { tabs -> ... } }
    // tabsLifecycleObserver.activeTabs().subscribe { tabs ->
    //   homeFeedController.tabs = tabs
    // }
  }

  private fun setupFeed() {
    binding.homeFeedRecyclerView.apply {
      setController(homeFeedController)
      (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }
    val owner = this
    homeActivityViewModel.run {
      providerInfoLiveData.watch(owner) { providerInfo ->
        homeFeedController.customTabProviderInfo = providerInfo
      }
      recentsLiveData.watch(owner) { recentWebsites ->
        homeFeedController.recentWebSites = recentWebsites
      }
    }
  }

  private fun setupSearchBar() {
    binding.materialSearchView.apply {
      // TODO: Phase 8 Migration - Convert MaterialSearchView RxJava to Flow
      // All these methods (voiceSearchFailed, searchPerforms, focusChanges, menuClicks)
      // return RxJava Observables and need to be migrated to Flow
      // Use lifecycleScope.launch { searchPerformsFlow().collect { ... } }
      /*
      // Handle voice item failed
      voiceSearchFailed()
        .takeUntil(lifecycleEvents.destroys)
        .subscribe {
          snack(getString(R.string.no_voice_rec_apps))
        }

      // Handle search events
      searchPerforms()
        .takeUntil(lifecycleEvents.destroys)
        .subscribe { url ->
          postDelayed(150) {
            tabsManager.openUrl(this@HomeActivity, Website(url))
          }
        }
      */

      // No focus initially
      clearFocus()

      /*
      // Handle focus changes
      focusChanges()
        .takeUntil(lifecycleEvents.destroys)
        .subscribe { hasFocus ->
          if (hasFocus) {
            binding.shadowView.show()
          } else {
            binding.shadowView.gone()
          }
        }

      binding.shadowView.clicks()
        .debounce(100, TimeUnit.MILLISECONDS, schedulerProvider.ui)
        .takeUntil(lifecycleEvents.destroys)
        .subscribe { clearFocus() }

      // Menu clicks
      menuClicks()
        .takeUntil(lifecycleEvents.destroys)
        .subscribe {
          HomeBottomSheet().show(supportFragmentManager, "home-bottom-shher")
        }
      */
    }
  }

  override fun onBackPressed() {
    if (binding.materialSearchView.hasFocus()) {
      binding.materialSearchView.clearFocus()
      return
    }
    super.onBackPressed()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    binding.materialSearchView.onActivityResult(requestCode, resultCode, data)
  }
}
