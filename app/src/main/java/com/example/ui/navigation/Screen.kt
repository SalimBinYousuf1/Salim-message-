package com.example.ui.navigation

sealed class Screen {
    data object FirstLaunch : Screen()
    data object ConversationList : Screen()
    data class Conversation(val threadId: Long, val address: String) : Screen()
    data object NewMessage : Screen()
    data object Search : Screen()
    data object Archived : Screen()
    data object Starred : Screen()
    data object Scheduled : Screen()
    data object Settings : Screen()
}
