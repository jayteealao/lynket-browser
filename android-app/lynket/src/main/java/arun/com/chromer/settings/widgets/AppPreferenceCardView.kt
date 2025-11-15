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

package arun.com.chromer.settings.widgets

import android.content.ComponentName
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.ImageView.ScaleType.CENTER
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.util.ColorUtil
import arun.com.chromer.util.Utils
import arun.com.chromer.util.glide.GlideApp
import arun.com.chromer.util.glide.appicon.ApplicationIcon
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.Unbinder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

class AppPreferenceCardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : CardView(context, attrs, defStyle) {

  @BindView(R.id.app_preference_icon)
  lateinit var icon: ImageView

  @BindView(R.id.app_preference_category)
  lateinit var categoryTextView: TextView

  @BindView(R.id.app_preference_selection)
  lateinit var appNameTextView: TextView

  private var unbinder: Unbinder? = null

  private var category: String? = null
  private var appName: String? = null
  private var appPackage: String? = null
  private var preferenceType: Int = 0

  init {
    init(attrs, defStyle)
  }

  private fun init(attrs: AttributeSet?, defStyle: Int) {
    // Load attributes
    val a = context.obtainStyledAttributes(
      attrs, R.styleable.AppPreferenceCardView, defStyle, 0
    )
    if (!a.hasValue(R.styleable.AppPreferenceCardView_preferenceType)) {
      throw IllegalArgumentException("Must specify app:preferenceType in xml")
    }

    preferenceType = a.getInt(R.styleable.AppPreferenceCardView_preferenceType, 0)
    setInitialValues()
    a.recycle()
    addView(LayoutInflater.from(context).inflate(R.layout.widget_app_preference_cardview_content, this, false))
    unbinder = ButterKnife.bind(this)
  }

  private fun setInitialValues() {
    when (preferenceType) {
      CUSTOM_TAB_PROVIDER -> {
        category = resources.getString(R.string.default_provider)
        val customTabProvider = Preferences.get(context).customTabPackage()
        if (customTabProvider != null) {
          appName = Utils.getAppNameWithPackage(context, customTabProvider)
          appPackage = customTabProvider
        } else {
          appName = resources.getString(R.string.not_found)
          appPackage = null
        }
      }
      SECONDARY_BROWSER -> {
        category = resources.getString(R.string.choose_secondary_browser)
        val secondaryBrowser = Preferences.get(context).secondaryBrowserPackage()
        if (secondaryBrowser != null) {
          appName = Utils.getAppNameWithPackage(context, secondaryBrowser)
          appPackage = secondaryBrowser
        } else {
          appName = resources.getString(R.string.not_set)
          appPackage = null
        }
      }
      FAVORITE_SHARE -> {
        category = resources.getString(R.string.fav_share_app)
        val favSharePackage = Preferences.get(context).favSharePackage()
        if (favSharePackage != null) {
          appName = Utils.getAppNameWithPackage(context, favSharePackage)
          appPackage = favSharePackage
        } else {
          appName = resources.getString(R.string.not_set)
          appPackage = null
        }
      }
    }
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    updateUI()
  }

  private fun updateUI() {
    appNameTextView.setTextColor(ContextCompat.getColor(context, R.color.material_dark_color))
    categoryTextView.text = category
    appNameTextView.text = appName
    applyIcon()
  }

  private fun applyIcon() {
    if (Utils.isPackageInstalled(context, appPackage)) {
      icon.scaleType = ImageView.ScaleType.FIT_CENTER
      GlideApp.with(context)
        .load(ApplicationIcon.createUri(appPackage))
        .fitCenter()
        .listener(object : RequestListener<android.graphics.drawable.Drawable> {
          override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<android.graphics.drawable.Drawable>?,
            isFirstResource: Boolean
          ): Boolean {
            return false
          }

          override fun onResourceReady(
            resource: android.graphics.drawable.Drawable?,
            model: Any?,
            target: Target<android.graphics.drawable.Drawable>?,
            dataSource: DataSource?,
            isFirstResource: Boolean
          ): Boolean {
            resource?.let {
              Palette.from(Utils.drawableToBitmap(it))
                .clearFilters()
                .generate { palette ->
                  val bestColor = ColorUtil.getBestColorFromPalette(palette)
                  val foreground = ColorUtil.getRippleDrawableCompat(bestColor)
                  this@AppPreferenceCardView.foreground = foreground
                }
            }
            return false
          }
        })
        .into(icon)
    } else {
      icon.scaleType = CENTER
      when (preferenceType) {
        CUSTOM_TAB_PROVIDER -> {
          icon.setImageDrawable(
            IconicsDrawable(context)
              .icon(CommunityMaterial.Icon.cmd_comment_alert_outline)
              .colorRes(R.color.error)
              .sizeDp(30)
          )
          appNameTextView.setTextColor(ContextCompat.getColor(context, R.color.error))
        }
        SECONDARY_BROWSER -> {
          icon.setImageDrawable(
            IconicsDrawable(context)
              .icon(CommunityMaterial.Icon.cmd_open_in_app)
              .colorRes(R.color.material_dark_light)
              .sizeDp(30)
          )
        }
        FAVORITE_SHARE -> {
          icon.setImageDrawable(
            IconicsDrawable(context)
              .icon(CommunityMaterial.Icon.cmd_share_variant)
              .colorRes(R.color.material_dark_light)
              .sizeDp(30)
          )
        }
      }
    }
  }

  fun updatePreference(componentName: ComponentName?) {
    val flatComponent = componentName?.flattenToString()
    when (preferenceType) {
      CUSTOM_TAB_PROVIDER -> {
        if (componentName != null) {
          Preferences.get(context).customTabPackage(componentName.packageName)
        }
      }
      SECONDARY_BROWSER -> {
        Preferences.get(context).secondaryBrowserComponent(flatComponent)
      }
      FAVORITE_SHARE -> {
        Preferences.get(context).favShareComponent(flatComponent)
      }
    }
    refreshState()
  }

  fun refreshState() {
    setInitialValues()
    updateUI()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    unbinder?.unbind()
  }

  companion object {
    private const val CUSTOM_TAB_PROVIDER = 0
    private const val SECONDARY_BROWSER = 1
    private const val FAVORITE_SHARE = 2
  }
}
