package com.aimanager.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DataStore(context: Context) {

    private val prefs = context.getSharedPreferences("ai_manager", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveServices(services: List<AiService>) {
        prefs.edit()
            .putString("services", gson.toJson(services))
            .apply()
    }

    fun loadServices(): List<AiService> {
        val json = prefs.getString("services", null) ?: return defaultServices()
        val type = object : TypeToken<List<AiService>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun defaultServices(): List<AiService> = listOf(
        AiService(
            id = "claude",
            name = "Claude",
            baseUrl = "https://claude.ai"
        ),
        AiService(
            id = "chatgpt",
            name = "ChatGPT",
            baseUrl = "https://chatgpt.com"
        ),
        AiService(
            id = "gemini",
            name = "Gemini",
            baseUrl = "https://gemini.google.com"
        )
    )
}