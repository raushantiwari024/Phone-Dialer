package com.raushan.phone.data

import com.raushan.phone.data.models.CallLogEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One Recents row: all of a contact's calls on a single day, collapsed together.
 */
data class CallLogGroup(
    /** The most recent call in the group; supplies the name, number and time shown on the row. */
    val mainEntry: CallLogEntry,
    val totalCount: Int,
    /** Every underlying call, newest first. Kept so detail views and delete still see them all. */
    val callLogs: List<CallLogEntry>,
) {
    val hasMissedCall: Boolean
        get() = callLogs.any { it.type == android.provider.CallLog.Calls.MISSED_TYPE }
}

/** A day's worth of grouped calls, with its display header. */
data class CallLogDay(
    val header: String,
    val groups: List<CallLogGroup>,
)

/**
 * Groups call log entries for the Recents list.
 *
 * Pure and Android-framework-free apart from the `CallLog.Calls` type constants, which are inlined
 * `static final int`s — so all of this is unit-testable on the JVM.
 *
 * ### Why this replaces the previous grouping
 *
 * The old implementation collapsed only *consecutive* runs of the same number within a day. Given
 * calls from A, then B, then A again on one day, A appeared twice. Grouping by (day, number) instead
 * means one row per contact per day regardless of what happened in between, which is what the Recents
 * list should show.
 */
object CallLogGrouping {

    private val PHONE_CLEAN_REGEX = Regex("[^0-9+]")

    /**
     * How many trailing digits identify a number.
     *
     * Nine rather than seven: seven collides noticeably on real address books, while nine still groups
     * a number stored with a country code together with the same number stored without one.
     */
    private const val MATCH_DIGITS = 9

    private const val DAY_FORMAT = "MMMM d, yyyy"

    /**
     * Canonical grouping key for a phone number.
     *
     * Digits only, reduced to the trailing [MATCH_DIGITS] so `+919812345678` and `9812345678` land in
     * the same group. Short codes and service numbers keep their full value. Withheld numbers have no
     * digits at all, so they fall back to a fixed key and group together as "unknown".
     */
    fun numberKey(number: String): String {
        val digits = number.replace(PHONE_CLEAN_REGEX, "").removePrefix("+")
        return when {
            digits.isEmpty() -> UNKNOWN_KEY
            digits.length <= MATCH_DIGITS -> digits
            else -> digits.takeLast(MATCH_DIGITS)
        }
    }

    /**
     * Groups [logs] into days, and within each day into one entry per number.
     *
     * [logs] is expected newest-first, as the call log provider returns it; day and group ordering
     * follow from that, so the newest activity stays at the top without a re-sort.
     */
    fun group(logs: List<CallLogEntry>, nowMillis: Long = System.currentTimeMillis()): List<CallLogDay> {
        if (logs.isEmpty()) return emptyList()

        val byDay = LinkedHashMap<Long, MutableList<CallLogEntry>>()
        for (log in logs) {
            byDay.getOrPut(dayStartOf(log.date)) { mutableListOf() }.add(log)
        }

        return byDay.map { (dayStart, dayLogs) ->
            val byNumber = LinkedHashMap<String, MutableList<CallLogEntry>>()
            for (log in dayLogs) {
                byNumber.getOrPut(numberKey(log.number)) { mutableListOf() }.add(log)
            }

            CallLogDay(
                header = headerFor(dayStart, nowMillis),
                groups = byNumber.values.map { calls ->
                    CallLogGroup(
                        // Input is newest-first, so the first entry is the most recent call.
                        mainEntry = calls.first(),
                        totalCount = calls.size,
                        callLogs = calls.toList(),
                    )
                },
            )
        }
    }

    /** Midnight of the day containing [timestamp], in the device's current time zone. */
    fun dayStartOf(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * "Today", "Yesterday", or a formatted date.
     *
     * Compared by day boundary rather than elapsed milliseconds, so a call at 23:55 is still
     * "Yesterday" at 00:05 rather than becoming a dated header.
     */
    fun headerFor(dayStart: Long, nowMillis: Long): String {
        val today = dayStartOf(nowMillis)
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DATE, -1)
        }.timeInMillis

        return when (dayStart) {
            today -> TODAY
            yesterday -> YESTERDAY
            else -> SimpleDateFormat(DAY_FORMAT, Locale.getDefault()).format(Date(dayStart))
        }
    }

    const val TODAY = "Today"
    const val YESTERDAY = "Yesterday"
    private const val UNKNOWN_KEY = "unknown"
}
