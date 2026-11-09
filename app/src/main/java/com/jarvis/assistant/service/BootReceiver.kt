package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.Prefs

/** Phone chalu hone par, agar Floating pehle ON tha, to Jarvis wapas shuru karo. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED &&
            a != "android.intent.action.QUICKBOOT_POWERON" &&
            a != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (!Prefs(context).floatingWasOn) return
        if (!Settings.canDrawOverlays(context)) return

        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+ boot par mic service chalne nahi deta, isliye tap wala notification
            askToStart(context)
        } else {
            try {
                ContextCompat.startForegroundService(context, Intent(context, JarvisService::class.java))
            } catch (e: Exception) {
                askToStart(context)
            }
        }
    }

    private fun askToStart(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("jarvis_boot", "Jarvis shuru", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getForegroundService(
            context, 2, Intent(context, JarvisService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, "jarvis_boot")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis")
            .setContentText("Shuru karne ke liye yahan dabao")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(7, n)
    }
}
