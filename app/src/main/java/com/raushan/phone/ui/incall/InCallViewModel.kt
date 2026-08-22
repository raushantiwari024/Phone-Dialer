package com.raushan.phone.ui.incall

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.telecom.CallAction
import com.raushan.phone.telecom.CallActionDispatcher
import com.raushan.phone.telecom.CallRepository
import com.raushan.phone.telecom.TelecomHelper
import com.raushan.phone.telecom.model.AudioRoute
import com.raushan.phone.telecom.model.CallDuration
import com.raushan.phone.telecom.model.CallModel
import com.raushan.phone.telecom.model.CallSessionState
import kotlinx.coroutines.Job
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

    private var dtmfStopJob: Job? = null
    private var dtmfStartedAtMillis = 0L
    private var dtmfCallId: String? = null

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

    /**
     * Translates a UI action into a canonical [CallAction] and dispatches it.
     *
     * This function decides only which action the tap means; [CallActionDispatcher] decides what
     * happens. That is what keeps the popup and the notification in lockstep — previously this branched
     * over three different [TelecomHelper] methods while the notification called a fourth.
     */
    fun onAction(action: InCallAction) {
        val session = CallRepository.state.value
        when (action) {
            InCallAction.Answer -> dispatchAnswer(session)

            InCallAction.AnswerHoldingCurrent -> session.ringingCall?.let {
                dispatch(CallAction.AnswerIncomingAndHoldCurrent(it.id))
            }

            InCallAction.AnswerEndingCurrent -> session.ringingCall?.let {
                dispatch(CallAction.AnswerIncomingAndEndCurrent(it.id))
            }

            InCallAction.Decline -> session.ringingCall?.let {
                dispatch(CallAction.DeclineIncoming(it.id))
            }

            InCallAction.EndCall -> endCall(session)

            InCallAction.ToggleMute -> dispatch(CallAction.SetMuted(!session.audio.isMuted))

            InCallAction.ToggleSpeaker -> dispatch(
                CallAction.SetAudioRoute(
                    if (session.audio.isSpeakerOn) session.audio.defaultEarRoute else AudioRoute.SPEAKER,
                ),
            )

            InCallAction.ToggleHold -> toggleHold(session)

            InCallAction.Swap -> dispatch(CallAction.Swap)

            InCallAction.Merge -> dispatch(CallAction.Merge)

            InCallAction.ShowDialpad -> dialpadVisible.value = true

            InCallAction.HideDialpad -> {
                dialpadVisible.value = false
                dialpadDigits.value = ""
            }

            is InCallAction.PressDialKey -> startDtmf(session, action.digit)

            InCallAction.ReleaseDialKey -> stopDtmf(session)

            InCallAction.AddCall -> _events.tryEmit(InCallUiEvent.OpenDialerForSecondCall)

            InCallAction.ReplyWithMessage -> replyWithMessage(session)
        }
    }

    private fun dispatch(action: CallAction) {
        CallActionDispatcher.dispatch(telecomHelper, action)
    }

    /**
     * Plain "Answer".
     *
     * Answering holds the current call. The one exception is a call that reports it cannot be held,
     * where holding is impossible and ending is the only way to accept — the UI hides "Hold & Accept"
     * in that case, and this keeps the bare Answer path consistent with it.
     */
    private fun dispatchAnswer(session: CallSessionState) {
        val ringing = session.ringingCall ?: return
        dispatch(
            if (session.mustEndActiveToAnswer) {
                CallAction.AnswerIncomingAndEndCurrent(ringing.id)
            } else {
                CallAction.AnswerIncomingAndHoldCurrent(ringing.id)
            },
        )
    }

    /**
     * Starts a DTMF tone.
     *
     * Falls back to the primary call so a state Telecom has not settled yet cannot silently swallow a
     * keypress; the resolver still rejects the tone if the call is not actually connected.
     */
    private fun startDtmf(session: CallSessionState, digit: Char) {
        val target = session.activeCall ?: session.primaryCall
        if (target == null) {
            Log.w(TAG, "DTMF '$digit' dropped: no active call")
            return
        }
        dtmfStopJob?.cancel()
        dtmfStartedAtMillis = System.currentTimeMillis()
        dtmfCallId = target.id
        dispatch(CallAction.PlayDtmf(target.id, digit))
        dialpadDigits.value += digit
    }

    /**
     * Stops the tone, but never before [MIN_DTMF_DURATION_MS] has elapsed.
     *
     * Playback is press-and-hold, so a quick tap would otherwise start and stop the tone within a few
     * tens of milliseconds — below the ~70ms the DTMF standard requires and well below what real IVR
     * systems detect. Holding a floor duration makes a tap behave like a deliberate keypress.
     */
    private fun stopDtmf(session: CallSessionState) {
        val callId = dtmfCallId ?: session.activeCall?.id ?: return
        val elapsed = System.currentTimeMillis() - dtmfStartedAtMillis
        val remaining = (MIN_DTMF_DURATION_MS - elapsed).coerceAtLeast(0L)
        dtmfStopJob = viewModelScope.launch {
            if (remaining > 0L) delay(remaining)
            dispatch(CallAction.StopDtmf(callId))
            dtmfCallId = null
        }
    }

    /** Hang up. The resolver rejects a ringing call and disconnects anything else. */
    private fun endCall(session: CallSessionState) {
        val target = session.primaryCall ?: session.ringingCall ?: return
        dispatch(CallAction.EndCall(target.id))
    }

    private fun toggleHold(session: CallSessionState) {
        val held = session.heldCall
        val active = session.activeCall
        when {
            // With two calls the control means swap, not hold.
            held != null && active != null -> dispatch(CallAction.Swap)
            held != null -> dispatch(CallAction.Unhold(held.id))
            active != null -> dispatch(CallAction.Hold(active.id))
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

    override fun onCleared() {
        dtmfStopJob?.cancel()
        super.onCleared()
    }

    private companion object {
        private const val TAG = "InCallViewModel"
        private const val TICK_INTERVAL_MS = 1000L
        private const val EVENT_BUFFER = 4

        /**
         * Floor duration for a DTMF tone. The standard requires at least ~70ms; real IVR systems want
         * more, and carriers vary. 250ms is comfortably detectable without feeling laggy.
         */
        private const val MIN_DTMF_DURATION_MS = 250L
    }
}
