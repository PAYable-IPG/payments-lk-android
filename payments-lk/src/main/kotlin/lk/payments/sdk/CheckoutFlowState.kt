package lk.payments.sdk

/**
 * The awaiting and dismissal bookkeeping behind [CheckoutLauncher], kept free
 * of the Android framework so it runs in plain JVM unit tests.
 *
 * A dismissal is reported only when the activity resumes while a checkout is
 * awaited AND the activity has been paused at least once since the launch.
 * That ignores any resume that happens before the browser has come to the
 * front, which would otherwise be misread as the customer closing the tab.
 */
internal class CheckoutFlowState {

    var isAwaiting: Boolean = false
        private set

    var pausedSinceLaunch: Boolean = false
        private set

    /** A checkout page is about to be opened. */
    fun launched() {
        isAwaiting = true
        pausedSinceLaunch = false
    }

    /** Opening the checkout page failed, so nothing is awaited. */
    fun launchAborted() {
        clear()
    }

    /** The activity that launched the checkout was paused. */
    fun paused() {
        if (isAwaiting) pausedSinceLaunch = true
    }

    /**
     * A redirect was parsed into [result] (null when the intent was not a
     * valid Payments.lk redirect). A valid result ends the wait.
     */
    fun redirected(result: CheckoutResult?): CheckoutResult? {
        if (result != null) clear()
        return result
    }

    /** The activity resumed. Returns DISMISSED when the customer came back without a redirect. */
    fun resumed(): CheckoutResult? {
        if (!isAwaiting || !pausedSinceLaunch) return null
        clear()
        return CheckoutResult.DISMISSED
    }

    /**
     * Restores a wait that survived activity recreation or process death. The
     * activity was necessarily paused before it was destroyed.
     */
    fun restoreAwaiting() {
        isAwaiting = true
        pausedSinceLaunch = true
    }

    private fun clear() {
        isAwaiting = false
        pausedSinceLaunch = false
    }
}
