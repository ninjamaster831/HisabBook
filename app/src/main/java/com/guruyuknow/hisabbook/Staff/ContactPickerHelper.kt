package com.guruyuknow.hisabbook.Staff

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String? = null
)

class ContactPickerHelper {

    suspend fun getAllContacts(context: Context): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
        val seenPhones = mutableSetOf<String>()

        try {
            val contentResolver = context.contentResolver

            val cursor: Cursor? = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                if (it.count == 0) {
                    Log.d("ContactPickerHelper", "No contacts found")
                    return@withContext emptyList()
                }

                val idColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                if (idColumn < 0 || nameColumn < 0 || phoneColumn < 0) {
                    Log.e("ContactPickerHelper", "Invalid column indices")
                    return@withContext emptyList()
                }

                while (it.moveToNext()) {
                    try {
                        val id = it.getString(idColumn) ?: continue
                        val name = it.getString(nameColumn) ?: continue
                        val phone = it.getString(phoneColumn) ?: continue

                        // Clean phone number
                        val cleanedPhone = phone.replace(Regex("[^\\d+]"), "")

                        // Skip if we've already seen this phone number
                        if (cleanedPhone.isEmpty() || seenPhones.contains(cleanedPhone)) {
                            continue
                        }

                        seenPhones.add(cleanedPhone)

                        contacts.add(
                            Contact(
                                id = id,
                                name = name.trim(),
                                phoneNumber = cleanedPhone,
                                email = getEmailForContact(context, id)
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("ContactPickerHelper", "Error reading contact row", e)
                        continue
                    }
                }

                Log.d("ContactPickerHelper", "Loaded ${contacts.size} contacts")
            }
        } catch (e: SecurityException) {
            Log.e("ContactPickerHelper", "Permission denied to read contacts", e)
            throw e
        } catch (e: Exception) {
            Log.e("ContactPickerHelper", "Error loading contacts", e)
        }

        return@withContext contacts
    }

    private fun getEmailForContact(context: Context, contactId: String): String? {
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val emailColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                    if (emailColumn >= 0) {
                        return it.getString(emailColumn)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactPickerHelper", "Error reading email for contact $contactId", e)
        }

        return null
    }

    fun searchContacts(contacts: List<Contact>, query: String): List<Contact> {
        if (query.isEmpty()) {
            return contacts
        }

        val lowerQuery = query.lowercase()

        return contacts.filter { contact ->
            // Search in name
            contact.name.lowercase().contains(lowerQuery) ||
                    // Search in phone (allows searching by digits)
                    contact.phoneNumber.contains(query) ||
                    // Search in email if available
                    (contact.email?.lowercase()?.contains(lowerQuery) == true)
        }
    }
}