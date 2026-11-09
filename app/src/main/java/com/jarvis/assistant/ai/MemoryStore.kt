package com.jarvis.assistant.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** User ke baare me yaad rakhi hui baatein. Phone me hi save hoti hain. */
class MemoryStore(context: Context) {

    private val sp = context.getSharedPreferences("memory", Context.MODE_PRIVATE)

    private val toolNames = setOf("remember_fact", "forget_fact", "list_memories")

    fun all(): List<String> {
        val raw = sp.getString("facts", "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<String>) {
        sp.edit().putString("facts", JSONArray(list).toString()).apply()
    }

    private fun isSensitive(text: String): Boolean {
        val t = text.lowercase()
        if (listOf("password", "पासवर्ड", "otp", "cvv", "pin code", "atm pin").any { t.contains(it) }) return true
        return Regex("\\d{12,}").containsMatchIn(text.replace(" ", "").replace("-", ""))
    }

    fun add(fact: String): String {
        val f = fact.trim()
        if (f.isEmpty()) return "FAIL: Kya yaad rakhna hai?"
        if (isSensitive(f)) return "FAIL: Password, OTP ya card jaisi baatein main yaad nahi rakhti"
        val list = all().toMutableList()
        if (list.any { it.equals(f, ignoreCase = true) }) return "Ye pehle se yaad hai"
        list.add(f.take(200))
        while (list.size > 60) list.removeAt(0)
        save(list)
        return "Yaad kar liya"
    }

    fun forget(keyword: String): String {
        val k = keyword.trim().lowercase()
        if (k.isEmpty()) return "FAIL: Kya bhoolna hai?"
        val list = all()
        val keep = list.filterNot { it.lowercase().contains(k) }
        save(keep)
        val n = list.size - keep.size
        return if (n == 0) "FAIL: Aisi koi baat yaad nahi thi" else "$n baat bhula di"
    }

    fun handles(name: String) = name in toolNames

    fun execute(name: String, args: JSONObject): String = when (name) {
        "remember_fact" -> add(args.optString("fact"))
        "forget_fact" -> forget(args.optString("keyword"))
        "list_memories" -> all().let { if (it.isEmpty()) "Abhi kuch khaas yaad nahi" else it.joinToString("\n") }
        else -> "FAIL: pata nahi"
    }

    private fun tool(name: String, desc: String, param: String?, paramDesc: String = ""): JSONObject {
        val o = JSONObject().put("name", name).put("description", desc)
        if (param != null) {
            o.put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject().put(param, JSONObject().put("type", "string").put("description", paramDesc))
                    )
                    .put("required", JSONArray().put(param))
            )
        }
        return o
    }

    fun declarations(): JSONArray = JSONArray()
        .put(
            tool(
                "remember_fact",
                "User ke baare me koi lambe time tak kaam aane wali baat yaad rakho (naam, pasand, ghar walon ke naam, routine, birthday)",
                "fact",
                "Ek chhota saaf vaakya, jaise: 'User ki behen ka naam Riya hai'"
            )
        )
        .put(tool("forget_fact", "Yaad ki hui koi baat bhula do", "keyword", "Us baat ka koi shabd"))
        .put(tool("list_memories", "Jo kuch user ke baare me yaad hai wo batao", null))
}
