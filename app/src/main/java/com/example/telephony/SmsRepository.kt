package com.example.telephony

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.data.local.ConversationMetaDao
import com.example.data.model.ConversationItem
import com.example.data.model.MessageItem
import com.example.data.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsRepository(
    private val context: Context,
    private val metaDao: ConversationMetaDao,
    private val contactRepository: ContactRepository
) {

    private fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Emits a tick whenever the Android Telephony SMS content provider changes.
     */
    fun observeSmsChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            // Ignore if security blocks registration
        }

        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Loads all distinct conversations from Android SMS provider, joined with app metadata.
     */
    suspend fun getConversations(): List<ConversationItem> = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) return@withContext emptyList()

        val conversationsMap = mutableMapOf<Long, ConversationItem>()
        val addressToThreadMap = mutableMapOf<String, Long>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)

                while (cursor.moveToNext()) {
                    var threadId = if (threadIdIdx >= 0) cursor.getLong(threadIdIdx) else 0L
                    val address = if (addressIdx >= 0) cursor.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val read = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true

                    if (threadId <= 0L && address.isNotBlank()) {
                        threadId = addressToThreadMap.getOrPut(address) {
                            address.hashCode().toLong()
                        }
                    } else if (address.isNotBlank()) {
                        addressToThreadMap[address] = threadId
                    }

                    if (threadId > 0L) {
                        val existing = conversationsMap[threadId]
                        if (existing == null) {
                            conversationsMap[threadId] = ConversationItem(
                                threadId = threadId,
                                address = address,
                                snippet = body,
                                timestamp = date,
                                unreadCount = if (!read) 1 else 0,
                                isRead = read
                            )
                        } else {
                            if (!read) {
                                conversationsMap[threadId] = existing.copy(
                                    unreadCount = existing.unreadCount + 1,
                                    isRead = false
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Return what we have or empty list
        }

        // Now enrich with contact info and Room metadata
        val result = mutableListOf<ConversationItem>()
        for ((threadId, item) in conversationsMap) {
            val contactInfo = contactRepository.resolveContact(item.address)
            val meta = metaDao.getMetaForThreadSync(threadId)

            result.add(
                item.copy(
                    contactName = contactInfo.name,
                    contactPhotoUri = contactInfo.photoUri,
                    isPinned = meta?.isPinned == true,
                    isArchived = meta?.isArchived == true,
                    isMuted = meta?.isMuted == true,
                    draftText = meta?.draftText
                )
            )
        }

        // Sort: Pinned first, then newest timestamp
        result.sortWith(
            compareByDescending<ConversationItem> { it.isPinned }
                .thenByDescending { it.timestamp }
        )

        result
    }

    /**
     * Loads messages for a specific conversation.
     */
    suspend fun getMessagesForConversation(threadId: Long, address: String): List<MessageItem> =
        withContext(Dispatchers.IO) {
            if (!hasReadSmsPermission()) return@withContext emptyList()

            val messages = mutableListOf<MessageItem>()
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
                Telephony.Sms.TYPE,
                Telephony.Sms.STATUS,
                Telephony.Sms.SUBSCRIPTION_ID
            )

            val selection: String
            val selectionArgs: Array<String>

            if (threadId > 0L) {
                selection = "${Telephony.Sms.THREAD_ID} = ? OR ${Telephony.Sms.ADDRESS} = ?"
                selectionArgs = arrayOf(threadId.toString(), address)
            } else {
                selection = "${Telephony.Sms.ADDRESS} = ?"
                selectionArgs = arrayOf(address)
            }

            try {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${Telephony.Sms.DATE} ASC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                    val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                    val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
                    val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                    val statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS)
                    val subIdIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                    while (cursor.moveToNext()) {
                        val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                        val mThreadId = if (threadIdIdx >= 0) cursor.getLong(threadIdIdx) else threadId
                        val mAddress = if (addressIdx >= 0) cursor.getString(addressIdx) ?: address else address
                        val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                        val read = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true
                        val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        val subId = if (subIdIdx >= 0) cursor.getInt(subIdIdx) else -1

                        val isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                        val messageStatus = when {
                            isIncoming -> MessageStatus.NONE
                            type == Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
                            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.SENDING
                            status == Telephony.Sms.STATUS_COMPLETE -> MessageStatus.DELIVERED
                            status == Telephony.Sms.STATUS_FAILED -> MessageStatus.FAILED
                            type == Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
                            else -> MessageStatus.SENT
                        }

                        messages.add(
                            MessageItem(
                                id = id,
                                threadId = mThreadId,
                                address = mAddress,
                                body = body,
                                timestamp = date,
                                isIncoming = isIncoming,
                                status = messageStatus,
                                isRead = read,
                                subId = subId,
                                isMms = false
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Safety catch
            }

            messages
        }

    /**
     * Mark a conversation as read in the Android provider.
     */
    suspend fun markConversationAsRead(threadId: Long, address: String) = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            if (threadId > 0L) {
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(threadId.toString())
                )
            } else if (address.isNotBlank()) {
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(address)
                )
            }
        } catch (e: Exception) {
            // Android may throw SecurityException if not default SMS app
        }
    }

    /**
     * Delete a single message from the Android SMS provider.
     */
    suspend fun deleteMessage(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete an entire conversation thread from the Android SMS provider.
     */
    suspend fun deleteConversation(threadId: Long, address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            var rows = 0
            if (threadId > 0L) {
                rows = context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString())
                )
            }
            if (rows == 0 && address.isNotBlank()) {
                rows = context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.ADDRESS} = ?",
                    arrayOf(address)
                )
            }
            metaDao.deleteMeta(threadId)
            rows > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Searches messages for matching query across body, address, or contact names.
     */
    suspend fun searchMessages(query: String): List<MessageItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !hasReadSmsPermission()) return@withContext emptyList()

        val results = mutableListOf<MessageItem>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE
        )

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?",
                arrayOf("%$trimmed%", "%$trimmed%"),
                "${Telephony.Sms.DATE} DESC LIMIT 100"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val threadId = if (threadIdIdx >= 0) cursor.getLong(threadIdIdx) else 0L
                    val address = if (addressIdx >= 0) cursor.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val read = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX

                    results.add(
                        MessageItem(
                            id = id,
                            threadId = threadId,
                            address = address,
                            body = body,
                            timestamp = date,
                            isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                            status = MessageStatus.SENT,
                            isRead = read
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Safety catch
        }

        results
    }
}
