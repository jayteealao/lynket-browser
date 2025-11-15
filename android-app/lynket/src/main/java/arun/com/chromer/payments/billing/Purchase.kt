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
package arun.com.chromer.payments.billing

import org.json.JSONException
import org.json.JSONObject

/**
 * Represents an in-app billing purchase.
 */
@Suppress("ALL")
class Purchase(
    val itemType: String,
    jsonPurchaseInfo: String,
    val signature: String
) {
    val orderId: String
    val packageName: String
    val sku: String
    val purchaseTime: Long
    val purchaseState: Int
    val developerPayload: String
    val token: String
    val originalJson: String = jsonPurchaseInfo
    val isAutoRenewing: Boolean

    init {
        val o = JSONObject(originalJson)
        orderId = o.optString("orderId")
        packageName = o.optString("packageName")
        sku = o.optString("productId")
        purchaseTime = o.optLong("purchaseTime")
        purchaseState = o.optInt("purchaseState")
        developerPayload = o.optString("developerPayload")
        token = o.optString("token", o.optString("purchaseToken"))
        isAutoRenewing = o.optBoolean("autoRenewing")
    }

    override fun toString(): String = "PurchaseInfo(type:$itemType):$originalJson"
}
