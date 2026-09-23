package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.model.ContactItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ContactRepository(private val context: Context) {

    private val contactCache = ConcurrentHashMap<String, ContactInfo>()

    data class ContactInfo(
        val name: String?,
        val photoUri: String?
    )

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun resolveContact(phoneNumber: String): ContactInfo = withContext(Dispatchers.IO) {
        val trimmed = phoneNumber.trim()
        if (trimmed.isEmpty()) return@withContext ContactInfo(null, null)

        contactCache[trimmed]?.let { return@withContext it }

        if (!hasContactsPermission()) {
            return@withContext ContactInfo(null, null)
        }

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(trimmed)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)

                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val photo = if (photoIndex >= 0) cursor.getString(photoIndex) else null

                    val result = ContactInfo(name, photo)
                    contactCache[trimmed] = result
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            // Safety catch for Contacts permission or security limitations
        }

        val empty = ContactInfo(null, null)
        contactCache[trimmed] = empty
        empty
    }

    suspend fun getAllContacts(): List<ContactItem> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext emptyList()

        val list = mutableListOf<ContactItem>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val number = if (numberIdx >= 0) cursor.getString(numberIdx) ?: "" else ""
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx) else null
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null

                    val label = when (type) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel ?: "Custom"
                        else -> "Other"
                    }

                    if (number.isNotBlank()) {
                        list.add(ContactItem(id = id, name = name, number = number, typeLabel = label, photoUri = photo))
                    }
                }
            }
        } catch (e: Exception) {
            // Handle error safely
        }
        list
    }

    fun clearCache() {
        contactCache.clear()
    }
}
