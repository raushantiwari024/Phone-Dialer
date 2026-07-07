package com.raushan.phone.ui.dialpad

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.telephony.TelephonyManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.data.CallLogRepository
import com.raushan.phone.data.ContactsRepository
import com.raushan.phone.data.models.Contact
import com.raushan.phone.data.models.CallLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DialpadSearchResult(
    val id: Long,
    val name: String,
    val number: String,
    val isCallLog: Boolean = false,
    val callType: Int? = null,
    val date: Long? = null
)

@OptIn(FlowPreview::class)
class DialpadViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        // Pre-compiled regex — avoids re-compiling on every search iteration
        private val PHONE_CLEAN_REGEX = Regex("[^0-9+]")
    }

    private val contactsRepository = ContactsRepository(application)
    private val callLogRepository = CallLogRepository(application)
    
    private val _phoneNumber = MutableStateFlow(TextFieldValue(""))
    val phoneNumber = _phoneNumber.asStateFlow()

    private val _searchResults = MutableStateFlow<List<DialpadSearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private var allContacts: List<Contact> = emptyList()
    private var allCallLogs: List<CallLogEntry> = emptyList()
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (e: Exception) {
            // Ignore if tone generator cannot be initialized
        }
        
        refreshData()
        
        phoneNumber
            .map { it.text }
            .distinctUntilChanged()
            .debounce(150) // Avoid redundant searches during rapid typing
            .onEach { query ->
                if (query.isEmpty()) {
                    _searchResults.value = emptyList()
                } else {
                    if (allContacts.isEmpty() || allCallLogs.isEmpty()) {
                        refreshData()
                    }
                    val results = searchContacts(query)
                    _searchResults.value = results
                }
            }
            .flowOn(Dispatchers.Default) // Run search off the main thread
            .launchIn(viewModelScope)
    }

    fun refreshData() {
        viewModelScope.launch {
            allContacts = contactsRepository.getContacts()
            allCallLogs = callLogRepository.getCallLogs()
        }
    }

    private suspend fun searchContacts(query: String): List<DialpadSearchResult> = withContext(Dispatchers.Default) {
        val filteredContacts = allContacts.filter { contact ->
            contact.number.contains(query, ignoreCase = true) || 
            matchesT9(contact.name, query)
        }.map { contact ->
            DialpadSearchResult(
                id = contact.id,
                name = contact.name,
                number = contact.number,
                isCallLog = false
            )
        }

        val filteredCallLogs = allCallLogs.filter { log ->
            log.number.contains(query, ignoreCase = true)
        }.map { log ->
            val matchingContact = allContacts.find { 
                val cleanContact = it.number.replace(PHONE_CLEAN_REGEX, "")
                val cleanLog = log.number.replace(PHONE_CLEAN_REGEX, "")
                cleanContact.endsWith(cleanLog) || cleanLog.endsWith(cleanContact)
            }
            DialpadSearchResult(
                id = log.id,
                name = matchingContact?.name ?: "Unknown Number",
                number = log.number,
                isCallLog = true,
                callType = log.type,
                date = log.date
            )
        }

        // Merge results, prioritizing contacts, then distinct by phone number
        (filteredContacts + filteredCallLogs).distinctBy { 
            it.number.replace(PHONE_CLEAN_REGEX, "") 
        }
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
        playDtmfTone(digit)
        _phoneNumber.update { currentValue ->
            val text = currentValue.text
            val selStart = currentValue.selection.start
            val selEnd = currentValue.selection.end
            val start = if (selStart < 0) text.length else selStart.coerceAtMost(text.length)
            val end = if (selEnd < 0) text.length else selEnd.coerceAtMost(text.length)
            val rangeStart = minOf(start, end)
            val rangeEnd = maxOf(start, end)
            
            val newText = text.replaceRange(rangeStart, rangeEnd, digit)
            val newSelectionStart = rangeStart + digit.length
            TextFieldValue(
                text = newText,
                selection = TextRange(newSelectionStart)
            )
        }
    }

    fun onPhoneNumberChange(newValue: TextFieldValue) {
        val text = newValue.text
        val selStart = newValue.selection.start
        val selEnd = newValue.selection.end
        val start = if (selStart < 0) text.length else selStart.coerceAtMost(text.length)
        val end = if (selEnd < 0) text.length else selEnd.coerceAtMost(text.length)
        val rangeStart = minOf(start, end)
        val rangeEnd = maxOf(start, end)
        
        val coercedValue = newValue.copy(selection = TextRange(rangeStart, rangeEnd))
        
        if (coercedValue.text != _phoneNumber.value.text) {
            val filteredText = coercedValue.text.filter { it.isDigit() || it == '*' || it == '#' || it == '+' }
            val difference = coercedValue.text.length - filteredText.length
            val newSelection = if (difference > 0) {
                TextRange(filteredText.length)
            } else {
                coercedValue.selection
            }
            _phoneNumber.value = TextFieldValue(text = filteredText, selection = newSelection)
        } else {
            _phoneNumber.value = coercedValue
        }
    }

    fun onPaste(text: String) {
        val cleanText = text.filter { it.isDigit() || it == '*' || it == '#' || it == '+' }
        if (cleanText.isEmpty()) return
        _phoneNumber.update { currentValue ->
            val currentText = currentValue.text
            val selStart = currentValue.selection.start
            val selEnd = currentValue.selection.end
            val start = if (selStart < 0) currentText.length else selStart.coerceAtMost(currentText.length)
            val end = if (selEnd < 0) currentText.length else selEnd.coerceAtMost(currentText.length)
            val rangeStart = minOf(start, end)
            val rangeEnd = maxOf(start, end)
            
            val newText = currentText.replaceRange(rangeStart, rangeEnd, cleanText)
            val newSelectionStart = rangeStart + cleanText.length
            TextFieldValue(
                text = newText,
                selection = TextRange(newSelectionStart)
            )
        }
    }

    fun onDeleteClick() {
        _phoneNumber.update { currentValue ->
            val text = currentValue.text
            val selStart = currentValue.selection.start
            val selEnd = currentValue.selection.end
            val start = if (selStart < 0) text.length else selStart.coerceAtMost(text.length)
            val end = if (selEnd < 0) text.length else selEnd.coerceAtMost(text.length)
            val rangeStart = minOf(start, end)
            val rangeEnd = maxOf(start, end)
            
            if (rangeStart != rangeEnd) {
                val newText = text.removeRange(rangeStart, rangeEnd)
                TextFieldValue(
                    text = newText,
                    selection = TextRange(rangeStart)
                )
            } else if (rangeStart > 0) {
                val newText = text.removeRange(rangeStart - 1, rangeStart)
                TextFieldValue(
                    text = newText,
                    selection = TextRange(rangeStart - 1)
                )
            } else {
                currentValue
            }
        }
    }

    fun onClearAll() {
        _phoneNumber.value = TextFieldValue("")
    }

    private fun playDtmfTone(digit: String) {
        val tone = when (digit) {
            "1" -> ToneGenerator.TONE_DTMF_1
            "2" -> ToneGenerator.TONE_DTMF_2
            "3" -> ToneGenerator.TONE_DTMF_3
            "4" -> ToneGenerator.TONE_DTMF_4
            "5" -> ToneGenerator.TONE_DTMF_5
            "6" -> ToneGenerator.TONE_DTMF_6
            "7" -> ToneGenerator.TONE_DTMF_7
            "8" -> ToneGenerator.TONE_DTMF_8
            "9" -> ToneGenerator.TONE_DTMF_9
            "0" -> ToneGenerator.TONE_DTMF_0
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            else -> -1
        }
        if (tone != -1) {
            viewModelScope.launch {
                try {
                    toneGenerator?.startTone(tone, 120)
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
        }
    }

    fun getSpeedDialNumber(context: Context, digit: String): String? {
        return when (digit) {
            "1" -> {
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    telephonyManager.voiceMailNumber ?: "1"
                } catch (e: SecurityException) {
                    "1"
                }
            }
            "2" -> "555-0102"
            "3" -> "555-0103"
            "4" -> "555-0104"
            "5" -> "555-0105"
            "6" -> "555-0106"
            "7" -> "555-0107"
            "8" -> "555-0108"
            "9" -> "555-0109"
            else -> null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
