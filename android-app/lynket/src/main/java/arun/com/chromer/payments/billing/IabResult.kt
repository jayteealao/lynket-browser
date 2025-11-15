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

package arun.com.chromer.payments.billing

/**
 * Phase 7: Converted from Java to Kotlin
 *
 * Represents the result of an in-app billing operation.
 * A result is composed of a response code (an integer) and possibly a
 * message (String). You can get those by calling
 * [response] and [message], respectively. You
 * can also inquire whether a result is a success or a failure by
 * calling [isSuccess] and [isFailure].
 */
@Suppress("ALL")
class IabResult(val response: Int, message: String?) {
    val message: String

    init {
        this.message = if (message.isNullOrBlank()) {
            IabHelper.getResponseDesc(response)
        } else {
            "$message (response: ${IabHelper.getResponseDesc(response)})"
        }
    }

    @Deprecated("Use response property", ReplaceWith("response"))
    fun getResponse(): Int = response

    @Deprecated("Use message property", ReplaceWith("message"))
    fun getMessage(): String = message

    fun isSuccess(): Boolean = response == IabHelper.BILLING_RESPONSE_RESULT_OK

    fun isFailure(): Boolean = !isSuccess()

    override fun toString(): String = "IabResult: $message"
}
