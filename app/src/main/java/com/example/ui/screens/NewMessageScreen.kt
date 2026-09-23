package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactItem
import com.example.ui.components.AvatarView
import com.example.ui.components.ComposerBar
import com.example.ui.viewmodel.MessagesUiState

data class SelectedRecipient(
    val name: String,
    val number: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(
    state: MessagesUiState,
    onBackClick: () -> Unit,
    onSendDirect: (List<String>, String, Uri?) -> Unit,
    onSelectSim: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var recipientQuery by remember { mutableStateOf("") }
    var selectedRecipients by remember { mutableStateOf(listOf<SelectedRecipient>()) }
    var composerText by remember { mutableStateOf("") }
    var selectedAttachmentUri by remember { mutableStateOf<Uri?>(null) }

    val filteredContacts = remember(state.contacts, recipientQuery) {
        val q = recipientQuery.trim().lowercase()
        if (q.isEmpty()) {
            state.contacts.take(40)
        } else {
            state.contacts.filter {
                it.name.lowercase().contains(q) || it.number.contains(q)
            }.take(30)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("new_message_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            val allRecipients = selectedRecipients.map { it.number }.toMutableList()
            if (recipientQuery.isNotBlank() && !allRecipients.contains(recipientQuery)) {
                // If user typed a number without clicking add
                allRecipients.add(recipientQuery.trim())
            }

            ComposerBar(
                text = composerText,
                onTextChange = { composerText = it },
                onSendClick = {
                    if (allRecipients.isNotEmpty()) {
                        val toSend = composerText
                        val attachment = selectedAttachmentUri
                        composerText = ""
                        selectedAttachmentUri = null
                        onSendDirect(allRecipients, toSend, attachment)
                    }
                },
                attachmentUri = selectedAttachmentUri,
                onAttachmentSelected = { selectedAttachmentUri = it },
                sims = state.sims,
                selectedSubId = state.selectedSubId,
                onSimSelected = onSelectSim,
                onScheduleClick = { }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Recipient Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "To",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    // Recipient chips
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedRecipients.forEach { recipient ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text("${recipient.name} (${recipient.number})", fontSize = 12.sp) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            selectedRecipients = selectedRecipients.filter { it != recipient }
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove")
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = recipientQuery,
                    onValueChange = { recipientQuery = it },
                    placeholder = { Text("Type a name or phone number") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recipient_input_field"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = recipientQuery.trim()
                            if (trimmed.isNotEmpty()) {
                                selectedRecipients = selectedRecipients + SelectedRecipient(name = trimmed, number = trimmed)
                                recipientQuery = ""
                            }
                        }
                    ),
                    trailingIcon = {
                        if (recipientQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val trimmed = recipientQuery.trim()
                                    if (trimmed.isNotEmpty()) {
                                        selectedRecipients = selectedRecipients + SelectedRecipient(name = trimmed, number = trimmed)
                                        recipientQuery = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add number")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            HorizontalDivider()

            // Contacts Suggestions
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = filteredContacts,
                    key = { "${it.id}_${it.number}" }
                ) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!selectedRecipients.any { it.number == contact.number }) {
                                    selectedRecipients = selectedRecipients + SelectedRecipient(name = contact.name, number = contact.number)
                                }
                                recipientQuery = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(
                            photoUri = contact.photoUri,
                            nameOrAddress = contact.name,
                            size = 44.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = contact.typeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = contact.number,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
