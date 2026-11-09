package com.jarvis.assistant.ai

import com.jarvis.assistant.actions.PhoneActions
import com.jarvis.assistant.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val prefs: Prefs,
    private val actions: PhoneActions,
    private val memory: MemoryStore
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<JSONObject>()

    /** Agar model thinkingConfig na le, to ise band karke dobara bhejte hain. */
    @Volatile
    private var useThinkingCfg = true

    fun clear() = history.clear()

    suspend fun chat(userText: String): String = withContext(Dispatchers.IO) {
        val key = prefs.apiKey
        if (key.isBlank()) throw IllegalStateException("NO_KEY")

        val startSize = history.size
        try {
            history.add(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", userText)))
            )
            trimHistory()

            var rounds = 0
            while (rounds++ < MAX_ROUNDS) {
                // Poora jawab waise ka waisa rakhte hain (thought signatures bachi rehti hain)
                val content = callGemini(key)
                if (!content.has("role")) content.put("role", "model")
                history.add(content)

                val parts = content.optJSONArray("parts") ?: JSONArray()
                val partList = (0 until parts.length()).map { parts.getJSONObject(it) }
                val calls = partList.filter { it.has("functionCall") }

                if (calls.isEmpty()) {
                    val text = partList
                        .filter { it.has("text") && !it.optBoolean("thought", false) }
                        .joinToString("") { it.getString("text") }
                        .trim()
                    return@withContext text.ifEmpty { "हो गया।" }
                }

                // Har functionCall ke liye ek functionResponse, wahi id aur name ke saath
                val responses = JSONArray()
                for (c in calls) {
                    val fc = c.getJSONObject("functionCall")
                    val name = fc.getString("name")
                    val args = fc.optJSONObject("args") ?: JSONObject()
                    val raw = if (memory.handles(name)) memory.execute(name, args)
                    else actions.execute(name, args)

                    val fr = JSONObject()
                        .put("name", name)
                        .put("response", JSONObject().put("result", raw.take(6000)))
                    if (fc.has("id")) fr.put("id", fc.getString("id"))
                    responses.put(JSONObject().put("functionResponse", fr))
                }
                history.add(JSONObject().put("role", "user").put("parts", responses))
            }
            "काम थोड़ा लंबा हो गया, एक बार फिर बोलो ना।"
        } catch (e: Exception) {
            while (history.size > startSize) history.removeAt(history.size - 1)
            throw e
        }
    }

    /** Pehle chuna hua model, 404 aaye to backup models. */
    private fun callGemini(key: String): JSONObject {
        val models = (listOf(prefs.model) + FALLBACK_MODELS).distinct()
        var last: IOException? = null
        for (m in models) {
            var tries = 0
            while (tries++ < 2) {
                val (code, txt) = post(key, m)
                if (code in 200..299) return parse(txt)
                if (code == 400 && useThinkingCfg && txt.contains("thinking", ignoreCase = true)) {
                    useThinkingCfg = false
                    continue
                }
                last = IOException("Gemini $code: ${txt.take(250)}")
                break
            }
            // Sirf "model nahi mila" par agla model try karo
            if (last?.message?.startsWith("Gemini 404") != true) break
        }
        throw last ?: IOException("Gemini se jawab nahi mila")
    }

    private fun post(key: String, model: String): Pair<Int, String> {
        val tools = actions.declarations()
        val mem = memory.declarations()
        for (i in 0 until mem.length()) tools.put(mem.get(i))

        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", Persona.build(prefs, memory))))
            )
            .put("contents", JSONArray(history))
            .put("tools", JSONArray().put(JSONObject().put("function_declarations", tools)))

        if (useThinkingCfg) {
            body.put(
                "generationConfig",
                JSONObject().put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            )
        }

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { r ->
            return Pair(r.code, r.body?.string().orEmpty())
        }
    }

    private fun parse(txt: String): JSONObject {
        val cands = JSONObject(txt).optJSONArray("candidates")
        if (cands == null || cands.length() == 0) throw IOException("Gemini ne khaali jawab diya")
        return cands.getJSONObject(0).optJSONObject("content")
            ?: throw IOException("Gemini ne jawab nahi diya")
    }

    private fun trimHistory() {
        while (history.size > 40) history.removeAt(0)
        while (history.isNotEmpty() && !isUserText(history[0])) history.removeAt(0)
    }

    private fun isUserText(c: JSONObject): Boolean {
        if (c.optString("role") != "user") return false
        val p = c.optJSONArray("parts") ?: return false
        return p.length() > 0 && p.getJSONObject(0).has("text")
    }

    companion object {
        private const val MAX_ROUNDS = 16
        private val FALLBACK_MODELS = listOf("gemini-3.7-flash", "gemini-3.5-flash-lite")
    }
}
