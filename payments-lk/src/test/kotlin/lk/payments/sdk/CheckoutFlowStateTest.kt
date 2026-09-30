package lk.payments.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutFlowStateTest {

    private val succeeded = CheckoutResult(CheckoutStatus.SUCCEEDED, "chk_abc123")

    @Test
    fun startsIdle() {
        val state = CheckoutFlowState()
        assertFalse(state.isAwaiting)
        assertNull(state.resumed())
    }

    @Test
    fun launchStartsAwaiting() {
        val state = CheckoutFlowState()
        state.launched()
        assertTrue(state.isAwaiting)
        assertFalse(state.pausedSinceLaunch)
    }

    @Test
    fun resumeBeforeAnyPauseIsNotADismissal() {
        val state = CheckoutFlowState()
        state.launched()
        assertNull(state.resumed())
        assertTrue(state.isAwaiting)
    }

    @Test
    fun resumeAfterPauseWithoutRedirectIsADismissal() {
        val state = CheckoutFlowState()
        state.launched()
        state.paused()
        assertSame(CheckoutResult.DISMISSED, state.resumed())
        assertFalse(state.isAwaiting)
    }

    @Test
    fun dismissalIsReportedOnce() {
        val state = CheckoutFlowState()
        state.launched()
        state.paused()
        assertEquals(CheckoutStatus.DISMISSED, state.resumed()?.status)
        state.paused()
        assertNull(state.resumed())
    }

    @Test
    fun redirectEndsTheWaitSoResumeReportsNothing() {
        val state = CheckoutFlowState()
        state.launched()
        state.paused()
        assertEquals(succeeded, state.redirected(succeeded))
        assertFalse(state.isAwaiting)
        assertNull(state.resumed())
    }

    @Test
    fun invalidRedirectKeepsWaiting() {
        val state = CheckoutFlowState()
        state.launched()
        state.paused()
        assertNull(state.redirected(null))
        assertTrue(state.isAwaiting)
        assertSame(CheckoutResult.DISMISSED, state.resumed())
    }

    @Test
    fun redirectWithoutLaunchIsStillReported() {
        val state = CheckoutFlowState()
        assertEquals(succeeded, state.redirected(succeeded))
        assertFalse(state.isAwaiting)
    }

    @Test
    fun pauseWhileIdleIsIgnored() {
        val state = CheckoutFlowState()
        state.paused()
        assertFalse(state.pausedSinceLaunch)
        state.launched()
        assertNull(state.resumed())
    }

    @Test
    fun relaunchResetsThePauseGuard() {
        val state = CheckoutFlowState()
        state.launched()
        state.paused()
        state.launched()
        assertFalse(state.pausedSinceLaunch)
        assertNull(state.resumed())
        state.paused()
        assertSame(CheckoutResult.DISMISSED, state.resumed())
    }

    @Test
    fun abortedLaunchAwaitsNothing() {
        val state = CheckoutFlowState()
        state.launched()
        state.launchAborted()
        assertFalse(state.isAwaiting)
        state.paused()
        assertNull(state.resumed())
    }

    @Test
    fun restoredWaitReportsDismissalOnFirstResume() {
        val state = CheckoutFlowState()
        state.restoreAwaiting()
        assertTrue(state.isAwaiting)
        assertSame(CheckoutResult.DISMISSED, state.resumed())
    }

    @Test
    fun restoredWaitStillPrefersARedirect() {
        val state = CheckoutFlowState()
        state.restoreAwaiting()
        assertEquals(succeeded, state.redirected(succeeded))
        assertNull(state.resumed())
    }
}
