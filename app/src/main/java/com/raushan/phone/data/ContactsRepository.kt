package com.raushan.phone.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.raushan.phone.data.models.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {
    
    private fun hasReadContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Every phone number in the address book, one [Contact] per number.
     *
     * Cached process-wide. Three separate callers used to invoke this on app start — the contacts list,
     * the dialpad's T9 search, and the call log's photo resolution — each performing a full scan of the
     * Phone table. On a large address book that was the bulk of the several seconds before the Contacts
     * screen showed anything.
     *
     * A contact with three numbers appears three times here, which is what number matching needs. Use
     * [getDistinctContacts] for anything that displays a list of people.
     */
    suspend fun getContacts(): List<Contact> {
        cache?.let { return it }
        return cacheMutex.withLock {
            // Re-check inside the lock: several callers racing on startup should produce one query,
            // not one each.
            cache ?: queryContacts().also { cache = it }
        }
    }

    /**
     * One entry per person, for display.
     *
     * The provider returns a row per phone number, so a contact with mobile, home and work numbers was
     * previously listed three times.
     */
    suspend fun getDistinctContacts(): List<Contact> =
        getContacts().distinctBy { it.id }

    /**
     * Numbers keyed for matching, built once.
     *
     * Lets the call log resolve a photo per row with a map lookup instead of scanning the whole contact
     * list for every entry, which was an O(logs x contacts) nested loop.
     */
    suspend fun photoUriByNumberKey(): Map<String, String> {
        val result = HashMap<String, String>()
        for (contact in getContacts()) {
            val photo = contact.photoUri ?: continue
            result.putIfAbsent(CallLogGrouping.numberKey(contact.number), photo)
        }
        return result
    }

    /** Drops the cache so the next read re-queries. Call after the address book may have changed. */
    fun invalidate() {
        cache = null
    }

    private suspend fun queryContacts(): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasReadContactsPermission()) return@withContext emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
        )

        val contacts = mutableListOf<Contact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex =
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

            while (cursor.moveToNext()) {
                contacts.add(
                    Contact(
                        id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                        name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString) ?: UNKNOWN_NAME,
                        number = numberIndex.takeIf { it >= 0 }?.let(cursor::getString).orEmpty(),
                        photoUri = photoIndex.takeIf { it >= 0 }?.let(cursor::getString),
                    ),
                )
            }
        }
        contacts
    }

    suspend fun getContactById(contactId: Long): Contact? = withContext(Dispatchers.IO) {
        if (!hasReadContactsPermission()) return@withContext null

        var contact: Contact? = null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )
        
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "Unknown"
                val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                val photoUri = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI))
                contact = Contact(id, name, number, photoUri)
            }
        }
        contact
    }

    /**
     * Looks up a contact by phone number.
     *
     * Uses [ContactsContract.PhoneLookup], an indexed and country-aware provider query. The previous
     * implementation loaded the entire phone table via [getContacts] and linear-scanned it with a
     * `Regex` recompiled on every element — and it ran two or three times per incoming call, at the
     * most latency-sensitive moment in the app.
     *
     * The raw number is passed straight through: `PhoneLookup` applies its own E.164 and min-match
     * normalisation, which is more correct than bidirectional `endsWith` on a digit-stripped string.
     * That old comparison had no minimum-length guard, so a short number like `911` matched any
     * contact ending in those digits.
     */
    suspend fun getContactByNumber(number: String): Contact? = withContext(Dispatchers.IO) {
        if (!hasReadContactsPermission()) return@withContext null
        if (number.replace(PHONE_CLEAN_REGEX, "").isEmpty()) return@withContext null

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(number)
            .apply {
                if (number.contains(SIP_MARKER)) {
                    appendQueryParameter(
                        ContactsContract.PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS,
                        true.toString(),
                    )
                }
            }
            .build()

        val projection = arrayOf(
            ContactsContract.PhoneLookup.CONTACT_ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.NUMBER,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
        )

        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)
                val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                val thumbnailIndex =
                    cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)

                val name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)
                    ?: return@use null

                Contact(
                    id = idIndex.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L,
                    name = name,
                    number = numberIndex.takeIf { it >= 0 }?.let(cursor::getString) ?: number,
                    photoUri = photoIndex.takeIf { it >= 0 }?.let(cursor::getString)
                        ?: thumbnailIndex.takeIf { it >= 0 }?.let(cursor::getString),
                )
            }
        }.getOrNull()
    }

    private companion object {
        /** Precompiled — the old code rebuilt this on every comparison. */
        private val PHONE_CLEAN_REGEX = Regex("[^0-9+]")
        private const val SIP_MARKER = "@"
        private const val UNKNOWN_NAME = "Unknown"

        /**
         * Shared across every [ContactsRepository] instance.
         *
         * There is no DI in this project, so each ViewModel constructs its own repository. Holding the
         * cache on the companion is what makes those instances share one query instead of one each.
         */
        @Volatile
        private var cache: List<Contact>? = null
        private val cacheMutex = Mutex()
    }
}
