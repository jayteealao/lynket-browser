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

// Phase 7: Converted to Kotlin
package arun.com.chromer.settings.browsingmode

import android.app.Application
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import arun.com.chromer.R
import arun.com.chromer.settings.Preferences
import arun.com.chromer.settings.RxPreferences
import arun.com.chromer.util.ColorUtil
import butterknife.BindView
import butterknife.ButterKnife
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

/**
 * Created by Arunkumar on 19-02-2017.
 */
@ActivityScoped
class BrowsingModeAdapter @Inject constructor(
  application: Application,
  private val rxPreferences: RxPreferences
) : RecyclerView.Adapter<BrowsingModeAdapter.BrowsingModeViewHolder>() {

  private val settingsItems: MutableList<String> = ArrayList()
  private var browsingModeClickListener: BrowsingModeClickListener = object : BrowsingModeClickListener {
    override fun onModeClicked(position: Int, view: View) {}
  }

  init {
    setHasStableIds(true)
    settingsItems.add(application.getString(R.string.browsing_mode_slide_over))
    settingsItems.add(application.getString(R.string.browsing_mode_web_heads))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      settingsItems.add(application.getString(R.string.browsing_mode_native_bubbles))
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrowsingModeViewHolder {
    return BrowsingModeViewHolder(
      LayoutInflater.from(parent.context).inflate(
        R.layout.activity_browsing_mode_item_template,
        parent,
        false
      ),
      rxPreferences
    )
  }

  override fun onBindViewHolder(holder: BrowsingModeViewHolder, position: Int) {
    holder.bind(settingsItems[position])
    holder.itemView.setOnClickListener {
      if (holder.adapterPosition != RecyclerView.NO_POSITION) {
        browsingModeClickListener.onModeClicked(holder.adapterPosition, holder.itemView)
      }
    }
  }

  override fun getItemId(position: Int): Long {
    return settingsItems[position].hashCode().toLong()
  }

  override fun getItemCount(): Int {
    return settingsItems.size
  }

  fun setBrowsingModeClickListener(browsingModeClickListener: BrowsingModeClickListener) {
    this.browsingModeClickListener = browsingModeClickListener
  }

  fun cleanUp() {
    browsingModeClickListener = object : BrowsingModeClickListener {
      override fun onModeClicked(position: Int, view: View) {}
    }
    settingsItems.clear()
  }

  interface BrowsingModeClickListener {
    fun onModeClicked(position: Int, view: View)
  }

  class BrowsingModeViewHolder(
    itemView: View,
    private val rxPreferences: RxPreferences
  ) : RecyclerView.ViewHolder(itemView) {
    @BindView(R.id.icon)
    lateinit var icon: ImageView
    @BindView(R.id.title)
    lateinit var title: TextView
    @BindView(R.id.subtitle)
    lateinit var subtitle: TextView
    @BindView(R.id.browsing_mode_selector)
    lateinit var selector: ImageView
    @BindView(R.id.browsing_mode_root)
    lateinit var browsingModeRoot: CardView

    init {
      ButterKnife.bind(this, itemView)
    }

    fun bind(item: String) {
      title.text = item
      val position = adapterPosition
      val webHeads = Preferences.get(selector.context).webHeads()
      val nativeBubbles = rxPreferences.nativeBubbles.get()
      browsingModeRoot.foreground = ColorUtil.getRippleDrawableCompat(Color.parseColor("#42ffffff"))
      when (position) {
        SLIDE_OVER -> {
          icon.setImageDrawable(
            IconicsDrawable(icon.context)
              .icon(CommunityMaterial.Icon.cmd_open_in_app)
              .color(Color.WHITE)
              .sizeDp(24)
          )
          selector.setImageDrawable(
            IconicsDrawable(selector.context)
              .icon(
                if (webHeads || nativeBubbles)
                  CommunityMaterial.Icon.cmd_checkbox_blank_circle_outline
                else
                  CommunityMaterial.Icon.cmd_checkbox_marked_circle
              )
              .color(Color.WHITE)
              .sizeDp(24)
          )
          title.setTextColor(Color.WHITE)
          subtitle.setTextColor(Color.WHITE)
          subtitle.setText(R.string.browsing_mode_slide_over_explanation)
          browsingModeRoot.setCardBackgroundColor(ContextCompat.getColor(browsingModeRoot.context, R.color.md_light_blue_A700))
        }
        WEB_HEADS -> {
          icon.setImageDrawable(
            IconicsDrawable(icon.context)
              .icon(CommunityMaterial.Icon.cmd_chart_bubble)
              .color(Color.WHITE)
              .sizeDp(24)
          )
          selector.setImageDrawable(
            IconicsDrawable(selector.context)
              .icon(
                if (!webHeads)
                  CommunityMaterial.Icon.cmd_checkbox_blank_circle_outline
                else
                  CommunityMaterial.Icon.cmd_checkbox_marked_circle
              )
              .color(Color.WHITE)
              .sizeDp(24)
          )
          title.setTextColor(Color.WHITE)
          subtitle.setTextColor(Color.WHITE)
          subtitle.setText(R.string.browsing_mode_web_heads_explanation)
          browsingModeRoot.setCardBackgroundColor(ContextCompat.getColor(browsingModeRoot.context, R.color.md_green_700))
        }
        NATIVE_BUBBLES -> {
          val materialDarkColor = ContextCompat.getColor(icon.context, R.color.material_dark_color)
          icon.setImageDrawable(
            IconicsDrawable(icon.context)
              .icon(CommunityMaterial.Icon.cmd_android_head)
              .color(materialDarkColor)
              .sizeDp(24)
          )
          selector.setImageDrawable(
            IconicsDrawable(selector.context)
              .icon(
                if (!nativeBubbles)
                  CommunityMaterial.Icon.cmd_checkbox_blank_circle_outline
                else
                  CommunityMaterial.Icon.cmd_checkbox_marked_circle
              )
              .color(materialDarkColor)
              .sizeDp(24)
          )
          title.setTextColor(materialDarkColor)
          subtitle.setTextColor(ColorUtils.setAlphaComponent(materialDarkColor, (0.8 * 255).toInt()))
          subtitle.setText(R.string.browsing_mode_native_bubbles_explanation)
          browsingModeRoot.setCardBackgroundColor(ContextCompat.getColor(browsingModeRoot.context, R.color.android_10_color))
        }
      }
    }
  }

  companion object {
    const val SLIDE_OVER = 0
    const val WEB_HEADS = 1
    const val NATIVE_BUBBLES = 2
  }
}
