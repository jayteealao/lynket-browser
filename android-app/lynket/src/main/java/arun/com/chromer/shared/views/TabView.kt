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

package arun.com.chromer.shared.views

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.core.graphics.ColorUtils
import arun.com.chromer.R
import arun.com.chromer.util.Utils
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

/**
 * Created by Arun on 16/06/2016.
 */
@Deprecated("Deprecated")
class TabView : FrameLayout {

  lateinit var tabIcon: ImageView

  lateinit var text: TextView

  private var initialIconX = 0f
  private var initialTextX = 0f
  private var selected = false

  @TabType
  private var mTabType: Int = TAB_TYPE_OPTIONS

  constructor(context: Context, @TabType tabType: Int) : super(context) {
    init(context, tabType)
  }

  constructor(context: Context, attrs: AttributeSet?, @TabType tabType: Int) : super(context, attrs) {
    init(context, tabType)
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, @TabType tabType: Int) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    init(context, tabType)
  }

  private fun init(context: Context, @TabType tabType: Int) {
    mTabType = tabType
    addView(LayoutInflater.from(context).inflate(R.layout.widget_tab_view_layout, this, false))
    ButterKnife.bind(this)
    when (mTabType) {
      TAB_TYPE_OPTIONS -> {
        tabIcon.setImageDrawable(
          IconicsDrawable(context)
            .icon(CommunityMaterial.Icon.cmd_settings)
            .color(SELECTED_COLOR)
            .sizeDp(23)
        )
        text.setText(R.string.options)
        text.setTextColor(SELECTED_COLOR)
      }
      TAB_TYPE_WEB_HEADS -> {
        tabIcon.setImageDrawable(
          IconicsDrawable(context)
            .icon(CommunityMaterial.Icon.cmd_chart_bubble)
            .color(UN_SELECTED_COLOR)
            .sizeDp(23)
        )
        text.setText(R.string.web_heads)
        text.setTextColor(UN_SELECTED_COLOR)
      }
      TAB_TYPE_CUSTOMIZE -> {
        tabIcon.setImageDrawable(
          IconicsDrawable(context)
            .icon(CommunityMaterial.Icon.cmd_format_paint)
            .color(UN_SELECTED_COLOR)
            .sizeDp(23)
        )
        text.setText(R.string.customize)
        text.setTextColor(UN_SELECTED_COLOR)
      }
    }
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
      override fun onGlobalLayout() {
        val totalWidth = tabIcon.width + Utils.dpToPx(5.0) + text.width
        val layoutWidth = width
        initialIconX = (layoutWidth / 2 - totalWidth / 2).toFloat()
        tabIcon.x = initialIconX
        initialTextX = initialIconX + tabIcon.width + Utils.dpToPx(10.0)
        text.x = initialTextX

        text.pivotX = 0f
        text.pivotY = (text.height / 2).toFloat()
        // Refresh animations
        setSelected(selected)
        viewTreeObserver.removeOnGlobalLayoutListener(this)
      }
    })
  }

  override fun setSelected(selected: Boolean) {
    this.selected = selected
    if (selected) {
      text.setTextColor(SELECTED_COLOR)
      val drawable = tabIcon.drawable as IconicsDrawable
      tabIcon.setImageDrawable(drawable.color(SELECTED_COLOR))
      selectedAnimation()
    } else {
      text.setTextColor(UN_SELECTED_COLOR)
      val drawable = tabIcon.drawable as IconicsDrawable
      tabIcon.setImageDrawable(drawable.color(UN_SELECTED_COLOR))
      unSelectedAnimation()
    }
  }

  private fun getIconCentreInLayout(): Float {
    return ((width / 2) - (tabIcon.width / 2)).toFloat()
  }

  private fun clearAnimations() {
    text.clearAnimation()
    tabIcon.clearAnimation()
  }

  private fun unSelectedAnimation() {
    clearAnimations()
    val transformAnimator = AnimatorSet()
    transformAnimator.playTogether(
      ObjectAnimator.ofFloat(tabIcon, "translationX", initialIconX),
      ObjectAnimator.ofFloat(tabIcon, "scaleX", 0.75f),
      ObjectAnimator.ofFloat(tabIcon, "scaleY", 0.75f),
      ObjectAnimator.ofFloat(text, "scaleX", 1f),
      ObjectAnimator.ofFloat(text, "scaleY", 1f),
      ObjectAnimator.ofFloat(text, "alpha", 1f)
    )
    transformAnimator.duration = 275
    transformAnimator.interpolator = AccelerateDecelerateInterpolator()

    val sequentialAnimator = AnimatorSet()
    sequentialAnimator.playTogether(
      transformAnimator,
      getIconUnSelectionAnimator()
    )
    sequentialAnimator.start()
  }

  private fun selectedAnimation() {
    clearAnimations()
    val transformAnimator = AnimatorSet()
    transformAnimator.playTogether(
      ObjectAnimator.ofFloat(tabIcon, "translationX", getIconCentreInLayout()),
      ObjectAnimator.ofFloat(tabIcon, "scaleX", 1f),
      ObjectAnimator.ofFloat(tabIcon, "scaleY", 1f),
      ObjectAnimator.ofFloat(text, "scaleX", 0f),
      ObjectAnimator.ofFloat(text, "scaleY", 0f),
      ObjectAnimator.ofFloat(text, "alpha", 0f)
    )
    transformAnimator.duration = 275
    transformAnimator.interpolator = AccelerateDecelerateInterpolator()

    val togetherAnimator = AnimatorSet()
    togetherAnimator.playSequentially(
      transformAnimator,
      getIconSelectionAnimator()
    )
    togetherAnimator.start()
  }

  private fun getIconSelectionAnimator(): Animator? {
    val animator: Animator? = when (mTabType) {
      TAB_TYPE_OPTIONS -> ObjectAnimator.ofFloat(tabIcon, "rotation", 180f)
      TAB_TYPE_WEB_HEADS -> ObjectAnimator.ofFloat(tabIcon, "rotation", 125f)
      TAB_TYPE_CUSTOMIZE -> {
        val anim = ObjectAnimator.ofFloat(tabIcon, "scaleY", 1.2f)
        anim.repeatMode = ValueAnimator.REVERSE
        anim.repeatCount = 3
        anim.interpolator = LinearInterpolator()
        anim
      }
      else -> null
    }
    animator?.duration = 250
    return animator
  }

  private fun getIconUnSelectionAnimator(): Animator? {
    val animator: Animator? = when (mTabType) {
      TAB_TYPE_OPTIONS -> ObjectAnimator.ofFloat(tabIcon, "rotation", -180f)
      TAB_TYPE_WEB_HEADS -> ObjectAnimator.ofFloat(tabIcon, "rotation", -90f)
      TAB_TYPE_CUSTOMIZE -> ObjectAnimator.ofFloat(tabIcon, "scaleY", 0.75f)
      else -> null
    }
    animator?.duration = 250
    return animator
  }

  @Retention(AnnotationRetention.SOURCE)
  @IntDef(TAB_TYPE_OPTIONS, TAB_TYPE_WEB_HEADS, TAB_TYPE_CUSTOMIZE)
  annotation class TabType

  companion object {
    const val TAB_TYPE_OPTIONS = 0
    const val TAB_TYPE_WEB_HEADS = 1
    const val TAB_TYPE_CUSTOMIZE = 2

    @ColorInt
    private val SELECTED_COLOR = Color.WHITE

    @ColorInt
    private val UN_SELECTED_COLOR = ColorUtils.setAlphaComponent(SELECTED_COLOR, 178)
  }
}
