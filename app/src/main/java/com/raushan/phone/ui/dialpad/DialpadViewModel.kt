package com.raushan.phone.ui.dialpad

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.data.ContactsRepository
import com.raushan.phone.data.models.Contact
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DialpadViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ContactsRepository(application)
    
    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber = _phoneNumber.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Contact>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private var allContacts: List<Contact> = emptyList()

    init {
        refreshContacts()
        
        phoneNumber.onEach { query ->
            if (query.isEmpty()) {
                _searchResults.value = emptyList()
            } else {
                if (allContacts.isEmpty()) {
                    refreshContacts()
                }
                searchContacts(query)
            }
        }.launchIn(viewModelScope)
    }

    fun refreshContacts() {
        viewModelScope.launch {
            allContacts = repository.getContacts()
        }
    }

    private fun searchContacts(query: String) {
        val filtered = allContacts.filter { contact ->
            contact.number.contains(query, ignoreCase = true) || 
            matchesT9(contact.name, query)
        }
        _searchResults.value = filtered
    }

    // Basic T9 matching logic
    private fun matchesT9(name: String, query: String): Boolean {
        val nameWords = name.lowercase().split(" ")
        return nameWords.any { word ->
            if (word.length < query.length) return@any false
            val t9Version = word.take(query.length).map { char ->
                when (char) {
                    'a', 'b', 'c' -> '2'
                    'd', 'e', 'f' -> '3'
                    'g', 'h', 'i' -> '4'
                    'j', 'k', 'l' -> '5'
                    'm', 'n', 'o' -> '6'
                    'p', 'q', 'r', 's' -> '7'
                    't', 'u', 'v' -> '8'
                    'w', 'x', 'y', 'z' -> '9'
                    else -> ' '
                }
            }.joinToString("")
            t9Version == query
        }
    }

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
