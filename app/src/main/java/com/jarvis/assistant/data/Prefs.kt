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

    /** Floating pehle ON tha ya nahi (boot par wapas chalane ke liye). */
    var floatingWasOn: Boolean
        get() = sp.getBoolean("floating_on", false)
        set(value) {
            sp.edit().putBoolean("floating_on", value).apply()
        }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
