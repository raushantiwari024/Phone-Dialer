package com.raushan.phone.ui.incall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.telecom.CallRepository
import com.raushan.phone.telecom.TelecomHelper
import com.raushan.phone.telecom.model.CallDuration
import com.raushan.phone.telecom.model.CallModel
import com.raushan.phone.telecom.model.CallSessionState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Projects [CallRepository] into a single immutable UI state, and turns UI actions into
 * [TelecomHelper] calls.
 *
 * Holds no call state of its own: it registers no `Call.Callback` and keeps no optimistic mute or
 * speaker flags. Both were previously true, and the callback was never unregistered.
 */
class InCallViewModel(application: Application) : AndroidViewModel(application) {

    private val telecomHelper = TelecomHelper(application)

    /** Ticks once a second so elapsed durations recompute. Independent of call state changes. */
    private val ticker = MutableStateFlow(0L)

    private val dialpadVisible = MutableStateFlow(false)
    private val dialpadDigits = MutableStateFlow("")

    private val _events = MutableSharedFlow<InCallUiEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<InCallUiEvent> = _events.asSharedFlow()

    /**
     * `Eagerly`, not `WhileSubscribed`. The old `WhileSubscribed(5000)` restarted the upstream on every
     * background/foreground cycle; combined with per-emission callback registration that produced
     * accumulating callbacks and a timer cancel/restart storm.
     */
    val uiState: StateFlow<InCallUiState> = combine(
        CallRepository.state,
        ticker,
        dialpadVisible,
        dialpadDigits,
    ) { session, _, dialpadOpen, digits ->
        session.toUiState(dialpadOpen, digits)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, InCallUiState.EMPTY)

    init {
        viewModelScope.launch {
            while (true) {
                delay(TICK_INTERVAL_MS)
                ticker.value += 1
            }
        }
    }

    fun onAction(action: InCallAction) {
        val session = CallRepository.state.value
        when (action) {
            InCallAction.Answer -> answer(session)

            InCallAction.AnswerHoldingCurrent ->
                session.ringingCall?.let { telecomHelper.answerAndHoldActive(it.id) }

            InCallAction.AnswerEndingCurrent ->
                session.ringingCall?.let { telecomHelper.answerAndEndActive(it.id) }

            InCallAction.Decline ->
                session.ringingCall?.let { telecomHelper.rejectCall(it.id) }

            InCallAction.EndCall -> endCall(session)

            InCallAction.ToggleMute -> telecomHelper.toggleMute()

            InCallAction.ToggleSpeaker -> telecomHelper.toggleSpeaker()

            InCallAction.ToggleHold -> toggleHold(session)

            InCallAction.Swap -> telecomHelper.swapCalls()

            InCallAction.Merge -> telecomHelper.mergeCalls()

            InCallAction.ShowDialpad -> dialpadVisible.value = true

            InCallAction.HideDialpad -> {
                dialpadVisible.value = false
                dialpadDigits.value = ""
            }

            is InCallAction.PressDialKey -> {
                session.activeCall?.let { telecomHelper.playDtmfTone(it.id, action.digit) }
                dialpadDigits.value += action.digit
            }

            InCallAction.ReleaseDialKey ->
                session.activeCall?.let { telecomHelper.stopDtmfTone(it.id) }

            InCallAction.AddCall -> _events.tryEmit(InCallUiEvent.OpenDialerForSecondCall)

            InCallAction.ReplyWithMessage -> replyWithMessage(session)
        }
    }

    private fun answer(session: CallSessionState) {
        val ringing = session.ringingCall ?: return
        if (session.mustEndActiveToAnswer) {
            telecomHelper.answerAndEndActive(ringing.id)
        } else {
            telecomHelper.answerCall(ringing.id)
        }
    }

    /** A ringing call is rejected; anything else is disconnected. */
    private fun endCall(session: CallSessionState) {
        val target = session.primaryCall ?: session.ringingCall ?: return
        if (target.isRinging) {
            telecomHelper.rejectCall(target.id)
        } else {
            telecomHelper.endCall(target.id)
        }
    }

    private fun toggleHold(session: CallSessionState) {
        val held = session.heldCall
        val active = session.activeCall
        when {
            // With two calls the control means swap, not hold.
            held != null && active != null -> telecomHelper.swapCalls()
            held != null -> telecomHelper.unholdCall(held.id)
            active != null -> telecomHelper.holdCall(active.id)
        }
    }

    /**
     * Order matters: the SMS app is opened first and the call is only released afterwards.
     *
     * The previous implementation ended the call and *then* fired the intent, so if the target could
     * not be launched the caller was hung up and no message was ever sent.
     */
    private fun replyWithMessage(session: CallSessionState) {
        val ringing = session.ringingCall ?: return
        if (!ringing.hasDisplayableNumber) return
        _events.tryEmit(InCallUiEvent.OpenSms(ringing.number))
    }

    private fun CallSessionState.toUiState(
        dialpadOpen: Boolean,
        digits: String,
    ): InCallUiState {
        val primaryModel = primaryCall
        val mode = when {
            primaryModel == null -> InCallUiState.Mode.NoCall
            isCallWaiting -> InCallUiState.Mode.IncomingWhileOngoing
            primaryModel.isRinging -> InCallUiState.Mode.Incoming
            canSwap -> InCallUiState.Mode.TwoOngoing
            else -> InCallUiState.Mode.Ongoing
        }

        return InCallUiState(
            mode = mode,
            primary = primaryModel?.toCardState(),
            secondary = secondaryCall?.toCardState(),
            isMuted = audio.isMuted,
            isSpeakerOn = audio.isSpeakerOn,
            canAddCall = canAddCall,
            canHold = primaryModel?.capabilities?.canHold == true,
            canSwap = canSwap,
            canMerge = canMerge,
            canReplyWithMessage = ringingCall?.capabilities?.canRespondViaText == true,
            mustEndActiveToAnswer = mustEndActiveToAnswer,
            dialpadVisible = dialpadOpen,
            dialpadDigits = digits,
        )
    }

    private fun CallModel.toCardState(): CallCardUiState = CallCardUiState(
        callId = id,
        displayName = displayName,
        number = number,
        photoUri = photoUri,
        state = state,
        durationText = CallDuration.since(connectTimeMillis, System.currentTimeMillis()),
        isEmergency = isEmergency,
    )

    private companion object {
        private const val TICK_INTERVAL_MS = 1000L
        private const val EVENT_BUFFER = 4
    }
}
