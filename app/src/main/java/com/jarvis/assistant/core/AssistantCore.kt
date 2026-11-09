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
import com.jarvis.assistant.ai.OfflineBrain
import com.jarvis.assistant.data.Prefs
import com.jarvis.assistant.ui.CharState
import com.jarvis.assistant.ui.Msg
import com.jarvis.assistant.voice.Stt
import com.jarvis.assistant.voice.Tts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Poori app ka dimaag. Activity aur floating service dono isi ko use karte hain. */
class AssistantCore(private val app: Application) {

    private val prefs = Prefs(app)
    private val actions = PhoneActions(app)
    private val gemini = GeminiClient(prefs, actions)
    private val offline = OfflineBrain(actions)
    private val stt = Stt(app)
    private val tts = Tts(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    /** Wake word ki halat (download %, ready, error). */
    var wakeStatus by mutableStateOf("")

    val messages = mutableStateListOf<Msg>()

    init {
        messages.add(
            Msg(
                false,
                if (prefs.apiKey.isBlank())
                    "Namaste! Main Jarvis hun. Pehle upar Settings me apni Gemini API key daalo."
                else
                    "Namaste! Bolo, kya karna hai?"
            )
        )
    }

    fun saveSettings(key: String, modelName: String) {
        prefs.apiKey = key
        prefs.model = modelName
        apiKey = prefs.apiKey
        model = prefs.model
        gemini.clear()
    }

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
        e.message == "NO_KEY" -> "Pehle Settings me Gemini API key daalo."
        e.message?.contains("429") == true -> "Gemini ki free limit poori ho gayi, thodi der baad try karo."
        e.message?.contains("400") == true || e.message?.contains("403") == true ->
            "API key ya model name galat lagta hai. Settings check karo."
        else -> "Internet ya Gemini me dikkat hai, aur ye command offline samajh nahi aaya."
    }

    private fun speak(reply: String) {
        state = CharState.SPEAKING
        lastReply = reply
        val clean = reply.replace("*", "").replace("#", "").replace("`", "")
        tts.speak(clean) { state = CharState.IDLE }
    }
}
