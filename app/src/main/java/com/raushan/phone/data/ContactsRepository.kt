package com.raushan.phone.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.raushan.phone.data.models.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {
    
    private fun hasReadContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getContacts(): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasReadContactsPermission()) return@withContext emptyList()

        val contacts = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )
        
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            val photoUriIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val name = it.getString(nameIndex) ?: "Unknown"
                val number = it.getString(numberIndex) ?: ""
                val photoUri = if (photoUriIndex >= 0) it.getString(photoUriIndex) else null
                contacts.add(Contact(id, name, number, photoUri))
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
    }
}
