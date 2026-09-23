package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_meta")
data class ConversationMetaEntity(
    @PrimaryKey
    val threadId: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val draftText: String? = null,
    val draftAttachmentUri: String? = null,
    val customName: String? = null
)

@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipient: String,
    val recipientName: String? = null,
    val message: String,
    val attachmentUri: String? = null,
    val scheduledTimestamp: Long,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // PENDING, SENT, FAILED, CANCELLED
    val failureReason: String? = null,
    val subId: Int = -1
)

@Entity(tableName = "favorite_messages")
data class FavoriteMessageEntity(
    @PrimaryKey
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean
)

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey
    val normalizedNumber: String,
    val displayName: String? = null,
    val blockedAt: Long = System.currentTimeMillis()
)
