package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ConversationItem
import com.example.ui.components.DefaultSmsBanner
import com.example.ui.components.SwipeableConversationItem
import com.example.ui.viewmodel.MessagesUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    state: MessagesUiState,
    onConversationClick: (ConversationItem) -> Unit,
    onNewMessageClick: () -> Unit,
    onSearchClick: () -> Unit,
    onOpenArchivedClick: () -> Unit,
    onOpenStarredClick: () -> Unit,
    onOpenScheduledClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onToggleSelect: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onPinSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onArchiveSingle: (ConversationItem) -> Unit,
    onDeleteSingle: (ConversationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var filterUnreadOnly by remember { mutableStateOf(false) }
    var pendingDeleteSingle by remember { mutableStateOf<ConversationItem?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val isSelectionMode = state.selectedThreadIds.isNotEmpty()

    val filteredConversations = remember(state.conversations, filterUnreadOnly) {
        val nonArchived = state.conversations.filter { !it.isArchived }
        if (filterUnreadOnly) {
            nonArchived.filter { !it.isRead }
        } else {
            nonArchived
        }
    }

    // Confirmation dialog for single conversation deletion
    pendingDeleteSingle?.let { conv ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSingle = null },
            title = { Text("Delete this conversation?") },
            text = { Text("Messages with ${conv.displayTitle} will be permanently removed from your device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSingle(conv)
                        pendingDeleteSingle = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSingle = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for batch conversation deletion
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Delete ${state.selectedThreadIds.size} conversations?") },
            text = { Text("The selected conversation threads will be removed from your device's SMS database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSelected()
                        showBatchDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedThreadIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Default.Check, contentDescription = "Select all")
                        }
                        IconButton(onClick = onPinSelected) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pin/Unpin")
                        }
                        IconButton(onClick = onArchiveSelected) {
                            Icon(Icons.Default.Archive, contentDescription = "Archive")
                        }
                        IconButton(onClick = { showBatchDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
                        Text(
                            text = "Salim Messages",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier.testTag("search_icon_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages")
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("overflow_menu_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Archived") },
                                    onClick = {
                                        showMenu = false
                                        onOpenArchivedClick()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Starred") },
                                    onClick = {
                                        showMenu = false
                                        onOpenStarredClick()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scheduled") },
                                    onClick = {
                                        showMenu = false
                                        onOpenScheduledClick()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        showMenu = false
                                        onOpenSettingsClick()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onNewMessageClick,
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text("New message") },
                    modifier = Modifier.testTag("new_message_fab"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Default SMS App Warning Banner
            DefaultSmsBanner(
                isDefaultSms = state.isDefaultSms,
                onSetDefaultClick = onRequestDefaultSms
            )

            // Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !filterUnreadOnly,
                    onClick = { filterUnreadOnly = false },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filterUnreadOnly,
                    onClick = { filterUnreadOnly = true },
                    label = { Text("Unread") }
                )
            }

            if (state.isLoadingConversations && state.conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (filterUnreadOnly) "No unread messages" else "No messages yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (filterUnreadOnly) "All your conversations are caught up." else "Tap 'New message' below to start your first conversation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = filteredConversations,
                        key = { it.threadId }
                    ) { conversation ->
                        SwipeableConversationItem(
                            conversation = conversation,
                            isSelected = state.selectedThreadIds.contains(conversation.threadId),
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleSelect(conversation.threadId)
                                } else {
                                    onConversationClick(conversation)
                                }
                            },
                            onLongClick = {
                                onToggleSelect(conversation.threadId)
                            },
                            onArchiveClick = { onArchiveSingle(conversation) },
                            onDeleteClick = { pendingDeleteSingle = conversation }
                        )
                    }
                }
            }
        }
    }
}
