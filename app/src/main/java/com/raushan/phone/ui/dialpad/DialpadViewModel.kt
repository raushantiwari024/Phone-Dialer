package com.raushan.phone.ui.dialpad

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DialpadViewModel : ViewModel() {
    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber = _phoneNumber.asStateFlow()

    fun onDigitClick(digit: String) {
        _phoneNumber.update { it + digit }
    }

    fun onDeleteClick() {
        _phoneNumber.update { if (it.isNotEmpty()) it.dropLast(1) else "" }
    }

    fun onClearAll() {
        _phoneNumber.value = ""
    }
}
