package com.raushan.phone.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.data.ContactsRepository
import com.raushan.phone.data.CallLogRepository
import com.raushan.phone.data.models.Contact
import com.raushan.phone.data.models.CallLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val contactsRepository = ContactsRepository(application)
    private val callLogRepository = CallLogRepository(application)
    
    private val _contact = MutableStateFlow<Contact?>(null)
    val contact = _contact.asStateFlow()

    private val _callHistory = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callHistory = _callHistory.asStateFlow()

    fun loadContact(contactId: Long?, phoneNumber: String?) {
        viewModelScope.launch {
            var resolvedContact: Contact? = null
            var resolvedNumber: String? = phoneNumber

            if (contactId != null && contactId > 0) {
                resolvedContact = contactsRepository.getContactById(contactId)
                if (resolvedContact != null) {
                    resolvedNumber = resolvedContact.number
                }
            } else if (!phoneNumber.isNullOrBlank()) {
                resolvedContact = contactsRepository.getContactByNumber(phoneNumber)
            }

            if (resolvedContact == null && !resolvedNumber.isNullOrBlank()) {
                // Return a mock contact for unsaved phone numbers
                resolvedContact = Contact(
                    id = -1L,
                    name = resolvedNumber,
                    number = resolvedNumber
                )
            }

            _contact.value = resolvedContact

            if (!resolvedNumber.isNullOrBlank()) {
                _callHistory.value = callLogRepository.getCallLogsForNumber(resolvedNumber)
            } else {
                _callHistory.value = emptyList()
            }
        }
    }
}
