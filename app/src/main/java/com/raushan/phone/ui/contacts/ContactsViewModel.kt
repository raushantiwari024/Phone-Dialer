package com.raushan.phone.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.data.ContactsRepository
import com.raushan.phone.data.models.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ContactsRepository(application)
    
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()

    init {
        fetchContacts()
    }

    private fun fetchContacts() {
        viewModelScope.launch {
            _contacts.value = repository.getContacts()
        }
    }
}
