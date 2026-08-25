package com.raushan.phone.ui.calllog

import android.app.Application
import android.content.Context
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raushan.phone.data.CallLogDay
import com.raushan.phone.data.CallLogGroup
import com.raushan.phone.data.CallLogGrouping
import com.raushan.phone.data.CallLogRepository
import com.raushan.phone.data.models.CallLogEntry
import com.raushan.phone.data.models.SwipeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class CallLogFilter {
    ALL, MISSED
}

class CallLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CallLogRepository(application)
    private val sharedPrefs = application.getSharedPreferences("dialer_settings", Context.MODE_PRIVATE)
    
    private val _rawCallLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    
    private val _currentFilter = MutableStateFlow(CallLogFilter.ALL)
    val currentFilter = _currentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _swipeLeftAction = MutableStateFlow(getSwipeAction("swipe_left", SwipeAction.DELETE))
    val swipeLeftAction = _swipeLeftAction.asStateFlow()

    private val _swipeRightAction = MutableStateFlow(getSwipeAction("swipe_right", SwipeAction.CALL))
    val swipeRightAction = _swipeRightAction.asStateFlow()
    
    /**
     * Recents rows, grouped by day and then by contact.
     *
     * Grouping is delegated to [CallLogGrouping], which is pure and unit-tested. The previous inline
     * implementation collapsed only *consecutive* runs of a number, so a contact called at 10:00 and
     * again at 15:00 with someone else in between appeared twice in the same day.
     */
    val groupedCallLogs: StateFlow<List<CallLogDay>> = 
        combine(_rawCallLogs, _currentFilter, _searchQuery) { logs, filter, query ->
            var filtered = when (filter) {
                CallLogFilter.ALL -> logs
                CallLogFilter.MISSED -> logs.filter { it.type == CallLog.Calls.MISSED_TYPE }
            }
            
            if (query.isNotBlank()) {
                filtered = filtered.filter { log ->
                    log.number.contains(query, ignoreCase = true) ||
                    (log.cachedName?.contains(query, ignoreCase = true) == true)
                }
            }
            
            CallLogGrouping.group(filtered)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        fetchCallLogs()
    }

    fun refresh() {
        fetchCallLogs()
        refreshSettings()
    }

    fun refreshSettings() {
        _swipeLeftAction.value = getSwipeAction("swipe_left", SwipeAction.DELETE)
        _swipeRightAction.value = getSwipeAction("swipe_right", SwipeAction.CALL)
    }

    private fun getSwipeAction(key: String, default: SwipeAction): SwipeAction {
        val name = sharedPrefs.getString(key, default.name) ?: default.name
        return try { SwipeAction.valueOf(name) } catch (e: Exception) { default }
    }

    fun setFilter(filter: CallLogFilter) {
        _currentFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteGroup(group: CallLogGroup) {
        viewModelScope.launch {
            var anyDeleted = false
            for (log in group.callLogs) {
                if (repository.deleteCallLog(log.id)) {
                    anyDeleted = true
                }
            }
            if (anyDeleted) {
                fetchCallLogs()
            }
        }
    }


    private fun fetchCallLogs() {
        viewModelScope.launch {
            _rawCallLogs.value = repository.getCallLogs()
        }
    }

}
