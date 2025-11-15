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

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.RemoteException
import android.text.TextUtils
import android.util.Log
import com.android.vending.billing.IInAppBillingService
import org.json.JSONException
import timber.log.Timber

/**
 * Provides convenience methods for in-app billing. You can create one instance of this
 * class for your application and use it to process in-app billing operations.
 * It provides synchronous (blocking) and asynchronous (non-blocking) methods for
 * many common in-app billing operations, as well as automatic signature
 * verification.
 *
 * After instantiating, you must perform setup in order to start using the object.
 * To perform setup, call the [startSetup] method and provide a listener;
 * that listener will be notified when setup is complete, after which (and not before)
 * you may call other methods.
 *
 * After setup is complete, you will typically want to request an inventory of owned
 * items and subscriptions. See [queryInventory], [queryInventoryAsync]
 * and related methods.
 *
 * When you are done with this object, don't forget to call [dispose]
 * to ensure proper cleanup. This object holds a binding to the in-app billing
 * service, which will leak unless you dispose of it correctly. If you created
 * the object on an Activity's onCreate method, then the recommended
 * place to dispose of it is the Activity's onDestroy method.
 *
 * A note about threading: When using this object from a background thread, you may
 * call the blocking versions of methods; when using from a UI thread, call
 * only the asynchronous versions and handle the results via callbacks.
 * Also, notice that you can only call one asynchronous operation at a time;
 * attempting to start a second asynchronous operation while the first one
 * has not yet completed will result in an exception being thrown.
 */
@Suppress("ALL")
class IabHelper(ctx: Context, base64PublicKey: String) {

    // Is debug logging enabled?
    private var debugLog = false
    private var debugTag = "IabHelper"

    // Is setup done?
    private var setupDone = false

    // Has this object been disposed of? (If so, we should ignore callbacks, etc)
    private var disposed = false

    // Are subscriptions supported?
    private var subscriptionsSupported = false

    // Is subscription update supported?
    private var subscriptionUpdateSupported = false

    // Is an asynchronous operation in progress?
    // (only one at a time can be in progress)
    private var asyncInProgress = false

    // (for logging/debugging)
    // if asyncInProgress == true, what asynchronous operation is in progress?
    private var asyncOperation = ""

    // Context we were passed during initialization
    private var context: Context? = ctx.applicationContext

    // Connection to the service
    private var service: IInAppBillingService? = null
    private var serviceConn: ServiceConnection? = null

    // The request code used to launch purchase flow
    private var requestCode = 0

    // The item type of the current purchase flow
    private var purchasingItemType: String? = null

    // Public key for verifying signature, in base64 encoding
    private var signatureBase64: String? = base64PublicKey

    // The listener registered on launchPurchaseFlow, which we have to call back when
    // the purchase finishes
    private var purchaseListener: OnIabPurchaseFinishedListener? = null

    init {
        logDebug("IAB helper created.")
    }

    /**
     * Enables or disable debug logging through LogCat.
     */
    fun enableDebugLogging(enable: Boolean, tag: String) {
        checkNotDisposed()
        debugLog = enable
        debugTag = tag
    }

    fun enableDebugLogging(enable: Boolean) {
        checkNotDisposed()
        debugLog = enable
    }

    /**
     * Starts the setup process. This will start up the setup process asynchronously.
     * You will be notified through the listener when the setup process is complete.
     * This method is safe to call from a UI thread.
     *
     * @param listener The listener to notify when the setup process is complete.
     */
    fun startSetup(listener: OnIabSetupFinishedListener?) {
        // If already set up, can't do it again.
        checkNotDisposed()
        check(!setupDone) { "IAB helper is already set up." }

        // Connection to IAB service
        logDebug("Starting in-app billing setup.")
        serviceConn = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName) {
                logDebug("Billing service disconnected.")
                service = null
            }

            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (disposed) return
                logDebug("Billing service connected.")
                service = IInAppBillingService.Stub.asInterface(binder)
                val packageName = context?.packageName ?: return
                try {
                    logDebug("Checking for in-app billing 3 support.")

                    // check for in-app billing v3 support
                    var response = service!!.isBillingSupported(3, packageName, ITEM_TYPE_INAPP)
                    if (response != BILLING_RESPONSE_RESULT_OK) {
                        listener?.onIabSetupFinished(
                            IabResult(
                                response,
                                "Error checking for billing v3 support."
                            )
                        )

                        // if in-app purchases aren't supported, neither are subscriptions
                        subscriptionsSupported = false
                        subscriptionUpdateSupported = false
                        return
                    } else {
                        logDebug("In-app billing version 3 supported for $packageName")
                    }

                    // Check for v5 subscriptions support. This is needed for
                    // getBuyIntentToReplaceSku which allows for subscription update
                    response = service!!.isBillingSupported(5, packageName, ITEM_TYPE_SUBS)
                    if (response == BILLING_RESPONSE_RESULT_OK) {
                        logDebug("Subscription re-signup AVAILABLE.")
                        subscriptionUpdateSupported = true
                    } else {
                        logDebug("Subscription re-signup not available.")
                        subscriptionUpdateSupported = false
                    }

                    if (subscriptionUpdateSupported) {
                        subscriptionsSupported = true
                    } else {
                        // check for v3 subscriptions support
                        response = service!!.isBillingSupported(3, packageName, ITEM_TYPE_SUBS)
                        if (response == BILLING_RESPONSE_RESULT_OK) {
                            logDebug("Subscriptions AVAILABLE.")
                            subscriptionsSupported = true
                        } else {
                            logDebug("Subscriptions NOT AVAILABLE. Response: $response")
                            subscriptionsSupported = false
                            subscriptionUpdateSupported = false
                        }
                    }

                    setupDone = true
                } catch (e: RemoteException) {
                    listener?.onIabSetupFinished(
                        IabResult(
                            IABHELPER_REMOTE_EXCEPTION,
                            "RemoteException while setting up in-app billing."
                        )
                    )
                    e.printStackTrace()
                    return
                }

                listener?.onIabSetupFinished(IabResult(BILLING_RESPONSE_RESULT_OK, "Setup successful."))
            }
        }

        val serviceIntent = Intent("com.android.vending.billing.InAppBillingService.BIND")
        serviceIntent.setPackage("com.android.vending")
        if (context?.packageManager?.queryIntentServices(serviceIntent, 0)?.isNotEmpty() == true) {
            // service available to handle that Intent
            context?.bindService(serviceIntent, serviceConn!!, Context.BIND_AUTO_CREATE)
        } else {
            // no service available to handle that Intent
            listener?.onIabSetupFinished(
                IabResult(
                    BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE,
                    "Billing service unavailable on device."
                )
            )
        }
    }

    /**
     * Dispose of object, releasing resources. It's very important to call this
     * method when you are done with this object. It will release any resources
     * used by it such as service connections. Naturally, once the object is
     * disposed of, it can't be used again.
     */
    fun dispose() {
        logDebug("Disposing.")
        setupDone = false
        serviceConn?.let {
            logDebug("Unbinding from service.")
            context?.unbindService(it)
        }
        disposed = true
        context = null
        serviceConn = null
        service = null
        purchaseListener = null
    }

    private fun checkNotDisposed() {
        check(!disposed) { "IabHelper was disposed of, so it cannot be used." }
    }

    /**
     * Returns whether subscriptions are supported.
     */
    fun subscriptionsSupported(): Boolean {
        checkNotDisposed()
        return subscriptionsSupported
    }

    fun launchPurchaseFlow(
        act: Activity,
        sku: String,
        requestCode: Int,
        listener: OnIabPurchaseFinishedListener
    ) {
        launchPurchaseFlow(act, sku, requestCode, listener, "")
    }

    fun launchPurchaseFlow(
        act: Activity,
        sku: String,
        requestCode: Int,
        listener: OnIabPurchaseFinishedListener,
        extraData: String
    ) {
        launchPurchaseFlow(act, sku, ITEM_TYPE_INAPP, null, requestCode, listener, extraData)
    }

    fun launchSubscriptionPurchaseFlow(
        act: Activity,
        sku: String,
        requestCode: Int,
        listener: OnIabPurchaseFinishedListener
    ) {
        launchSubscriptionPurchaseFlow(act, sku, requestCode, listener, "")
    }

    fun launchSubscriptionPurchaseFlow(
        act: Activity,
        sku: String,
        requestCode: Int,
        listener: OnIabPurchaseFinishedListener,
        extraData: String
    ) {
        launchPurchaseFlow(act, sku, ITEM_TYPE_SUBS, null, requestCode, listener, extraData)
    }

    /**
     * Initiate the UI flow for an in-app purchase. Call this method to initiate an in-app purchase,
     * which will involve bringing up the Google Play screen. The calling activity will be paused
     * while the user interacts with Google Play, and the result will be delivered via the
     * activity's [Activity.onActivityResult] method, at which point you must call
     * this object's [handleActivityResult] method to continue the purchase flow. This method
     * MUST be called from the UI thread of the Activity.
     *
     * @param act         The calling activity.
     * @param sku         The sku of the item to purchase.
     * @param itemType    indicates if it's a product or a subscription (ITEM_TYPE_INAPP or
     *                    ITEM_TYPE_SUBS)
     * @param oldSkus     A list of SKUs which the new SKU is replacing or null if there are none
     * @param requestCode A request code (to differentiate from other responses -- as in
     *                    [Activity.startActivityForResult]).
     * @param listener    The listener to notify when the purchase process finishes
     * @param extraData   Extra data (developer payload), which will be returned with the purchase
     *                    data when the purchase completes. This extra data will be permanently bound to that
     *                    purchase and will always be returned when the purchase is queried.
     */
    fun launchPurchaseFlow(
        act: Activity,
        sku: String,
        itemType: String,
        oldSkus: List<String>?,
        requestCode: Int,
        listener: OnIabPurchaseFinishedListener?,
        extraData: String
    ) {
        checkNotDisposed()
        checkSetupDone("launchPurchaseFlow")
        flagStartAsync("launchPurchaseFlow")

        if (itemType == ITEM_TYPE_SUBS && !subscriptionsSupported) {
            val r = IabResult(
                IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE,
                "Subscriptions are not available."
            )
            flagEndAsync()
            listener?.onIabPurchaseFinished(r, null)
            return
        }

        try {
            logDebug("Constructing buy intent for $sku, item type: $itemType")
            val buyIntentBundle: Bundle = if (oldSkus.isNullOrEmpty()) {
                // Purchasing a new item or subscription re-signup
                service!!.getBuyIntent(3, context!!.packageName, sku, itemType, extraData)
            } else {
                // Subscription upgrade/downgrade
                if (!subscriptionUpdateSupported) {
                    val r = IabResult(
                        IABHELPER_SUBSCRIPTION_UPDATE_NOT_AVAILABLE,
                        "Subscription updates are not available."
                    )
                    flagEndAsync()
                    listener?.onIabPurchaseFinished(r, null)
                    return
                }
                service!!.getBuyIntentToReplaceSkus(
                    5,
                    context!!.packageName,
                    oldSkus,
                    sku,
                    itemType,
                    extraData
                )
            }

            val response = getResponseCodeFromBundle(buyIntentBundle)
            if (response != BILLING_RESPONSE_RESULT_OK) {
                logError("Unable to buy item, Error response: ${getResponseDesc(response)}")
                flagEndAsync()
                val result = IabResult(response, "Unable to buy item")
                listener?.onIabPurchaseFinished(result, null)
                return
            }

            val pendingIntent = buyIntentBundle.getParcelable<PendingIntent>(RESPONSE_BUY_INTENT)
            logDebug("Launching buy intent for $sku. Request code: $requestCode")
            this.requestCode = requestCode
            purchaseListener = listener
            purchasingItemType = itemType
            act.startIntentSenderForResult(
                pendingIntent?.intentSender,
                requestCode, Intent(),
                0, 0, 0
            )
        } catch (e: SendIntentException) {
            logError("SendIntentException while launching purchase flow for sku $sku")
            e.printStackTrace()
            flagEndAsync()

            val result = IabResult(IABHELPER_SEND_INTENT_FAILED, "Failed to send intent.")
            listener?.onIabPurchaseFinished(result, null)
        } catch (e: RemoteException) {
            logError("RemoteException while launching purchase flow for sku $sku")
            e.printStackTrace()
            flagEndAsync()

            val result = IabResult(IABHELPER_REMOTE_EXCEPTION, "Remote exception while starting purchase flow")
            listener?.onIabPurchaseFinished(result, null)
        }
    }

    /**
     * Handles an activity result that's part of the purchase flow in in-app billing. If you
     * are calling [launchPurchaseFlow], then you must call this method from your
     * Activity's [Activity.onActivityResult] method. This method
     * MUST be called from the UI thread of the Activity.
     *
     * @param requestCode The requestCode as you received it.
     * @param resultCode  The resultCode as you received it.
     * @param data        The data (Intent) as you received it.
     * @return Returns true if the result was related to a purchase flow and was handled;
     * false if the result was not related to a purchase, in which case you should
     * handle it normally.
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != this.requestCode) return false

        checkNotDisposed()
        checkSetupDone("handleActivityResult")

        // end of async purchase operation that started on launchPurchaseFlow
        flagEndAsync()

        if (data == null) {
            logError("Null data in IAB activity result.")
            val result = IabResult(IABHELPER_BAD_RESPONSE, "Null data in IAB result")
            purchaseListener?.onIabPurchaseFinished(result, null)
            return true
        }

        val responseCode = getResponseCodeFromIntent(data)
        val purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA)
        val dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE)

        if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
            logDebug("Successful resultcode from purchase activity.")
            logDebug("Purchase data: $purchaseData")
            logDebug("Data signature: $dataSignature")
            logDebug("Extras: ${data.extras}")
            logDebug("Expected item type: $purchasingItemType")

            if (purchaseData == null || dataSignature == null) {
                logError("BUG: either purchaseData or dataSignature is null.")
                logDebug("Extras: ${data.extras}")
                val result = IabResult(IABHELPER_UNKNOWN_ERROR, "IAB returned null purchaseData or dataSignature")
                purchaseListener?.onIabPurchaseFinished(result, null)
                return true
            }

            var purchase: Purchase? = null
            try {
                purchase = Purchase(purchasingItemType!!, purchaseData, dataSignature)
                val sku = purchase.sku

                // Verify signature
                if (!Security.verifyPurchase(signatureBase64, purchaseData, dataSignature)) {
                    logError("Purchase signature verification FAILED for sku $sku")
                    val result = IabResult(
                        IABHELPER_VERIFICATION_FAILED,
                        "Signature verification failed for sku $sku"
                    )
                    purchaseListener?.onIabPurchaseFinished(result, purchase)
                    return true
                }
                logDebug("Purchase signature successfully verified.")
            } catch (e: JSONException) {
                logError("Failed to parse purchase data.")
                e.printStackTrace()
                val result = IabResult(IABHELPER_BAD_RESPONSE, "Failed to parse purchase data.")
                purchaseListener?.onIabPurchaseFinished(result, null)
                return true
            }

            purchaseListener?.onIabPurchaseFinished(
                IabResult(BILLING_RESPONSE_RESULT_OK, "Success"),
                purchase
            )
        } else if (resultCode == Activity.RESULT_OK) {
            // result code was OK, but in-app billing response was not OK.
            logDebug("Result code was OK but in-app billing response was not OK: ${getResponseDesc(responseCode)}")
            val result = IabResult(responseCode, "Problem purchashing item.")
            purchaseListener?.onIabPurchaseFinished(result, null)
        } else if (resultCode == Activity.RESULT_CANCELED) {
            logDebug("Purchase canceled - Response: ${getResponseDesc(responseCode)}")
            val result = IabResult(IABHELPER_USER_CANCELLED, "User canceled.")
            purchaseListener?.onIabPurchaseFinished(result, null)
        } else {
            logError("Purchase failed. Result code: $resultCode. Response: ${getResponseDesc(responseCode)}")
            val result = IabResult(IABHELPER_UNKNOWN_PURCHASE_RESPONSE, "Unknown purchase response.")
            purchaseListener?.onIabPurchaseFinished(result, null)
        }
        return true
    }

    @Throws(IabException::class)
    fun queryInventory(querySkuDetails: Boolean, moreSkus: List<String>?): Inventory {
        return queryInventory(querySkuDetails, moreSkus, null)
    }

    /**
     * Queries the inventory. This will query all owned items from the server, as well as
     * information on additional skus, if specified. This method may block or take long to execute.
     * Do not call from a UI thread. For that, use the non-blocking version [queryInventoryAsync].
     *
     * @param querySkuDetails if true, SKU details (price, description, etc) will be queried as well
     *                        as purchase information.
     * @param moreItemSkus    additional PRODUCT skus to query information on, regardless of ownership.
     *                        Ignored if null or if querySkuDetails is false.
     * @param moreSubsSkus    additional SUBSCRIPTIONS skus to query information on, regardless of ownership.
     *                        Ignored if null or if querySkuDetails is false.
     * @throws IabException if a problem occurs while refreshing the inventory.
     */
    @Throws(IabException::class)
    fun queryInventory(
        querySkuDetails: Boolean,
        moreItemSkus: List<String>?,
        moreSubsSkus: List<String>?
    ): Inventory {
        checkNotDisposed()
        checkSetupDone("queryInventory")
        try {
            val inv = Inventory()
            var r = queryPurchases(inv, ITEM_TYPE_INAPP)
            if (r != BILLING_RESPONSE_RESULT_OK) {
                throw IabException(r, "Error refreshing inventory (querying owned items).")
            }

            if (querySkuDetails) {
                r = querySkuDetails(ITEM_TYPE_INAPP, inv, moreItemSkus)
                if (r != BILLING_RESPONSE_RESULT_OK) {
                    throw IabException(r, "Error refreshing inventory (querying prices of items).")
                }
            }

            // if subscriptions are supported, then also query for subscriptions
            if (subscriptionsSupported) {
                r = queryPurchases(inv, ITEM_TYPE_SUBS)
                if (r != BILLING_RESPONSE_RESULT_OK) {
                    throw IabException(r, "Error refreshing inventory (querying owned subscriptions).")
                }

                if (querySkuDetails) {
                    r = querySkuDetails(ITEM_TYPE_SUBS, inv, moreItemSkus)
                    if (r != BILLING_RESPONSE_RESULT_OK) {
                        throw IabException(r, "Error refreshing inventory (querying prices of subscriptions).")
                    }
                }
            }

            return inv
        } catch (e: RemoteException) {
            throw IabException(IABHELPER_REMOTE_EXCEPTION, "Remote exception while refreshing inventory.", e)
        } catch (e: JSONException) {
            throw IabException(IABHELPER_BAD_RESPONSE, "Error parsing JSON response while refreshing inventory.", e)
        }
    }

    /**
     * Asynchronous wrapper for inventory query. This will perform an inventory
     * query as described in [queryInventory], but will do so asynchronously
     * and call back the specified listener upon completion. This method is safe to
     * call from a UI thread.
     *
     * @param querySkuDetails as in [queryInventory]
     * @param moreSkus        as in [queryInventory]
     * @param listener        The listener to notify when the refresh operation completes.
     */
    fun queryInventoryAsync(
        querySkuDetails: Boolean,
        moreSkus: List<String>?,
        listener: QueryInventoryFinishedListener?
    ) {
        val handler = Handler()
        checkNotDisposed()
        checkSetupDone("queryInventory")
        flagStartAsync("refresh inventory")
        Thread {
            var result = IabResult(BILLING_RESPONSE_RESULT_OK, "Inventory refresh successful.")
            var inv: Inventory? = null
            try {
                inv = queryInventory(querySkuDetails, moreSkus)
            } catch (ex: IabException) {
                result = ex.result
            }

            flagEndAsync()

            if (!disposed && listener != null) {
                handler.post {
                    listener.onQueryInventoryFinished(result, inv)
                }
            }
        }.start()
    }

    fun queryInventoryAsync(listener: QueryInventoryFinishedListener?) {
        queryInventoryAsync(true, null, listener)
    }

    fun queryInventoryAsync(querySkuDetails: Boolean, listener: QueryInventoryFinishedListener?) {
        queryInventoryAsync(querySkuDetails, null, listener)
    }

    /**
     * Consumes a given in-app product. Consuming can only be done on an item
     * that's owned, and as a result of consumption, the user will no longer own it.
     * This method may block or take long to return. Do not call from the UI thread.
     * For that, see [consumeAsync].
     *
     * @param itemInfo The PurchaseInfo that represents the item to consume.
     * @throws IabException if there is a problem during consumption.
     */
    @Throws(IabException::class)
    internal fun consume(itemInfo: Purchase) {
        checkNotDisposed()
        checkSetupDone("consume")

        if (itemInfo.itemType != ITEM_TYPE_INAPP) {
            throw IabException(
                IABHELPER_INVALID_CONSUMPTION,
                "Items of type '${itemInfo.itemType}' can't be consumed."
            )
        }

        try {
            val token = itemInfo.token
            val sku = itemInfo.sku
            if (token.isEmpty()) {
                logError("Can't consume $sku. No token.")
                throw IabException(
                    IABHELPER_MISSING_TOKEN,
                    "PurchaseInfo is missing token for sku: $sku $itemInfo"
                )
            }

            logDebug("Consuming sku: $sku, token: $token")
            val response = service!!.consumePurchase(3, context!!.packageName, token)
            if (response == BILLING_RESPONSE_RESULT_OK) {
                logDebug("Successfully consumed sku: $sku")
            } else {
                logDebug("Error consuming consuming sku $sku. ${getResponseDesc(response)}")
                throw IabException(response, "Error consuming sku $sku")
            }
        } catch (e: RemoteException) {
            throw IabException(
                IABHELPER_REMOTE_EXCEPTION,
                "Remote exception while consuming. PurchaseInfo: $itemInfo",
                e
            )
        }
    }

    /**
     * Asynchronous wrapper to item consumption. Works like [consume], but
     * performs the consumption in the background and notifies completion through
     * the provided listener. This method is safe to call from a UI thread.
     *
     * @param purchase The purchase to be consumed.
     * @param listener The listener to notify when the consumption operation finishes.
     */
    fun consumeAsync(purchase: Purchase, listener: OnConsumeFinishedListener?) {
        checkNotDisposed()
        checkSetupDone("consume")
        val purchases = listOf(purchase)
        consumeAsyncInternal(purchases, listener, null)
    }

    /**
     * Same as [consumeAsync], but for multiple items at once.
     *
     * @param purchases The list of PurchaseInfo objects representing the purchases to consume.
     * @param listener  The listener to notify when the consumption operation finishes.
     */
    fun consumeAsync(purchases: List<Purchase>, listener: OnConsumeMultiFinishedListener?) {
        checkNotDisposed()
        checkSetupDone("consume")
        consumeAsyncInternal(purchases, null, listener)
    }

    // Checks that setup was done; if not, throws an exception.
    private fun checkSetupDone(operation: String) {
        if (!setupDone) {
            logError("Illegal state for operation ($operation): IAB helper is not set up.")
            throw IllegalStateException("IAB helper is not set up. Can't perform operation: $operation")
        }
    }

    // Workaround to bug where sometimes response codes come as Long instead of Integer
    private fun getResponseCodeFromBundle(b: Bundle): Int {
        val o = b.get(RESPONSE_CODE)
        return when {
            o == null -> {
                logDebug("Bundle with null response code, assuming OK (known issue)")
                BILLING_RESPONSE_RESULT_OK
            }
            o is Int -> o
            o is Long -> o.toInt()
            else -> {
                logError("Unexpected type for bundle response code.")
                logError(o.javaClass.name)
                throw RuntimeException("Unexpected type for bundle response code: ${o.javaClass.name}")
            }
        }
    }

    // Workaround to bug where sometimes response codes come as Long instead of Integer
    private fun getResponseCodeFromIntent(i: Intent): Int {
        val o = i.extras?.get(RESPONSE_CODE)
        return when {
            o == null -> {
                logError("Intent with no response code, assuming OK (known issue)")
                BILLING_RESPONSE_RESULT_OK
            }
            o is Int -> o
            o is Long -> o.toInt()
            else -> {
                logError("Unexpected type for intent response code.")
                logError(o.javaClass.name)
                throw RuntimeException("Unexpected type for intent response code: ${o.javaClass.name}")
            }
        }
    }

    private fun flagStartAsync(operation: String) {
        check(!asyncInProgress) {
            "Can't start async operation ($operation) because another async operation($asyncOperation) is in progress."
        }
        asyncOperation = operation
        asyncInProgress = true
        logDebug("Starting async operation: $operation")
    }

    private fun flagEndAsync() {
        logDebug("Ending async operation: $asyncOperation")
        asyncOperation = ""
        asyncInProgress = false
    }

    @Throws(JSONException::class, RemoteException::class)
    private fun queryPurchases(inv: Inventory, itemType: String): Int {
        // Query purchases
        if (context == null) {
            Timber.d("User cancelled")
            return BILLING_RESPONSE_RESULT_USER_CANCELED
        }
        logDebug("Querying owned items, item type: $itemType")
        logDebug("Package name: ${context!!.packageName}")
        var verificationFailed = false
        var continueToken: String? = null

        do {
            logDebug("Calling getPurchases with continuation token: $continueToken")
            val ownedItems = service!!.getPurchases(3, context!!.packageName, itemType, continueToken)

            val response = getResponseCodeFromBundle(ownedItems)
            logDebug("Owned items response: $response")
            if (response != BILLING_RESPONSE_RESULT_OK) {
                logDebug("getPurchases() failed: ${getResponseDesc(response)}")
                return response
            }
            if (!ownedItems.containsKey(RESPONSE_INAPP_ITEM_LIST) ||
                !ownedItems.containsKey(RESPONSE_INAPP_PURCHASE_DATA_LIST) ||
                !ownedItems.containsKey(RESPONSE_INAPP_SIGNATURE_LIST)
            ) {
                logError("Bundle returned from getPurchases() doesn't contain required fields.")
                return IABHELPER_BAD_RESPONSE
            }

            val ownedSkus = ownedItems.getStringArrayList(RESPONSE_INAPP_ITEM_LIST)
            val purchaseDataList = ownedItems.getStringArrayList(RESPONSE_INAPP_PURCHASE_DATA_LIST)
            val signatureList = ownedItems.getStringArrayList(RESPONSE_INAPP_SIGNATURE_LIST)

            for (i in 0 until (purchaseDataList?.size ?: 0)) {
                val purchaseData = purchaseDataList!![i]
                val signature = signatureList!![i]
                val sku = ownedSkus!![i]
                if (Security.verifyPurchase(signatureBase64, purchaseData, signature)) {
                    logDebug("Sku is owned: $sku")
                    val purchase = Purchase(itemType, purchaseData, signature)

                    if (TextUtils.isEmpty(purchase.token)) {
                        logWarn("BUG: empty/null token!")
                        logDebug("Purchase data: $purchaseData")
                    }

                    // Record ownership and token
                    inv.addPurchase(purchase)
                } else {
                    logWarn("Purchase signature verification **FAILED**. Not adding item.")
                    logDebug("   Purchase data: $purchaseData")
                    logDebug("   Signature: $signature")
                    verificationFailed = true
                }
            }

            continueToken = ownedItems.getString(INAPP_CONTINUATION_TOKEN)
            logDebug("Continuation token: $continueToken")
        } while (!TextUtils.isEmpty(continueToken))

        return if (verificationFailed) IABHELPER_VERIFICATION_FAILED else BILLING_RESPONSE_RESULT_OK
    }

    @Throws(RemoteException::class, JSONException::class)
    private fun querySkuDetails(itemType: String, inv: Inventory, moreSkus: List<String>?): Int {
        logDebug("Querying SKU details.")
        val skuList = ArrayList<String>()
        skuList.addAll(inv.getAllOwnedSkus(itemType))
        if (moreSkus != null) {
            for (sku in moreSkus) {
                if (!skuList.contains(sku)) {
                    skuList.add(sku)
                }
            }
        }

        if (skuList.isEmpty()) {
            logDebug("queryPrices: nothing to do because there are no SKUs.")
            return BILLING_RESPONSE_RESULT_OK
        }

        // Split the sku list in blocks of no more than 20 elements.
        val packs = ArrayList<ArrayList<String>>()
        val n = skuList.size / 20
        val mod = skuList.size % 20
        for (i in 0 until n) {
            val tempList = ArrayList<String>()
            for (s in skuList.subList(i * 20, i * 20 + 20)) {
                tempList.add(s)
            }
            packs.add(tempList)
        }
        if (mod != 0) {
            val tempList = ArrayList<String>()
            for (s in skuList.subList(n * 20, n * 20 + mod)) {
                tempList.add(s)
            }
            packs.add(tempList)
        }

        for (skuPartList in packs) {
            val querySkus = Bundle()
            querySkus.putStringArrayList(GET_SKU_DETAILS_ITEM_LIST, skuPartList)
            val skuDetails = service!!.getSkuDetails(3, context!!.packageName, itemType, querySkus)

            if (!skuDetails.containsKey(RESPONSE_GET_SKU_DETAILS_LIST)) {
                val response = getResponseCodeFromBundle(skuDetails)
                if (response != BILLING_RESPONSE_RESULT_OK) {
                    logDebug("getSkuDetails() failed: ${getResponseDesc(response)}")
                    return response
                } else {
                    logError("getSkuDetails() returned a bundle with neither an error nor a detail list.")
                    return IABHELPER_BAD_RESPONSE
                }
            }

            val responseList = skuDetails.getStringArrayList(RESPONSE_GET_SKU_DETAILS_LIST)

            responseList?.forEach { thisResponse ->
                val d = SkuDetails(itemType, thisResponse)
                logDebug("Got sku details: $d")
                inv.addSkuDetails(d)
            }
        }

        return BILLING_RESPONSE_RESULT_OK
    }

    private fun consumeAsyncInternal(
        purchases: List<Purchase>,
        singleListener: OnConsumeFinishedListener?,
        multiListener: OnConsumeMultiFinishedListener?
    ) {
        val handler = Handler()
        flagStartAsync("consume")
        Thread {
            val results = mutableListOf<IabResult>()
            for (purchase in purchases) {
                try {
                    consume(purchase)
                    results.add(IabResult(BILLING_RESPONSE_RESULT_OK, "Successful consume of sku ${purchase.sku}"))
                } catch (ex: IabException) {
                    results.add(ex.result)
                }
            }

            flagEndAsync()
            if (!disposed && singleListener != null) {
                handler.post {
                    singleListener.onConsumeFinished(purchases[0], results[0])
                }
            }
            if (!disposed && multiListener != null) {
                handler.post {
                    multiListener.onConsumeMultiFinished(purchases, results)
                }
            }
        }.start()
    }

    private fun logDebug(msg: String) {
        if (debugLog) Log.d(debugTag, msg)
    }

    private fun logError(msg: String) {
        Log.e(debugTag, "In-app billing error: $msg")
    }

    private fun logWarn(msg: String) {
        Log.w(debugTag, "In-app billing warning: $msg")
    }

    /**
     * Callback for setup process. This listener's [onIabSetupFinished] method is called
     * when the setup process is complete.
     */
    interface OnIabSetupFinishedListener {
        /**
         * Called to notify that setup is complete.
         *
         * @param result The result of the setup process.
         */
        fun onIabSetupFinished(result: IabResult)
    }

    /**
     * Callback that notifies when a purchase is finished.
     */
    interface OnIabPurchaseFinishedListener {
        /**
         * Called to notify that an in-app purchase finished. If the purchase was successful,
         * then the sku parameter specifies which item was purchased. If the purchase failed,
         * the sku and extraData parameters may or may not be null, depending on how far the purchase
         * process went.
         *
         * @param result The result of the purchase.
         * @param info   The purchase information (null if purchase failed)
         */
        fun onIabPurchaseFinished(result: IabResult, info: Purchase?)
    }

    /**
     * Listener that notifies when an inventory query operation completes.
     */
    interface QueryInventoryFinishedListener {
        /**
         * Called to notify that an inventory query operation completed.
         *
         * @param result The result of the operation.
         * @param inv    The inventory.
         */
        fun onQueryInventoryFinished(result: IabResult, inv: Inventory?)
    }

    /**
     * Callback that notifies when a consumption operation finishes.
     */
    interface OnConsumeFinishedListener {
        /**
         * Called to notify that a consumption has finished.
         *
         * @param purchase The purchase that was (or was to be) consumed.
         * @param result   The result of the consumption operation.
         */
        fun onConsumeFinished(purchase: Purchase, result: IabResult)
    }

    /**
     * Callback that notifies when a multi-item consumption operation finishes.
     */
    interface OnConsumeMultiFinishedListener {
        /**
         * Called to notify that a consumption of multiple items has finished.
         *
         * @param purchases The purchases that were (or were to be) consumed.
         * @param results   The results of each consumption operation, corresponding to each
         *                  sku.
         */
        fun onConsumeMultiFinished(purchases: List<Purchase>, results: List<IabResult>)
    }

    companion object {
        // Billing response codes
        const val BILLING_RESPONSE_RESULT_OK = 0
        const val BILLING_RESPONSE_RESULT_USER_CANCELED = 1
        const val BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2
        const val BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3
        const val BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4
        const val BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5
        const val BILLING_RESPONSE_RESULT_ERROR = 6
        const val BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7
        const val BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8

        // IAB Helper error codes
        const val IABHELPER_ERROR_BASE = -1000
        const val IABHELPER_REMOTE_EXCEPTION = -1001
        const val IABHELPER_BAD_RESPONSE = -1002
        const val IABHELPER_VERIFICATION_FAILED = -1003
        const val IABHELPER_SEND_INTENT_FAILED = -1004
        const val IABHELPER_USER_CANCELLED = -1005
        const val IABHELPER_UNKNOWN_PURCHASE_RESPONSE = -1006
        const val IABHELPER_MISSING_TOKEN = -1007
        const val IABHELPER_UNKNOWN_ERROR = -1008
        const val IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE = -1009
        const val IABHELPER_INVALID_CONSUMPTION = -1010
        const val IABHELPER_SUBSCRIPTION_UPDATE_NOT_AVAILABLE = -1011

        // Keys for the responses from InAppBillingService
        const val RESPONSE_CODE = "RESPONSE_CODE"
        const val RESPONSE_GET_SKU_DETAILS_LIST = "DETAILS_LIST"
        const val RESPONSE_BUY_INTENT = "BUY_INTENT"
        const val RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA"
        const val RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE"
        const val RESPONSE_INAPP_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST"
        const val RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST"
        const val RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST"
        const val INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN"

        // Item types
        const val ITEM_TYPE_INAPP = "inapp"
        const val ITEM_TYPE_SUBS = "subs"

        // some fields on the getSkuDetails response bundle
        const val GET_SKU_DETAILS_ITEM_LIST = "ITEM_ID_LIST"
        const val GET_SKU_DETAILS_ITEM_TYPE_LIST = "ITEM_TYPE_LIST"

        /**
         * Returns a human-readable description for the given response code.
         *
         * @param code The response code
         * @return A human-readable string explaining the result code.
         * It also includes the result code numerically.
         */
        fun getResponseDesc(code: Int): String {
            val iabMsgs = ("0:OK/1:User Canceled/2:Unknown/" +
                    "3:Billing Unavailable/4:Item unavailable/" +
                    "5:Developer Error/6:Error/7:Item Already Owned/" +
                    "8:Item not owned").split("/")
            val iabhelperMsgs = ("0:OK/-1001:Remote exception during initialization/" +
                    "-1002:Bad response received/" +
                    "-1003:Purchase signature verification failed/" +
                    "-1004:Send intent failed/" +
                    "-1005:User cancelled/" +
                    "-1006:Unknown purchase response/" +
                    "-1007:Missing token/" +
                    "-1008:Unknown error/" +
                    "-1009:Subscriptions not available/" +
                    "-1010:Invalid consumption attempt").split("/")

            return when {
                code <= IABHELPER_ERROR_BASE -> {
                    val index = IABHELPER_ERROR_BASE - code
                    if (index >= 0 && index < iabhelperMsgs.size) iabhelperMsgs[index]
                    else "$code:Unknown IAB Helper Error"
                }
                code < 0 || code >= iabMsgs.size -> "$code:Unknown"
                else -> iabMsgs[code]
            }
        }
    }
}
