package com.jarvis.assistant.actions

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.SmsManager
import com.jarvis.assistant.service.JarvisNotificationListener
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Phone ke saare kaam yahan hain. Gemini inhe "tools" ki tarah bulata hai. Ye background thread se chalta hai. */
class PhoneActions(private val ctx: Context) {

    private val macros = MacroStore(ctx)

    @Volatile
    private var recording = false
    private val recorded = mutableListOf<JSONObject>()

    private val notRecorded = setOf(
        "read_screen", "read_notifications", "start_recording", "save_macro",
        "run_macro", "list_macros", "delete_macro", "battery_status", "current_time"
    )

    // ---------- Gemini ko batane ke liye tools ki list ----------

    private fun decl(
        name: String,
        desc: String,
        vararg props: Triple<String, String, String>,
        required: List<String> = props.map { it.first }
    ): JSONObject {
        val o = JSONObject().put("name", name).put("description", desc)
        if (props.isNotEmpty()) {
            val p = JSONObject()
            props.forEach { (n, type, d) ->
                p.put(n, JSONObject().put("type", type).put("description", d))
            }
            o.put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put("properties", p)
                    .put("required", JSONArray(required))
            )
        }
        return o
    }

    fun declarations(): JSONArray = JSONArray()
        .put(decl("open_app", "Phone me koi app kholo", Triple("name", "string", "App ka naam, jaise WhatsApp, YouTube, Camera")))
        .put(decl("call_contact", "Kisi ko call lagao", Triple("target", "string", "Contact ka naam ya phone number")))
        .put(
            decl(
                "send_sms", "SMS bhejo",
                Triple("target", "string", "Contact ka naam ya number"),
                Triple("message", "string", "SMS ka text")
            )
        )
        .put(
            decl(
                "whatsapp_message", "WhatsApp par message bhejo (poora bhej deta hai)",
                Triple("target", "string", "Contact ka naam ya number"),
                Triple("message", "string", "Message ka text")
            )
        )
        .put(
            decl(
                "set_alarm", "Alarm lagao",
                Triple("hour", "integer", "Ghanta, 24 ghante format (0-23)"),
                Triple("minute", "integer", "Minute (0-59)"),
                Triple("label", "string", "Alarm ka naam"),
                required = listOf("hour", "minute")
            )
        )
        .put(decl("set_timer", "Timer lagao", Triple("seconds", "integer", "Kitne second ka timer")))
        .put(decl("torch", "Torch / flashlight on ya off karo", Triple("on", "boolean", "true = on, false = off")))
        .put(decl("web_search", "Google par kuch search karo", Triple("query", "string", "Search ka text")))
        .put(decl("play_youtube", "YouTube par gaana ya video search karke kholo", Triple("query", "string", "Kya chalana hai")))
        .put(decl("open_url", "Browser me link kholo", Triple("url", "string", "Poora link")))
        .put(decl("set_volume", "Media volume set karo", Triple("percent", "integer", "0 se 100")))
        .put(decl("battery_status", "Battery kitni hai batao"))
        .put(decl("current_time", "Abhi ka time aur date batao"))
        .put(decl("read_notifications", "Phone ki naye notifications padho"))
        .put(decl("answer_call", "Aa rahi call utha lo"))
        .put(decl("end_call", "Chal rahi call kaat do"))
        .put(
            decl(
                "open_settings", "Phone ki Settings ka koi hissa kholo. Toggle badalna ho to iske baad read_screen aur tap_text use karo",
                Triple("panel", "string", "wifi, bluetooth, network, airplane, location, battery, display, sound, accessibility, apps, main")
            )
        )
        .put(decl("read_screen", "Abhi screen par kya likha hai wo padho. Koi bhi tap/type se pehle ise chalao taaki exact label pata chale"))
        .put(decl("tap_text", "Screen par us text/label wali cheez dabao", Triple("text", "string", "Button ya item ka text jaisa screen par dikhe")))
        .put(decl("type_text", "Khule hue input box me text likho (box ka purana text badal jata hai)", Triple("text", "string", "Kya likhna hai")))
        .put(decl("scroll", "Screen scroll karo", Triple("direction", "string", "down ya up")))
        .put(decl("press", "Phone ka button dabao", Triple("key", "string", "back, home, recents, notifications, quick_settings, lock, screenshot")))
        .put(decl("wait", "Kuch second ruko", Triple("seconds", "integer", "1 se 30")))
        .put(
            decl(
                "wait_for_text", "Screen par text aane ya jaane tak ruko",
                Triple("text", "string", "Wo text"),
                Triple("seconds", "integer", "Zyada se zyada kitna ruko"),
                Triple("disappear", "boolean", "true = text gayab hone tak ruko"),
                required = listOf("text")
            )
        )
        .put(decl("start_recording", "Macro record karna shuru karo. Iske baad jo kaam karoge wo yaad rahenge"))
        .put(
            decl(
                "save_macro", "Record kiye hue kaam ko macro ke naam se save karo",
                Triple("name", "string", "Macro ka naam"),
                Triple("steps_json", "string", "Optional. Khud ke steps JSON array me: [{\"tool\":\"open_app\",\"args\":{\"name\":\"X\"}}]. Variable ke liye {param} likho"),
                required = listOf("name")
            )
        )
        .put(
            decl(
                "run_macro", "Save kiya hua macro chalao",
                Triple("name", "string", "Macro ka naam"),
                Triple("params_json", "string", "Optional. Variables ki JSON, jaise {\"prompt\":\"billi\"}"),
                required = listOf("name")
            )
        )
        .put(decl("list_macros", "Saare saved macros ke naam batao"))
        .put(decl("delete_macro", "Macro hata do", Triple("name", "string", "Macro ka naam")))

    // ---------- Tool chalane wala ----------

    fun execute(name: String, args: JSONObject): String {
        val result = try {
            dispatch(name, args)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
        if (recording && name !in notRecorded && !isFail(result)) {
            synchronized(recorded) {
                recorded.add(JSONObject().put("tool", name).put("args", JSONObject(args.toString())))
            }
        }
        return result
    }

    private fun isFail(r: String) = r.startsWith("FAIL") || r.startsWith("Error")

    private fun dispatch(name: String, args: JSONObject): String = when (name) {
        "open_app" -> openApp(args.optString("name"))
        "call_contact" -> call(args.optString("target"))
        "send_sms" -> sms(args.optString("target"), args.optString("message"))
        "whatsapp_message" -> whatsapp(args.optString("target"), args.optString("message"))
        "set_alarm" -> alarm(args.getInt("hour"), args.getInt("minute"), args.optString("label", "Jarvis"))
        "set_timer" -> timer(args.getInt("seconds"))
        "torch" -> torch(args.optBoolean("on", true))
        "web_search" -> view("https://www.google.com/search?q=" + Uri.encode(args.optString("query")), "Search khol diya")
        "play_youtube" -> view("https://www.youtube.com/results?search_query=" + Uri.encode(args.optString("query")), "YouTube khol diya")
        "open_url" -> view(args.optString("url"), "Link khol diya")
        "set_volume" -> volume(args.getInt("percent"))
        "battery_status" -> battery()
        "current_time" -> SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        "read_notifications" -> JarvisNotificationListener.recent()
        "answer_call" -> answerCall()
        "end_call" -> endCall()
        "open_settings" -> openSettings(args.optString("panel"))
        "read_screen" -> Screen.read()
        "tap_text" -> Screen.tapText(args.optString("text"))
        "type_text" -> Screen.typeText(args.optString("text"))
        "scroll" -> Screen.scroll(args.optString("direction").lowercase() != "up")
        "press" -> Screen.press(args.optString("key"))
        "wait" -> {
            val s = args.optInt("seconds", 2).coerceIn(1, 30)
            Thread.sleep(s * 1000L)
            "$s second ruka"
        }
        "wait_for_text" -> {
            val ok = Screen.waitFor(
                args.optString("text"),
                args.optInt("seconds", 20),
                args.optBoolean("disappear", false)
            )
            if (ok) "Ho gaya" else "FAIL: Time khatam, wait poora nahi hua"
        }
        "start_recording" -> {
            synchronized(recorded) { recorded.clear() }
            recording = true
            "Recording shuru. Ab jo kaam karoge wo yaad rahega."
        }
        "save_macro" -> saveMacro(args.optString("name"), args.optString("steps_json"))
        "run_macro" -> runMacro(args.optString("name"), args.optString("params_json"))
        "list_macros" -> macros.names().let { if (it.isEmpty()) "Koi macro save nahi hai" else it.joinToString(", ") }
        "delete_macro" -> {
            val k = macros.find(args.optString("name"))
            if (k == null) "FAIL: Macro nahi mila" else {
                macros.delete(k)
                "Macro '$k' hata diya"
            }
        }
        else -> "FAIL: Ye kaam abhi nahi aata: $name"
    }

    // ---------- Macros ----------

    private fun saveMacro(name: String, stepsJson: String): String {
        if (name.isBlank()) return "FAIL: Macro ka naam batao"
        val steps = if (stepsJson.isNotBlank()) {
            JSONArray(stepsJson)
        } else {
            synchronized(recorded) { JSONArray(recorded.toList()) }
        }
        if (steps.length() == 0) return "FAIL: Koi step record nahi hua"
        macros.save(name, steps)
        recording = false
        synchronized(recorded) { recorded.clear() }
        return "Macro '${name.trim().lowercase()}' save ho gaya (${steps.length()} steps)"
    }

    private fun runMacro(name: String, paramsJson: String): String {
        val key = macros.find(name)
            ?: return "FAIL: '$name' naam ka macro nahi mila. Macros: ${macros.names().joinToString()}"
        val steps = macros.get(key) ?: return "FAIL: Macro kharab hai"
        val params = try {
            JSONObject(paramsJson.ifBlank { "{}" })
        } catch (e: Exception) {
            JSONObject()
        }
        val paramKeys = params.keys().asSequence().toList()

        for (i in 0 until steps.length()) {
            val st = steps.getJSONObject(i)
            val tool = st.getString("tool")
            val args = JSONObject((st.optJSONObject("args") ?: JSONObject()).toString())
            val optional = args.optBoolean("_optional", false)
            args.remove("_optional")

            for (k in args.keys().asSequence().toList()) {
                val v = args.get(k)
                if (v is String) {
                    var s: String = v
                    for (p in paramKeys) s = s.replace("{$p}", params.optString(p))
                    args.put(k, s)
                }
            }
            val r = try {
                dispatch(tool, args)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            if (isFail(r) && !optional) return "Macro '$key' step ${i + 1} ($tool) par ruk gaya: $r"
        }
        return "Macro '$key' poora ho gaya"
    }

    // ---------- Helpers ----------

    private fun launch(intent: Intent) {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun has(permission: String): Boolean =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun view(url: String, ok: String): String {
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return ok
    }

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val q = name.lowercase().trim()
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "FAIL: $name naam ka app nahi mila"
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "FAIL: $name khul nahi paya"
        launch(intent)
        return "${match.loadLabel(pm)} khol diya"
    }

    private fun resolveNumber(target: String): String? {
        val t = target.trim()
        if (t.length >= 6 && t.none { it.isLetter() }) return t.filter { it.isDigit() || it == '+' }
        if (!has(Manifest.permission.READ_CONTACTS)) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$t%"),
            null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    private fun call(target: String): String {
        val number = resolveNumber(target)
            ?: return "FAIL: $target ka number nahi mila (contacts permission check karo)"
        val uri = Uri.parse("tel:" + Uri.encode(number))
        return if (has(Manifest.permission.CALL_PHONE)) {
            launch(Intent(Intent.ACTION_CALL, uri))
            "$target ko call laga diya"
        } else {
            launch(Intent(Intent.ACTION_DIAL, uri))
            "Dialer khol diya, call button dabao"
        }
    }

    @Suppress("DEPRECATION")
    private fun sms(target: String, message: String): String {
        if (!has(Manifest.permission.SEND_SMS)) return "FAIL: SMS ki permission nahi hai"
        val number = resolveNumber(target) ?: return "FAIL: $target ka number nahi mila"
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        sm.sendMultipartTextMessage(number, null, sm.divideMessage(message), null, null)
        return "SMS bhej diya"
    }

    private fun whatsapp(target: String, message: String): String {
        val number = resolveNumber(target) ?: return "FAIL: $target ka number nahi mila"
        var digits = number.filter { it.isDigit() }
        if (digits.length == 10) digits = "91$digits" // India ka code, apne desh ke hisaab se badlo
        val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(message))
        try {
            launch(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
        } catch (e: ActivityNotFoundException) {
            launch(Intent(Intent.ACTION_VIEW, uri))
        }
        if (!Screen.enabled()) return "WhatsApp khol diya, message likha hai. Send aap dabao (Accessibility ON nahi hai)"
        if (!Screen.waitFor("Send", 10, false)) return "FAIL: WhatsApp me Send button nahi dikha"
        Thread.sleep(600)
        val r = Screen.tapText("Send")
        return if (isFail(r)) "FAIL: Message likha hai par Send nahi dab paya" else "WhatsApp message bhej diya"
    }

    private fun alarm(hour: Int, minute: Int, label: String): String {
        launch(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return "Alarm %02d:%02d par laga diya".format(hour, minute)
    }

    private fun timer(seconds: Int): String {
        launch(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return "$seconds second ka timer laga diya"
    }

    private fun torch(on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "FAIL: Is phone me flash nahi mila"
        cm.setTorchMode(id, on)
        return if (on) "Torch on" else "Torch off"
    }

    private fun volume(percent: Int): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val p = percent.coerceIn(0, 100)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, max * p / 100, AudioManager.FLAG_SHOW_UI)
        return "Volume $p% kar diya"
    }

    private fun battery(): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery $pct% hai" + if (bm.isCharging) ", charging chal rahi hai" else ""
    }

    @SuppressLint("MissingPermission")
    private fun answerCall(): String {
        if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) return "FAIL: Phone ki permission do (Settings -> Apps -> Jarvis -> Permissions)"
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.acceptRingingCall()
        return "Call utha liya"
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun endCall(): String {
        if (Build.VERSION.SDK_INT < 28) return "FAIL: Is Android version me call kaatna nahi ho sakta"
        if (!has(Manifest.permission.ANSWER_PHONE_CALLS)) return "FAIL: Phone ki permission do (Settings -> Apps -> Jarvis -> Permissions)"
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return if (tm.endCall()) "Call kaat di" else "FAIL: Koi call chal nahi rahi"
    }

    private fun openSettings(panel: String): String {
        val action = when (panel.lowercase().trim()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "network" -> Settings.ACTION_WIRELESS_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "main" -> Settings.ACTION_SETTINGS
            else -> return "FAIL: '$panel' settings pata nahi"
        }
        launch(Intent(action))
        return "$panel settings khol di"
    }
}
