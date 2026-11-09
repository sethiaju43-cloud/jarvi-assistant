package com.jarvis.assistant

import android.app.Application
import com.jarvis.assistant.core.AssistantCore

class JarvisApp : Application() {
    val core: AssistantCore by lazy { AssistantCore(this) }
}
