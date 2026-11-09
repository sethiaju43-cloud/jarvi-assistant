package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Phone ke kaam "tools" ki shakal mein. Online model (Gemini ya OpenAI-jaise) seedha function chunta hai,
 * to text-format ki galti nahi hoti. App us function ko purani "OPEN:" / "ACTION:" line mein badal deti hai,
 * isliye aage ka poora system (Brain, PhoneActions) jaisa tha waisa hi chalta hai.
 */
object ToolSpecs {

    class P(
        val name: String,
        val type: String,
        val desc: String,
        val choices: List<String>? = null,
        val required: Boolean = true
    )

    class Tool(val name: String, val desc: String, val params: List<P> = emptyList())

    class Call(val name: String, val args: JSONObject)

    private val settingsPages = listOf(
        "WIFI", "BLUETOOTH", "DISPLAY", "SOUND", "BATTERY", "LOCATION", "APPS", "AIRPLANE", "MAIN"
    )

    val all: List<Tool> = listOf(
        Tool("open_app", "Koi app kholo. Naam list wale app ka bilkul waisa hi likho.",
            listOf(P("app_name", "string", "App ka naam"))),
        Tool("go_back", "Peeche jao (back)."),
        Tool("go_home", "Home screen par jao."),
        Tool("show_recents", "Recent apps dikhao."),
        Tool("scroll", "Screen scroll karo.",
            listOf(
                P("direction", "string", "up ya down", listOf("up", "down")),
                P("times", "integer", "Kitni baar, 1 se 12", null, false)
            )),
        Tool("stop_action", "Chalta hua kaam roko."),
        Tool("call", "Kisi ko call karo. Naam ya number. Naam ho to doosri spelling '|' se jod sakte ho, jaise Rahul|Rahool.",
            listOf(P("target", "string", "Naam ya number"))),
        Tool("send_sms", "SMS (text message) bhejo, WhatsApp nahi. Sirf tab jab user ne saaf SMS kaha ho.",
            listOf(P("target", "string", "Naam ya number"), P("message", "string", "Wahi message jo user ne bola"))),
        Tool("send_whatsapp", "WhatsApp par message bhejo (chat khud khulti hai).",
            listOf(P("target", "string", "Naam ya number"), P("message", "string", "Wahi message jo user ne bola"))),
        Tool("type_text", "Screen par jo text box khula hai usme likho.",
            listOf(P("text", "string", "Jo likhna hai"))),
        Tool("click", "Screen par kisi cheez ko dabao. Naam [SCREEN TEXT] se bilkul waisa hi lo, na mile to English mein batao.",
            listOf(P("label", "string", "Us cheez ka naam"))),
        Tool("set_alarm", "Alarm lagao. Agar subah/raat saaf na ho to abhi ke baad ka agla waqt chuno.",
            listOf(P("time", "string", "24 ghante ke format mein HH:MM, jaise 17:00"))),
        Tool("set_timer", "Timer lagao.",
            listOf(P("seconds", "integer", "Kitne second ka"))),
        Tool("flashlight", "Torch (flashlight) on ya off karo.",
            listOf(P("state", "string", "ON ya OFF", listOf("ON", "OFF")))),
        Tool("volume", "Awaaz badlo.",
            listOf(P("level", "string", "UP, DOWN, MUTE, MAX ya 0 se 100 ka number"))),
        Tool("battery_status", "Battery kitni hai batao."),
        Tool("show_notifications", "Notifications dikhao."),
        Tool("lock_phone", "Phone lock karo."),
        Tool("take_screenshot", "Screenshot lo."),
        Tool("open_settings", "Phone ki settings ka page kholo.",
            listOf(P("page", "string", "Kaun sa page", settingsPages))),
        Tool("play_music", "Gaana ya music chalao.",
            listOf(P("query", "string", "Gaane ya artist ka naam"))),
        Tool("navigate", "Kisi jagah ka raasta (navigation) shuru karo.",
            listOf(P("place", "string", "Jagah"))),
        Tool("show_on_map", "Map par kisi jagah ko dikhao.",
            listOf(P("place", "string", "Jagah"))),
        Tool("open_url", "Website kholo.",
            listOf(P("url", "string", "Poora link"))),
        Tool("add_calendar_event", "Calendar mein event ya meeting jodo.",
            listOf(
                P("title", "string", "Event ka naam"),
                P("datetime", "string", "YYYY-MM-DD HH:MM")
            )),
        Tool("take_photo", "Camera se photo kheencho (camera khud khulta hai).",
            listOf(P("camera", "string", "back ya front (selfie)", listOf("back", "front"), false))),
        Tool("fullscreen", "Video ko full screen karo."),
        Tool("seek_video", "Video aage ya peeche karo. Aage ke liye 10, peeche ke liye -10.",
            listOf(P("seconds", "integer", "10 ya -10")))
    )

    /** Tools tabhi lagao jab ye wahi system prompt ho jisme phone-control ke niyam hain (aur koi call nahi) */
    fun enabledFor(system: String): Boolean = system.contains("ACTION: BACK")

    private fun schemaOf(t: Tool): JSONObject {
        val props = JSONObject()
        val req = JSONArray()
        for (p in t.params) {
            val o = JSONObject().put("type", p.type).put("description", p.desc)
            p.choices?.let { o.put("enum", JSONArray(it)) }
            props.put(p.name, o)
            if (p.required) req.put(p.name)
        }
        val s = JSONObject().put("type", "object").put("properties", props)
        if (req.length() > 0) s.put("required", req)
        return s
    }

    /** Gemini ke "tools" ka hissa */
    fun geminiTools(): JSONArray {
        val decls = JSONArray()
        for (t in all) {
            val d = JSONObject().put("name", t.name).put("description", t.desc)
            // Gemini bina parameter wale function mein khaali "parameters" pasand nahi karta
            if (t.params.isNotEmpty()) d.put("parameters", schemaOf(t))
            decls.put(d)
        }
        return JSONArray().put(JSONObject().put("functionDeclarations", decls))
    }

    /** OpenAI-jaise providers ke "tools" */
    fun openAiTools(): JSONArray {
        val arr = JSONArray()
        for (t in all) {
            val f = JSONObject().put("name", t.name).put("description", t.desc)
                .put("parameters", schemaOf(t))
            arr.put(JSONObject().put("type", "function").put("function", f))
        }
        return arr
    }

    fun callsFromGemini(raw: String): List<Call> {
        val out = ArrayList<Call>()
        try {
            val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            for (i in 0 until parts.length()) {
                val fc = parts.getJSONObject(i).optJSONObject("functionCall") ?: continue
                out.add(Call(fc.optString("name", ""), fc.optJSONObject("args") ?: JSONObject()))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun callsFromOpenAi(raw: String): List<Call> {
        val out = ArrayList<Call>()
        try {
            val msg = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val tc = msg.optJSONArray("tool_calls") ?: return out
            for (i in 0 until tc.length()) {
                val f = tc.getJSONObject(i).optJSONObject("function") ?: continue
                val argStr = f.optString("arguments", "")
                val args = try {
                    if (argStr.isBlank()) JSONObject() else JSONObject(argStr)
                } catch (e: Exception) {
                    JSONObject()
                }
                out.add(Call(f.optString("name", ""), args))
            }
        } catch (e: Exception) {
        }
        return out
    }

    /**
     * Function calls ko purani command line mein badlo. Kai calls hon to sirf PEHLI line aur ' | MORE'
     * (screen har step par badalti hai, isliye agla kaam Brain dobara poochh kar karta hai).
     */
    fun toActionText(calls: List<Call>): String? {
        val lines = calls.mapNotNull { toLine(it) }
        if (lines.isEmpty()) return null
        return if (lines.size > 1) lines[0] + " | MORE" else lines[0]
    }

    private fun toLine(c: Call): String? {
        fun s(k: String): String = c.args.optString(k, "").trim()
        return when (c.name) {
            "open_app" -> s("app_name").ifBlank { null }?.let { "OPEN: $it" }
            "go_back" -> "ACTION: BACK"
            "go_home" -> "ACTION: HOME"
            "show_recents" -> "ACTION: RECENTS"
            "scroll" -> {
                val n = c.args.optInt("times", 5).coerceIn(1, 12)
                if (s("direction").lowercase() == "up") "ACTION: SCROLL_UP $n" else "ACTION: SCROLL_DOWN $n"
            }
            "stop_action" -> "ACTION: STOP"
            "call" -> s("target").ifBlank { null }?.let { "ACTION: CALL $it" }
            "send_sms" -> {
                val t = s("target")
                val m = s("message")
                if (t.isBlank() || m.isBlank()) null else "ACTION: SMS $t :: $m"
            }
            "send_whatsapp" -> {
                val t = s("target")
                val m = s("message")
                if (t.isBlank() || m.isBlank()) null else "ACTION: WHATSAPP $t :: $m"
            }
            "type_text" -> s("text").ifBlank { null }?.let { "ACTION: TYPE $it" }
            "click" -> s("label").ifBlank { null }?.let { "ACTION: CLICK $it" }
            "set_alarm" -> {
                val m = Regex("(\\d{1,2}):(\\d{2})").find(s("time"))
                val h = m?.groupValues?.get(1)?.toIntOrNull()
                val mi = m?.groupValues?.get(2)?.toIntOrNull()
                if (h == null || mi == null || h > 23 || mi > 59) null
                else String.format(java.util.Locale.ENGLISH, "ACTION: ALARM %02d:%02d", h, mi)
            }
            "set_timer" -> {
                val n = c.args.optLong("seconds", 0L)
                if (n <= 0L) null else "ACTION: TIMER $n"
            }
            "flashlight" -> if (s("state").uppercase() == "OFF") "ACTION: FLASH OFF" else "ACTION: FLASH ON"
            "volume" -> {
                val v = s("level").uppercase()
                val num = v.toIntOrNull()
                when {
                    num != null -> "ACTION: VOLUME " + num.coerceIn(0, 100)
                    v == "UP" || v == "DOWN" || v == "MUTE" || v == "MAX" -> "ACTION: VOLUME $v"
                    else -> null
                }
            }
            "battery_status" -> "ACTION: BATTERY"
            "show_notifications" -> "ACTION: NOTIFICATIONS"
            "lock_phone" -> "ACTION: LOCK"
            "take_screenshot" -> "ACTION: SCREENSHOT"
            "open_settings" -> {
                val p = s("page").uppercase()
                if (p in settingsPages) "ACTION: SETTINGS $p" else "ACTION: SETTINGS MAIN"
            }
            "play_music" -> s("query").ifBlank { null }?.let { "ACTION: MUSIC $it" }
            "navigate" -> s("place").ifBlank { null }?.let { "ACTION: NAVIGATE $it" }
            "show_on_map" -> s("place").ifBlank { null }?.let { "ACTION: MAPS $it" }
            "open_url" -> s("url").ifBlank { null }?.let { "ACTION: URL $it" }
            "add_calendar_event" -> {
                val t = s("title")
                val d = s("datetime")
                if (t.isBlank() || d.isBlank()) null else "ACTION: EVENT $t :: $d"
            }
            "take_photo" -> if (s("camera").lowercase() == "front") "ACTION: SELFIE" else "ACTION: PHOTO"
            "fullscreen" -> "ACTION: FULLSCREEN"
            "seek_video" -> {
                val n = c.args.optInt("seconds", 10)
                "ACTION: SEEK " + (if (n < 0) "-10" else "10")
            }
            else -> null
        }
    }
}
