package com.raushan.phone.ui.incall

import android.telecom.Call
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.telecom.CallRepository
import kotlinx.coroutines.flow.*

class InCallViewModel : ViewModel() {
    
    val activeCall: StateFlow<Call?> = CallRepository.currentCalls
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState = _callState.asStateFlow()

    init {
        activeCall.onEach { call ->
            call?.let {
                _callState.value = it.state
                it.registerCallback(object : Call.Callback() {
                    override fun onStateChanged(call: Call, state: Int) {
                        _callState.value = state
                    }
                })
            } ?: run {
                _callState.value = Call.STATE_DISCONNECTED
            }
        }.launchIn(viewModelScope)
    }

    fun endCall() {
        activeCall.value?.disconnect()
    }

    fun answerCall() {
        activeCall.value?.answer(0)
    }
}
