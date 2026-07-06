package com.raushan.phone.telecom

import android.telecom.Call
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object CallRepository {
    private const val TAG = "CallRepository"

    private val _currentCalls = MutableStateFlow<List<Call>>(emptyList())
    val currentCalls = _currentCalls.asStateFlow()

    fun addCall(call: Call) {
        Log.d(TAG, "addCall: $call")
        _currentCalls.update { it + call }
    }

    fun removeCall(call: Call) {
        Log.d(TAG, "removeCall: $call")
        _currentCalls.update { it - call }
    }
}