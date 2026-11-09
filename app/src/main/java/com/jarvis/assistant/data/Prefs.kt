package com.jarvis.assistant.data

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(value) {
            sp.edit().putString("api_key", value.trim()).apply()
        }

    /** Purane (2.0 / 2.5) model ke naam khud naye model par badal jate hain. */
    var model: String
        get() {
            val m = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
            return if (m.isBlank() || m.startsWith("gemini-2.")) DEFAULT_MODEL else m
        }
        set(value) {
            sp.edit().putString("model", value.trim().ifEmpty { DEFAULT_MODEL }).apply()
        }

    /** Floating pehle ON tha ya nahi (boot par wapas chalane ke liye). */
    var floatingWasOn: Boolean
        get() = sp.getBoolean("floating_on", false)
        set(value) {
            sp.edit().putBoolean("floating_on", value).apply()
        }

    var userName: String
        get() = sp.getString("user_name", "") ?: ""
        set(value) {
            sp.edit().putString("user_name", value.trim()).apply()
        }

    var nickname: String
        get() = sp.getString("nickname", "") ?: ""
        set(value) {
            sp.edit().putString("nickname", value.trim()).apply()
        }

    /** Phone ki (offline) awaaz ka naam. */
    var voiceName: String
        get() = sp.getString("voice_name", "") ?: ""
        set(value) {
            sp.edit().putString("voice_name", value).apply()
        }

    var chatMode: Boolean
        get() = sp.getBoolean("chat_mode", true)
        set(value) {
            sp.edit().putBoolean("chat_mode", value).apply()
        }

    var wakeReply: Boolean
        get() = sp.getBoolean("wake_reply", true)
        set(value) {
            sp.edit().putBoolean("wake_reply", value).apply()
        }

    /** Gemini ki natural awaaz (online). Na chale to phone ki awaaz apne aap. */
    var geminiVoice: Boolean
        get() = sp.getBoolean("gemini_voice", true)
        set(value) {
            sp.edit().putBoolean("gemini_voice", value).apply()
        }

    var geminiVoiceName: String
        get() = sp.getString("gemini_voice_name", "Kore") ?: "Kore"
        set(value) {
            sp.edit().putString("gemini_voice_name", value).apply()
        }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val TTS_MODEL = "gemini-3.8-flash-tts"
    }
}
