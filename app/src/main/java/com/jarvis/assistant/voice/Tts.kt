package com.jarvis.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jarvis.assistant.data.Prefs
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Pehle Gemini ki natural awaaz try karta hai (agar ON ho).
 * Kuch bhi fail ho to phone ki apni Hindi awaaz bol deti hai.
 */
class Tts(context: Context) {

    private val appCtx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val prefs = Prefs(appCtx)
    private val gv = GeminiVoice(appCtx, prefs)
    private val pool = Executors.newCachedThreadPool()

    private var engine: TextToSpeech? = null
    private var ready = false
    private var voices: List<Voice> = emptyList()

    @Volatile
    private var counter = 0

    init {
        engine = TextToSpeech(appCtx) { status ->
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

    // ---------- Phone ki awaaz badalna ----------

    fun nextVoice(): String {
        val e = engine
        if (!ready || e == null || voices.isEmpty()) return "Is phone me dusri Hindi awaaz nahi mili"
        val cur = voices.indexOfFirst { it.name == e.voice?.name }
        val next = voices[(cur + 1).mod(voices.size)]
        e.voice = next
        prefs.voiceName = next.name
        val id = ++counter
        gv.stop()
        localSpeak("हाय, अब मेरी आवाज़ ऐसी है।", id) { }
        return "Awaaz ${voices.indexOf(next) + 1}/${voices.size}"
    }

    fun voiceLabel(): String {
        val e = engine
        if (!ready || e == null || voices.isEmpty()) return "Phone ki awaaz badlo"
        val i = voices.indexOfFirst { it.name == e.voice?.name }
        return "Phone ki awaaz badlo (abhi ${i + 1}/${voices.size})"
    }

    // ---------- Gemini awaaz badalna ----------

    fun nextGeminiVoice(): String {
        val list = GeminiVoice.VOICES
        val cur = list.indexOf(prefs.geminiVoiceName)
        val next = list[(cur + 1).mod(list.size)]
        prefs.geminiVoiceName = next
        GeminiVoice.blockedUntil = 0L
        speak("हाय, अब मेरी आवाज़ ऐसी है।") { }
        return next
    }

    fun geminiVoiceLabel(): String = "Gemini awaaz: ${prefs.geminiVoiceName} (badlo)"

    // ---------- Bolna ----------

    /** finished tab chalta hai jab ye wali awaaz poori ho. Purani awaaz ka callback nahi chalta. */
    fun speak(text: String, finished: () -> Unit) {
        if (text.isBlank()) {
            finished()
            return
        }
        val id = ++counter
        gv.stop()

        if (prefs.geminiVoice && prefs.apiKey.isNotBlank() && gv.available()) {
            engine?.stop()
            pool.execute {
                try {
                    val pcm = gv.fetch(text)
                    if (id != counter) return@execute
                    gv.play(pcm) { id == counter }
                    if (id == counter) main.post { finished() }
                } catch (e: Exception) {
                    if (id == counter) {
                        main.post { if (id == counter) localSpeak(text, id, finished) }
                    }
                }
            }
        } else {
            localSpeak(text, id, finished)
        }
    }

    private fun localSpeak(text: String, id: Int, finished: () -> Unit) {
        val e = engine
        if (!ready || e == null) {
            finished()
            return
        }
        val uid = "j$id"
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == uid) main.post { finished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == uid) main.post { finished() }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == uid) main.post { finished() }
            }
        })
        e.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid)
    }

    fun stop() {
        counter++
        gv.stop()
        engine?.stop()
    }

    fun shutdown() {
        counter++
        gv.stop()
        pool.shutdownNow()
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
