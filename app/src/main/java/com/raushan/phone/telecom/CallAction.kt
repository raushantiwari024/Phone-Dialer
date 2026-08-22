package com.raushan.phone.telecom

import com.raushan.phone.telecom.model.AudioRoute

/**
 * The canonical set of call operations.
 *
 * Every surface — notification action, in-app popup, in-call controls — dispatches one of these.
 * Previously the notification called `TelecomHelper.answerCall` while the popup called
 * `answerAndHoldActive`, `answerAndEndActive` or `answerCall` depending on its own branching, so the
 * same user intent took four different code paths with four different behaviours. The UI layer now only
 * decides *which* action to send, never how it is carried out.
 */
sealed interface CallAction {

    /** The call this action targets, or null for session-wide operations. */
    val callId: String?

    /**
     * Answer the ringing call, letting Telecom put any call in progress on hold.
     *
     * The plain "answer" for every surface, including the notification's Answer button. Telecom and the
     * `ConnectionService` perform the hold themselves.
     */
    data class AnswerIncomingAndHoldCurrent(override val callId: String) : CallAction

    /** Answer the ringing call after disconnecting the call in progress. */
    data class AnswerIncomingAndEndCurrent(override val callId: String) : CallAction

    /** Reject the ringing call, leaving any other call exactly as it is. */
    data class DeclineIncoming(
        override val callId: String,
        val message: String? = null,
    ) : CallAction

    /** Hang up a call that is already connected. */
    data class EndCall(override val callId: String) : CallAction

    data class Hold(override val callId: String) : CallAction

    data class Unhold(override val callId: String) : CallAction

    data object Swap : CallAction {
        override val callId: String? get() = null
    }

    data object Merge : CallAction {
        override val callId: String? get() = null
    }

    data class SetMuted(val muted: Boolean) : CallAction {
        override val callId: String? get() = null
    }

    data class SetAudioRoute(val route: AudioRoute) : CallAction {
        override val callId: String? get() = null
    }

    data class PlayDtmf(override val callId: String, val digit: Char) : CallAction

    data class StopDtmf(override val callId: String) : CallAction
}
