# Payments.lk Android SDK

A small Kotlin library that opens a Payments.lk hosted checkout page from an Android app and reads the result when the page sends the customer back.

It does three things:

- `PaymentsLkCheckout.open` opens the hosted checkout `url` in a Custom Tab, or in the default browser when no browser on the device supports Custom Tabs. It refuses any URL that is not https (plain http is allowed only on `localhost`, `127.0.0.1` and `10.0.2.2` for local development).
- `CheckoutResult.fromUrl` / `fromUri` strictly parse the redirect deep link (`checkout=chk_...` and `status=succeeded|failed|canceled|expired`).
- `CheckoutLauncher` ties both to an Activity and also tells you when the customer came back without any redirect (`DISMISSED`).

The SDK never sees card data and never holds an API key. Card entry happens on the hosted page.

> **The redirect is not proof of payment.** Any app can fire a deep link at your activity, and another app can register the same URL scheme. Your app must confirm every result with your backend, which calls `GET /v1/checkouts/{id}` and trusts `payment.status`, or waits for the signed `payment.succeeded` webhook. See [Confirm on the server](#confirm-on-the-server).

Requirements: minSdk 23, compileSdk 36 or later in the app (required by `androidx.browser`), Kotlin or Java.

## Installation

The library is on Maven Central as `lk.payments:payments-lk-android`, signed, with its sources. New Android projects already list `mavenCentral()` in `settings.gradle.kts`, so add the dependency:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("lk.payments:payments-lk-android:0.1.1")
}
```

With Groovy: `implementation 'lk.payments:payments-lk-android:0.1.1'`. It brings in `androidx.browser` for Custom Tabs. The source is at [github.com/PAYable-IPG/payments-lk-android](https://github.com/PAYable-IPG/payments-lk-android).

## 1. Backend: create the checkout

Secret keys (`sk_test_...`, `sk_live_...`) never go in the app. The app asks **your** backend for a checkout; your backend decides the amount from its own order data and calls Payments.lk:

```http
POST https://api.payments.lk/v1/checkouts
Authorization: Bearer sk_test_...
Idempotency-Key: <unique per checkout attempt, for example the order id plus an attempt number>
Content-Type: application/json

{
  "amountCents": 250000,
  "description": "Order 1042",
  "reference": "order_1042",
  "successUrl": "myshop://payments-lk/return",
  "cancelUrl": "myshop://payments-lk/return"
}
```

`amountCents` is an integer in LKR cents (250000 is Rs. 2,500). `reference` is optional. For an app, `successUrl` and `cancelUrl` use the app's own URL scheme; they may be the same URL or different paths on the same host.

The response carries `id` (`chk_` followed by lowercase letters and digits) and `url` (the https hosted checkout page). Your backend returns both to the app and remembers which order the checkout id belongs to.

When the checkout ends, the hosted page sends the browser to `successUrl` (payment succeeded) or `cancelUrl` (failed, canceled or expired) with `checkout=<chk id>` and `status=<succeeded|failed|canceled|expired>` appended. Any query string already in the URL is kept.

## Saving a card for later

The checkout can ask the customer to keep their card, so your server charges it again later without them present: a subscription, instalments, or one tap repeat orders. Your server sets `saveCard` when it creates the checkout, and the customer decides on the hosted page; the app does nothing differently, and no card number ever reaches it.

```json
{ "amountCents": 250000, "description": "First month", "saveCard": true, "customer": { "name": "Ruwan", "email": "ruwan@example.lk" } }
```

When that first payment succeeds, your server receives the `card.saved` webhook with a card id (`card_...`) to keep against the customer, and charges it later with `POST /v1/cards/{id}/charge`. The card carries the month it expires and what the customer agreed to, so your server can show both. Only that first payment asks the cardholder to authenticate: a later charge is made without them, so a bank that insists on a check declines it, and you send the customer back to a checkout.

Saved cards use Payable's Advanced plan, switched on in the dashboard. The steps are at https://payments.lk/developers/guide#card-on-file.

## 2. Manifest: receive the redirect

Declare the activity that starts the checkout as `singleTop` and give it an intent filter for your scheme and host. `singleTop` makes the redirect arrive in `onNewIntent` of the existing activity instead of stacking a second copy.

```xml
<activity
    android:name=".CheckoutActivity"
    android:exported="true"
    android:launchMode="singleTop">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="myshop"
            android:host="payments-lk" />
    </intent-filter>
</activity>
```

Pick a scheme that is unlikely to collide with another app. The SDK's own manifest adds the `<queries>` entry that lets it find a Custom Tabs browser on Android 11 and later; it is merged into your app automatically.

## 3. Kotlin usage

```kotlin
class CheckoutActivity : AppCompatActivity() {

    private val launcher = CheckoutLauncher(returnScheme = "myshop")

    // The checkout id your backend returned. A DISMISSED result carries no id,
    // so keep it yourself (and save it across recreation).
    private var pendingCheckoutId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        launcher.restoreInstanceState(savedInstanceState)
        pendingCheckoutId = savedInstanceState?.getString("pendingCheckoutId")

        // The activity may be started fresh by the redirect itself.
        if (savedInstanceState == null) {
            launcher.handleRedirect(intent)?.let(::onCheckoutResult)
        }

        findViewById<Button>(R.id.pay).setOnClickListener { startCheckout() }
    }

    private fun startCheckout() {
        lifecycleScope.launch {
            // Your own API: the backend creates the checkout with its secret key.
            val checkout = myBackend.createCheckout(orderId = "order_1042")
            pendingCheckoutId = checkout.id
            launcher.launch(this@CheckoutActivity, checkout.url)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launcher.handleRedirect(intent)?.let(::onCheckoutResult)
    }

    override fun onResume() {
        super.onResume()
        // Returns DISMISSED if the customer closed the tab without a redirect.
        launcher.onResume()?.let(::onCheckoutResult)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        launcher.saveInstanceState(outState)
        outState.putString("pendingCheckoutId", pendingCheckoutId)
    }

    private fun onCheckoutResult(result: CheckoutResult) {
        // See "Handle each status" below.
    }
}
```

Notes:

- `launch` throws `IllegalArgumentException` if the URL is not allowed, and `ActivityNotFoundException` if the device has no browser at all.
- The launcher detects the pause caused by opening the browser on its own, so a resume that happens before the browser is in front is never reported as `DISMISSED`.
- `handleRedirect` returns null for an intent that is not a valid redirect for your scheme (wrong scheme, missing or duplicated parameters, malformed id, unknown status). Such an intent does not end the wait.
- `PaymentsLkCheckout.open(context, url, toolbarColor)` and `CheckoutResult.fromUri(uri, "myshop")` can be used directly if you do not want the launcher.

## 4. Handle each status

Every branch ends with a call to your backend. The status only decides what to show while you wait for the answer.

```kotlin
private fun onCheckoutResult(result: CheckoutResult) {
    val checkoutId = result.checkoutId ?: pendingCheckoutId ?: return
    when (result.status) {
        CheckoutStatus.SUCCEEDED -> {
            // Show "Confirming your payment", then ask the backend.
            // Fulfil the order only when the backend says payment.status is succeeded.
            confirmWithBackend(checkoutId)
        }
        CheckoutStatus.FAILED,
        CheckoutStatus.CANCELED -> {
            // Show "Payment not completed". Still ask the backend for the real state,
            // then offer to try again with a new checkout from the backend.
            confirmWithBackend(checkoutId)
        }
        CheckoutStatus.EXPIRED -> {
            // The checkout timed out. Ask the backend, then create a new checkout to retry.
            confirmWithBackend(checkoutId)
        }
        CheckoutStatus.DISMISSED -> {
            // The customer came back without a redirect, for example by closing the tab.
            // The payment may still have gone through, so ask the backend before
            // telling the customer anything final.
            confirmWithBackend(checkoutId)
        }
    }
}
```

If `result.checkoutId` differs from the id you are waiting for, treat it as unexpected and confirm both with the backend rather than trusting either.

## Confirm on the server

The deep link only tells the app where to look. The source of truth is the Payments.lk API, reached from your backend:

1. The app sends the checkout id to your backend (for example `GET /orders/1042/payment`).
2. Your backend checks that the checkout id belongs to that order and customer, then calls `GET https://api.payments.lk/v1/checkouts/{id}` with `Authorization: Bearer sk_...`.
3. Your backend trusts `payment.status` from that response, and checks that the amount and reference match the order, before it marks the order paid.

Alternatively (and ideally in addition) your backend listens for the `payment.succeeded` webhook, verifies its signature, and marks the order paid from there. The app then only polls your backend for the order state.

Never mark an order paid because the app received `status=succeeded`.

## Subscriptions

A subscription's first payment is a hosted checkout, so the app opens it exactly as above. Your backend creates the subscription instead of a checkout, with `successUrl` and `cancelUrl` in your app's scheme, and answers `subscription.checkoutUrl` (null when the customer already has a card on file, in which case the first period was charged at once).

Two more pages have no checkout id to return. Open them in a Custom Tab and let your backend, which receives the webhooks, tell the app what changed:

```kotlin
// A subscription link made in the dashboard, with one of its plans chosen first. Give the link a success
// address in your app's scheme when you make it, so the customer comes back after paying.
PaymentsLkCheckout.openSubscriptionLink(activity, code = "LINKCODE", plan = "price_0123456789abcdefghij")

// The customer portal, from a session your backend created with returnUrl in your app's scheme.
PaymentsLkCheckout.openPortal(activity, portalUrl = session.url)
```

`PaymentsLkCheckout.subscriptionLinkUrl(code, plan, locale)` builds the link's address on its own, for a `WebView` or a share sheet.

## Building this SDK

From `sdks/android`, with JDK 17 and an Android SDK (set `sdk.dir` in `local.properties`, which is gitignored):

```sh
./gradlew :payments-lk:assembleRelease :payments-lk:testDebugUnitTest
```

The parsing, URL policy and dismissal logic are covered by plain JVM unit tests in `payments-lk/src/test`.
