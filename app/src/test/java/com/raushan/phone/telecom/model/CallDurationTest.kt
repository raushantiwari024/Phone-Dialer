package com.raushan.phone.telecom.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CallDurationTest {

    @Test
    fun `formats seconds and minutes below an hour`() {
        assertEquals("00:00", CallDuration.format(0))
        assertEquals("00:09", CallDuration.format(9))
        assertEquals("01:00", CallDuration.format(60))
        assertEquals("09:05", CallDuration.format(545))
        assertEquals("59:59", CallDuration.format(3599))
    }

    /**
     * The regression this exists for: the old inline `"%02d:%02d"` had no hour component, so a
     * 75-minute call rendered as "75:03".
     */
    @Test
    fun `widens to hours past the hour boundary`() {
        assertEquals("1:00:00", CallDuration.format(3600))
        assertEquals("1:15:03", CallDuration.format(4503))
        assertEquals("2:00:01", CallDuration.format(7201))
        assertEquals("10:00:00", CallDuration.format(36000))
    }

    @Test
    fun `clamps negative elapsed time to zero`() {
        assertEquals("00:00", CallDuration.format(-5))
    }

    @Test
    fun `since returns zero for a call that never connected`() {
        assertEquals(CallDuration.ZERO, CallDuration.since(connectTimeMillis = 0L, nowMillis = 5_000L))
        assertEquals(CallDuration.ZERO, CallDuration.since(connectTimeMillis = -1L, nowMillis = 5_000L))
    }

    @Test
    fun `since measures from the connect time`() {
        assertEquals("00:30", CallDuration.since(connectTimeMillis = 1_000L, nowMillis = 31_000L))
        assertEquals("1:00:00", CallDuration.since(connectTimeMillis = 0L + 1, nowMillis = 3_600_001L))
    }

    /** A backwards clock jump during a call must not render a negative duration. */
    @Test
    fun `since clamps when the clock moves backwards`() {
        assertEquals(CallDuration.ZERO, CallDuration.since(connectTimeMillis = 10_000L, nowMillis = 5_000L))
    }
}
