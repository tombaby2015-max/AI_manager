package com.aimanager.data

data class AiService(
    val id: String,
    val name: String,
    val baseUrl: String,
    val iconUrl: String = "",
    val isExpanded: Boolean = false,
    val accounts: List<Account> = emptyList()
)

data class Account(
    val id: String,
    val name: String,
    val browserPackage: String,
    val accountUrl: String = "",
    val iconUrl: String = "",
    val timerEndTimestamp: Long? = null,
    val isExpanded: Boolean = false,
    val chats: List<Chat> = emptyList()
)

data class Chat(
    val id: String,
    val name: String,
    val url: String
)
