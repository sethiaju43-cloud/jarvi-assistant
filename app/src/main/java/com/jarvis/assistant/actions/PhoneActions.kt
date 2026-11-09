package com.jarvis.assistant.actions

import android.Manifest
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
import android.telephony.SmsManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Phone ke saare kaam yahan hain. Gemini inhe "tools" ki tarah bulata hai. */
class PhoneActions(private val ctx: Context) {

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

    // ---------- Tool chalane wala ----------

    fun execute(name: String, args: JSONObject): String {
        return try {
            when (name) {
                "open_app" -> openApp(args.optString("name"))
                "call_contact" -> call(args.optString("target"))
                "send_sms" -> sms(args.optString("target"), args.optString("message"))
                "set_alarm" -> alarm(args.getInt("hour"), args.getInt("minute"), args.optString("label", "Jarvis"))
                "set_timer" -> timer(args.getInt("seconds"))
                "torch" -> torch(args.optBoolean("on", true))
                "web_search" -> view("https://www.google.com/search?q=" + Uri.encode(args.optString("query")), "Search khol diya")
                "play_youtube" -> view("https://www.youtube.com/results?search_query=" + Uri.encode(args.optString("query")), "YouTube khol diya")
                "open_url" -> view(args.optString("url"), "Link khol diya")
                "set_volume" -> volume(args.getInt("percent"))
                "battery_status" -> battery()
                "current_time" -> SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
                else -> "Ye kaam abhi nahi aata: $name"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
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
            ?: return "$name naam ka app nahi mila"
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "$name khul nahi paya"
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
        val number = resolveNumber(target) ?: return "$target ka number nahi mila (contacts permission check karo)"
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
        if (!has(Manifest.permission.SEND_SMS)) return "SMS ki permission nahi hai"
        val number = resolveNumber(target) ?: return "$target ka number nahi mila"
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        sm.sendMultipartTextMessage(number, null, sm.divideMessage(message), null, null)
        return "SMS bhej diya"
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
        } ?: return "Is phone me flash nahi mila"
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
}
