package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConversationItem
import com.example.data.model.MessageItem
import com.example.ui.components.AvatarView
import com.example.ui.components.ComposerBar
import com.example.ui.components.DateTimeUtils
import com.example.ui.components.MessageBubble
import com.example.ui.viewmodel.MessagesUiState
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversation: ConversationItem,
    state: MessagesUiState,
    onBackClick: () -> Unit,
    onSendMessage: (String, Uri?) -> Unit,
    onRetryMessage: (MessageItem) -> Unit,
    onSaveDraft: (String?) -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFavorite: (MessageItem) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onDeleteConversation: () -> Unit,
    onBlockNumber: (String) -> Unit,
    onScheduleMessage: (Long, String) -> Unit,
    onToggleSelectMessage: (Long) -> Unit,
    onClearMessageSelection: () -> Unit,
    onDeleteSelectedMessages: () -> Unit,
    onSelectSim: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var composerText by remember { mutableStateOf(conversation.draftText ?: "") }
    var selectedAttachmentUri by remember { mutableStateOf<Uri?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSingleMsgDeleteConfirm by remember { mutableStateOf<Long?>(null) }
    var showMessageDetails by remember { mutableStateOf<MessageItem?>(null) }

    // Save draft when screen leaves or on text change
    DisposableEffect(composerText) {
        onDispose {
            onSaveDraft(composerText.ifBlank { null })
        }
    }

    // Scroll to bottom on new message
    LaunchedEffect(state.currentMessages.size) {
        if (state.currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.currentMessages.size - 1)
        }
    }

    val isSelectionMode = state.selectedMessageIds.isNotEmpty()

    // Confirmation dialog: Delete entire conversation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this conversation?") },
            text = { Text("All SMS/MMS messages in this conversation will be permanently removed from your device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteConversation()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog: Delete single message
    showSingleMsgDeleteConfirm?.let { msgId ->
        AlertDialog(
            onDismissRequest = { showSingleMsgDeleteConfirm = null },
            title = { Text("Delete message?") },
            text = { Text("This message will be removed from your device's SMS database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMessage(msgId)
                        showSingleMsgDeleteConfirm = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSingleMsgDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Message details dialog
    showMessageDetails?.let { msg ->
        AlertDialog(
            onDismissRequest = { showMessageDetails = null },
            title = { Text("Message Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Type: ${if (msg.isMms) "MMS" else "SMS"}", fontWeight = FontWeight.SemiBold)
                    Text("Address: ${msg.address}")
                    Text("Direction: ${if (msg.isIncoming) "Incoming" else "Outgoing"}")
                    Text("Status: ${msg.status.name}")
                    Text("Timestamp: ${DateTimeUtils.formatDetailedTimestamp(context, msg.timestamp)}")
                    if (msg.subId > 0) {
                        Text("SIM: Slot ${msg.subId}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMessageDetails = null }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedMessageIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onClearMessageSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        // Copy action
                        IconButton(
                            onClick = {
                                val selectedMsgs = state.currentMessages.filter { state.selectedMessageIds.contains(it.id) }
                                val textToCopy = selectedMsgs.joinToString("\n") { it.body }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("SMS message", textToCopy))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                onClearMessageSelection()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                        }

                        // Share action
                        IconButton(
                            onClick = {
                                val selectedMsgs = state.currentMessages.filter { state.selectedMessageIds.contains(it.id) }
                                val textToShare = selectedMsgs.joinToString("\n") { it.body }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, textToShare)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share message via"))
                                onClearMessageSelection()
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share message")
                        }

                        // Star/favorite action if single message selected
                        if (state.selectedMessageIds.size == 1) {
                            val msg = state.currentMessages.firstOrNull { it.id == state.selectedMessageIds.first() }
                            if (msg != null) {
                                val isStarred = state.favoriteMessageIds.contains(msg.id)
                                IconButton(onClick = { onToggleFavorite(msg) }) {
                                    Icon(
                                        imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Star message",
                                        tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Delete action
                        IconButton(
                            onClick = onDeleteSelectedMessages
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarView(
                                photoUri = conversation.contactPhotoUri,
                                nameOrAddress = conversation.displayTitle,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = conversation.displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (conversation.contactName != null && conversation.address.isNotBlank()) {
                                    Text(
                                        text = conversation.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("conversation_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Call action
                        if (conversation.address.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${conversation.address}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot initiate call", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call")
                            }
                        }

                        // Overflow menu
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (conversation.isPinned) "Unpin" else "Pin") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onTogglePin()
                                    },
                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                                )

                                DropdownMenuItem(
                                    text = { Text(if (conversation.isMuted) "Unmute notifications" else "Mute notifications") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onToggleMute()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (conversation.isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                            contentDescription = null
                                        )
                                    }
                                )

                                if (conversation.contactName == null && conversation.address.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Add to Contacts") },
                                        onClick = {
                                            showOverflowMenu = false
                                            try {
                                                val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                                    putExtra(ContactsContract.Intents.Insert.PHONE, conversation.address)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Handle error
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Block Number") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onBlockNumber(conversation.address)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) }
                                )

                                DropdownMenuItem(
                                    text = { Text("Delete Conversation") },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteConfirm = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            ComposerBar(
                text = composerText,
                onTextChange = { composerText = it },
                onSendClick = {
                    val toSend = composerText
                    val attachment = selectedAttachmentUri
                    composerText = ""
                    selectedAttachmentUri = null
                    onSendMessage(toSend, attachment)
                },
                attachmentUri = selectedAttachmentUri,
                onAttachmentSelected = { selectedAttachmentUri = it },
                sims = state.sims,
                selectedSubId = state.selectedSubId,
                onSimSelected = onSelectSim,
                onScheduleClick = {
                    // Open date and time picker
                    val cal = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    val targetCal = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth, hourOfDay, minute, 0)
                                    }
                                    if (targetCal.timeInMillis > System.currentTimeMillis() && composerText.isNotBlank()) {
                                        onScheduleMessage(targetCal.timeInMillis, composerText)
                                        composerText = ""
                                    } else {
                                        Toast.makeText(context, "Please enter message text and choose a future time", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoadingMessages && state.currentMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.currentMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "No messages yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Send a text below to start the conversation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    itemsIndexed(
                        items = state.currentMessages,
                        key = { _, msg -> msg.id }
                    ) { index, message ->
                        // Show date separator when date changes
                        val showDateSeparator = if (index == 0) {
                            true
                        } else {
                            val prev = state.currentMessages[index - 1]
                            val calPrev = Calendar.getInstance().apply { timeInMillis = prev.timestamp }
                            val calCurr = Calendar.getInstance().apply { timeInMillis = message.timestamp }
                            calPrev.get(Calendar.DAY_OF_YEAR) != calCurr.get(Calendar.DAY_OF_YEAR) ||
                                    calPrev.get(Calendar.YEAR) != calCurr.get(Calendar.YEAR)
                        }

                        if (showDateSeparator) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    tonalElevation = 1.dp
                                ) {
                                    Text(
                                        text = DateTimeUtils.formatSmartTimestamp(context, message.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        MessageBubble(
                            message = message,
                            isSelected = state.selectedMessageIds.contains(message.id),
                            onLongClick = { onToggleSelectMessage(message.id) },
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleSelectMessage(message.id)
                                } else {
                                    showMessageDetails = message
                                }
                            },
                            onRetryClick = { onRetryMessage(message) }
                        )
                    }
                }
            }
        }
    }
}
