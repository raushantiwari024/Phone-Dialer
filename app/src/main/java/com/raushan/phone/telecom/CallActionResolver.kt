package com.raushan.phone.telecom

import com.raushan.phone.telecom.model.CallSessionState

/**
 * Turns a [CallAction] into an executable [CallActionPlan], given the current session state.
 *
 * All the decision-making lives here, and it is deliberately **pure** — no Android types, no side
 * effects — so every transition and every edge case in the incoming-call flow is unit-testable on the
 * JVM. [CallActionDispatcher] does nothing but execute what this returns.
 *
 * Every action is validated against live state rather than assumed valid, which is what makes the flow
 * safe when events arrive out of order: a plan is only produced if the action still makes sense for the
 * calls that actually exist right now.
 */
object CallActionResolver {

    /**
     * @param decided call ids whose answer/decline decision has already been dispatched and has not
     *   yet been observed in [state]. This is the idempotency guard: state updates arrive
     *   asynchronously, so two rapid taps would otherwise both pass the "is still ringing" check.
     */
    fun resolve(
        state: CallSessionState,
        action: CallAction,
        decided: Set<String> = emptySet(),
    ): CallActionPlan = when (action) {
        is CallAction.AnswerIncomingAndHoldCurrent -> resolveAnswerHolding(state, action, decided)
        is CallAction.AnswerIncomingAndEndCurrent -> resolveAnswerEnding(state, action, decided)
        is CallAction.DeclineIncoming -> resolveDecline(state, action, decided)
        is CallAction.EndCall -> resolveEndCall(state, action)
        is CallAction.Hold -> resolveHold(state, action)
        is CallAction.Unhold -> resolveUnhold(state, action)
        CallAction.Swap -> resolveSwap(state)
        CallAction.Merge -> resolveMerge(state)
        is CallAction.SetMuted -> CallActionPlan.SetMuted(action.muted)
        is CallAction.SetAudioRoute -> CallActionPlan.SetAudioRoute(action.route)
        is CallAction.PlayDtmf -> resolveDtmf(state, action)
        is CallAction.StopDtmf -> CallActionPlan.StopDtmf(action.callId)
    }

    /**
     * Answer, letting Telecom hold whatever is in progress.
     *
     * Deliberately does *not* issue an explicit hold first: Telecom and the `ConnectionService`
     * auto-hold, and racing that with our own hold makes some implementations reject the answer.
     */
    private fun resolveAnswerHolding(
        state: CallSessionState,
        action: CallAction.AnswerIncomingAndHoldCurrent,
        decided: Set<String>,
    ): CallActionPlan {
        guardIncoming(state, action.callId, decided)?.let { return it }
        return CallActionPlan.Answer(action.callId)
    }

    /**
     * Answer after ending the call in progress.
     *
     * Degrades to a plain answer when there is nothing left to end — which is exactly what happens if
     * the other party hangs up while the incoming banner is still on screen.
     */
    private fun resolveAnswerEnding(
        state: CallSessionState,
        action: CallAction.AnswerIncomingAndEndCurrent,
        decided: Set<String>,
    ): CallActionPlan {
        guardIncoming(state, action.callId, decided)?.let { return it }

        val toEnd = state.activeCall ?: state.outgoingCall ?: state.heldCall
        return if (toEnd == null || toEnd.id == action.callId) {
            CallActionPlan.Answer(action.callId)
        } else {
            CallActionPlan.EndThenAnswer(endCallId = toEnd.id, answerCallId = action.callId)
        }
    }

    private fun resolveDecline(
        state: CallSessionState,
        action: CallAction.DeclineIncoming,
        decided: Set<String>,
    ): CallActionPlan {
        guardIncoming(state, action.callId, decided)?.let { return it }
        return CallActionPlan.Reject(action.callId, action.message)
    }

    /**
     * Shared validation for the three incoming-call decisions.
     *
     * Returns a non-null [CallActionPlan.Ignored] when the action must not proceed, so all three
     * transitions reject stale and duplicate input identically.
     */
    private fun guardIncoming(
        state: CallSessionState,
        callId: String,
        decided: Set<String>,
    ): CallActionPlan? {
        if (callId in decided) return CallActionPlan.Ignored(IgnoreReason.AlreadyDecided)
        val call = state.calls.firstOrNull { it.id == callId }
            ?: return CallActionPlan.Ignored(IgnoreReason.NoSuchCall)
        if (!call.isRinging) return CallActionPlan.Ignored(IgnoreReason.NotRinging)
        return null
    }

    /**
     * Hang up.
     *
     * A ringing call is rejected rather than disconnected, so the proper release cause reaches the
     * network — some carriers treat a disconnect on a ringing call differently.
     */
    private fun resolveEndCall(state: CallSessionState, action: CallAction.EndCall): CallActionPlan {
        val call = state.calls.firstOrNull { it.id == action.callId }
            ?: return CallActionPlan.Ignored(IgnoreReason.NoSuchCall)
        if (call.state.isTerminal) return CallActionPlan.Ignored(IgnoreReason.WrongState)
        return if (call.isRinging) {
            CallActionPlan.Reject(call.id, null)
        } else {
            CallActionPlan.Disconnect(call.id)
        }
    }

    private fun resolveHold(state: CallSessionState, action: CallAction.Hold): CallActionPlan {
        val call = state.calls.firstOrNull { it.id == action.callId }
            ?: return CallActionPlan.Ignored(IgnoreReason.NoSuchCall)
        if (call.isOnHold) return CallActionPlan.Ignored(IgnoreReason.WrongState)
        if (!call.capabilities.canHold) return CallActionPlan.Ignored(IgnoreReason.NotCapable)
        return CallActionPlan.Hold(call.id)
    }

    private fun resolveUnhold(state: CallSessionState, action: CallAction.Unhold): CallActionPlan {
        val call = state.calls.firstOrNull { it.id == action.callId }
            ?: return CallActionPlan.Ignored(IgnoreReason.NoSuchCall)
        if (!call.isOnHold) return CallActionPlan.Ignored(IgnoreReason.WrongState)
        return CallActionPlan.Unhold(call.id)
    }

    private fun resolveSwap(state: CallSessionState): CallActionPlan =
        if (state.canSwap) CallActionPlan.Swap else CallActionPlan.Ignored(IgnoreReason.WrongState)

    private fun resolveMerge(state: CallSessionState): CallActionPlan = when {
        !state.canSwap -> CallActionPlan.Ignored(IgnoreReason.WrongState)
        !state.canMerge -> CallActionPlan.Ignored(IgnoreReason.NotCapable)
        else -> CallActionPlan.Merge
    }

    /** DTMF only reaches the network on a connected call. */
    private fun resolveDtmf(state: CallSessionState, action: CallAction.PlayDtmf): CallActionPlan {
        val call = state.calls.firstOrNull { it.id == action.callId }
            ?: return CallActionPlan.Ignored(IgnoreReason.NoSuchCall)
        if (!call.isOngoing || call.isOnHold) return CallActionPlan.Ignored(IgnoreReason.WrongState)
        return CallActionPlan.PlayDtmf(call.id, action.digit)
    }
}
