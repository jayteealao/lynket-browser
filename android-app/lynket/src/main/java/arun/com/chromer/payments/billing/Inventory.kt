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

/**
 * Represents a block of information about in-app items.
 * An Inventory is returned by such methods as [IabHelper.queryInventory].
 */
@Suppress("ALL")
class Inventory {
    private val skuMap = mutableMapOf<String, SkuDetails>()
    private val purchaseMap = mutableMapOf<String, Purchase>()

    /**
     * Returns the listing details for an in-app product.
     */
    fun getSkuDetails(sku: String): SkuDetails? = skuMap[sku]

    /**
     * Returns purchase information for a given product, or null if there is no purchase.
     */
    fun getPurchase(sku: String): Purchase? = purchaseMap[sku]

    /**
     * Returns whether or not there exists a purchase of the given product.
     */
    fun hasPurchase(sku: String): Boolean = purchaseMap.containsKey(sku)

    /**
     * Return whether or not details about the given product are available.
     */
    fun hasDetails(sku: String): Boolean = skuMap.containsKey(sku)

    /**
     * Erase a purchase (locally) from the inventory, given its product ID. This just
     * modifies the Inventory object locally and has no effect on the server! This is
     * useful when you have an existing Inventory object which you know to be up to date,
     * and you have just consumed an item successfully, which means that erasing its
     * purchase data from the Inventory you already have is quicker than querying for
     * a new Inventory.
     */
    fun erasePurchase(sku: String) {
        purchaseMap.remove(sku)
    }

    /**
     * Returns a list of all owned product IDs.
     */
    internal fun getAllOwnedSkus(): List<String> = purchaseMap.keys.toList()

    /**
     * Returns a list of all owned product IDs of a given type
     */
    internal fun getAllOwnedSkus(itemType: String): List<String> =
        purchaseMap.values.filter { it.itemType == itemType }.map { it.sku }

    /**
     * Returns a list of all purchases.
     */
    internal fun getAllPurchases(): List<Purchase> = purchaseMap.values.toList()

    internal fun addSkuDetails(d: SkuDetails) {
        skuMap[d.sku] = d
    }

    internal fun addPurchase(p: Purchase) {
        purchaseMap[p.sku] = p
    }
}
