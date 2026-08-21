package com.raushan.phone.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.raushan.phone.data.models.CallLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {
    
    private fun hasReadCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasWriteCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCallLogs(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        if (!hasReadCallLogPermission()) return@withContext emptyList()

        val callLogs = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.CACHED_NUMBER_LABEL,
            CallLog.Calls.CACHED_PHOTO_URI
        )

        val contactsRepository = ContactsRepository(context)
        val contactsList = try {
            contactsRepository.getContacts()
        } catch (e: Exception) {
            emptyList()
        }
        
        val contactsMap = contactsList.filter { it.photoUri != null }.associate { contact ->
            contact.number.replace(Regex("[^0-9+]"), "") to contact.photoUri
        }
        
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
            val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberTypeIndex = it.getColumnIndex(CallLog.Calls.CACHED_NUMBER_TYPE)
            val numberLabelIndex = it.getColumnIndex(CallLog.Calls.CACHED_NUMBER_LABEL)
            val photoUriIndex = it.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
            
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val number = it.getString(numberIndex) ?: ""
                val date = it.getLong(dateIndex)
                val duration = it.getLong(durationIndex)
                val type = it.getInt(typeIndex)
                
                val name = if (nameIndex >= 0) it.getString(nameIndex) else null
                val numberType = if (numberTypeIndex >= 0 && !it.isNull(numberTypeIndex)) it.getInt(numberTypeIndex) else null
                val numberLabel = if (numberLabelIndex >= 0) it.getString(numberLabelIndex) else null
                
                val cleanLogNum = number.replace(Regex("[^0-9+]"), "")
                var resolvedPhoto = contactsMap[cleanLogNum]
                if (resolvedPhoto == null && cleanLogNum.isNotEmpty()) {
                    resolvedPhoto = contactsList.find { contact ->
                        val cleanContact = contact.number.replace(Regex("[^0-9+]"), "")
                        if (cleanContact.length >= 7 && cleanLogNum.length >= 7) {
                            cleanContact.endsWith(cleanLogNum.takeLast(7)) || cleanLogNum.endsWith(cleanContact.takeLast(7))
                        } else {
                            cleanContact == cleanLogNum
                        }
                    }?.photoUri
                }
                val photoUri = resolvedPhoto ?: (if (photoUriIndex >= 0) it.getString(photoUriIndex) else null)
                
                callLogs.add(
                    CallLogEntry(
                        id = id,
                        number = number,
                        date = date,
                        duration = duration,
                        type = type,
                        cachedName = name,
                        cachedNumberType = numberType,
                        cachedNumberLabel = numberLabel,
                        photoUri = photoUri
                    )
                )
            }
        }
        callLogs
    }

    suspend fun getCallLogsForNumber(targetNumber: String): List<CallLogEntry> {
        val allLogs = getCallLogs()
        val cleanTarget = targetNumber.replace(Regex("[^0-9+]"), "")
        if (cleanTarget.isEmpty()) return emptyList()
        return allLogs.filter { log ->
            val cleanLog = log.number.replace(Regex("[^0-9+]"), "")
            cleanLog.endsWith(cleanTarget) || cleanTarget.endsWith(cleanLog)
        }
    }

    suspend fun deleteCallLog(id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteCallLogPermission()) return@withContext false
        try {
            val deletedCount = context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(id.toString())
            )
            deletedCount > 0
        } catch (e: Exception) {
            false
        }
    }
}
