package com.raushan.phone.telecom

import android.util.Log
import com.raushan.phone.telecom.model.CallSessionState

/**
 * The single entry point for acting on calls.
 *
 * Every surface goes through here: notification actions, the in-app call-waiting popup, and the in-call
 * controls. Previously each implemented its own call handling — the notification's Answer called a bare
 * `answer()` while the popup chose between three different helper methods — so the same user intent
 * behaved differently depending on where it was triggered.
 *
 * Responsibilities are split deliberately:
 * - [CallActionResolver] decides *what* should happen. Pure, and therefore unit-testable.
 * - This class performs it, and owns the idempotency bookkeeping that needs real mutable state.
 *
 * ### Idempotency
 *
 * [CallRepository] is the source of truth, but it updates asynchronously: Telecom reports the new state
 * some time after `answer()` returns. Two rapid taps — Answer on the notification then Answer on the
 * popup — would both see a still-ringing call and both execute. [decidedCallIds] records calls whose
 * answer/decline has been dispatched but not yet observed, so the second attempt resolves to
 * [IgnoreReason.AlreadyDecided] instead. Entries are pruned once the call stops ringing or disappears.
 *
 * ### Threading
 *
 * Manifest-declared receivers and ViewModel calls both land on the main thread, so contention is not
 * expected — but the guard is the one piece of state that must never be observed half-updated, so
 * access is synchronised rather than relying on that.
 */
object CallActionDispatcher {

    private val lock = Any()
    private val decidedCallIds = mutableSetOf<String>()

    /**
     * Resolves [action] against current state and performs it.
     *
     * @return the plan that was executed, for logging and diagnostics. Callers may ignore it; nothing
     *   about correctness depends on the return value.
     */
    fun dispatch(helper: TelecomHelper, action: CallAction): CallActionPlan {
        val state = CallRepository.state.value

        val plan = synchronized(lock) {
            prune(state)
            val resolved = CallActionResolver.resolve(state, action, decidedCallIds)
            if (resolved.commitsDecision) {
                action.callId?.let(decidedCallIds::add)
            }
            resolved
        }

        if (plan is CallActionPlan.Ignored) {
            Log.d(TAG, "Ignored $action: ${plan.reason}")
            return plan
        }

        execute(helper, plan)
        return plan
    }

    /**
     * Drops guard entries for calls that are no longer awaiting a decision.
     *
     * Without this a call id would be blocked for the process lifetime, so a later call reusing that id
     * could never be answered. Ids are monotonic, so this is belt-and-braces rather than load-bearing —
     * but the guard must not be allowed to grow without bound either.
     */
    private fun prune(state: CallSessionState) {
        if (decidedCallIds.isEmpty()) return
        val stillRinging = state.calls.filter { it.isRinging }.map { it.id }.toSet()
        decidedCallIds.retainAll(stillRinging)
    }

    /**
     * Carries out a resolved plan.
     *
     * Each branch is wrapped so a failure in the telecom stack — a rejected operation, a service that
     * has gone away — is logged and contained. A failed answer must never leave the app in a state
     * where the user cannot then decline.
     */
    private fun execute(helper: TelecomHelper, plan: CallActionPlan) {
        runCatching {
            when (plan) {
                is CallActionPlan.Answer -> helper.answerCall(plan.callId)

                is CallActionPlan.EndThenAnswer -> {
                    // Release the current call first so the radio is free before accepting.
                    helper.endCall(plan.endCallId)
                    helper.answerCall(plan.answerCallId)
                }

                is CallActionPlan.Reject -> helper.rejectCall(plan.callId, plan.message)

                is CallActionPlan.Disconnect -> helper.endCall(plan.callId)

                is CallActionPlan.Hold -> helper.holdCall(plan.callId)

                is CallActionPlan.Unhold -> helper.unholdCall(plan.callId)

                CallActionPlan.Swap -> helper.swapCalls()

                CallActionPlan.Merge -> helper.mergeCalls()

                is CallActionPlan.SetMuted -> helper.setMuted(plan.muted)

                is CallActionPlan.SetAudioRoute -> helper.setAudioRoute(plan.route)

                is CallActionPlan.PlayDtmf -> helper.playDtmfTone(plan.callId, plan.digit)

                is CallActionPlan.StopDtmf -> helper.stopDtmfTone(plan.callId)

                is CallActionPlan.Ignored -> Unit
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to execute $plan", error)
            // Release the guard so the user can retry, rather than being locked out by a failed attempt.
            if (plan.commitsDecision) {
                synchronized(lock) { decidedCallIds.clear() }
            }
        }
    }

    /** Clears all bookkeeping. Called on service teardown. */
    fun reset() {
        synchronized(lock) { decidedCallIds.clear() }
    }

    private const val TAG = "CallActionDispatcher"
}
