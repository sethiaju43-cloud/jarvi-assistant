package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.jarvis.assistant.data.Prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Gemini TTS se natural, pyaari awaaz. Fail ho to Tts khud phone ki awaaz par chala jata hai. */
class GeminiVoice(private val context: Context, private val prefs: Prefs) {

    class Pcm(val data: ByteArray, val rate: Int)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var track: AudioTrack? = null

    fun available(): Boolean = System.currentTimeMillis() >= blockedUntil

    // ---------- Awaaz banwana ----------

    fun fetch(text: String): Pcm {
        val cache = cacheFile(text)
        if (cache != null && cache.exists()) return Pcm(cache.readBytes(), 24000)
        if (!available()) throw IOException("Gemini awaaz abhi band hai")

        var res = request(text, true)
        if (res.first == 400) res = request(text, false)
        val code = res.first
        if (code !in 200..299) {
            val wait = when (code) {
                429 -> 2 * 60_000L
                400, 403, 404 -> 10 * 60_000L
                else -> 30_000L
            }
            blockedUntil = System.currentTimeMillis() + wait
            throw IOException("Gemini awaaz $code")
        }
        val pcm = parse(res.second)
        if (cache != null && pcm.rate == 24000 && pcm.data.size < 600_000) {
            runCatching {
                cache.parentFile?.mkdirs()
                cache.writeBytes(pcm.data)
            }
        }
        return pcm
    }

    private fun request(text: String, modern: Boolean): Pair<Int, String> {
        val part = JSONObject().put("text", text)
        if (modern) part.put("speechMetadata", JSONObject().put("style", STYLE))

        val voiceConfig = if (modern) {
            JSONObject().put("voice", prefs.geminiVoiceName)
        } else {
            JSONObject().put(
                "prebuiltVoiceConfig",
                JSONObject().put("voiceName", prefs.geminiVoiceName)
            )
        }

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(part))
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject().put("voiceConfig", voiceConfig))
            )

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${Prefs.TTS_MODEL}:generateContent")
            .header("x-goog-api-key", prefs.apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { r ->
            return Pair(r.code, r.body?.string().orEmpty())
        }
    }

    private fun parse(txt: String): Pcm {
        val parts = JSONObject(txt)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini ne awaaz nahi di")

        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val inl = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data") ?: continue
            var bytes = Base64.decode(inl.getString("data"), Base64.DEFAULT)
            val mime = inl.optString("mimeType", inl.optString("mime_type", ""))
            val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
            // Agar WAV aa gayi ho to uska 44 byte ka header hata do
            if (bytes.size > 44 &&
                bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
            ) {
                bytes = bytes.copyOfRange(44, bytes.size)
            }
            return Pcm(bytes, rate)
        }
        throw IOException("Awaaz ka data nahi mila")
    }

    private fun cacheFile(text: String): File? {
        if (text.length > 80) return null
        val key = "${Prefs.TTS_MODEL}|${prefs.geminiVoiceName}|$STYLE|$text"
        val name = Integer.toHexString(key.hashCode()) + "_" + text.length + ".pcm"
        return File(File(context.cacheDir, "voice"), name)
    }

    // ---------- Bajana ----------

    /** Background thread se chalao. valid() false ho jaye to turant ruk jata hai. */
    fun play(pcm: Pcm, valid: () -> Boolean) {
        val rate = pcm.rate
        val minBuf = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, rate))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        try {
            t.play()
            val data = pcm.data
            var off = 0
            while (off < data.size && valid()) {
                val n = t.write(data, off, minOf(4096, data.size - off))
                if (n < 0) break
                off += n
            }
            val totalFrames = data.size / 2
            val deadline = System.currentTimeMillis() + totalFrames * 1000L / rate + 1500
            while (valid() && t.playbackHeadPosition < totalFrames &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(40)
            }
        } catch (e: Exception) {
            // stop() beech me release kar de to yahan aayega, koi baat nahi
        } finally {
            if (track === t) track = null
            if (!valid()) runCatching {
                t.pause()
                t.flush()
            }
            runCatching { t.release() }
        }
    }

    fun stop() {
        val t = track
        track = null
        runCatching {
            t?.pause()
            t?.flush()
            t?.release()
        }
    }

    companion object {
        @Volatile
        var blockedUntil = 0L

        private const val STYLE =
            "warm, soft and affectionate, like a caring close friend talking in natural everyday Hindi"

        /** Zyadatar female awaazein. */
        val VOICES = listOf(
            "Kore", "Aoede", "Leda", "Zephyr", "Callirrhoe",
            "Despina", "Sulafat", "Vindemiatrix", "Achernar"
        )
    }
}
