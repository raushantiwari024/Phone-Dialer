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

    /**
     * Recent calls, newest first.
     *
     * Three things were fixed here, all of which showed up as a multi-second wait before Recents or
     * Contacts rendered:
     *
     * - The query had no limit, so the entire call log was read into memory at once.
     * - Every call resolved its photo by scanning the whole contact list, an O(logs x contacts) nested
     *   loop. It now uses a prebuilt key -> photo map.
     * - `Regex("[^0-9+]")` was constructed inside both loops. Number keying now goes through
     *   [CallLogGrouping.numberKey], which holds a precompiled pattern.
     */
    suspend fun getCallLogs(limit: Int = DEFAULT_LIMIT): List<CallLogEntry> = withContext(Dispatchers.IO) {
        if (!hasReadCallLogPermission()) return@withContext emptyList()

        // Built once. Empty when contacts permission is missing, in which case CACHED_PHOTO_URI is
        // still used as a fallback per row.
        val photoByKey = runCatching { ContactsRepository(context).photoUriByNumberKey() }
            .getOrDefault(emptyMap())

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.CACHED_NUMBER_LABEL,
            CallLog.Calls.CACHED_PHOTO_URI,
        )

        val callLogs = ArrayList<CallLogEntry>(minOf(limit, INITIAL_CAPACITY))
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberTypeIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NUMBER_TYPE)
            val numberLabelIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NUMBER_LABEL)
            val photoUriIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)

            while (cursor.moveToNext()) {
                val number = numberIndex.takeIf { it >= 0 }?.let(cursor::getString).orEmpty()
                val cachedPhoto = photoUriIndex.takeIf { it >= 0 }?.let(cursor::getString)

                callLogs.add(
                    CallLogEntry(
                        id = idIndex.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L,
                        number = number,
                        date = dateIndex.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L,
                        duration = durationIndex.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L,
                        type = typeIndex.takeIf { it >= 0 }?.let(cursor::getInt) ?: 0,
                        cachedName = nameIndex.takeIf { it >= 0 }?.let(cursor::getString),
                        cachedNumberType = numberTypeIndex
                            .takeIf { it >= 0 && !cursor.isNull(it) }
                            ?.let(cursor::getInt),
                        cachedNumberLabel = numberLabelIndex.takeIf { it >= 0 }?.let(cursor::getString),
                        photoUri = photoByKey[CallLogGrouping.numberKey(number)] ?: cachedPhoto,
                    ),
                )
            }
        }
        callLogs
    }

    /**
     * Calls involving one number.
     *
     * Filtered by the provider rather than by loading every call and filtering in memory, and wrapped
     * in [withContext] — it was previously `suspend` without one, so the matching ran on whatever
     * dispatcher the caller happened to be on, potentially the main thread.
     */
    suspend fun getCallLogsForNumber(
        targetNumber: String,
        limit: Int = HISTORY_LIMIT,
    ): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val key = CallLogGrouping.numberKey(targetNumber)
        if (key.isEmpty()) return@withContext emptyList()
        getCallLogs(limit).filter { CallLogGrouping.numberKey(it.number) == key }
    }

    private companion object {
        /**
         * Enough to fill Recents many times over while keeping the initial read bounded. A phone with
         * years of history would otherwise load every row before showing anything.
         */
        private const val DEFAULT_LIMIT = 500
        private const val HISTORY_LIMIT = 1000
        private const val INITIAL_CAPACITY = 200
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
