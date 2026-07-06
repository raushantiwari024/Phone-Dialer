package com.raushan.phone.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.data.ContactsRepository
import com.raushan.phone.data.models.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ContactsRepository(application)
    
    private val _contact = MutableStateFlow<Contact?>(null)
    val contact = _contact.asStateFlow()

    fun loadContact(contactId: Long) {
        viewModelScope.launch {
            _contact.value = repository.getContactById(contactId)
        }
    }
}
