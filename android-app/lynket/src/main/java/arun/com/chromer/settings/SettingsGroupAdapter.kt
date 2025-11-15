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
package arun.com.chromer.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import arun.com.chromer.R
import butterknife.BindView
import butterknife.ButterKnife
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable

/**
 * Created by Arunkumar on 19-02-2017.
 */
internal class SettingsGroupAdapter(context: Context) :
  RecyclerView.Adapter<SettingsGroupAdapter.SettingsItemViewHolder>() {

  private val context: Context = context.applicationContext
  private val settingsItems: MutableList<String> = ArrayList()
  private var groupItemClickListener: GroupItemClickListener = GroupItemClickListener { _, _ -> }

  init {
    setHasStableIds(true)
    settingsItems.add(context.getString(R.string.settings_browsing_mode))
    settingsItems.add(context.getString(R.string.settings_look_and_feel))
    settingsItems.add(context.getString(R.string.settings_browsing_options))
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsItemViewHolder {
    return SettingsItemViewHolder(
      LayoutInflater.from(context).inflate(R.layout.activity_settings_list_item_template, parent, false)
    )
  }

  override fun onBindViewHolder(holder: SettingsItemViewHolder, position: Int) {
    holder.bind(settingsItems[position])
    holder.itemView.setOnClickListener {
      if (holder.adapterPosition != RecyclerView.NO_POSITION) {
        groupItemClickListener.onGroupItemClicked(holder.adapterPosition, holder.itemView)
      }
    }
  }

  override fun getItemId(position: Int): Long {
    return settingsItems[position].hashCode().toLong()
  }

  override fun getItemCount(): Int {
    return settingsItems.size
  }

  fun setGroupItemClickListener(groupItemClickListener: GroupItemClickListener) {
    this.groupItemClickListener = groupItemClickListener
  }

  fun cleanUp() {
    groupItemClickListener = GroupItemClickListener { _, _ -> }
    settingsItems.clear()
  }

  interface GroupItemClickListener {
    fun onGroupItemClicked(position: Int, view: View)
  }

  class SettingsItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    @BindView(R.id.settins_list_icon)
    lateinit var icon: ImageView
    @BindView(R.id.settings_list_title)
    lateinit var title: TextView
    @BindView(R.id.settings_list_subtitle)
    lateinit var subtitle: TextView

    init {
      ButterKnife.bind(this, itemView)
    }

    fun bind(item: String) {
      val position = adapterPosition
      title.text = item
      when (position) {
        0 -> {
          icon.setImageDrawable(
            IconicsDrawable(icon.context)
              .icon(CommunityMaterial.Icon.cmd_earth)
              .colorRes(R.color.colorAccent)
              .sizeDp(24)
          )
          subtitle.visibility = View.GONE
        }
        1 -> {
          icon.setImageDrawable(
            IconicsDrawable(icon.context)
              .icon(CommunityMaterial.Icon.cmd_format_paint)
              .colorRes(R.color.colorAccent)
              .sizeDp(24)
          )
          subtitle.visibility = View.GONE
        }
        2 -> {
          icon.setImageDrawable(
            IconicsDrawable(icon.context)
              .icon(CommunityMaterial.Icon.cmd_settings)
              .colorRes(R.color.colorAccent)
              .sizeDp(24)
          )
          subtitle.visibility = View.GONE
        }
      }
    }
  }
}
