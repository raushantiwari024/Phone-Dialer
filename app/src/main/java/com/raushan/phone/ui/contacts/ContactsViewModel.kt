package com.raushan.phone.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.data.CallLogGrouping
import com.raushan.phone.data.ContactsRepository
import com.raushan.phone.data.models.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the contacts list is currently showing. */
sealed interface ContactsUiState {
    data object Loading : ContactsUiState

    data class Loaded(val contacts: List<Contact>, val isFiltered: Boolean) : ContactsUiState

    /** Loaded successfully but there is nothing to show. */
    data class Empty(val isFiltered: Boolean) : ContactsUiState
}

@OptIn(FlowPreview::class)
class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContactsRepository(application)

    private val allContacts = MutableStateFlow<List<Contact>?>(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Distinguishes loading from genuinely empty.
     *
     * The screen previously keyed off `contacts.isEmpty()`, so during the initial load it showed
     * "No contacts found" — which read as a blank screen for several seconds and was
     * indistinguishable from having no contacts or from a denied permission.
     */
    val uiState: StateFlow<ContactsUiState> = combine(
        allContacts,
        _searchQuery.debounce(SEARCH_DEBOUNCE_MS),
    ) { contacts, query ->
        when {
            contacts == null -> ContactsUiState.Loading

            query.isBlank() -> if (contacts.isEmpty()) {
                ContactsUiState.Empty(isFiltered = false)
            } else {
                ContactsUiState.Loaded(contacts, isFiltered = false)
            }

            else -> {
                val matches = contacts.filter { it.matches(query) }
                if (matches.isEmpty()) {
                    ContactsUiState.Empty(isFiltered = true)
                } else {
                    ContactsUiState.Loaded(matches, isFiltered = true)
                }
            }
        }
    }
        // Filtering happens off the main thread, so typing stays responsive on a large address book.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), ContactsUiState.Loading)

    init {
        load()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    /** Re-reads the address book, bypassing the shared cache. */
    fun refresh() {
        repository.invalidate()
        allContacts.value = null
        load()
    }

    private fun load() {
        viewModelScope.launch {
            // Distinct, so a contact with mobile, home and work numbers appears once rather than
            // three times.
            allContacts.value = repository.getDistinctContacts()
        }
    }

    /**
     * Matches a contact against a query by name or number.
     *
     * Number matching compares canonical keys, so searching `9812` finds a contact stored as
     * `+91 98123 45678`. Name matching is a plain substring, case-insensitive.
     */
    private fun Contact.matches(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        if (name.contains(trimmed, ignoreCase = true)) return true

        val queryDigits = trimmed.filter { it.isDigit() }
        if (queryDigits.isEmpty()) return false
        return CallLogGrouping.numberKey(number).contains(queryDigits)
    }

    private companion object {
        private const val SEARCH_DEBOUNCE_MS = 120L
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
