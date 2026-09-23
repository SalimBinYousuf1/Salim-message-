package com.example.data.model

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED,
    NONE
}

data class MessageItem(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val status: MessageStatus = MessageStatus.NONE,
    val isRead: Boolean = true,
    val subId: Int = -1,
    val isMms: Boolean = false,
    val attachmentUri: String? = null,
    val attachmentMimeType: String? = null
)

data class ConversationItem(
    val threadId: Long,
    val address: String,
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val snippet: String = "",
    val timestamp: Long = 0L,
    val unreadCount: Int = 0,
    val isRead: Boolean = true,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val draftText: String? = null,
    val hasAttachment: Boolean = false
) {
    val displayTitle: String
        get() = when {
            !contactName.isNullOrBlank() -> contactName
            address.isNotBlank() -> address
            else -> "Unknown"
        }
}

data class ContactItem(
    val id: String,
    val name: String,
    val number: String,
    val typeLabel: String = "Mobile",
    val photoUri: String? = null
)

data class SimInfo(
    val subId: Int,
    val simSlotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val number: String? = null
)
