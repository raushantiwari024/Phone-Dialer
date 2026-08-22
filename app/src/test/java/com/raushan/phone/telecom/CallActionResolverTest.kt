package com.raushan.phone.telecom

import com.raushan.phone.telecom.model.CallCapabilities
import com.raushan.phone.telecom.model.CallPhase
import com.raushan.phone.telecom.model.CallSessionState
import com.raushan.phone.telecom.model.CallState
import com.raushan.phone.telecom.model.callModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the incoming-call state machine and every edge case the refactor had to handle.
 *
 * [CallActionResolver] is pure, so all of this runs on the JVM with plain JUnit — no device, no mocking
 * framework, and no coroutine test infrastructure.
 */
class CallActionResolverTest {

    private val active = callModel("active", CallState.ACTIVE, isIncoming = false)
    private val ringing = callModel("ringing", CallState.RINGING)

    private fun callWaiting(activeCall: com.raushan.phone.telecom.model.CallModel = active) =
        CallSessionState(calls = listOf(activeCall, ringing), isServiceConnected = true)

    private fun incomingOnly() =
        CallSessionState(calls = listOf(ringing), isServiceConnected = true)

    // --- phase model ---

    @Test
    fun `phase reflects the session shape`() {
        assertEquals(CallPhase.Idle, CallSessionState(isServiceConnected = true).phase)
        assertEquals(CallPhase.IncomingOnly, incomingOnly().phase)
        assertEquals(CallPhase.SingleCall, CallSessionState(calls = listOf(active)).phase)
        assertEquals(CallPhase.CallWaiting, callWaiting().phase)
        assertEquals(
            CallPhase.TwoCalls,
            CallSessionState(calls = listOf(active, callModel("held", CallState.HOLDING))).phase,
        )
    }

    // --- the three canonical transitions ---

    @Test
    fun `answer while a call is in progress answers without an explicit hold`() {
        val plan = CallActionResolver.resolve(
            callWaiting(),
            CallAction.AnswerIncomingAndHoldCurrent("ringing"),
        )
        // Telecom auto-holds; issuing our own hold races that and some ConnectionServices then
        // reject the answer outright.
        assertEquals(CallActionPlan.Answer("ringing"), plan)
    }

    @Test
    fun `answer and end releases the in-progress call first`() {
        val plan = CallActionResolver.resolve(
            callWaiting(),
            CallAction.AnswerIncomingAndEndCurrent("ringing"),
        )
        assertEquals(
            CallActionPlan.EndThenAnswer(endCallId = "active", answerCallId = "ringing"),
            plan,
        )
    }

    @Test
    fun `decline rejects the incoming call and never touches the other one`() {
        val plan = CallActionResolver.resolve(callWaiting(), CallAction.DeclineIncoming("ringing"))
        assertEquals(CallActionPlan.Reject("ringing", null), plan)
    }

    @Test
    fun `answer with no other call is a plain answer`() {
        val plan = CallActionResolver.resolve(
            incomingOnly(),
            CallAction.AnswerIncomingAndHoldCurrent("ringing"),
        )
        assertEquals(CallActionPlan.Answer("ringing"), plan)
    }

    /** The notification's Answer and the popup's Answer must resolve to the identical plan. */
    @Test
    fun `both surfaces resolve the same action to the same plan`() {
        val state = callWaiting()
        val fromNotification = CallActionResolver.resolve(
            state,
            CallAction.AnswerIncomingAndHoldCurrent("ringing"),
        )
        val fromPopup = CallActionResolver.resolve(
            state,
            CallAction.AnswerIncomingAndHoldCurrent("ringing"),
        )
        assertEquals(fromNotification, fromPopup)
    }

    // --- edge case: duplicate and rapid input ---

    @Test
    fun `a second answer for an already decided call is ignored`() {
        val plan = CallActionResolver.resolve(
            callWaiting(),
            CallAction.AnswerIncomingAndHoldCurrent("ringing"),
            decided = setOf("ringing"),
        )
        assertEquals(CallActionPlan.Ignored(IgnoreReason.AlreadyDecided), plan)
    }

    @Test
    fun `decline after answer is ignored, so the two cannot both run`() {
        val plan = CallActionResolver.resolve(
            callWaiting(),
            CallAction.DeclineIncoming("ringing"),
            decided = setOf("ringing"),
        )
        assertEquals(CallActionPlan.Ignored(IgnoreReason.AlreadyDecided), plan)
    }

    @Test
    fun `repeated answer and end is ignored after the first`() {
        val plan = CallActionResolver.resolve(
            callWaiting(),
            CallAction.AnswerIncomingAndEndCurrent("ringing"),
            decided = setOf("ringing"),
        )
        assertEquals(CallActionPlan.Ignored(IgnoreReason.AlreadyDecided), plan)
    }

    /** The guard is per call, so a decision about one must not block the other. */
    @Test
    fun `a decision about one call does not block another`() {
        val second = callModel("ringing2", CallState.RINGING)
        val state = CallSessionState(calls = listOf(active, ringing, second))
        val plan = CallActionResolver.resolve(
            state,
            CallAction.DeclineIncoming("ringing2"),
            decided = setOf("ringing"),
        )
        assertEquals(CallActionPlan.Reject("ringing2", null), plan)
    }

    // --- edge case: the incoming caller gives up ---

    @Test
    fun `answering a call that has disappeared is ignored`() {
        val plan = CallActionResolver.resolve(
            CallSessionState(calls = listOf(active)),
            CallAction.AnswerIncomingAndHoldCurrent("ringing"),
        )
        assertEquals(CallActionPlan.Ignored(IgnoreReason.NoSuchCall), plan)
    }

    @Test
    fun `answering a call that stopped ringing is ignored`() {
        val alreadyAnswered = callModel("ringing", CallState.ACTIVE)
        val plan = CallActionResolver.resolve(
            CallSessionState(calls = listOf(alreadyAnswered)),
            CallAction.AnswerIncomingAndHoldCurrent("ringing"),
        )
        assertEquals(CallActionPlan.Ignored(IgnoreReason.NotRinging), plan)
    }

    @Test
    fun `declining a call that has disappeared is ignored`() {
        val plan = CallActionResolver.resolve(
            CallSessionState(calls = listOf(active)),
            CallAction.DeclineIncoming("ringing"),
        )
        assertEquals(CallActionPlan.Ignored(IgnoreReason.NoSuchCall), plan)
    }

    // --- edge case: the in-progress call ends while the banner is up ---

    @Test
    fun `answer and end degrades to a plain answer when there is nothing left to end`() {
        val plan = CallActionResolver.resolve(
            incomingOnly(),
            CallAction.AnswerIncomingAndEndCurrent("ringing"),
        )
        assertEquals(CallActionPlan.Answer("ringing"), plan)
    }

    @Test
    fun `answer and end never disconnects the call it is about to answer`() {
        val plan = CallActionResolver.resolve(
            incomingOnly(),
            CallAction.AnswerIncomingAndEndCurrent("ringing"),
        )
        assertEquals(CallActionPlan.Answer("ringing"), plan)
    }

    @Test
    fun `answer and end targets a held call when there is no active one`() {
        val held = callModel("held", CallState.HOLDING)
        val plan = CallActionResolver.resolve(
            CallSessionState(calls = listOf(held, ringing)),
            CallAction.AnswerIncomingAndEndCurrent("ringing"),
        )
        assertEquals(
            CallActionPlan.EndThenAnswer(endCallId = "held", answerCallId = "ringing"),
            plan,
        )
    }

    // --- hang up ---

    @Test
    fun `ending a ringing call rejects it rather than disconnecting`() {
        val plan = CallActionResolver.resolve(incomingOnly(), CallAction.EndCall("ringing"))
        assertEquals(CallActionPlan.Reject("ringing", null), plan)
    }

    @Test
    fun `ending a connected call disconnects it`() {
        val plan = CallActionResolver.resolve(
            CallSessionState(calls = listOf(active)),
            CallAction.EndCall("active"),
        )
        assertEquals(CallActionPlan.Disconnect("active"), plan)
    }

    @Test
    fun `ending an already terminal call is ignored`() {
        val gone = callModel("gone", CallState.DISCONNECTED)
        val plan = CallActionResolver.resolve(
            CallSessionState(calls = listOf(gone)),
            CallAction.EndCall("gone"),
        )
        assertEquals(CallActionPlan.Ignored(IgnoreReason.WrongState), plan)
    }

    // --- hold, swap, merge ---

    @Test
    fun `hold is refused when the call reports it cannot be held`() {
        val nonHoldable = callModel("a", CallState.ACTIVE, capabilities = CallCapabilities(canHold = false))
        val plan = CallActionResolver.resolve(
            CallSessionState(calls = listOf(nonHoldable)),
            CallAction.Hold("a"),
        )
        assertEquals(CallActionPlan.Ignored(IgnoreReason.NotCapable), plan)
    }

    @Test
    fun `holding an already held call is ignored`() {
        val held = callModel("h", CallState.HOLDING, capabilities = CallCapabilities(canHold = true))
        val plan = CallActionResolver.resolve(
            CallSessionState(calls = listOf(held)),
            CallAction.Hold("h"),
        )
        assertEquals(CallActionPlan.Ignored(IgnoreReason.WrongState), plan)
    }

    @Test
    fun `swap requires both an active and a held call`() {
        assertEquals(
            CallActionPlan.Ignored(IgnoreReason.WrongState),
            CallActionResolver.resolve(CallSessionState(calls = listOf(active)), CallAction.Swap),
        )
        assertEquals(
            CallActionPlan.Swap,
            CallActionResolver.resolve(
                CallSessionState(calls = listOf(active, callModel("h", CallState.HOLDING))),
                CallAction.Swap,
            ),
        )
    }

    @Test
    fun `merge is refused without the capability`() {
        val state = CallSessionState(calls = listOf(active, callModel("h", CallState.HOLDING)))
        assertEquals(
            CallActionPlan.Ignored(IgnoreReason.NotCapable),
            CallActionResolver.resolve(state, CallAction.Merge),
        )
    }

    // --- DTMF ---

    @Test
    fun `dtmf is dropped on a call that is not connected`() {
        assertEquals(
            CallActionPlan.Ignored(IgnoreReason.WrongState),
            CallActionResolver.resolve(incomingOnly(), CallAction.PlayDtmf("ringing", '5')),
        )
    }

    @Test
    fun `dtmf is dropped on a held call`() {
        val held = callModel("h", CallState.HOLDING)
        assertEquals(
            CallActionPlan.Ignored(IgnoreReason.WrongState),
            CallActionResolver.resolve(
                CallSessionState(calls = listOf(held)),
                CallAction.PlayDtmf("h", '5'),
            ),
        )
    }

    @Test
    fun `dtmf reaches a connected call`() {
        assertEquals(
            CallActionPlan.PlayDtmf("active", '5'),
            CallActionResolver.resolve(
                CallSessionState(calls = listOf(active)),
                CallAction.PlayDtmf("active", '5'),
            ),
        )
    }

    // --- plans that commit a decision ---

    @Test
    fun `only answer, reject and end-then-answer commit a decision`() {
        assertEquals(true, CallActionPlan.Answer("a").commitsDecision)
        assertEquals(true, CallActionPlan.Reject("a", null).commitsDecision)
        assertEquals(true, CallActionPlan.EndThenAnswer("a", "b").commitsDecision)

        assertEquals(false, CallActionPlan.Disconnect("a").commitsDecision)
        assertEquals(false, CallActionPlan.Hold("a").commitsDecision)
        assertEquals(false, CallActionPlan.Swap.commitsDecision)
        assertEquals(false, CallActionPlan.Ignored(IgnoreReason.NoSuchCall).commitsDecision)
    }
}
