package com.raushan.phone.telecom

import android.content.Context
import android.net.Uri
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log

class TelecomHelper(private val context: Context) {

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    fun makeCall(phoneNumber: String) {
        if (CallRepository.currentCalls.value.isNotEmpty()) {
            Log.w("TelecomHelper", "A call is already in progress. Ignoring makeCall request.")
            return
        }
        try {
            val uri = Uri.fromParts("tel", phoneNumber, null)
            telecomManager.placeCall(uri, null)
        } catch (e: SecurityException) {
            Log.e("TelecomHelper", "Permission denied for placeCall", e)
        }
    }

    fun endCall(call: Call) {
        call.disconnect()
    }

    fun answerCall(call: Call) {
        call.answer(0)
    }

    fun holdCall(call: Call) {
        call.hold()
    }

    fun unholdCall(call: Call) {
        call.unhold()
    }
}