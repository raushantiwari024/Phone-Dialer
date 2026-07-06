package com.raushan.phone.telecom

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class MyInCallService : InCallService() {

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "onStateChanged: $call, state: ${callStateToString(state)}")
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: $call")
        call.registerCallback(callCallback)
        CallRepository.addCall(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: $call")
        call.unregisterCallback(callCallback)
        CallRepository.removeCall(call)
    }

    private fun callStateToString(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_RINGING -> "RINGING"
            Call.STATE_ACTIVE -> "ACTIVE"
            Call.STATE_HOLDING -> "HOLDING"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_DISCONNECTING -> "DISCONNECTING"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "SELECT_PHONE_ACCOUNT"
            else -> "UNKNOWN ($state)"
        }
    }

    companion object {
        private const val TAG = "MyInCallService"
    }
}