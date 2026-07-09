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
            ContactsContract.CommonDataKinds.Phone.NUMBER
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
            
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val name = it.getString(nameIndex) ?: "Unknown"
                val number = it.getString(numberIndex) ?: ""
                contacts.add(Contact(id, name, number))
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
            ContactsContract.CommonDataKinds.Phone.NUMBER
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
                contact = Contact(id, name, number)
            }
        }
        contact
    }

    suspend fun getContactByNumber(number: String): Contact? = withContext(Dispatchers.IO) {
        if (!hasReadContactsPermission()) return@withContext null
        
        val cleanTarget = number.replace(Regex("[^0-9+]"), "")
        if (cleanTarget.isEmpty()) return@withContext null
        
        val allContacts = getContacts()
        allContacts.find { 
            val cleanContact = it.number.replace(Regex("[^0-9+]"), "")
            cleanContact.endsWith(cleanTarget) || cleanTarget.endsWith(cleanContact)
        }
    }
}
