package com.jarvis.assistant.actions

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.service.JarvisAccessibilityService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Accessibility se screen padhna aur chalana. Ye sab background thread se chalao. */
object Screen {

    const val NOT_ON = "FAIL: Accessibility ON nahi hai. Jarvis Settings me 'Accessibility ON karo' dabao."

    private val svc get() = JarvisAccessibilityService.instance

    fun enabled() = svc != null

    // ---------- Padhna ----------

    fun read(): String {
        val root = svc?.rootInActiveWindow ?: return NOT_ON
        val out = ArrayList<String>()
        collect(root, out, 0)
        return if (out.isEmpty()) "Screen par koi text nahi mila" else out.take(150).joinToString("\n")
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (n == null || out.size > 200 || depth > 40) return
        if (n.isVisibleToUser) {
            val t = n.text?.toString()?.trim().orEmpty()
            val d = n.contentDescription?.toString()?.trim().orEmpty()
            val label = if (t.isNotEmpty()) t else d
            val tags = ArrayList<String>()
            if (n.isClickable) tags.add("tap")
            if (n.isEditable) tags.add("input")
            if (n.isScrollable) tags.add("scroll")
            if (label.isNotEmpty()) {
                out.add(if (tags.isEmpty()) label.take(120) else label.take(120) + " [" + tags.joinToString(",") + "]")
            } else if (n.isEditable) {
                out.add("(khaali input box) [input]")
            }
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    // ---------- Dhoondhna ----------

    private fun find(query: String): AccessibilityNodeInfo? {
        val root = svc?.rootInActiveWindow ?: return null
        val key = query.trim().lowercase()
        if (key.isEmpty()) return null
        var partial: AccessibilityNodeInfo? = null

        fun walk(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (n == null || depth > 40) return null
            if (n.isVisibleToUser) {
                val labels = listOfNotNull(n.text?.toString(), n.contentDescription?.toString())
                    .map { it.trim().lowercase() }
                if (labels.any { it == key }) return n
                if (partial == null && labels.any { it.contains(key) }) partial = n
            }
            for (i in 0 until n.childCount) {
                val r = walk(n.getChild(i), depth + 1)
                if (r != null) return r
            }
            return null
        }
        return walk(root, 0) ?: partial
    }

    private fun firstEditable(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val r = firstEditable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    private fun firstScrollable(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        if (n.isScrollable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val r = firstScrollable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    // ---------- Kaam ----------

    fun tapText(text: String): String {
        if (svc == null) return NOT_ON
        val n = find(text) ?: return "FAIL: '$text' screen par nahi mila"
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "'$text' dabaya"
        val r = Rect()
        n.getBoundsInScreen(r)
        return if (tapXY(r.centerX().toFloat(), r.centerY().toFloat())) "'$text' dabaya"
        else "FAIL: '$text' dab nahi paya"
    }

    fun tapXY(x: Float, y: Float): Boolean {
        val s = svc ?: return false
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60))
            .build()
        val latch = CountDownLatch(1)
        var ok = false
        val accepted = s.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                ok = true
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                latch.countDown()
            }
        }, null)
        if (!accepted) return false
        latch.await(2, TimeUnit.SECONDS)
        return ok
    }

    fun typeText(text: String): String {
        val root = svc?.rootInActiveWindow ?: return NOT_ON
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: firstEditable(root)
            ?: return "FAIL: Koi input box nahi mila"
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Likh diya"
        else "FAIL: Likh nahi paya"
    }

    fun scroll(down: Boolean): String {
        val root = svc?.rootInActiveWindow ?: return NOT_ON
        val n = firstScrollable(root) ?: return "FAIL: Scroll hone wali cheez nahi mili"
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return if (n.performAction(action)) "Scroll kar diya" else "FAIL: Aur scroll nahi ho sakta"
    }

    fun press(key: String): String {
        val s = svc ?: return NOT_ON
        val action = when (key.lowercase().trim()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "lock" ->
                if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                else return "FAIL: Is Android version me lock nahi ho sakta"
            "screenshot" ->
                if (Build.VERSION.SDK_INT >= 30) AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
                else return "FAIL: Is Android version me screenshot nahi ho sakta"
            else -> return "FAIL: '$key' button pata nahi"
        }
        return if (s.performGlobalAction(action)) "$key ho gaya" else "FAIL: $key nahi ho paya"
    }

    /** text screen par aaye (disappear=false) ya gayab ho (disappear=true), tab tak ruko. */
    fun waitFor(text: String, seconds: Int, disappear: Boolean): Boolean {
        val end = System.currentTimeMillis() + seconds.coerceIn(1, 120) * 1000L
        while (System.currentTimeMillis() < end) {
            val present = find(text) != null
            if (present != disappear) return true
            Thread.sleep(500)
        }
        return false
    }
}
