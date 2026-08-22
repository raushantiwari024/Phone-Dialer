package com.raushan.phone.telecom.model

import androidx.compose.runtime.Immutable

/**
 * The complete, immutable state of the current call session — the single source of truth every
 * consumer reads.
 *
 * Derived values are declared in the class body rather than the constructor on purpose. They are
 * computed exactly once at construction, and they are excluded from the generated `equals`/`hashCode`,
 * so `StateFlow` conflation dedupes on the raw inputs only. That gives both properties we need: a
 * genuinely unchanged re-map is conflated away, and any real change always emits.
 *
 * Nothing anywhere should call `firstOrNull()` on a raw call list again. Every consumer reads the
 * named selectors below, which is what makes call waiting target the right call.
 */
@Immutable
data class CallSessionState(
    val calls: List<CallModel> = emptyList(),
    val audio: CallAudioModel = CallAudioModel.DEFAULT,
    /** Telecom's authoritative answer to "may the user add another call right now?". */
    val canAddCall: Boolean = false,
    /**
     * Whether `InCallService` is bound.
     *
     * Lets the UI tell "no calls" apart from "Telecom has not bound us yet", so the call screen does
     * not close itself during the bind window.
     */
    val isServiceConnected: Boolean = false,
    /** The user silenced the ringer with a volume key; stop the ring animation and drop the FSI. */
    val isRingerSilenced: Boolean = false,
) {
    /** External calls live on another device and conference children are represented by their parent. */
    private val visibleCalls: List<CallModel> =
        calls.filter { !it.isExternal && !it.isConferenceChild }

    private val liveCalls: List<CallModel> = visibleCalls.filter { it.state.isLive }

    val ringingCall: CallModel? = liveCalls.firstOrNull { it.state.isRinging }

    val outgoingCall: CallModel? = liveCalls.firstOrNull { it.state.isOutgoingPending }

    val activeCall: CallModel? = liveCalls.firstOrNull { it.state == CallState.ACTIVE }

    val heldCall: CallModel? = liveCalls.firstOrNull { it.state == CallState.HOLDING }

    val accountSelectionCall: CallModel? = liveCalls.firstOrNull { it.state.needsAccountSelection }

    val conferenceCall: CallModel? = liveCalls.firstOrNull { it.isConference }

    /**
     * The call the full-screen UI features.
     *
     * An in-progress conversation outranks a newly ringing call: during call waiting the person you
     * are already talking to stays on screen and the new caller arrives as a banner. A ringing call
     * becomes primary only when it is the sole call. This is deliberately *not* the same ranking as
     * [notificationCall].
     */
    val primaryCall: CallModel? =
        activeCall ?: outgoingCall ?: heldCall ?: accountSelectionCall ?: ringingCall

    /** The other line — the waiting caller during call waiting, or the held party after a swap. */
    val secondaryCall: CallModel? = when {
        ringingCall != null && ringingCall !== primaryCall -> ringingCall
        primaryCall === activeCall -> heldCall
        else -> null
    }

    /**
     * The call that owns the notification and the ringtone.
     *
     * A ringing call always wins here even when it is not the primary UI subject, so call waiting
     * still produces a proper incoming-call notification.
     */
    val notificationCall: CallModel? = ringingCall ?: primaryCall

    val hasCalls: Boolean = liveCalls.isNotEmpty()

    val isMultiCall: Boolean = liveCalls.size > 1

    val isCallWaiting: Boolean = ringingCall != null && (activeCall != null || heldCall != null)

    val canSwap: Boolean = activeCall != null && heldCall != null

    val canMerge: Boolean = canSwap && activeCall?.capabilities?.canMergeConference == true

    /**
     * Whether answering the ringing call requires ending the current one.
     *
     * Some carrier and radio configurations report a non-holdable active call; in that case the UI
     * must offer "End & Answer" instead of "Hold & Answer".
     */
    val mustEndActiveToAnswer: Boolean =
        ringingCall != null && activeCall != null && !activeCall.capabilities.canHold

    /**
     * The explicit session phase.
     *
     * The single value both the notification layer and the UI branch on, so there is no way for them
     * to hold different opinions about whether a call is waiting.
     */
    val phase: CallPhase = when {
        !hasCalls -> CallPhase.Idle
        ringingCall != null && (activeCall != null || heldCall != null) -> CallPhase.CallWaiting
        ringingCall != null -> CallPhase.IncomingOnly
        activeCall != null && heldCall != null -> CallPhase.TwoCalls
        else -> CallPhase.SingleCall
    }

    /** Conference members, keyed by their parent call id. */
    val conferenceChildrenOf: Map<String, List<CallModel>> =
        calls.filter { it.isConferenceChild }.groupBy { it.parentId.orEmpty() }

    /** True once Telecom is bound and every call is gone — the cue for the call UI to close. */
    val isSessionFinished: Boolean = isServiceConnected && !hasCalls

    companion object {
        val EMPTY = CallSessionState()
    }
}
