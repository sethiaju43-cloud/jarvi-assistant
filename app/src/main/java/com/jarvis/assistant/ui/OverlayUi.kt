package com.jarvis.assistant.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.AssistantCore

/** Dusre apps ke upar tairne wala character. */
@Composable
fun OverlayCharacter(
    core: AssistantCore,
    onDrag: (Float, Float) -> Unit,
    onOpenApp: () -> Unit
) {
    val bubble = when (core.state) {
        CharState.IDLE -> ""
        CharState.LISTENING -> if (core.partial.isNotEmpty()) core.partial else "Sun rahi hun..."
        CharState.THINKING -> "Soch rahi hun..."
        CharState.SPEAKING -> core.lastReply
    }

    Column(
        modifier = Modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bubble.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xE6101733)
            ) {
                Text(
                    text = bubble,
                    color = Color(0xFFE6F1FF),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        AnimeCharacter(
            state = core.state,
            modifier = Modifier
                .size(120.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onDrag(drag.x, drag.y)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { core.onMic() },
                        onLongPress = { onOpenApp() }
                    )
                }
        )
    }
}
