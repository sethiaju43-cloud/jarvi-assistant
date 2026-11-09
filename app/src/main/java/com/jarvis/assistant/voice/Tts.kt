package com.jarvis.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class Tts(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.let { e ->
                    val r = e.setLanguage(Locale("hi", "IN"))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        e.setLanguage(Locale.getDefault())
                    }
                    e.setSpeechRate(1.0f)
                    e.setPitch(1.1f)
                    ready = true
                }
            }
        }
    }

    fun speak(text: String, finished: () -> Unit) {
        val e = engine
        if (!ready || e == null || text.isBlank()) {
            finished()
            return
        }
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                main.post { finished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { finished() }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                main.post { finished() }
            }
        })
        e.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
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
