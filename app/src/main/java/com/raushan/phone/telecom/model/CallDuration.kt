package com.raushan.phone.telecom.model

import java.util.Locale

/**
 * Formats call durations.
 *
 * Pure and Android-free so it is unit-testable. The previous inline `"%02d:%02d"` had no hour
 * component, so a 75-minute call rendered as `"75:03"` instead of `"1:15:03"`.
 */
object CallDuration {

    private const val MINUTES_ONLY_FORMAT = "%02d:%02d"
    private const val WITH_HOURS_FORMAT = "%d:%02d:%02d"
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
    private const val MILLIS_PER_SECOND = 1000L

    const val ZERO = "00:00"

    /**
     * Formats elapsed seconds as `mm:ss`, widening to `h:mm:ss` past the hour.
     *
     * Negative input clamps to zero, which can happen if the device clock moves backwards while a call
     * is up.
     */
    fun format(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0L)
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val remainingSeconds = seconds % SECONDS_PER_MINUTE

        return if (hours > 0L) {
            String.format(Locale.getDefault(), WITH_HOURS_FORMAT, hours, minutes, remainingSeconds)
        } else {
            String.format(Locale.getDefault(), MINUTES_ONLY_FORMAT, minutes, remainingSeconds)
        }
    }

    /**
     * Elapsed time of a call that connected at [connectTimeMillis], as of [nowMillis].
     *
     * Returns [ZERO] for a call that never connected, so a dialing call does not count up from the
     * epoch.
     */
    fun since(connectTimeMillis: Long, nowMillis: Long): String {
        if (connectTimeMillis <= 0L) return ZERO
        return format((nowMillis - connectTimeMillis) / MILLIS_PER_SECOND)
    }
}
