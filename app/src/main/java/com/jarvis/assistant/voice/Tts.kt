package com.jarvis.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jarvis.assistant.data.Prefs
import java.util.Locale

class Tts(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val prefs = Prefs(context)
    private var engine: TextToSpeech? = null
    private var ready = false
    private var voices: List<Voice> = emptyList()
    private var counter = 0

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.let { e ->
                    val r = e.setLanguage(Locale("hi", "IN"))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        e.setLanguage(Locale.getDefault())
                    }
                    loadVoices(e)
                    e.setSpeechRate(0.95f)
                    e.setPitch(1.1f)
                    ready = true
                }
            }
        }
    }

    private fun loadVoices(e: TextToSpeech) {
        voices = try {
            e.voices.orEmpty().filter { it.locale.language == "hi" }.sortedBy { it.name }
        } catch (ex: Exception) {
            emptyList()
        }
        val saved = prefs.voiceName
        val pick = voices.firstOrNull { it.name == saved }
            ?: voices.firstOrNull { it.name.contains("hia") }
            ?: voices.firstOrNull()
        if (pick != null) e.voice = pick
    }

    /** Agli Hindi awaaz par jao aur ek line bolkar sunao. Main thread se chalao. */
    fun nextVoice(): String {
        val e = engine
        if (!ready || e == null || voices.isEmpty()) return "Is phone me dusri Hindi awaaz nahi mili"
        val cur = voices.indexOfFirst { it.name == e.voice?.name }
        val next = voices[(cur + 1).mod(voices.size)]
        e.voice = next
        prefs.voiceName = next.name
        speak("हाय, अब मेरी आवाज़ ऐसी है।") { }
        return "Awaaz ${voices.indexOf(next) + 1}/${voices.size}"
    }

    fun voiceLabel(): String {
        val e = engine
        if (!ready || e == null || voices.isEmpty()) return "Awaaz badlo"
        val i = voices.indexOfFirst { it.name == e.voice?.name }
        return "Awaaz badlo (abhi ${i + 1}/${voices.size})"
    }

    /** finished tab chalta hai jab ye wali awaaz poori ho, ya ruk jaye. Purani awaaz ka callback nahi chalta. */
    fun speak(text: String, finished: () -> Unit) {
        val e = engine
        if (!ready || e == null || text.isBlank()) {
            finished()
            return
        }
        val id = "j${++counter}"
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) main.post { finished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) main.post { finished() }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == id) main.post { finished() }
            }
        })
        e.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
