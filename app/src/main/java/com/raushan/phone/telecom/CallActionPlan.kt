package com.raushan.phone.telecom

import com.raushan.phone.telecom.model.AudioRoute

/** Why a dispatched action was not carried out. */
enum class IgnoreReason {
    /** The call is gone — the caller hung up, or the id came from a stale notification. */
    NoSuchCall,

    /** The target call is no longer ringing, so answering or declining it is meaningless. */
    NotRinging,

    /**
     * A decision for this call has already been taken and is still settling.
     *
     * The guard against duplicate taps: pressing Answer on the notification and then on the popup, or
     * double-tapping "Answer & End", must not run the transition twice.
     */
    AlreadyDecided,

    /** The call is not in a state this action applies to. */
    WrongState,

    /** Telecom reports the operation is not available on this call. */
    NotCapable,
}

/**
 * A resolved, executable description of what should happen.
 *
 * Deliberately a pure data type: [CallActionResolver] turns a [CallAction] plus the current
 * [com.raushan.phone.telecom.model.CallSessionState] into one of these with no Android dependency, so
 * every transition and edge case is unit-testable on the JVM.
 */
sealed interface CallActionPlan {

    /** Whether carrying this out commits a decision about a ringing call. */
    val commitsDecision: Boolean get() = false

    data class Ignored(val reason: IgnoreReason) : CallActionPlan

    data class Answer(val callId: String) : CallActionPlan {
        override val commitsDecision: Boolean get() = true
    }

    /**
     * Disconnect one call, then answer another.
     *
     * Ordering matters and is encoded here rather than left to a call site: the current call is
     * released first so the radio is free before the incoming call is accepted.
     */
    data class EndThenAnswer(val endCallId: String, val answerCallId: String) : CallActionPlan {
        override val commitsDecision: Boolean get() = true
    }

    data class Reject(val callId: String, val message: String?) : CallActionPlan {
        override val commitsDecision: Boolean get() = true
    }

    data class Disconnect(val callId: String) : CallActionPlan

    data class Hold(val callId: String) : CallActionPlan

    data class Unhold(val callId: String) : CallActionPlan

    data object Swap : CallActionPlan

    data object Merge : CallActionPlan

    data class SetMuted(val muted: Boolean) : CallActionPlan

    data class SetAudioRoute(val route: AudioRoute) : CallActionPlan

    data class PlayDtmf(val callId: String, val digit: Char) : CallActionPlan

    data class StopDtmf(val callId: String) : CallActionPlan
}
