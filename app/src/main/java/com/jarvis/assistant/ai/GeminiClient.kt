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

                val responses = JSONArray()
                for (c in calls) {
                    val fc = c.getJSONObject("functionCall")
                    val name = fc.getString("name")
                    val args = fc.optJSONObject("args") ?: JSONObject()
                    val raw = if (memory.handles(name)) memory.execute(name, args)
                    else actions.execute(name, args)
                    responses.put(
                        JSONObject().put(
                            "functionResponse",
                            JSONObject().put("name", name)
                                .put("response", JSONObject().put("result", raw.take(6000)))
                        )
                    )
                }
                history.add(JSONObject().put("role", "user").put("parts", responses))
            }
            "काम थोड़ा लंबा हो गया, एक बार फिर बोलो ना।"
        } catch (e: Exception) {
            while (history.size > startSize) history.removeAt(history.size - 1)
            throw e
        }
    }

    private fun callGemini(key: String): JSONObject {
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

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${prefs.model}:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("Gemini ${r.code}: ${txt.take(250)}")
            val cands = JSONObject(txt).optJSONArray("candidates")
            if (cands == null || cands.length() == 0) throw IOException("Gemini ne khaali jawab diya")
            return cands.getJSONObject(0).optJSONObject("content")
                ?: throw IOException("Gemini ne jawab nahi diya")
        }
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
    }
}
