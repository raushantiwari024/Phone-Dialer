package com.raushan.phone.telecom.model

/**
 * One-shot signals from Telecom that are not part of [CallSessionState].
 *
 * These are events rather than state because replaying them would be wrong: bringing the call UI
 * forward a second time because a new collector subscribed would be a bug, not a recovery.
 */
sealed interface CallSessionEvent {

    /** Telecom asked the dialer to show its in-call UI, e.g. after a headset button press. */
    data class ShowInCallUi(val showDialpad: Boolean) : CallSessionEvent

    /** A dialled string hit a `;` and is waiting for the user to confirm the rest. */
    data class PostDialWait(val callId: String, val remaining: String) : CallSessionEvent

    /** A carrier or connection-level event, e.g. `EVENT_CALL_MERGE_FAILED`. */
    data class ConnectionEvent(val callId: String, val event: String) : CallSessionEvent

    /** The user silenced the ringer with a volume key. */
    data object RingerSilenced : CallSessionEvent
}
