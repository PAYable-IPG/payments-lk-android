package lk.payments.sdk

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Opens a Payments.lk hosted checkout page in the browser. */
public object PaymentsLkCheckout {

    private val LOCAL_DEVELOPMENT_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
    private const val DEFAULT_SITE = "https://payments.lk"
    private val LINK_CODE = Regex("^[A-Za-z0-9_-]{8,64}$")
    private val PRICE_ID = Regex("^price_[0-9a-z]{20,32}$")

    /**
     * The address of a subscription link on the Payments.lk site, with the plan
     * to choose first when [plan] names one: what an app opens for a customer
     * to pick a plan and pay the first period. The link's success address, set
     * in the dashboard to your app's scheme, brings the customer back.
     *
     * @throws IllegalArgumentException if the code, the plan id or the site is not shaped like one.
     */
    @JvmStatic
    @JvmOverloads
    public fun subscriptionLinkUrl(code: String, plan: String? = null, locale: String? = null, site: String = DEFAULT_SITE): String {
        require(LINK_CODE.matches(code)) { "A subscription link's code is the last part of its address, as the dashboard shows it." }
        require(plan == null || PRICE_ID.matches(plan)) { "A plan is a price id, such as price_0123456789abcdefghij." }
        require(isAllowedCheckoutUrl(site) && !site.contains('?') && !site.contains('#')) { "The site must be an https origin." }
        val base = site.trimEnd('/')
        val lang = if (locale == "si" || locale == "ta") "/$locale" else ""
        val query = if (plan != null) "?plan=$plan" else ""
        return "$base$lang/s/$code$query"
    }

    /**
     * Opens a subscription link (see [subscriptionLinkUrl]) in a Custom Tab.
     * Your app hears about the new member from your backend, which receives
     * the customer.subscription.created webhook; the return to the app is not
     * proof of anything.
     */
    @JvmStatic
    @JvmOverloads
    public fun openSubscriptionLink(context: Context, code: String, plan: String? = null, locale: String? = null, toolbarColor: Int? = null, site: String = DEFAULT_SITE) {
        open(context, subscriptionLinkUrl(code, plan, locale, site), toolbarColor)
    }

    /**
     * Opens the customer portal from a portal session's url, which your backend
     * made with `POST /v1/billing_portal/sessions` and a returnUrl in your app's
     * scheme. The customer changes their card or plan, pauses or cancels there.
     *
     * @throws IllegalArgumentException if [portalUrl] is not https (see [isAllowedCheckoutUrl]).
     */
    @JvmStatic
    @JvmOverloads
    public fun openPortal(context: Context, portalUrl: String, toolbarColor: Int? = null) {
        open(context, portalUrl, toolbarColor)
    }

    /**
     * True for an https URL with a host and no user info, or an http URL on a
     * local development host (localhost, 127.0.0.1, or 10.0.2.2 which is the
     * host machine as seen from the Android emulator). Everything else,
     * including other schemes and unparseable strings, is false.
     */
    @JvmStatic
    public fun isAllowedCheckoutUrl(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return false
        }
        if (uri.isOpaque || uri.rawUserInfo != null) return false
        val host = uri.host
        if (host.isNullOrEmpty()) return false
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "https" -> true
            "http" -> host.lowercase(Locale.ROOT) in LOCAL_DEVELOPMENT_HOSTS
            else -> false
        }
    }

    /**
     * Opens [checkoutUrl] (the `url` your backend got from POST /v1/checkouts)
     * in a Custom Tab, or in the default browser through ACTION_VIEW when no
     * browser on the device supports Custom Tabs.
     *
     * Pass an Activity as [context] so the tab opens in the app's task.
     *
     * @throws IllegalArgumentException if [isAllowedCheckoutUrl] is false.
     * @throws android.content.ActivityNotFoundException if the device has no browser at all.
     */
    @JvmStatic
    @JvmOverloads
    public fun open(context: Context, checkoutUrl: String, toolbarColor: Int? = null) {
        require(isAllowedCheckoutUrl(checkoutUrl)) {
            "Refusing to open a checkout URL that is not https (or http on a local development host)."
        }
        val uri = Uri.parse(checkoutUrl)
        // Cross check the Android parser against java.net.URI so a URL that the
        // two parsers read differently is never opened.
        val checked = URI(checkoutUrl)
        require(
            uri.scheme.equals(checked.scheme, ignoreCase = true) &&
                uri.host.equals(checked.host, ignoreCase = true),
        ) {
            "Refusing to open a checkout URL that parses inconsistently."
        }

        val needsNewTask = context.findActivity() == null
        val customTabsPackage = CustomTabsClient.getPackageName(context, emptyList<String>())
        if (customTabsPackage != null) {
            val builder = CustomTabsIntent.Builder().setShowTitle(true)
            if (toolbarColor != null) {
                builder.setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build(),
                )
            }
            val customTab = builder.build()
            customTab.intent.setPackage(customTabsPackage)
            if (needsNewTask) customTab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTab.launchUrl(context, uri)
        } else {
            val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            if (needsNewTask) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(view)
        }
    }

    private fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findActivity()
        else -> null
    }
}
