package com.jarvis.assistant

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.jarvis.assistant.ui.JarvisScreen
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.JarvisViewModel

class MainActivity : ComponentActivity() {

    private val vm: JarvisViewModel by viewModels()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.SEND_SMS
            )
        )
        setContent {
            JarvisTheme { JarvisScreen(vm) }
        }
    }
}
