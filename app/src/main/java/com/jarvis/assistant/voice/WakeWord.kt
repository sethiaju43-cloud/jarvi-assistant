package com.jarvis.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Offline "Jarvis" sunne wala. Pehli baar ek chhota model download karta hai. */
class WakeWord(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun prepare(onStatus: (String) -> Unit) = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "vosk")
        val ok = File(root, ".ok")
        if (!ok.exists()) {
            root.deleteRecursively()
            root.mkdirs()
            val zip = File(root, "model.zip")
            download(zip, onStatus)
            onStatus("Model khol rahi hun...")
            unzip(zip, root)
            zip.delete()
            ok.writeText("ok")
        }
        onStatus("Model load ho raha hai...")
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        model = Model(File(root, MODEL_DIR).absolutePath)
    }

    private fun download(dest: File, onStatus: (String) -> Unit) {
        val req = Request.Builder().url(MODEL_URL).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Download fail ${r.code}")
            val body = r.body ?: throw IOException("Khaali jawab")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val p = (read * 100 / total).toInt()
                            if (p != last && p % 5 == 0) {
                                last = p
                                onStatus("Wake word model download: $p%")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun unzip(zip: File, dest: File) {
        val destPath = dest.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(dest, e.name)
                if (!out.canonicalPath.startsWith(destPath)) throw IOException("Galat zip entry")
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
    }

    /** Main thread se chalao. */
    fun start(onWake: () -> Unit) {
        if (service != null) return
        val m = model ?: return
        val rec = Recognizer(m, 16000f, "[\"jarvis\", \"[unk]\"]")
        val svc = SpeechService(rec, 16000f)
        var fired = false

        val listener = object : RecognitionListener {
            private fun check(json: String?) {
                if (!fired && json != null && json.contains("jarvis")) {
                    fired = true
                    main.post { onWake() }
                }
            }

            override fun onPartialResult(hypothesis: String?) = check(hypothesis)
            override fun onResult(hypothesis: String?) = check(hypothesis)
            override fun onFinalResult(hypothesis: String?) = check(hypothesis)
            override fun onError(exception: Exception?) {}
            override fun onTimeout() {}
        }

        recognizer = rec
        service = svc
        svc.startListening(listener)
    }

    /** Mic chhod do, taaki Hindi STT use kar sake. */
    fun stop() {
        service?.let {
            it.stop()
            it.shutdown()
        }
        service = null
        recognizer?.close()
        recognizer = null
    }

    fun release() {
        stop()
        model?.close()
        model = null
    }

    companion object {
        private const val MODEL_DIR = "vosk-model-small-en-us-0.15"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_DIR.zip"
    }
}
