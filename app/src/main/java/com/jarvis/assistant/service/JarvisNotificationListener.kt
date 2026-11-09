package com.jarvis.assistant.service

import android.app.Notification
import android.service.notification.NotificationListenerService

class JarvisNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    companion object {
        @Volatile
        var instance: JarvisNotificationListener? = null

        fun recent(max: Int = 10): String {
            val s = instance
                ?: return "FAIL: Notification access ON nahi hai. Jarvis Settings me button dabao."
            val items = try {
                s.activeNotifications?.toList().orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
            val pm = s.packageManager
            val lines = items
                .filter { it.packageName != s.packageName }
                .sortedByDescending { it.postTime }
                .take(max)
                .mapNotNull { sbn ->
                    val ex = sbn.notification.extras
                    val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                    val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                    if (title.isEmpty() && text.isEmpty()) return@mapNotNull null
                    val app = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                    } catch (e: Exception) {
                        sbn.packageName
                    }
                    "$app: $title - $text"
                }
            return if (lines.isEmpty()) "Koi notification nahi hai" else lines.joinToString("\n")
        }
    }
}
