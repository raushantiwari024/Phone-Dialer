package com.raushan.phone.telecom.model

import android.telecom.Call

/**
 * Framework-independent view of [android.telecom.Call]'s integer state.
 *
 * Deliberately an enum rather than a sealed hierarchy: the only state-specific payload a call carries
 * is the disconnect cause, which lives on [CallModel] instead. An enum costs no allocation, gives
 * exhaustive `when`, and gets a correct `equals` for free so `StateFlow` conflation behaves.
 */
enum class CallState {
    NEW,
    CONNECTING,
    DIALING,
    RINGING,
    SIMULATED_RINGING,
    ACTIVE,
    HOLDING,
    AUDIO_PROCESSING,
    SELECT_PHONE_ACCOUNT,
    PULLING_CALL,
    DISCONNECTING,
    DISCONNECTED,
    UNKNOWN,
    ;

    /**
     * A call the user is currently on, in any sense.
     *
     * Replaces the `ACTIVE || DIALING || CONNECTING || HOLDING` predicate that was copy-pasted into
     * four separate call sites.
     */
    val isOngoing: Boolean
        get() = this == ACTIVE || this == DIALING || this == CONNECTING || this == HOLDING

    /** An unanswered inbound call. [SIMULATED_RINGING] is the call-screening variant. */
    val isRinging: Boolean
        get() = this == RINGING || this == SIMULATED_RINGING

    /** An outbound call that has not yet connected. */
    val isOutgoingPending: Boolean
        get() = this == DIALING || this == CONNECTING

    /** Terminal states. A call here will never become live again. */
    val isTerminal: Boolean
        get() = this == DISCONNECTING || this == DISCONNECTED

    /** Whether this call should be represented in the UI at all. */
    val isLive: Boolean
        get() = !isTerminal && this != NEW && this != UNKNOWN

    /** Telecom is waiting for the dialer to choose a SIM before it can place the call. */
    val needsAccountSelection: Boolean
        get() = this == SELECT_PHONE_ACCOUNT

    companion object {
        /**
         * Maps a platform `Call.STATE_*` constant.
         *
         * Every constant referenced here exists at or below API 29, so all branches are reachable at
         * the project's `minSdk 30`. Unrecognised values map to [UNKNOWN] rather than throwing, so a
         * future platform state cannot crash the dialer.
         */
        fun fromTelecom(state: Int): CallState = when (state) {
            Call.STATE_NEW -> NEW
            Call.STATE_CONNECTING -> CONNECTING
            Call.STATE_DIALING -> DIALING
            Call.STATE_RINGING -> RINGING
            Call.STATE_SIMULATED_RINGING -> SIMULATED_RINGING
            Call.STATE_ACTIVE -> ACTIVE
            Call.STATE_HOLDING -> HOLDING
            Call.STATE_AUDIO_PROCESSING -> AUDIO_PROCESSING
            Call.STATE_SELECT_PHONE_ACCOUNT -> SELECT_PHONE_ACCOUNT
            Call.STATE_PULLING_CALL -> PULLING_CALL
            Call.STATE_DISCONNECTING -> DISCONNECTING
            Call.STATE_DISCONNECTED -> DISCONNECTED
            else -> UNKNOWN
        }
    }
}
