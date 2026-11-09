package com.jarvis.assistant.ai

import com.jarvis.assistant.actions.PhoneActions
import org.json.JSONObject

class OfflineBrain(private val actions: PhoneActions) {

    fun handle(raw: String): String? {
        val t = raw.lowercase().trim()

        if (listOf("torch", "टॉर्च", "flashlight", "फ्लैशलाइट").any { t.contains(it) }) {
            val off = listOf("off", "band", "बंद").any { t.contains(it) }
            return actions.execute("torch", JSONObject().put("on", !off))
        }
        if (listOf("battery", "बैटरी").any { t.contains(it) }) {
            return actions.execute("battery_status", JSONObject())
        }
        if (listOf("time", "टाइम", "कितने बजे", "kitne baje", "समय").any { t.contains(it) }) {
            return actions.execute("current_time", JSONObject())
        }
        val kw = listOf("kholo", "khol do", "खोलो", "खोल दो", "खोलिए", "open").firstOrNull { t.contains(it) }
        if (kw != null) {
            val name = t.replace(kw, "").replace("jarvis", "").replace("जार्विस", "").trim()
            if (name.isNotEmpty()) return actions.execute("open_app", JSONObject().put("name", name))
        }
        return null
    }
}
