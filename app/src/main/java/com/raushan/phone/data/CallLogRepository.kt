package com.raushan.phone.data

import android.content.Context
import android.provider.CallLog
import com.raushan.phone.data.models.CallLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {
    
    suspend fun getCallLogs(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val callLogs = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )
        
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val number = it.getString(numberIndex) ?: ""
                val date = it.getLong(dateIndex)
                val duration = it.getLong(durationIndex)
                val type = it.getInt(typeIndex)
                callLogs.add(CallLogEntry(id, number, date, duration, type))
            }
        }
        callLogs
    }
}
