package lk.payments.sdk

import android.net.Uri
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/** How a hosted checkout ended, as far as the app can tell from the device. */
public enum class CheckoutStatus {
    /** The hosted page redirected to successUrl with status=succeeded. */
    SUCCEEDED,

    /** The hosted page redirected to cancelUrl with status=failed. */
    FAILED,

    /** The hosted page redirected to cancelUrl with status=canceled. */
    CANCELED,

    /** The hosted page redirected to cancelUrl with status=expired. */
    EXPIRED,

    /**
     * The customer came back to the app without any redirect, for example by
     * closing the Custom Tab. The payment may still have completed.
     */
    DISMISSED,
}

/**
 * The outcome of a checkout as reported by the redirect back into the app.
 *
 * This is NOT proof of payment. Anyone can open a deep link into an app, and
 * another app can register the same URL scheme. Always confirm with your
 * backend, which calls GET /v1/checkouts/{id} and trusts payment.status, or
 * waits for the signed payment.succeeded webhook.
 *
 * [checkoutId] is null for [CheckoutStatus.DISMISSED] and only then.
 */
public data class CheckoutResult(
    public val status: CheckoutStatus,
    public val checkoutId: String?,
) {
    init {
        if (status == CheckoutStatus.DISMISSED) {
            require(checkoutId == null) { "A DISMISSED result has no checkout id." }
        } else {
            require(checkoutId != null && CHECKOUT_ID_PATTERN.matches(checkoutId)) {
                "A $status result needs a checkout id of the form chk_ followed by lowercase letters and digits."
            }
        }
    }

    public companion object {
        private val CHECKOUT_ID_PATTERN = Regex("^chk_[0-9a-z]{1,64}$")

        private const val PARAM_CHECKOUT = "checkout"
        private const val PARAM_STATUS = "status"

        /** The result reported when the customer returns without a redirect. */
        @JvmField
        public val DISMISSED: CheckoutResult = CheckoutResult(CheckoutStatus.DISMISSED, null)

        /**
         * Parses a redirect URL such as
         * `myshop://payments-lk/return?checkout=chk_abc123&status=succeeded`.
         *
         * Strict: returns null unless the scheme equals [expectedScheme]
         * (ignoring case), the URL is hierarchical, and the query holds exactly
         * one `checkout` matching `^chk_[0-9a-z]{1,64}$` and exactly one
         * `status` that is succeeded, failed, canceled or expired. Other query
         * parameters are ignored. Never returns a DISMISSED result.
         */
        @JvmStatic
        public fun fromUrl(url: String, expectedScheme: String): CheckoutResult? {
            if (expectedScheme.isBlank()) return null
            val uri = try {
                URI(url)
            } catch (e: URISyntaxException) {
                return null
            }
            val scheme = uri.scheme ?: return null
            if (!scheme.equals(expectedScheme, ignoreCase = true)) return null
            if (uri.isOpaque) return null
            val query = uri.rawQuery ?: return null

            var checkout: String? = null
            var status: String? = null
            for (pair in query.split('&')) {
                if (pair.isEmpty()) continue
                val separator = pair.indexOf('=')
                val rawName = if (separator < 0) pair else pair.substring(0, separator)
                val name = decode(rawName) ?: return null
                if (name != PARAM_CHECKOUT && name != PARAM_STATUS) continue
                if (separator < 0) return null
                val value = decode(pair.substring(separator + 1)) ?: return null
                if (name == PARAM_CHECKOUT) {
                    if (checkout != null) return null
                    checkout = value
                } else {
                    if (status != null) return null
                    status = value
                }
            }

            if (checkout == null || !CHECKOUT_ID_PATTERN.matches(checkout)) return null
            val parsedStatus = when (status) {
                "succeeded" -> CheckoutStatus.SUCCEEDED
                "failed" -> CheckoutStatus.FAILED
                "canceled" -> CheckoutStatus.CANCELED
                "expired" -> CheckoutStatus.EXPIRED
                else -> return null
            }
            return CheckoutResult(parsedStatus, checkout)
        }

        /** Android convenience for [fromUrl]; returns null for a null [uri]. */
        @JvmStatic
        public fun fromUri(uri: Uri?, expectedScheme: String): CheckoutResult? {
            if (uri == null) return null
            return fromUrl(uri.toString(), expectedScheme)
        }

        private fun decode(value: String): String? = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: UnsupportedEncodingException) {
            null
        }
    }
}
