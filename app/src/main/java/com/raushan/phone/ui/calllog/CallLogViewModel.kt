package com.raushan.phone.ui.calllog

import android.app.Application
import android.content.Context
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class CallLogGroup(
    val mainEntry: CallLogEntry,
    val totalCount: Int,
    val callLogs: List<CallLogEntry>
)

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
    
    val groupedCallLogs: StateFlow<Map<String, List<CallLogGroup>>> = 
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
            
            groupAndCollapseConsecutiveLogs(filtered)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
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

    private fun groupAndCollapseConsecutiveLogs(logs: List<CallLogEntry>): Map<String, List<CallLogGroup>> {
        val groups = LinkedHashMap<String, MutableList<CallLogGroup>>()
        
        // Group by day first
        val dayGrouped = LinkedHashMap<String, MutableList<CallLogEntry>>()
        for (log in logs) {
            val header = getGroupHeader(log.date)
            if (!dayGrouped.containsKey(header)) {
                dayGrouped[header] = mutableListOf()
            }
            dayGrouped[header]?.add(log)
        }
        
        // Collapse consecutive logs of the same number in each day
        for ((header, dayLogs) in dayGrouped) {
            val collapsedList = mutableListOf<CallLogGroup>()
            var i = 0
            while (i < dayLogs.size) {
                val currentLog = dayLogs[i]
                val currentGroupLogs = mutableListOf(currentLog)
                
                var j = i + 1
                while (j < dayLogs.size) {
                    val nextLog = dayLogs[j]
                    if (isSameNumber(currentLog.number, nextLog.number)) {
                        currentGroupLogs.add(nextLog)
                        j++
                    } else {
                        break
                    }
                }
                
                collapsedList.add(
                    CallLogGroup(
                        mainEntry = currentLog,
                        totalCount = currentGroupLogs.size,
                        callLogs = currentGroupLogs
                    )
                )
                i = j
            }
            groups[header] = collapsedList
        }
        
        return groups
    }

    private fun isSameNumber(num1: String, num2: String): Boolean {
        val clean1 = num1.replace(Regex("[^0-9+]"), "")
        val clean2 = num2.replace(Regex("[^0-9+]"), "")
        if (clean1.isEmpty() || clean2.isEmpty()) return num1 == num2
        return clean1.endsWith(clean2) || clean2.endsWith(clean1)
    }

    private fun getGroupHeader(timestamp: Long): String {
        val logCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
        
        return when {
            isSameDay(logCal, today) -> "Today"
            isSameDay(logCal, yesterday) -> "Yesterday"
            else -> {
                val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
