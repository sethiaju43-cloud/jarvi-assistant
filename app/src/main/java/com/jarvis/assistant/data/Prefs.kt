package com.jarvis.assistant.data

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(value) {
            sp.edit().putString("api_key", value.trim()).apply()
        }

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) {
            sp.edit().putString("model", value.trim().ifEmpty { DEFAULT_MODEL }).apply()
        }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
