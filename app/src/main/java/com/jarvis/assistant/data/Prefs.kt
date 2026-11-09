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

    /** User ka naam. */
    var userName: String
        get() = sp.getString("user_name", "") ?: ""
        set(value) {
            sp.edit().putString("user_name", value.trim()).apply()
        }

    /** Pyaar se bulane ka naam. */
    var nickname: String
        get() = sp.getString("nickname", "") ?: ""
        set(value) {
            sp.edit().putString("nickname", value.trim()).apply()
        }

    /** Chuni hui TTS awaaz ka naam. */
    var voiceName: String
        get() = sp.getString("voice_name", "") ?: ""
        set(value) {
            sp.edit().putString("voice_name", value).apply()
        }

    /** Jawab ke baad khud dobara sunna. */
    var chatMode: Boolean
        get() = sp.getBoolean("chat_mode", true)
        set(value) {
            sp.edit().putBoolean("chat_mode", value).apply()
        }

    /** "Jarvis" bolne par "haan bolo" kehna. */
    var wakeReply: Boolean
        get() = sp.getBoolean("wake_reply", true)
        set(value) {
            sp.edit().putBoolean("wake_reply", value).apply()
        }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
