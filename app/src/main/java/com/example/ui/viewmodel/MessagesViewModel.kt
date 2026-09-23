package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BlockedNumberEntity
import com.example.data.local.ConversationMetaEntity
import com.example.data.local.FavoriteMessageEntity
import com.example.data.local.ScheduledMessageEntity
import com.example.data.model.ContactItem
import com.example.data.model.ConversationItem
import com.example.data.model.MessageItem
import com.example.data.model.SimInfo
import com.example.telephony.ContactRepository
import com.example.telephony.ScheduledMessageScheduler
import com.example.telephony.SmsRepository
import com.example.telephony.SmsSender
import com.example.telephony.SubscriptionHelper
import com.example.telephony.TelephonyRoleHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MessagesUiState(
    val conversations: List<ConversationItem> = emptyList(),
    val isLoadingConversations: Boolean = false,
    val isDefaultSms: Boolean = false,
    val selectedConversation: ConversationItem? = null,
    val currentMessages: List<MessageItem> = emptyList(),
    val isLoadingMessages: Boolean = false,
    val sims: List<SimInfo> = emptyList(),
    val selectedSubId: Int = -1,
    val searchQuery: String = "",
    val searchResults: List<MessageItem> = emptyList(),
    val isSearching: Boolean = false,
    val selectedThreadIds: Set<Long> = emptySet(),
    val selectedMessageIds: Set<Long> = emptySet(),
    val contacts: List<ContactItem> = emptyList(),
    val scheduledMessages: List<ScheduledMessageEntity> = emptyList(),
    val favoriteMessages: List<FavoriteMessageEntity> = emptyList(),
    val favoriteMessageIds: Set<Long> = emptySet(),
    val blockedNumbers: List<BlockedNumberEntity> = emptyList(),
    val infoMessage: String? = null
)

class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val contactRepo = ContactRepository(application)
    private val smsRepo = SmsRepository(application, db.conversationMetaDao(), contactRepo)
    private val smsSender = SmsSender(application)

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        checkDefaultSmsRole()
        loadSims()
        observeDataSources()
        refreshConversations()
        loadContacts()
    }

    fun checkDefaultSmsRole() {
        val isDefault = TelephonyRoleHelper.isDefaultSmsApp(getApplication())
        _uiState.update { it.copy(isDefaultSms = isDefault) }
    }

    fun loadSims() {
        val detectedSims = SubscriptionHelper.getActiveSubscriptions(getApplication())
        val defaultSubId = SubscriptionHelper.getDefaultSubscriptionId()
        _uiState.update {
            it.copy(
                sims = detectedSims,
                selectedSubId = if (detectedSims.any { s -> s.subId == defaultSubId }) defaultSubId else (detectedSims.firstOrNull()?.subId ?: -1)
            )
        }
    }

    fun selectSim(subId: Int) {
        _uiState.update { it.copy(selectedSubId = subId) }
    }

    private fun observeDataSources() {
        // Observe Android Telephony provider changes
        viewModelScope.launch {
            smsRepo.observeSmsChanges().collect {
                refreshConversations()
                val current = _uiState.value.selectedConversation
                if (current != null) {
                    loadMessagesForConversation(current.threadId, current.address)
                }
            }
        }

        // Observe Scheduled messages
        viewModelScope.launch {
            db.scheduledMessageDao().getAllScheduledMessages().collect { list ->
                _uiState.update { it.copy(scheduledMessages = list) }
            }
        }

        // Observe Favorites
        viewModelScope.launch {
            db.favoriteMessageDao().getAllFavorites().collect { list ->
                _uiState.update { it.copy(favoriteMessages = list) }
            }
        }

        // Observe Favorite IDs
        viewModelScope.launch {
            db.favoriteMessageDao().getAllFavoriteIds().collect { ids ->
                _uiState.update { it.copy(favoriteMessageIds = ids.toSet()) }
            }
        }

        // Observe Blocked numbers
        viewModelScope.launch {
            db.blockedNumberDao().getAllBlocked().collect { list ->
                _uiState.update { it.copy(blockedNumbers = list) }
            }
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingConversations = true) }
            val list = smsRepo.getConversations()
            _uiState.update { it.copy(conversations = list, isLoadingConversations = false) }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            val contacts = contactRepo.getAllContacts()
            _uiState.update { it.copy(contacts = contacts) }
        }
    }

    fun openConversation(threadId: Long, address: String) {
        viewModelScope.launch {
            val meta = db.conversationMetaDao().getMetaForThreadSync(threadId)
            val contactInfo = contactRepo.resolveContact(address)
            val conv = ConversationItem(
                threadId = threadId,
                address = address,
                contactName = contactInfo.name,
                contactPhotoUri = contactInfo.photoUri,
                isPinned = meta?.isPinned == true,
                isArchived = meta?.isArchived == true,
                isMuted = meta?.isMuted == true,
                draftText = meta?.draftText
            )

            _uiState.update {
                it.copy(
                    selectedConversation = conv,
                    isLoadingMessages = true,
                    selectedMessageIds = emptySet()
                )
            }

            loadMessagesForConversation(threadId, address)
            smsRepo.markConversationAsRead(threadId, address)
        }
    }

    fun closeConversation() {
        _uiState.update {
            it.copy(
                selectedConversation = null,
                currentMessages = emptyList(),
                selectedMessageIds = emptySet()
            )
        }
        refreshConversations()
    }

    private suspend fun loadMessagesForConversation(threadId: Long, address: String) {
        val messages = smsRepo.getMessagesForConversation(threadId, address)
        _uiState.update {
            it.copy(
                currentMessages = messages,
                isLoadingMessages = false
            )
        }
    }

    fun sendSms(text: String, attachmentUri: Uri? = null) {
        val conv = _uiState.value.selectedConversation ?: return
        val subId = _uiState.value.selectedSubId

        viewModelScope.launch {
            val result = smsSender.sendSms(
                destinationAddress = conv.address,
                messageText = text,
                subId = subId
            )

            if (result.isSuccess) {
                // Clear draft if any
                db.conversationMetaDao().setDraft(conv.threadId, null, null)
                loadMessagesForConversation(conv.threadId, conv.address)
                refreshConversations()
            } else {
                _uiState.update {
                    it.copy(infoMessage = "Failed to send: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
                }
            }
        }
    }

    fun sendDirectSms(recipientNumber: String, text: String, onSent: (Long) -> Unit) {
        val subId = _uiState.value.selectedSubId
        viewModelScope.launch {
            val result = smsSender.sendSms(
                destinationAddress = recipientNumber,
                messageText = text,
                subId = subId
            )
            if (result.isSuccess) {
                refreshConversations()
                onSent(result.getOrDefault(0L))
            } else {
                _uiState.update {
                    it.copy(infoMessage = "Failed to send: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun retrySms(message: MessageItem) {
        viewModelScope.launch {
            val result = smsSender.sendSms(
                destinationAddress = message.address,
                messageText = message.body,
                subId = message.subId
            )
            if (result.isSuccess) {
                val conv = _uiState.value.selectedConversation
                if (conv != null) {
                    loadMessagesForConversation(conv.threadId, conv.address)
                }
            }
        }
    }

    fun saveDraft(threadId: Long, draftText: String?) {
        viewModelScope.launch {
            val current = db.conversationMetaDao().getMetaForThreadSync(threadId)
            if (current != null) {
                db.conversationMetaDao().setDraft(threadId, draftText, null)
            } else {
                db.conversationMetaDao().insertOrUpdate(
                    ConversationMetaEntity(
                        threadId = threadId,
                        draftText = draftText
                    )
                )
            }
        }
    }

    fun togglePin(threadId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            val current = db.conversationMetaDao().getMetaForThreadSync(threadId)
            if (current != null) {
                db.conversationMetaDao().setPinned(threadId, isPinned)
            } else {
                db.conversationMetaDao().insertOrUpdate(
                    ConversationMetaEntity(threadId = threadId, isPinned = isPinned)
                )
            }
            refreshConversations()
        }
    }

    fun toggleArchive(threadId: Long, isArchived: Boolean) {
        viewModelScope.launch {
            val current = db.conversationMetaDao().getMetaForThreadSync(threadId)
            if (current != null) {
                db.conversationMetaDao().setArchived(threadId, isArchived)
            } else {
                db.conversationMetaDao().insertOrUpdate(
                    ConversationMetaEntity(threadId = threadId, isArchived = isArchived)
                )
            }
            refreshConversations()
        }
    }

    fun toggleMute(threadId: Long, isMuted: Boolean) {
        viewModelScope.launch {
            val current = db.conversationMetaDao().getMetaForThreadSync(threadId)
            if (current != null) {
                db.conversationMetaDao().setMuted(threadId, isMuted)
            } else {
                db.conversationMetaDao().insertOrUpdate(
                    ConversationMetaEntity(threadId = threadId, isMuted = isMuted)
                )
            }
            refreshConversations()
        }
    }

    fun toggleFavorite(message: MessageItem) {
        viewModelScope.launch {
            val isFav = _uiState.value.favoriteMessageIds.contains(message.id)
            if (isFav) {
                db.favoriteMessageDao().delete(message.id)
            } else {
                db.favoriteMessageDao().insert(
                    FavoriteMessageEntity(
                        messageId = message.id,
                        threadId = message.threadId,
                        address = message.address,
                        body = message.body,
                        timestamp = message.timestamp,
                        isIncoming = message.isIncoming
                    )
                )
            }
        }
    }

    fun blockNumber(number: String, name: String?) {
        viewModelScope.launch {
            db.blockedNumberDao().blockNumber(
                BlockedNumberEntity(
                    normalizedNumber = number,
                    displayName = name
                )
            )
            _uiState.update { it.copy(infoMessage = "$number blocked") }
        }
    }

    fun unblockNumber(number: String) {
        viewModelScope.launch {
            db.blockedNumberDao().unblockNumber(number)
            _uiState.update { it.copy(infoMessage = "$number unblocked") }
        }
    }

    fun deleteConversation(threadId: Long, address: String) {
        viewModelScope.launch {
            val success = smsRepo.deleteConversation(threadId, address)
            if (success) {
                if (_uiState.value.selectedConversation?.threadId == threadId) {
                    closeConversation()
                } else {
                    refreshConversations()
                }
            } else {
                _uiState.update { it.copy(infoMessage = "Requires Default SMS app role to delete") }
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            val success = smsRepo.deleteMessage(messageId)
            if (success) {
                val conv = _uiState.value.selectedConversation
                if (conv != null) {
                    loadMessagesForConversation(conv.threadId, conv.address)
                }
                refreshConversations()
            } else {
                _uiState.update { it.copy(infoMessage = "Requires Default SMS app role to delete") }
            }
        }
    }

    fun scheduleMessage(
        recipient: String,
        recipientName: String?,
        message: String,
        triggerAtMillis: Long,
        subId: Int
    ) {
        viewModelScope.launch {
            val id = db.scheduledMessageDao().insert(
                ScheduledMessageEntity(
                    recipient = recipient,
                    recipientName = recipientName,
                    message = message,
                    scheduledTimestamp = triggerAtMillis,
                    subId = subId
                )
            )
            ScheduledMessageScheduler.schedule(getApplication(), id, triggerAtMillis)
            _uiState.update { it.copy(infoMessage = "Message scheduled") }
        }
    }

    fun cancelScheduledMessage(id: Long) {
        viewModelScope.launch {
            ScheduledMessageScheduler.cancel(getApplication(), id)
            db.scheduledMessageDao().delete(id)
            _uiState.update { it.copy(infoMessage = "Scheduled message cancelled") }
        }
    }

    // Selection mode for conversations
    fun toggleConversationSelection(threadId: Long) {
        _uiState.update { state ->
            val set = state.selectedThreadIds.toMutableSet()
            if (set.contains(threadId)) set.remove(threadId) else set.add(threadId)
            state.copy(selectedThreadIds = set)
        }
    }

    fun clearConversationSelection() {
        _uiState.update { it.copy(selectedThreadIds = emptySet()) }
    }

    fun selectAllConversations() {
        _uiState.update { state ->
            state.copy(selectedThreadIds = state.conversations.map { it.threadId }.toSet())
        }
    }

    fun deleteSelectedConversations() {
        val selected = _uiState.value.selectedThreadIds.toList()
        viewModelScope.launch {
            selected.forEach { threadId ->
                val conv = _uiState.value.conversations.firstOrNull { it.threadId == threadId }
                if (conv != null) {
                    smsRepo.deleteConversation(threadId, conv.address)
                }
            }
            clearConversationSelection()
            refreshConversations()
        }
    }

    // Selection mode for messages inside conversation
    fun toggleMessageSelection(messageId: Long) {
        _uiState.update { state ->
            val set = state.selectedMessageIds.toMutableSet()
            if (set.contains(messageId)) set.remove(messageId) else set.add(messageId)
            state.copy(selectedMessageIds = set)
        }
    }

    fun clearMessageSelection() {
        _uiState.update { it.copy(selectedMessageIds = emptySet()) }
    }

    fun deleteSelectedMessages() {
        val selected = _uiState.value.selectedMessageIds.toList()
        viewModelScope.launch {
            selected.forEach { id ->
                smsRepo.deleteMessage(id)
            }
            clearMessageSelection()
            val conv = _uiState.value.selectedConversation
            if (conv != null) {
                loadMessagesForConversation(conv.threadId, conv.address)
            }
            refreshConversations()
        }
    }

    // Live search with debounce
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.trim().isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(200) // 200ms debounce
            _uiState.update { it.copy(isSearching = true) }
            val results = smsRepo.searchMessages(query)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }
}
