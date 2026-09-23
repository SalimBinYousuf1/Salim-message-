package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.notifications.NotificationHelper
import com.example.telephony.TelephonyRoleHelper
import com.example.ui.navigation.Screen
import com.example.ui.screens.ArchivedScreen
import com.example.ui.screens.ConversationListScreen
import com.example.ui.screens.ConversationScreen
import com.example.ui.screens.FirstLaunchScreen
import com.example.ui.screens.NewMessageScreen
import com.example.ui.screens.ScheduledMessagesScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StarredMessagesScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MessagesViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MessagesViewModel by viewModels()

    private val defaultSmsLauncher = TelephonyRoleHelper.createDefaultSmsLauncher(this) {
        viewModel.checkDefaultSmsRole()
        viewModel.refreshConversations()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Notification Channels
        NotificationHelper.createNotificationChannels(this)

        // Check if first launch
        val prefs = getSharedPreferences("salim_prefs", Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)

        setContent {
            MyApplicationTheme {
                SalimApp(
                    viewModel = viewModel,
                    isFirstLaunchInitial = isFirstLaunch,
                    onRequestDefaultSms = {
                        TelephonyRoleHelper.requestDefaultSmsRole(this, defaultSmsLauncher)
                    },
                    onFirstLaunchCompleted = {
                        prefs.edit().putBoolean("is_first_launch", false).apply()
                    },
                    initialIntent = intent
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkDefaultSmsRole()
        viewModel.loadSims()
        viewModel.refreshConversations()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val address = intent.getStringExtra("thread_address")
        if (!address.isNullOrBlank()) {
            viewModel.openConversation(0L, address)
        } else if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SENDTO) {
            val uri: Uri? = intent.data
            val recipient = uri?.schemeSpecificPart ?: ""
            if (recipient.isNotBlank()) {
                viewModel.openConversation(0L, recipient)
            }
        }
    }
}

@Composable
fun SalimApp(
    viewModel: MessagesViewModel,
    isFirstLaunchInitial: Boolean,
    onRequestDefaultSms: () -> Unit,
    onFirstLaunchCompleted: () -> Unit,
    initialIntent: Intent?
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var navigationStack by remember {
        mutableStateOf<List<Screen>>(
            if (isFirstLaunchInitial && !state.isDefaultSms) {
                listOf(Screen.FirstLaunch)
            } else {
                listOf(Screen.ConversationList)
            }
        )
    }

    val currentScreen = navigationStack.lastOrNull() ?: Screen.ConversationList

    fun navigateTo(screen: Screen) {
        navigationStack = navigationStack + screen
    }

    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
        }
    }

    // Handle initial intent if coming from notification
    LaunchedEffect(initialIntent) {
        if (initialIntent != null) {
            val threadAddress = initialIntent.getStringExtra("thread_address")
            if (!threadAddress.isNullOrBlank()) {
                viewModel.openConversation(0L, threadAddress)
                navigateTo(Screen.Conversation(0L, threadAddress))
            } else if (initialIntent.action == Intent.ACTION_SEND || initialIntent.action == Intent.ACTION_SENDTO) {
                val uri: Uri? = initialIntent.data
                val recipient = uri?.schemeSpecificPart ?: ""
                if (recipient.isNotBlank()) {
                    viewModel.openConversation(0L, recipient)
                    navigateTo(Screen.Conversation(0L, recipient))
                }
            }
        }
    }

    // Handle Snackbars
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfoMessage()
        }
    }

    // Back button handling
    BackHandler(enabled = navigationStack.size > 1) {
        if (state.selectedConversation != null) {
            viewModel.closeConversation()
        }
        navigateBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (currentScreen) {
            is Screen.FirstLaunch -> {
                FirstLaunchScreen(
                    isDefaultSms = state.isDefaultSms,
                    onRequestDefaultSms = onRequestDefaultSms,
                    onComplete = {
                        onFirstLaunchCompleted()
                        navigationStack = listOf(Screen.ConversationList)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is Screen.ConversationList -> {
                ConversationListScreen(
                    state = state,
                    onConversationClick = { conv ->
                        viewModel.openConversation(conv.threadId, conv.address)
                        navigateTo(Screen.Conversation(conv.threadId, conv.address))
                    },
                    onNewMessageClick = {
                        navigateTo(Screen.NewMessage)
                    },
                    onSearchClick = {
                        navigateTo(Screen.Search)
                    },
                    onOpenArchivedClick = {
                        navigateTo(Screen.Archived)
                    },
                    onOpenStarredClick = {
                        navigateTo(Screen.Starred)
                    },
                    onOpenScheduledClick = {
                        navigateTo(Screen.Scheduled)
                    },
                    onOpenSettingsClick = {
                        navigateTo(Screen.Settings)
                    },
                    onRequestDefaultSms = onRequestDefaultSms,
                    onToggleSelect = { viewModel.toggleConversationSelection(it) },
                    onClearSelection = { viewModel.clearConversationSelection() },
                    onSelectAll = { viewModel.selectAllConversations() },
                    onPinSelected = {
                        state.selectedThreadIds.forEach { threadId ->
                            val conv = state.conversations.firstOrNull { it.threadId == threadId }
                            if (conv != null) {
                                viewModel.togglePin(threadId, !conv.isPinned)
                            }
                        }
                        viewModel.clearConversationSelection()
                    },
                    onArchiveSelected = {
                        state.selectedThreadIds.forEach { threadId ->
                            viewModel.toggleArchive(threadId, true)
                        }
                        viewModel.clearConversationSelection()
                    },
                    onDeleteSelected = {
                        viewModel.deleteSelectedConversations()
                    },
                    onArchiveSingle = { conv ->
                        viewModel.toggleArchive(conv.threadId, !conv.isArchived)
                    },
                    onDeleteSingle = { conv ->
                        viewModel.deleteConversation(conv.threadId, conv.address)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is Screen.Conversation -> {
                val selectedConv = state.selectedConversation
                if (selectedConv != null) {
                    ConversationScreen(
                        conversation = selectedConv,
                        state = state,
                        onBackClick = {
                            viewModel.closeConversation()
                            navigateBack()
                        },
                        onSendMessage = { text, attachment ->
                            viewModel.sendSms(text, attachment)
                        },
                        onRetryMessage = { msg ->
                            viewModel.retrySms(msg)
                        },
                        onSaveDraft = { draft ->
                            viewModel.saveDraft(selectedConv.threadId, draft)
                        },
                        onTogglePin = {
                            viewModel.togglePin(selectedConv.threadId, !selectedConv.isPinned)
                        },
                        onToggleMute = {
                            viewModel.toggleMute(selectedConv.threadId, !selectedConv.isMuted)
                        },
                        onToggleFavorite = { msg ->
                            viewModel.toggleFavorite(msg)
                        },
                        onDeleteMessage = { msgId ->
                            viewModel.deleteMessage(msgId)
                        },
                        onDeleteConversation = {
                            viewModel.deleteConversation(selectedConv.threadId, selectedConv.address)
                            navigateBack()
                        },
                        onBlockNumber = { number ->
                            viewModel.blockNumber(number, selectedConv.contactName)
                            navigateBack()
                        },
                        onScheduleMessage = { triggerAtMillis, messageText ->
                            viewModel.scheduleMessage(
                                recipient = selectedConv.address,
                                recipientName = selectedConv.contactName,
                                message = messageText,
                                triggerAtMillis = triggerAtMillis,
                                subId = state.selectedSubId
                            )
                        },
                        onToggleSelectMessage = { viewModel.toggleMessageSelection(it) },
                        onClearMessageSelection = { viewModel.clearMessageSelection() },
                        onDeleteSelectedMessages = { viewModel.deleteSelectedMessages() },
                        onSelectSim = { viewModel.selectSim(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    LaunchedEffect(Unit) {
                        viewModel.openConversation(currentScreen.threadId, currentScreen.address)
                    }
                }
            }

            is Screen.NewMessage -> {
                NewMessageScreen(
                    state = state,
                    onBackClick = { navigateBack() },
                    onSendDirect = { recipients, text, _ ->
                        recipients.forEach { recipient ->
                            viewModel.sendDirectSms(recipient, text) { _ ->
                                viewModel.openConversation(0L, recipient)
                                navigationStack = listOf(Screen.ConversationList, Screen.Conversation(0L, recipient))
                            }
                        }
                    },
                    onSelectSim = { viewModel.selectSim(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is Screen.Search -> {
                SearchScreen(
                    state = state,
                    onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onBackClick = { navigateBack() },
                    onResultClick = { threadId, address ->
                        viewModel.openConversation(threadId, address)
                        navigateTo(Screen.Conversation(threadId, address))
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is Screen.Archived -> {
                ArchivedScreen(
                    state = state,
                    onBackClick = { navigateBack() },
                    onConversationClick = { conv ->
                        viewModel.openConversation(conv.threadId, conv.address)
                        navigateTo(Screen.Conversation(conv.threadId, conv.address))
                    },
                    onUnarchiveClick = { conv ->
                        viewModel.toggleArchive(conv.threadId, false)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is Screen.Starred -> {
                StarredMessagesScreen(
                    state = state,
                    onBackClick = { navigateBack() },
                    onMessageClick = { threadId, address ->
                        viewModel.openConversation(threadId, address)
                        navigateTo(Screen.Conversation(threadId, address))
                    },
                    onUnstarClick = { fav ->
                        viewModel.toggleFavorite(
                            com.example.data.model.MessageItem(
                                id = fav.messageId,
                                threadId = fav.threadId,
                                address = fav.address,
                                body = fav.body,
                                timestamp = fav.timestamp,
                                isIncoming = fav.isIncoming
                            )
                        )
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is Screen.Scheduled -> {
                ScheduledMessagesScreen(
                    state = state,
                    onBackClick = { navigateBack() },
                    onCancelScheduled = { viewModel.cancelScheduledMessage(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is Screen.Settings -> {
                SettingsScreen(
                    state = state,
                    onBackClick = { navigateBack() },
                    onRequestDefaultSms = onRequestDefaultSms,
                    onBlockNumber = { num, name -> viewModel.blockNumber(num, name) },
                    onUnblockNumber = { num -> viewModel.unblockNumber(num) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
