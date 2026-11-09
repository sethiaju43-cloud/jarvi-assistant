package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.assistant.service.JarvisService
import com.jarvis.assistant.ui.JarvisScreen
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.JarvisViewModel

class MainActivity : ComponentActivity() {

    private val vm: JarvisViewModel by viewModels()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(perms.toTypedArray())

        setContent {
            JarvisTheme {
                Box(Modifier.fillMaxSize()) {
                    JarvisScreen(vm)
                    FloatingChip(
                        on = JarvisService.running,
                        onClick = { toggleFloating() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 14.dp)
                    )
                }
            }
        }
    }

    private fun toggleFloating() {
        if (JarvisService.running) {
            stopService(Intent(this, JarvisService::class.java))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "'Display over other apps' ko Jarvis ke liye ON karo", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Pehle mic ki permission do, phir dobara dabao", Toast.LENGTH_LONG).show()
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, JarvisService::class.java))
    }
}

@androidx.compose.runtime.Composable
private fun FloatingChip(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) Color(0xFF00E5FF) else Color(0xFF1B2547))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (on) "Floating: ON" else "Floating: OFF",
            color = if (on) Color(0xFF001018) else Color(0xFFE6F1FF),
            fontSize = 12.sp
        )
    }
}
