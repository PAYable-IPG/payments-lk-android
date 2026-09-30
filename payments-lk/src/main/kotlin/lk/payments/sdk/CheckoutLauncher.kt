package lk.payments.sdk

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * A small helper an Activity holds to open a hosted checkout and to turn what
 * happens next into a [CheckoutResult].
 *
 * Wire it into the activity (see the README for a full example):
 * - [restoreInstanceState] in onCreate, and [handleRedirect] with the launch
 *   intent in onCreate when there is no saved state;
 * - [handleRedirect] in onNewIntent;
 * - [onResume] in onResume;
 * - [saveInstanceState] in onSaveInstanceState.
 *
 * The launcher watches the launching activity's pauses by itself through
 * [Application.ActivityLifecycleCallbacks], so the app does not forward onPause.
 *
 * @param returnScheme the URL scheme used in successUrl and cancelUrl, for example "myshop".
 * @param toolbarColor optional Custom Tab toolbar color.
 */
public class CheckoutLauncher @JvmOverloads constructor(
    private val returnScheme: String,
    private val toolbarColor: Int? = null,
) {
    init {
        require(returnScheme.isNotBlank()) { "returnScheme must not be blank." }
    }

    private val state = CheckoutFlowState()
    private var pauseWatcher: PauseWatcher? = null

    /** True between [launch] and the redirect or dismissal that ends it. */
    public val isAwaitingResult: Boolean
        get() = state.isAwaiting

    /**
     * Opens [checkoutUrl] and starts awaiting a result.
     *
     * @throws IllegalArgumentException if the URL is not allowed (see [PaymentsLkCheckout.isAllowedCheckoutUrl]).
     */
    public fun launch(activity: Activity, checkoutUrl: String) {
        require(PaymentsLkCheckout.isAllowedCheckoutUrl(checkoutUrl)) {
            "Refusing to open a checkout URL that is not https (or http on a local development host)."
        }
        stopWatching()
        pauseWatcher = PauseWatcher(activity).also { it.register() }
        state.launched()
        try {
            PaymentsLkCheckout.open(activity, checkoutUrl, toolbarColor)
        } catch (e: RuntimeException) {
            state.launchAborted()
            stopWatching()
            throw e
        }
    }

    /**
     * Parses the redirect deep link carried by [intent]. Returns null when the
     * intent is not a valid Payments.lk redirect for [returnScheme]. A valid
     * result ends the wait. A result is returned even when nothing was awaited
     * (for example after the app process was killed), because the app confirms
     * every result with its backend anyway.
     */
    public fun handleRedirect(intent: Intent?): CheckoutResult? {
        val result = state.redirected(CheckoutResult.fromUri(intent?.data, returnScheme))
        if (result != null) stopWatching()
        return result
    }

    /**
     * Call from the activity's onResume. Returns [CheckoutResult.DISMISSED]
     * when the customer came back while a checkout was awaited and no redirect
     * arrived, otherwise null.
     */
    public fun onResume(): CheckoutResult? {
        val result = state.resumed()
        if (result != null) stopWatching()
        return result
    }

    /** Call from the activity's onSaveInstanceState. */
    public fun saveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_AWAITING, state.isAwaiting)
    }

    /** Call from the activity's onCreate with its savedInstanceState. */
    public fun restoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState?.getBoolean(KEY_AWAITING, false) == true) {
            state.restoreAwaiting()
        }
    }

    private fun stopWatching() {
        pauseWatcher?.unregister()
        pauseWatcher = null
    }

    private inner class PauseWatcher(activity: Activity) : Application.ActivityLifecycleCallbacks {
        private val application: Application = activity.application
        private val target = WeakReference(activity)

        fun register() {
            application.registerActivityLifecycleCallbacks(this)
        }

        fun unregister() {
            application.unregisterActivityLifecycleCallbacks(this)
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity === target.get()) state.paused()
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity === target.get() && pauseWatcher === this) stopWatching()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }

    private companion object {
        const val KEY_AWAITING = "lk.payments.sdk.CheckoutLauncher.awaiting"
    }
}
