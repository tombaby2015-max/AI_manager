package com.aimanager.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = DataStore(application)

    private val _services = MutableStateFlow<List<AiService>>(emptyList())
    val services: StateFlow<List<AiService>> = _services

    init {
        _services.value = store.loadServices()
    }

    private fun save() {
        viewModelScope.launch {
            store.saveServices(_services.value)
        }
    }

    fun toggleAi(aiId: String) {
        _services.value = _services.value.map { ai ->
            if (ai.id == aiId) ai.copy(isExpanded = !ai.isExpanded) else ai
        }
        save()
    }

    fun toggleAccount(aiId: String, accountId: String) {
        _services.value = _services.value.map { ai ->
            if (ai.id != aiId) return@map ai
            ai.copy(accounts = ai.accounts.map { acc ->
                if (acc.id == accountId) acc.copy(isExpanded = !acc.isExpanded) else acc
            })
        }
        save()
    }

    fun collapseAll() {
        val allExpanded = _services.value.any { it.isExpanded }
        _services.value = _services.value.map { ai ->
            ai.copy(
                isExpanded = !allExpanded,
                accounts = ai.accounts.map { acc ->
                    acc.copy(isExpanded = !allExpanded)
                }
            )
        }
        save()
    }

    fun addAiService(name: String, baseUrl: String, iconUrl: String = "") {
        val new = AiService(
            id = UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            iconUrl = iconUrl
        )
        _services.value = _services.value + new
        save()
    }

    fun deleteAiService(aiId: String) {
        _services.value = _services.value.filter { it.id != aiId }
        save()
    }

    fun editAiService(aiId: String, name: String, baseUrl: String, iconUrl: String) {
        _services.value = _services.value.map { ai ->
            if (ai.id == aiId) ai.copy(name = name, baseUrl = baseUrl, iconUrl = iconUrl) else ai
        }
        save()
    }

    fun addAccount(aiId: String, name: String, browserPackage: String, accountUrl: String = "") {
        _services.value = _services.value.map { ai ->
            if (ai.id != aiId) return@map ai
            val new = Account(
                id = UUID.randomUUID().toString(),
                name = name,
                browserPackage = browserPackage,
                accountUrl = accountUrl
            )
            ai.copy(accounts = ai.accounts + new)
        }
        save()
    }

    fun deleteAccount(aiId: String, accountId: String) {
        _services.value = _services.value.map { ai ->
            if (ai.id != aiId) return@map ai
            ai.copy(accounts = ai.accounts.filter { it.id != accountId })
        }
        save()
    }

    fun editAccount(aiId: String, accountId: String, name: String, browserPackage: String, accountUrl: String = "") {
        _services.value = _services.value.map { ai ->
            if (ai.id != aiId) return@map ai
            ai.copy(accounts = ai.accounts.map { acc ->
                if (acc.id != accountId) return@map acc
                acc.copy(name = name, browserPackage = browserPackage, accountUrl = accountUrl)
            })
        }
        save()
    }

    fun addChat(aiId: String, accountId: String, name: String, url: String) {
        _services.value = _services.value.map { ai ->
            if (ai.id != aiId) return@map ai
            ai.copy(accounts = ai.accounts.map { acc ->
                if (acc.id != accountId) return@map acc
                val new = Chat(id = UUID.randomUUID().toString(), name = name, url = url)
                acc.copy(chats = acc.chats + new)
            })
        }
        save()
    }

    fun deleteChat(aiId: String, accountId: String, chatId: String) {
        _services.value = _services.value.map { ai ->
            if (ai.id != aiId) return@map ai
            ai.copy(accounts = ai.accounts.map { acc ->
                if (acc.id != accountId) return@map acc
                acc.copy(chats = acc.chats.filter { it.id != chatId })
            })
        }
        save()
    }

    fun editChat(aiId: String, accountId: String, chatId: String, name: String, url: String) {
        _services.value = _services.value.map { ai ->
            if (ai.id != aiId) return@map ai
            ai.copy(accounts = ai.accounts.map { acc ->
                if (acc.id != accountId) return@map acc
                acc.copy(chats = acc.chats.map { chat ->
                    if (chat.id != chatId) return@map chat
                    chat.copy(name = name, url = url)
                })
            })
        }
        save()
    }

    fun setTimer(aiId: String, accountId: String, durationMs: Long) {
        val endTime = System.currentTimeMillis() + durationMs
        _services.value = _services.value.map { ai ->
            if (ai.id != aiId) return@map ai
            ai.copy(accounts = ai.accounts.map { acc ->
                if (acc.id != accountId) return@map acc
                acc.copy(timerEndTimestamp = endTime)
            })
        }
        save()
    }

    fun clearTimer(aiId: String, accountId: String) {
        _services.value = _services.value.map { ai ->
            if (ai.id != aiId) return@map ai
            ai.copy(accounts = ai.accounts.map { acc ->
                if (acc.id != accountId) return@map acc
                acc.copy(timerEndTimestamp = null)
            })
        }
        save()
    }
}