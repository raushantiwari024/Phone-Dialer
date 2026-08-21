package com.raushan.phone.telecom.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSessionStateTest {

    @Test
    fun `empty state has no calls and no primary`() {
        val state = CallSessionState.EMPTY
        assertFalse(state.hasCalls)
        assertNull(state.primaryCall)
        assertNull(state.secondaryCall)
        assertNull(state.notificationCall)
        assertFalse(state.isCallWaiting)
    }

    @Test
    fun `a sole ringing call is primary`() {
        val ringing = callModel("c1", CallState.RINGING)
        val state = CallSessionState(calls = listOf(ringing))

        assertSame(ringing, state.primaryCall)
        assertSame(ringing, state.ringingCall)
        assertSame(ringing, state.notificationCall)
        assertNull(state.secondaryCall)
        assertFalse(state.isCallWaiting)
        assertFalse(state.isMultiCall)
    }

    /**
     * The call-waiting ranking. The person already on the line stays the UI subject; the new caller
     * arrives as a secondary banner. This is the ordering the two design passes disagreed on.
     */
    @Test
    fun `during call waiting the active call is primary and the ringing call is secondary`() {
        val active = callModel("active", CallState.ACTIVE)
        val ringing = callModel("ringing", CallState.RINGING)
        val state = CallSessionState(calls = listOf(active, ringing))

        assertSame(active, state.primaryCall)
        assertSame(ringing, state.secondaryCall)
        assertTrue(state.isCallWaiting)
        assertTrue(state.isMultiCall)
    }

    /** …but the notification and ringtone still belong to the ringing call. */
    @Test
    fun `during call waiting the notification follows the ringing call`() {
        val active = callModel("active", CallState.ACTIVE)
        val ringing = callModel("ringing", CallState.RINGING)
        val state = CallSessionState(calls = listOf(active, ringing))

        assertSame(ringing, state.notificationCall)
    }

    @Test
    fun `a held call ringing alongside is still call waiting`() {
        val held = callModel("held", CallState.HOLDING)
        val ringing = callModel("ringing", CallState.RINGING)
        val state = CallSessionState(calls = listOf(held, ringing))

        assertSame(held, state.primaryCall)
        assertSame(ringing, state.secondaryCall)
        assertTrue(state.isCallWaiting)
    }

    @Test
    fun `two ongoing calls expose the active as primary and the held as secondary`() {
        val active = callModel("active", CallState.ACTIVE)
        val held = callModel("held", CallState.HOLDING)
        val state = CallSessionState(calls = listOf(held, active))

        assertSame(active, state.primaryCall)
        assertSame(held, state.secondaryCall)
        assertTrue(state.canSwap)
        assertFalse(state.isCallWaiting)
    }

    @Test
    fun `canSwap requires both an active and a held call`() {
        assertFalse(CallSessionState(calls = listOf(callModel("a", CallState.ACTIVE))).canSwap)
        assertFalse(CallSessionState(calls = listOf(callModel("h", CallState.HOLDING))).canSwap)
    }

    @Test
    fun `canMerge requires the capability as well as two calls`() {
        val withoutCapability = CallSessionState(
            calls = listOf(callModel("a", CallState.ACTIVE), callModel("h", CallState.HOLDING)),
        )
        assertFalse(withoutCapability.canMerge)

        val withCapability = CallSessionState(
            calls = listOf(
                callModel("a", CallState.ACTIVE, capabilities = CallCapabilities(canMergeConference = true)),
                callModel("h", CallState.HOLDING),
            ),
        )
        assertTrue(withCapability.canMerge)
    }

    @Test
    fun `mustEndActiveToAnswer is set when the active call cannot be held`() {
        val nonHoldable = CallSessionState(
            calls = listOf(
                callModel("a", CallState.ACTIVE, capabilities = CallCapabilities(canHold = false)),
                callModel("r", CallState.RINGING),
            ),
        )
        assertTrue(nonHoldable.mustEndActiveToAnswer)

        val holdable = CallSessionState(
            calls = listOf(
                callModel("a", CallState.ACTIVE, capabilities = CallCapabilities(canHold = true)),
                callModel("r", CallState.RINGING),
            ),
        )
        assertFalse(holdable.mustEndActiveToAnswer)
    }

    @Test
    fun `external calls are excluded from selection`() {
        val external = callModel("ext", CallState.ACTIVE, isExternal = true)
        val state = CallSessionState(calls = listOf(external))

        assertNull(state.primaryCall)
        assertFalse(state.hasCalls)
    }

    @Test
    fun `conference children are excluded from selection but grouped by parent`() {
        val parent = callModel("conf", CallState.ACTIVE, isConference = true)
        val childA = callModel("a", CallState.ACTIVE, isConferenceChild = true, parentId = "conf")
        val childB = callModel("b", CallState.ACTIVE, isConferenceChild = true, parentId = "conf")
        val state = CallSessionState(calls = listOf(parent, childA, childB))

        assertSame(parent, state.primaryCall)
        assertFalse(state.isMultiCall)
        assertEquals(listOf(childA, childB), state.conferenceChildrenOf["conf"])
    }

    @Test
    fun `terminal calls are not live so a disconnected call yields no primary`() {
        val state = CallSessionState(calls = listOf(callModel("c1", CallState.DISCONNECTED)))

        assertNull(state.primaryCall)
        assertFalse(state.hasCalls)
    }

    /** Ending one of two calls must fall back to the survivor, not to a finished session. */
    @Test
    fun `ending one of two calls leaves the survivor as primary`() {
        val ended = callModel("gone", CallState.DISCONNECTED)
        val survivor = callModel("stay", CallState.ACTIVE)
        val state = CallSessionState(calls = listOf(ended, survivor), isServiceConnected = true)

        assertSame(survivor, state.primaryCall)
        assertNull(state.secondaryCall)
        assertTrue(state.hasCalls)
        assertFalse(state.isSessionFinished)
    }

    @Test
    fun `an outgoing call is primary over a held call`() {
        val dialing = callModel("out", CallState.DIALING, isIncoming = false)
        val held = callModel("held", CallState.HOLDING)
        val state = CallSessionState(calls = listOf(held, dialing))

        assertSame(dialing, state.primaryCall)
    }

    @Test
    fun `isSessionFinished only once the service is connected`() {
        assertFalse(CallSessionState(isServiceConnected = false).isSessionFinished)
        assertTrue(CallSessionState(isServiceConnected = true).isSessionFinished)
    }

    /**
     * Derived selectors must stay out of `equals` so `StateFlow` conflates a genuinely unchanged
     * re-map, while any real change still emits.
     */
    @Test
    fun `equality is decided by the raw inputs only`() {
        val calls = listOf(callModel("c1", CallState.ACTIVE))
        assertEquals(CallSessionState(calls = calls), CallSessionState(calls = calls))

        val changed = listOf(callModel("c1", CallState.HOLDING))
        assertFalse(CallSessionState(calls = calls) == CallSessionState(calls = changed))
    }
}
