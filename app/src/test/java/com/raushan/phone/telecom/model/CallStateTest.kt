package com.raushan.phone.telecom.model

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Call.STATE_*` are Java `static final int`s, so the compiler inlines them and these tests run on the
 * JVM without a device or a mocking framework.
 */
class CallStateTest {

    @Test
    fun `fromTelecom maps every platform state`() {
        assertEquals(CallState.NEW, CallState.fromTelecom(Call.STATE_NEW))
        assertEquals(CallState.CONNECTING, CallState.fromTelecom(Call.STATE_CONNECTING))
        assertEquals(CallState.DIALING, CallState.fromTelecom(Call.STATE_DIALING))
        assertEquals(CallState.RINGING, CallState.fromTelecom(Call.STATE_RINGING))
        assertEquals(CallState.SIMULATED_RINGING, CallState.fromTelecom(Call.STATE_SIMULATED_RINGING))
        assertEquals(CallState.ACTIVE, CallState.fromTelecom(Call.STATE_ACTIVE))
        assertEquals(CallState.HOLDING, CallState.fromTelecom(Call.STATE_HOLDING))
        assertEquals(CallState.AUDIO_PROCESSING, CallState.fromTelecom(Call.STATE_AUDIO_PROCESSING))
        assertEquals(
            CallState.SELECT_PHONE_ACCOUNT,
            CallState.fromTelecom(Call.STATE_SELECT_PHONE_ACCOUNT),
        )
        assertEquals(CallState.PULLING_CALL, CallState.fromTelecom(Call.STATE_PULLING_CALL))
        assertEquals(CallState.DISCONNECTING, CallState.fromTelecom(Call.STATE_DISCONNECTING))
        assertEquals(CallState.DISCONNECTED, CallState.fromTelecom(Call.STATE_DISCONNECTED))
    }

    @Test
    fun `fromTelecom maps an unrecognised state to UNKNOWN instead of throwing`() {
        assertEquals(CallState.UNKNOWN, CallState.fromTelecom(Int.MAX_VALUE))
        assertEquals(CallState.UNKNOWN, CallState.fromTelecom(-1))
    }

    @Test
    fun `isOngoing covers exactly the four in-progress states`() {
        val ongoing = CallState.entries.filter { it.isOngoing }
        assertEquals(
            setOf(CallState.ACTIVE, CallState.DIALING, CallState.CONNECTING, CallState.HOLDING),
            ongoing.toSet(),
        )
    }

    @Test
    fun `isRinging covers both ringing variants`() {
        assertTrue(CallState.RINGING.isRinging)
        assertTrue(CallState.SIMULATED_RINGING.isRinging)
        assertFalse(CallState.ACTIVE.isRinging)
    }

    @Test
    fun `isTerminal covers disconnecting and disconnected`() {
        assertEquals(
            setOf(CallState.DISCONNECTING, CallState.DISCONNECTED),
            CallState.entries.filter { it.isTerminal }.toSet(),
        )
    }

    @Test
    fun `terminal, NEW and UNKNOWN states are not live`() {
        assertFalse(CallState.DISCONNECTED.isLive)
        assertFalse(CallState.DISCONNECTING.isLive)
        assertFalse(CallState.NEW.isLive)
        assertFalse(CallState.UNKNOWN.isLive)
        assertTrue(CallState.RINGING.isLive)
        assertTrue(CallState.ACTIVE.isLive)
        assertTrue(CallState.HOLDING.isLive)
    }

    @Test
    fun `isOutgoingPending covers dialing and connecting only`() {
        assertEquals(
            setOf(CallState.DIALING, CallState.CONNECTING),
            CallState.entries.filter { it.isOutgoingPending }.toSet(),
        )
    }
}
