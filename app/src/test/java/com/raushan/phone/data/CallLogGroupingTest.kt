package com.raushan.phone.data

import android.provider.CallLog
import com.raushan.phone.data.models.CallLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CallLogGroupingTest {

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.AUGUST, 23, 18, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun at(hour: Int, minute: Int = 0, daysAgo: Int = 0): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DATE, -daysAgo)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun entry(
        id: Long,
        number: String,
        date: Long,
        type: Int = CallLog.Calls.INCOMING_TYPE,
        name: String? = null,
    ) = CallLogEntry(
        id = id,
        number = number,
        date = date,
        duration = 30L,
        type = type,
        cachedName = name,
        cachedNumberType = null,
        cachedNumberLabel = null,
        photoUri = null,
    )

    // --- number keying ---

    @Test
    fun `numbers stored with and without a country code share a key`() {
        assertEquals(
            CallLogGrouping.numberKey("9812345678"),
            CallLogGrouping.numberKey("+919812345678"),
        )
    }

    @Test
    fun `formatting is ignored when keying`() {
        assertEquals(
            CallLogGrouping.numberKey("9812345678"),
            CallLogGrouping.numberKey("(98) 1234-5678"),
        )
    }

    @Test
    fun `short numbers keep their full value`() {
        assertEquals("112", CallLogGrouping.numberKey("112"))
        assertEquals("12345", CallLogGrouping.numberKey("12345"))
    }

    @Test
    fun `different numbers do not collide`() {
        assertTrue(
            CallLogGrouping.numberKey("9812345678") != CallLogGrouping.numberKey("9812345679"),
        )
    }

    @Test
    fun `withheld numbers group together under one key`() {
        assertEquals(CallLogGrouping.numberKey(""), CallLogGrouping.numberKey("   "))
    }

    // --- the reported bug ---

    /**
     * The regression this exists for. The previous implementation collapsed only *consecutive* runs,
     * so A, B, A on one day produced three rows with A appearing twice.
     */
    @Test
    fun `a contact called at three separate times in a day yields one row`() {
        val logs = listOf(
            entry(3, "9812345678", at(15), CallLog.Calls.MISSED_TYPE),
            entry(2, "9998887777", at(12, 30), CallLog.Calls.OUTGOING_TYPE),
            entry(1, "9812345678", at(10), CallLog.Calls.INCOMING_TYPE),
        )

        val days = CallLogGrouping.group(logs, now)

        assertEquals(1, days.size)
        assertEquals(CallLogGrouping.TODAY, days[0].header)
        // Two contacts, not three rows.
        assertEquals(2, days[0].groups.size)

        val first = days[0].groups[0]
        assertEquals("9812345678", first.mainEntry.number)
        assertEquals(2, first.totalCount)
    }

    @Test
    fun `the group is represented by its most recent call`() {
        val logs = listOf(
            entry(2, "9812345678", at(15), CallLog.Calls.MISSED_TYPE),
            entry(1, "9812345678", at(10), CallLog.Calls.INCOMING_TYPE),
        )

        val group = CallLogGrouping.group(logs, now).single().groups.single()

        assertEquals(2L, group.mainEntry.id)
        assertEquals(CallLog.Calls.MISSED_TYPE, group.mainEntry.type)
    }

    /** Detailed history must still be recoverable from a collapsed row. */
    @Test
    fun `no underlying call is lost when collapsing`() {
        val logs = listOf(
            entry(3, "9812345678", at(15)),
            entry(2, "9812345678", at(12)),
            entry(1, "9812345678", at(10)),
        )

        val group = CallLogGrouping.group(logs, now).single().groups.single()

        assertEquals(3, group.totalCount)
        assertEquals(listOf(3L, 2L, 1L), group.callLogs.map { it.id })
    }

    @Test
    fun `a missed call anywhere in the group is reported`() {
        val logs = listOf(
            entry(2, "9812345678", at(15), CallLog.Calls.INCOMING_TYPE),
            entry(1, "9812345678", at(10), CallLog.Calls.MISSED_TYPE),
        )
        assertTrue(CallLogGrouping.group(logs, now).single().groups.single().hasMissedCall)
    }

    // --- day separation ---

    @Test
    fun `the same contact on different days stays on separate rows`() {
        val logs = listOf(
            entry(2, "9812345678", at(10)),
            entry(1, "9812345678", at(10, daysAgo = 1)),
        )

        val days = CallLogGrouping.group(logs, now)

        assertEquals(2, days.size)
        assertEquals(CallLogGrouping.TODAY, days[0].header)
        assertEquals(CallLogGrouping.YESTERDAY, days[1].header)
        assertEquals(1, days[0].groups.size)
        assertEquals(1, days[1].groups.size)
    }

    @Test
    fun `older days get a dated header`() {
        val logs = listOf(entry(1, "9812345678", at(10, daysAgo = 5)))
        val header = CallLogGrouping.group(logs, now).single().header

        assertTrue(header != CallLogGrouping.TODAY)
        assertTrue(header != CallLogGrouping.YESTERDAY)
        assertTrue(header.contains("2026"))
    }

    /** A late-evening call must not flip to a dated header just after midnight. */
    @Test
    fun `headers are decided by day boundary not elapsed time`() {
        val justAfterMidnight = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
        }.timeInMillis

        val lastNight = at(23, 55, daysAgo = 1)
        val header = CallLogGrouping.group(
            listOf(entry(1, "9812345678", lastNight)),
            justAfterMidnight,
        ).single().header

        assertEquals(CallLogGrouping.YESTERDAY, header)
    }

    // --- ordering and edges ---

    @Test
    fun `newest activity stays first`() {
        val logs = listOf(
            entry(3, "111", at(16)),
            entry(2, "222", at(14)),
            entry(1, "333", at(9)),
        )
        val groups = CallLogGrouping.group(logs, now).single().groups
        assertEquals(listOf("111", "222", "333"), groups.map { it.mainEntry.number })
    }

    @Test
    fun `an empty log produces no days`() {
        assertTrue(CallLogGrouping.group(emptyList(), now).isEmpty())
    }

    @Test
    fun `a single call produces one day with one group`() {
        val days = CallLogGrouping.group(listOf(entry(1, "9812345678", at(10))), now)
        assertEquals(1, days.size)
        assertEquals(1, days.single().groups.size)
        assertEquals(1, days.single().groups.single().totalCount)
    }
}