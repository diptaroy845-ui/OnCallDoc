package com.example.oncalldoc

data class ChatMessage(
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = 0
)
