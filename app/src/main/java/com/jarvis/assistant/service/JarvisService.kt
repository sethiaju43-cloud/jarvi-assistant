package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.core.AssistantCore
import com.jarvis.assistant.ui.CharState
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.OverlayCharacter
import com.jarvis.assistant.voice.WakeWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class JarvisService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private lateinit var core: AssistantCore
    private var overlay: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var owner: OverlayOwner? = null
    private var wake: WakeWord? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        core = (application as JarvisApp).core
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotif("Jarvis chal raha hai"), type)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        running = true
        addOverlay()
        setupWake()
        watchStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    // ---------- Floating character ----------

    private fun addOverlay() {
        val o = OverlayOwner()
        owner = o

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                JarvisTheme {
                    OverlayCharacter(
                        core = core,
                        onDrag = { dx, dy -> moveOverlay(dx, dy) },
                        onOpenApp = {
                            startActivity(
                                Intent(this@JarvisService, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                        }
                    )
                }
            }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 400
        }

        overlay = view
        overlayParams = lp
        wm.addView(view, lp)
        o.start()
    }

    private fun moveOverlay(dx: Float, dy: Float) {
        val v = overlay ?: return
        val lp = overlayParams ?: return
        lp.x += dx.toInt()
        lp.y += dy.toInt()
        wm.updateViewLayout(v, lp)
    }

    // ---------- Wake word ----------

    private fun setupWake() {
        val w = WakeWord(this)
        wake = w
        scope.launch {
            try {
                w.prepare { core.wakeStatus = it }
                core.wakeStatus = "Taiyaar. 'Jarvis' bolo."
            } catch (e: Throwable) {
                core.wakeStatus = "Wake word nahi chala (${e.message}). Character par tap karke bolo."
                return@launch
            }

            snapshotFlow { core.state }.collectLatest { st ->
                if (st == CharState.IDLE) {
                    delay(700) // apni awaaz ki gunj khatam hone do
                    w.start {
                        w.stop()
                        core.onMic()
                    }
                } else {
                    w.stop()
                }
            }
        }
    }

    private fun watchStatus() {
        scope.launch {
            snapshotFlow { core.wakeStatus }.collect { s ->
                if (s.isNotEmpty()) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIF_ID, buildNotif(s))
                }
            }
        }
    }

    // ---------- Notification ----------

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Jarvis", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, JarvisService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Band karo", stop).build())
            .build()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        wake?.release()
        wake = null
        core.cancel()
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        owner?.destroy()
        owner = null
        super.onDestroy()
    }

    /** ComposeView ko service me chalane ke liye nakli lifecycle. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun start() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    companion object {
        const val ACTION_STOP = "com.jarvis.assistant.STOP"
        private const val CHANNEL_ID = "jarvis_fg"
        private const val NOTIF_ID = 1

        var running by mutableStateOf(false)
    }
}
