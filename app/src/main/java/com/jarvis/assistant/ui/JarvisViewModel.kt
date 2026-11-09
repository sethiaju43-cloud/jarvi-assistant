package com.jarvis.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jarvis.assistant.JarvisApp

enum class CharState { IDLE, LISTENING, THINKING, SPEAKING }

data class Msg(val fromUser: Boolean, val text: String)

/** Ab sirf AssistantCore ka seedha rasta hai. */
class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    private val core = (app as JarvisApp).core

    init {
        core.greetOnce()
    }

    val state: CharState get() = core.state
    val partial: String get() = core.partial
    val apiKey: String get() = core.apiKey
    val model: String get() = core.model
    val userName: String get() = core.userName
    val nickname: String get() = core.nickname
    val chatMode: Boolean get() = core.chatMode
    val wakeReply: Boolean get() = core.wakeReply
    val geminiVoice: Boolean get() = core.geminiVoice
    val messages get() = core.messages

    fun saveSettings(
        key: String,
        modelName: String,
        name: String,
        nick: String,
        chat: Boolean,
        wake: Boolean,
        gVoice: Boolean
    ) = core.saveSettings(key, modelName, name, nick, chat, wake, gVoice)

    fun nextVoice(): String = core.nextVoice()
    fun voiceLabel(): String = core.voiceLabel()
    fun nextGeminiVoice(): String = core.nextGeminiVoice()
    fun geminiVoiceLabel(): String = core.geminiVoiceLabel()
    fun onMic() = core.onMic()
    fun submit(text: String) = core.submit(text)
}
