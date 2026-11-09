package com.jarvis.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jarvis.assistant.JarvisApp

enum class CharState { IDLE, LISTENING, THINKING, SPEAKING }

data class Msg(val fromUser: Boolean, val text: String)

/** Ab sirf AssistantCore ka seedha rasta hai. */
class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    private val core = (app as JarvisApp).core

    val state: CharState get() = core.state
    val partial: String get() = core.partial
    val apiKey: String get() = core.apiKey
    val model: String get() = core.model
    val messages get() = core.messages

    fun saveSettings(key: String, modelName: String) = core.saveSettings(key, modelName)
    fun onMic() = core.onMic()
    fun submit(text: String) = core.submit(text)
}
