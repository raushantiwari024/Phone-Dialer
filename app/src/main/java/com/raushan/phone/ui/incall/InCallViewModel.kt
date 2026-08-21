package com.raushan.phone.ui.incall

import android.app.Application
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.data.ContactsRepository
import com.raushan.phone.telecom.CallRepository
import com.raushan.phone.telecom.MyInCallService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Suppress("DEPRECATION")
class InCallViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository(application)

    val activeCall: StateFlow<Call?> = CallRepository.currentCalls
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState = _callState.asStateFlow()

    private val _callerNumber = MutableStateFlow("")
    val callerNumber = _callerNumber.asStateFlow()

    private val _callerName = MutableStateFlow("")
    val callerName = _callerName.asStateFlow()

    private val _callDuration = MutableStateFlow(DEFAULT_DURATION)
    val callDuration = _callDuration.asStateFlow()

    private val _callerPhotoUri = MutableStateFlow<String?>(null)
    val callerPhotoUri = _callerPhotoUri.asStateFlow()

    private val _isCallScreenExpanded = MutableStateFlow(true)
    val isCallScreenExpanded = _isCallScreenExpanded.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn = _isSpeakerOn.asStateFlow()

    private var timerJob: Job? = null

    init {
        activeCall.onEach { call ->
            timerJob?.cancel()
            _callDuration.value = DEFAULT_DURATION
            
            call?.let {
                val number = it.details?.handle?.schemeSpecificPart ?: ""
                _callerNumber.value = number
                _callerName.value = CALLER_LOADING
                _isCallScreenExpanded.value = true
                
                // Fetch contact name & photo asynchronously
                viewModelScope.launch {
                    val contact = contactsRepository.getContactByNumber(number)
                    _callerName.value = contact?.name ?: CALLER_UNKNOWN
                    _callerPhotoUri.value = contact?.photoUri
                }

                _callState.value = it.state
                checkAndManageTimer(it.state)
                
                it.registerCallback(object : Call.Callback() {
                    override fun onStateChanged(call: Call, state: Int) {
                        _callState.value = state
                        checkAndManageTimer(state)
                    }
                })
            } ?: run {
                _callerNumber.value = ""
                _callerName.value = ""
                _callerPhotoUri.value = null
                _callState.value = Call.STATE_DISCONNECTED
                _isMuted.value = false
                _isSpeakerOn.value = false
            }
        }.launchIn(viewModelScope)
    }

    private fun checkAndManageTimer(state: Int) {
        if (state == Call.STATE_ACTIVE) {
            startTimer()
        } else {
            timerJob?.cancel()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        val currentCall = activeCall.value ?: return
        timerJob = viewModelScope.launch {
            while (true) {
                val connectTime = currentCall.details?.connectTimeMillis ?: 0L
                val now = System.currentTimeMillis()
                val elapsedMs = if (connectTime > 0L) now - connectTime else 0L
                val seconds = elapsedMs / 1000
                _callDuration.value = String.format(
                    Locale.getDefault(),
                    DURATION_FORMAT,
                    seconds / 60,
                    seconds % 60
                )
                delay(TIMER_INTERVAL_MS.milliseconds)
            }
        }
    }

    fun endCall() {
        activeCall.value?.disconnect()
    }

    fun answerCall() {
        activeCall.value?.answer(0)
    }

    fun toggleMute() {
        val nextMute = !_isMuted.value
        _isMuted.value = nextMute
        MyInCallService.instance?.setMuted(nextMute)
    }

    fun toggleSpeaker() {
        val nextSpeaker = !_isSpeakerOn.value
        _isSpeakerOn.value = nextSpeaker
        val route = if (nextSpeaker) {
            CallAudioState.ROUTE_SPEAKER
        } else {
            CallAudioState.ROUTE_WIRED_OR_EARPIECE
        }
        MyInCallService.instance?.setAudioRoute(route)
    }

    fun setCallScreenExpanded(expanded: Boolean) {
        _isCallScreenExpanded.value = expanded
    }

    companion object {
        const val CALLER_LOADING = "Loading\u2026"
        const val CALLER_UNKNOWN = "Unknown"
        private const val DEFAULT_DURATION = "00:00"
        private const val DURATION_FORMAT = "%02d:%02d"
        private const val TIMER_INTERVAL_MS = 1000L
    }
}
