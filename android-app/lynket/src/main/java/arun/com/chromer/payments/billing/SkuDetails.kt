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
 * Represents an in-app product's listing details.
 */
@Suppress("FieldCanBeLocal")
class SkuDetails {
    private val itemType: String
    val sku: String
    private val type: String
    val price: String
    private val priceAmountMicros: Long
    private val priceCurrencyCode: String
    private val title: String
    private val description: String
    private val json: String

    @Throws(JSONException::class)
    constructor(jsonSkuDetails: String) : this(IabHelper.ITEM_TYPE_INAPP, jsonSkuDetails)

    @Throws(JSONException::class)
    constructor(itemType: String, jsonSkuDetails: String) {
        this.itemType = itemType
        this.json = jsonSkuDetails
        val o = JSONObject(json)
        sku = o.optString("productId")
        type = o.optString("type")
        price = o.optString("price")
        priceAmountMicros = o.optLong("price_amount_micros")
        priceCurrencyCode = o.optString("price_currency_code")
        title = o.optString("title")
        description = o.optString("description")
    }

    fun getType(): String = type

    fun getPrice(): String = price

    fun getPriceAmountMicros(): Long = priceAmountMicros

    fun getPriceCurrencyCode(): String = priceCurrencyCode

    fun getTitle(): String = title

    fun getDescription(): String = description

    override fun toString(): String = "SkuDetails:$json"
}
