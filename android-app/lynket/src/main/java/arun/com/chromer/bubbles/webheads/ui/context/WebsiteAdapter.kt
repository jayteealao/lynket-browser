/*
 * Phase 7: Converted from Java to Kotlin
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

package arun.com.chromer.bubbles.webheads.ui.context

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import arun.com.chromer.R
import arun.com.chromer.data.website.model.Website
import arun.com.chromer.util.glide.GlideApp

/**
 * Created by Arun on 05/09/2016.
 */
internal class WebsiteAdapter(
    context: Context,
    private val listener: WebSiteAdapterListener
) : RecyclerView.Adapter<WebsiteAdapter.WebSiteHolder>() {

    private val context: Context = context.applicationContext
    val websites = ArrayList<Website>()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebSiteHolder {
        return WebSiteHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.activity_web_head_context_item_template,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: WebSiteHolder, position: Int) {
        val website = websites[position]
        holder.deleteIcon.setImageDrawable(
            IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_close)
                .color(ContextCompat.getColor(context, R.color.accent_icon_no_focus))
                .sizeDp(16)
        )
        holder.shareIcon.setImageDrawable(
            IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_share_variant)
                .color(ContextCompat.getColor(context, R.color.accent_icon_no_focus))
                .sizeDp(16)
        )
        holder.url.text = website.preferredUrl()
        holder.title.text = website.safeLabel()
        GlideApp.with(context)
            .load(website.faviconUrl)
            .into(holder.icon)
    }

    override fun getItemCount(): Int {
        return websites.size
    }

    override fun getItemId(position: Int): Long {
        return websites[position].hashCode().toLong()
    }

    fun getWebsites(): List<Website> {
        return websites
    }

    fun setWebsites(websites: ArrayList<Website>) {
        this.websites.clear()
        this.websites.addAll(websites)
        notifyDataSetChanged()
    }

    fun delete(website: Website) {
        val index = websites.indexOf(website)
        if (index != -1) {
            websites.removeAt(index)
            notifyItemRemoved(index)
            listener.onWebSiteDelete(website)
        }
    }

    fun update(web: Website) {
        val index = websites.indexOf(web)
        if (index != -1) {
            websites.removeAt(index)
            websites.add(index, web)
            notifyItemChanged(index)
        }
    }

    interface WebSiteAdapterListener {
        fun onWebSiteItemClicked(website: Website)
        fun onWebSiteDelete(website: Website)
        fun onWebSiteShare(website: Website)
        fun onWebSiteLongClicked(website: Website)
    }

    inner class WebSiteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var icon: ImageView

        lateinit var title: TextView

        lateinit var url: TextView

        lateinit var deleteIcon: ImageView

        lateinit var shareIcon: ImageView

        init {
            ButterKnife.bind(this, itemView)
            itemView.setOnClickListener {
                val website = getWebsite()
                if (website != null) {
                    listener.onWebSiteItemClicked(website)
                }
            }

            itemView.setOnLongClickListener {
                val website = getWebsite()
                if (website != null) {
                    listener.onWebSiteLongClicked(website)
                }
                true
            }

            deleteIcon.setOnClickListener {
                val website = getWebsite()
                if (website != null) {
                    websites.remove(website)
                    listener.onWebSiteDelete(website)
                    notifyDataSetChanged()
                }
            }

            shareIcon.setOnClickListener {
                val website = getWebsite()
                if (website != null) {
                    listener.onWebSiteShare(website)
                }
            }
        }

        private fun getWebsite(): Website? {
            val position = adapterPosition
            return if (position != RecyclerView.NO_POSITION) {
                websites[position]
            } else {
                null
            }
        }
    }
}
