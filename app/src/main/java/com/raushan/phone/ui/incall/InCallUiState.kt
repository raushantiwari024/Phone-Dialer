package com.raushan.phone.ui.incall

import androidx.compose.runtime.Immutable
import com.raushan.phone.telecom.model.CallDuration
import com.raushan.phone.telecom.model.CallState

/**
 * Everything one call card needs to render.
 *
 * [displayName] is resolved upstream and is never blank, so the UI no longer compares a ViewModel
 * string constant against a string resource to decide whether to draw a monogram — a comparison that
 * silently failed under any localisation.
 */
@Immutable
data class CallCardUiState(
    val callId: String,
    val displayName: String,
    val number: String,
    val photoUri: String?,
    val state: CallState,
    val durationText: String = CallDuration.ZERO,
    val isEmergency: Boolean = false,
    /** Set once two calls have been merged, so the UI can name it rather than showing one party. */
    val isConference: Boolean = false,
) {
    val isOnHold: Boolean get() = state == CallState.HOLDING

    /** Only a connected call has a meaningful elapsed time; a dialing call shows a status instead. */
    val showsDuration: Boolean get() = state == CallState.ACTIVE
}

/**
 * The single immutable UI state for the call screens, replacing the nine separate flows the ViewModel
 * used to expose.
 */
@Immutable
data class InCallUiState(
    val mode: Mode = Mode.NoCall,
    val primary: CallCardUiState? = null,
    val secondary: CallCardUiState? = null,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val canAddCall: Boolean = false,
    val canHold: Boolean = false,
    val canSwap: Boolean = false,
    val canMerge: Boolean = false,
    val canReplyWithMessage: Boolean = false,
    /** Answering requires hanging up the current call because it reports it cannot be held. */
    val mustEndActiveToAnswer: Boolean = false,
    val dialpadVisible: Boolean = false,
    val dialpadDigits: String = "",
) {
    enum class Mode {
        NoCall,
        Incoming,
        Ongoing,
        IncomingWhileOngoing,
        TwoOngoing,
        Ended,
    }

    val isRinging: Boolean get() = mode == Mode.Incoming || mode == Mode.IncomingWhileOngoing

    companion object {
        val EMPTY = InCallUiState()
    }
}
