package com.raushan.phone.ui.calllog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.data.CallLogRepository
import com.raushan.phone.data.models.CallLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CallLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CallLogRepository(application)
    
    private val _callLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callLogs = _callLogs.asStateFlow()

    init {
        fetchCallLogs()
    }

    private fun fetchCallLogs() {
        viewModelScope.launch {
            _callLogs.value = repository.getCallLogs()
        }
    }
}
