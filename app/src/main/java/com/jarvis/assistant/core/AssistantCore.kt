package com.jarvis.assistant.core

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jarvis.assistant.actions.PhoneActions
import com.jarvis.assistant.ai.GeminiClient
import com.jarvis.assistant.ai.MemoryStore
import com.jarvis.assistant.ai.OfflineBrain
import com.jarvis.assistant.ai.Persona
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.ui.CharState
import com.jarvis.assistant.ui.Msg
import com.jarvis.assistant.voice.Stt
import com.jarvis.assistant.voice.Tts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Poori app ka dimaag. Activity aur floating service dono isi ko use karte hain. */
class AssistantCore(private val app: Application) {

    private val prefs = Prefs(app)
    private val actions = PhoneActions(app)
    private val memory = MemoryStore(app)
    private val gemini = GeminiClient(prefs, actions, memory)
    private val offline = OfflineBrain(actions)
    private val stt = Stt(app)
    private val tts = Tts(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var greeted = false
    private var greetingText = ""

    var state by mutableStateOf(CharState.IDLE)
        private set

    var partial by mutableStateOf("")
        private set

    var lastReply by mutableStateOf("")
        private set

    var apiKey by mutableStateOf(prefs.apiKey)
        private set

    var model by mutableStateOf(prefs.model)
        private set

    var userName by mutableStateOf(prefs.userName)
        private set

    var nickname by mutableStateOf(prefs.nickname)
        private set

    /** Wake word ki halat (download %, ready, error). */
    var wakeStatus by mutableStateOf("")

    val messages = mutableStateListOf<Msg>()

    init {
        greetingText = Persona.greeting(prefs)
        messages.add(
            Msg(
                false,
                if (prefs.apiKey.isBlank())
                    "Namaste! Main Jarvis hun. Pehle upar Settings me apni Gemini API key daalo, aur apna naam bhi bata dena."
                else
                    greetingText
            )
        )
    }

    /** Pehli baar app khulne par pyaar se hello bolo. */
    fun greetOnce() {
        if (greeted) return
        greeted = true
        if (prefs.apiKey.isBlank()) return
        scope.launch {
            delay(1200)
            if (state == CharState.IDLE) speak(greetingText)
        }
    }

    fun saveSettings(key: String, modelName: String, name: String, nick: String) {
        prefs.apiKey = key
        prefs.model = modelName
        prefs.userName = name
        prefs.nickname = nick
        apiKey = prefs.apiKey
        model = prefs.model
        userName = prefs.userName
        nickname = prefs.nickname
        greetingText = Persona.greeting(prefs)
        gemini.clear()
    }

    fun nextVoice(): String = tts.nextVoice()

    fun voiceLabel(): String = tts.voiceLabel()

    fun onMic() {
        if (state == CharState.LISTENING) {
            stt.stop()
            state = CharState.IDLE
            return
        }
        if (state == CharState.THINKING) return
        if (state == CharState.SPEAKING) tts.stop()

        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            messages.add(Msg(false, "Mic ki permission do (phone Settings -> Apps -> Jarvis -> Permissions)."))
            state = CharState.IDLE
            return
        }
        if (!stt.available) {
            messages.add(Msg(false, "Is phone me speech recognition nahi hai. Google app install/update karo."))
            state = CharState.IDLE
            return
        }

        state = CharState.LISTENING
        partial = ""
        stt.start(
            onPartial = { partial = it },
            onResult = {
                partial = ""
                if (it.isBlank()) state = CharState.IDLE else submit(it)
            },
            onFail = { code ->
                partial = ""
                state = CharState.IDLE
                // 6 = kuch bola nahi, 7 = samajh nahi aaya: chup-chaap ignore
                if (code != 6 && code != 7) {
                    messages.add(Msg(false, "Mic me dikkat aayi (code $code), dobara try karo."))
                }
            }
        )
    }

    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        messages.add(Msg(true, t))
        state = CharState.THINKING

        scope.launch {
            val reply = try {
                gemini.chat(t)
            } catch (e: Exception) {
                offline.handle(t) ?: errorText(e)
            }
            messages.add(Msg(false, reply))
            speak(reply)
        }
    }

    /** Sab kuch band karke IDLE par aa jao. */
    fun cancel() {
        stt.stop()
        tts.stop()
        partial = ""
        state = CharState.IDLE
    }

    private fun errorText(e: Exception): String = when {
        e.message == "NO_KEY" -> "पहले सेटिंग्स में अपनी Gemini API key डाल दो ना।"
        e.message?.contains("429") == true -> "अरे, Gemini की फ्री लिमिट पूरी हो गई। थोड़ी देर बाद फिर कोशिश करते हैं।"
        e.message?.contains("400") == true || e.message?.contains("403") == true ->
            "लगता है API key या मॉडल का नाम गलत है। सेटिंग्स एक बार देख लो।"
        else -> "अरे, इंटरनेट या Gemini में कुछ गड़बड़ है, और ये काम मुझे ऑफलाइन समझ नहीं आया।"
    }

    private fun speak(reply: String) {
        state = CharState.SPEAKING
        lastReply = reply
        val clean = reply.replace("*", "").replace("#", "").replace("`", "")
        tts.speak(clean) { state = CharState.IDLE }
    }
}
