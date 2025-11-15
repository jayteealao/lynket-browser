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
package arun.com.chromer.payments

import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import arun.com.chromer.R
import arun.com.chromer.payments.billing.*
import butterknife.BindView
import butterknife.ButterKnife
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import timber.log.Timber

class DonateActivity : AppCompatActivity(), IabBroadcastReceiver.IabBroadcastListener,
    DialogInterface.OnClickListener {

    private var coffeeDone = false
    private var lunchDone = false
    private var premiumDone = false

    private var helper: IabHelper? = null
    private var broadcastReceiver: IabBroadcastReceiver? = null

    // Callback for when a purchase is finished
    private val purchaseFinishedListener = IabHelper.OnIabPurchaseFinishedListener { result, purchase ->
        Timber.d("Purchase finished: %s, purchase: %s", result, purchase)

        // if we were disposed of in the meantime, quit.
        if (helper == null) return@OnIabPurchaseFinishedListener

        if (result.isFailure) {
            return@OnIabPurchaseFinishedListener
        }
        Timber.d("Purchase successful.")
    }

    // Listener that's called when we finish querying the items and subscriptions we own
    private val gotInventoryListener = IabHelper.QueryInventoryFinishedListener { result, inventory ->
        Timber.d("Query inventory finished.")

        // Have we been disposed of in the meantime? If so, quit.
        if (helper == null) return@QueryInventoryFinishedListener

        // Is it a failure?
        if (result.isFailure) {
            return@QueryInventoryFinishedListener
        }

        Timber.d("Query inventory was successful.")

        // Get coffee sku
        val coffeeSku = inventory.getSkuDetails(COFEE_SKU)
        val lunchSku = inventory.getSkuDetails(LUNCH_SKU)
        val premiumSku = inventory.getSkuDetails(PREMIUM_SKU)

        val list = listOfNotNull(coffeeSku, lunchSku, premiumSku)

        coffeeDone = inventory.getPurchase(COFEE_SKU) != null
        lunchDone = inventory.getPurchase(LUNCH_SKU) != null
        premiumDone = inventory.getPurchase(PREMIUM_SKU) != null

        if (coffeeDone || lunchDone || premiumDone) {
            findViewById<View>(R.id.thank_you).visibility = View.VISIBLE
        }
        loadData(list)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donate)

        // compute your public key and store it in base64EncodedPublicKey
        // TODO Fix play license key
        // helper = IabHelper(this, getString(R.string.play_license_key))

        // enable debug logging (for a production application, you should set this to false).
        helper?.enableDebugLogging(false)

        Timber.d("Starting setup.")
        helper?.startSetup { result ->
            Timber.d("Setup finished.")
            if (!result.isSuccess) {
                Timber.d("Problem setting up In-app Billing: %s", result)
                return@startSetup
            }
            // Have we been disposed of in the meantime? If so, quit.
            if (helper == null) return@startSetup

            broadcastReceiver = IabBroadcastReceiver(this@DonateActivity)
            val broadcastFilter = IntentFilter(IabBroadcastReceiver.ACTION)
            registerReceiver(broadcastReceiver, broadcastFilter)

            // IAB is fully set up. Now, let's get an inventory of stuff we own.
            Timber.d("Setup successful. Querying inventory.")
            val additionalSku = listOf(COFEE_SKU, LUNCH_SKU, PREMIUM_SKU)
            helper?.queryInventoryAsync(true, additionalSku, gotInventoryListener)
        }
    }

    private fun loadData(details: List<SkuDetails>) {
        val donateList = findViewById<RecyclerView>(R.id.donate_item_list)
        donateList.layoutManager = LinearLayoutManager(this)
        donateList.adapter = DonationAdapter(details)
    }

    private fun setGreen(holder: DonationAdapter.ViewHolder?) {
        holder?.let {
            val color = ContextCompat.getColor(this, R.color.donate_green)
            it.title.setTextColor(color)
            it.subtitle.setTextColor(color)
        }
    }

    private fun setBlack(holder: DonationAdapter.ViewHolder?) {
        holder?.let {
            val color = ContextCompat.getColor(this, R.color.material_dark_color)
            it.title.setTextColor(color)
            it.subtitle.setTextColor(color)
        }
    }

    // We're being destroyed. It's important to dispose of the helper here!
    override fun onDestroy() {
        super.onDestroy()

        // very important:
        broadcastReceiver?.let {
            unregisterReceiver(it)
        }

        // very important:
        Timber.d("Destroying helper.")
        helper?.dispose()
        helper = null
    }

    override fun receivedBroadcast() {
        // Received a broadcast notification that the inventory of items has changed
        Timber.d("Received broadcast notification. Querying inventory.")
        helper?.queryInventoryAsync(gotInventoryListener)
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        // Empty implementation
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (helper == null) return

        // Pass on the activity result to the helper for handling
        if (!helper!!.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data)
        } else {
            Timber.d("onActivityResult handled by IABUtil.")
        }
    }

    inner class DonationAdapter(private val details: List<SkuDetails>) :
        RecyclerView.Adapter<DonationAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(applicationContext)
                .inflate(R.layout.fragment_about_list_item_template, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val error = getString(R.string.couldnt_load_price)
            when (position) {
                0 -> {
                    if (coffeeDone) setGreen(holder) else setBlack(holder)
                    holder.title.text = getString(R.string.coffee)
                    holder.subtitle.text = details.getOrNull(0)?.price ?: error
                    holder.image.background = IconicsDrawable(applicationContext)
                        .icon(CommunityMaterial.Icon.cmd_coffee)
                        .color(ContextCompat.getColor(applicationContext, R.color.coffee_color))
                        .sizeDp(ICON_SIZE_DP)
                }
                1 -> {
                    if (lunchDone) setGreen(holder) else setBlack(holder)
                    holder.title.text = getString(R.string.lunch)
                    holder.subtitle.text = details.getOrNull(1)?.price ?: error
                    holder.image.background = IconicsDrawable(applicationContext)
                        .icon(CommunityMaterial.Icon.cmd_food)
                        .color(ContextCompat.getColor(applicationContext, R.color.lunch_color))
                        .sizeDp(ICON_SIZE_DP)
                }
                2 -> {
                    if (premiumDone) setGreen(holder) else setBlack(holder)
                    holder.title.text = getString(R.string.premium_donation)
                    holder.subtitle.text = details.getOrNull(2)?.price ?: error
                    holder.image.background = IconicsDrawable(applicationContext)
                        .icon(CommunityMaterial.Icon.cmd_cash_usd)
                        .color(ContextCompat.getColor(applicationContext, R.color.premium_color))
                        .sizeDp(ICON_SIZE_DP)
                }
            }
        }

        override fun getItemCount() = details.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            @BindView(R.id.about_row_item_image)
            lateinit var image: ImageView

            @BindView(R.id.about_app_title)
            lateinit var title: TextView

            @BindView(R.id.about_app_subtitle)
            lateinit var subtitle: TextView

            init {
                ButterKnife.bind(this, itemView)
                itemView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        when (position) {
                            0 -> helper?.launchPurchaseFlow(
                                this@DonateActivity,
                                COFEE_SKU,
                                RC_REQUEST,
                                purchaseFinishedListener,
                                "coffee"
                            )
                            1 -> helper?.launchPurchaseFlow(
                                this@DonateActivity,
                                LUNCH_SKU,
                                RC_REQUEST,
                                purchaseFinishedListener,
                                "lunch"
                            )
                            2 -> helper?.launchPurchaseFlow(
                                this@DonateActivity,
                                PREMIUM_SKU,
                                RC_REQUEST,
                                purchaseFinishedListener,
                                "premium"
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val COFEE_SKU = "coffee_small"
        private const val LUNCH_SKU = "lunch_mega"
        private const val PREMIUM_SKU = "premium_donation"
        // (arbitrary) request code for the purchase flow
        private const val RC_REQUEST = 10001
        private const val ICON_SIZE_DP = 24
    }
}
