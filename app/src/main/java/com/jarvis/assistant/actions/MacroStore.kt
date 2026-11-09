package com.jarvis.assistant.actions

import android.content.Context
import org.json.JSONArray

/** Macros phone me save hote hain: naam -> steps ki list. */
class MacroStore(context: Context) {

    private val sp = context.getSharedPreferences("macros", Context.MODE_PRIVATE)

    fun names(): List<String> = sp.all.keys.sorted()

    fun get(key: String): JSONArray? =
        sp.getString(key, null)?.let { runCatching { JSONArray(it) }.getOrNull() }

    fun save(name: String, steps: JSONArray) {
        sp.edit().putString(name.trim().lowercase(), steps.toString()).apply()
    }

    fun delete(key: String) {
        sp.edit().remove(key).apply()
    }

    /** Pehle poora naam, warna jisme naam aaye. */
    fun find(name: String): String? {
        val q = name.trim().lowercase()
        val all = names()
        return all.firstOrNull { it == q }
            ?: all.firstOrNull { it.contains(q) || q.contains(it) }
    }
}
