package com.raushan.phone.ui.incall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.telecom.CallRepository
import com.raushan.phone.telecom.TelecomHelper
import com.raushan.phone.telecom.model.CallDuration
import com.raushan.phone.telecom.model.CallModel
import com.raushan.phone.telecom.model.CallSessionState
import com.raushan.phone.telecom.model.CallState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * In-call state for the current UI.
 *
 * A projection of [CallRepository] — it registers no `Call.Callback` of its own and keeps no optimistic
 * audio state. The previous version did both: it registered an anonymous callback inside a flow
 * collector and never unregistered it, and it flipped local mute and speaker booleans that were never
 * reconciled with the real audio route.
 *
 * The nine separate flows here are retained so the existing screens keep working while the UI is
 * migrated to a single immutable state wrapper in a later step.
 */
class InCallViewModel(application: Application) : AndroidViewModel(application) {

    private val telecomHelper = TelecomHelper(application)

    /**
     * `Eagerly`, not `WhileSubscribed`. The old `WhileSubscribed(5000)` restarted the upstream on every
     * background/foreground cycle, and because the collector registered a callback each time, those
     * accumulated and triggered a timer cancel/restart storm on every state change.
     */
    private val session: StateFlow<CallSessionState> = CallRepository.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, CallSessionState.EMPTY)

    val activeCall: StateFlow<CallModel?> = session
        .map { it.primaryCall }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val callState: StateFlow<CallState> = session
        .map { it.primaryCall?.state ?: CallState.DISCONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CallState.DISCONNECTED)

    val callerNumber: StateFlow<String> = session
        .map { it.primaryCall?.number.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /**
     * Already resolved upstream, so there is no "Loading…" sentinel to compare against any more. The
     * old code compared a ViewModel string constant against a *string resource* to decide whether to
     * draw a monogram, which broke under localisation.
     */
    val callerName: StateFlow<String> = session
        .map { it.primaryCall?.displayName.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val callerPhotoUri: StateFlow<String?> = session
        .map { it.primaryCall?.photoUri }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isMuted: StateFlow<Boolean> = session
        .map { it.audio.isMuted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isSpeakerOn: StateFlow<Boolean> = session
        .map { it.audio.isSpeakerOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _callDuration = MutableStateFlow(CallDuration.ZERO)
    val callDuration: StateFlow<String> = _callDuration.asStateFlow()

    private val _isCallScreenExpanded = MutableStateFlow(true)
    val isCallScreenExpanded: StateFlow<Boolean> = _isCallScreenExpanded.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Re-expand for each new call, keyed on the call id so a state change within one call does not
        // yank the screen back open.
        session
            .map { it.primaryCall?.id }
            .distinctUntilChanged()
            .onEach { id -> if (id != null) _isCallScreenExpanded.value = true }
            .launchIn(viewModelScope)

        session
            .map { it.primaryCall?.takeIf { call -> call.state == CallState.ACTIVE }?.connectTimeMillis }
            .distinctUntilChanged()
            .onEach(::restartTimer)
            .launchIn(viewModelScope)
    }

    private fun restartTimer(connectTimeMillis: Long?) {
        timerJob?.cancel()
        if (connectTimeMillis == null || connectTimeMillis <= 0L) {
            _callDuration.value = CallDuration.ZERO
            return
        }
        timerJob = viewModelScope.launch {
            while (true) {
                _callDuration.value =
                    CallDuration.since(connectTimeMillis, System.currentTimeMillis())
                delay(TIMER_INTERVAL_MS)
            }
        }
    }

    fun endCall() {
        val session = session.value
        val target = session.ringingCall ?: session.primaryCall ?: return
        if (target.isRinging) {
            telecomHelper.rejectCall(target.id)
        } else {
            telecomHelper.endCall(target.id)
        }
    }

    fun answerCall() {
        val session = session.value
        val ringing = session.ringingCall ?: return
        when {
            session.mustEndActiveToAnswer -> telecomHelper.answerAndEndActive(ringing.id)
            else -> telecomHelper.answerCall(ringing.id)
        }
    }

    fun toggleMute() {
        telecomHelper.toggleMute()
    }

    fun toggleSpeaker() {
        telecomHelper.toggleSpeaker()
    }

    fun setCallScreenExpanded(expanded: Boolean) {
        _isCallScreenExpanded.value = expanded
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    private companion object {
        private const val TIMER_INTERVAL_MS = 1000L
    }
}
