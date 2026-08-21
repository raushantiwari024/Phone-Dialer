package com.raushan.phone.telecom

import com.raushan.phone.telecom.model.CallSessionState

/**
 * Decides when the app may bring the call screen forward on its own.
 *
 * Needed because "show the call UI when a call exists" and "let the user dismiss the call UI" are in
 * direct conflict: minimising the call screen resumes whatever was behind it, which would immediately
 * satisfy the show condition again and relaunch it. Tracking explicitly dismissed calls breaks that
 * loop.
 */
object CallUiCoordinator {

    private val dismissedCallIds = mutableSetOf<String>()

    /** The user minimised or backed out of the call screen for this call. */
    fun markDismissed(callId: String) {
        dismissedCallIds.add(callId)
    }

    /** The user asked for the call screen again, e.g. by tapping the ongoing-call banner. */
    fun clearDismissed(callId: String) {
        dismissedCallIds.remove(callId)
    }

    fun isDismissed(callId: String): Boolean = callId in dismissedCallIds

    /**
     * The call the app should auto-present, or `null` to stay out of the way.
     *
     * Only ever a ringing or outgoing call. An answered call is deliberately excluded: once the user is
     * talking, the notification and the in-app banner are the only affordances, and the app must not
     * shove itself back in front of whatever they moved on to.
     */
    fun autoShowTarget(state: CallSessionState): String? {
        val candidate = state.ringingCall ?: state.outgoingCall ?: return null
        return candidate.id.takeUnless(::isDismissed)
    }

    /** Drops bookkeeping for calls that no longer exist, so ids cannot accumulate. */
    fun prune(state: CallSessionState) {
        if (dismissedCallIds.isEmpty()) return
        val liveIds = state.calls.map { it.id }.toSet()
        dismissedCallIds.retainAll(liveIds)
    }
}
