package com.jarvis.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00E5FF),
            background = Color(0xFF070B1A),
            surface = Color(0xFF101733),
            onSurface = Color(0xFFE6F1FF),
            onBackground = Color(0xFFE6F1FF)
        ),
        content = content
    )
}

@Composable
fun JarvisScreen(vm: JarvisViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "JARVIS",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    letterSpacing = 6.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }

            AnimeCharacter(
                state = vm.state,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .align(Alignment.CenterHorizontally)
            )

            Text(
                text = when (vm.state) {
                    CharState.IDLE -> "Taiyaar hun"
                    CharState.LISTENING -> if (vm.partial.isNotEmpty()) vm.partial else "Sun rahi hun..."
                    CharState.THINKING -> "Soch rahi hun..."
                    CharState.SPEAKING -> "Bol rahi hun..."
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
                fontSize = 14.sp
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(vm.messages) { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (m.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surface
                        ) {
                            Text(m.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type karo...") },
                    singleLine = true
                )
                IconButton(onClick = {
                    vm.submit(input)
                    input = ""
                }) {
                    Icon(Icons.Filled.Send, contentDescription = "Bhejo")
                }
                FilledIconButton(onClick = { vm.onMic() }, modifier = Modifier.size(52.dp)) {
                    Icon(
                        if (vm.state == CharState.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = "Mic"
                    )
                }
            }
        }
    }

    if (showSettings) {
        var key by remember { mutableStateOf(vm.apiKey) }
        var model by remember { mutableStateOf(vm.model) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column {
                    Text("Gemini API key (aistudio.google.com se free milti hai)", fontSize = 12.sp)
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Model name", fontSize = 12.sp)
                    OutlinedTextField(value = model, onValueChange = { model = it }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.saveSettings(key, model)
                    showSettings = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("Cancel") }
            }
        )
    }
}

/** Anime-style ladki ka character, sab kuch code se bana hai (koi image nahi). */
@Composable
fun AnimeCharacter(state: CharState, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "char")
    val pulse by inf.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val bob by inf.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val mouthAnim by inf.animateFloat(
        initialValue = 0.15f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(160), RepeatMode.Reverse),
        label = "mouth"
    )

    var blink by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2500L + Random.nextLong(2500L))
            blink = 0.08f
            delay(110)
            blink = 1f
        }
    }

    val mouthOpen = if (state == CharState.SPEAKING) mouthAnim else 0f
    val glow = when (state) {
        CharState.IDLE -> Color(0xFF4FC3F7)
        CharState.LISTENING -> Color(0xFF00E5FF)
        CharState.THINKING -> Color(0xFFB388FF)
        CharState.SPEAKING -> Color(0xFF69F0AE)
    }
    val lookX = if (state == CharState.THINKING) 1.6f else 0f

    val skin = Color(0xFFFFE3D1)
    val hair = Color(0xFF2B2F77)
    val hairDark = Color(0xFF1A1D4F)
    val ink = Color(0xFF111133)
    val lip = Color(0xFF8B2E3C)

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val u = size.width / 100f
        val cx = size.width / 2f
        val cy = size.height * 0.46f + bob * 1.5f * u

        // glow
        val gr = size.width * 0.5f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.5f), Color.Transparent),
                center = Offset(cx, cy),
                radius = gr
            ),
            radius = gr,
            center = Offset(cx, cy)
        )
        if (state == CharState.LISTENING) {
            drawCircle(glow, radius = 46f * u * pulse, center = Offset(cx, cy), style = Stroke(0.8f * u))
        }

        // peeche ke baal
        drawOval(hairDark, Offset(cx - 34f * u, cy - 42f * u), Size(68f * u, 84f * u))
        drawRoundRect(hairDark, Offset(cx - 38f * u, cy - 12f * u), Size(16f * u, 58f * u), CornerRadius(8f * u))
        drawRoundRect(hairDark, Offset(cx + 22f * u, cy - 12f * u), Size(16f * u, 58f * u), CornerRadius(8f * u))

        // body aur gardan
        drawRoundRect(Color(0xFF263159), Offset(cx - 30f * u, cy + 34f * u), Size(60f * u, 40f * u), CornerRadius(16f * u))
        drawRect(skin, Offset(cx - 6f * u, cy + 24f * u), Size(12f * u, 14f * u))
        drawLine(glow, Offset(cx - 14f * u, cy + 36f * u), Offset(cx, cy + 44f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
        drawLine(glow, Offset(cx + 14f * u, cy + 36f * u), Offset(cx, cy + 44f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)

        // chehra
        drawOval(skin, Offset(cx - 24f * u, cy - 30f * u), Size(48f * u, 58f * u))

        // headset
        drawCircle(glow, 4f * u, Offset(cx - 25f * u, cy + 2f * u))
        drawCircle(glow, 4f * u, Offset(cx + 25f * u, cy + 2f * u))

        // gaal ki laali
        drawOval(Color(0x55FF8A80), Offset(cx - 20f * u, cy + 10f * u), Size(9f * u, 4f * u))
        drawOval(Color(0x55FF8A80), Offset(cx + 11f * u, cy + 10f * u), Size(9f * u, 4f * u))

        // aankhein
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * 11f * u
            val ey = cy + 2f * u
            val eh = 16f * u * blink
            drawOval(Color.White, Offset(ex - 7f * u, ey - eh / 2f), Size(14f * u, eh))
            if (blink > 0.5f) {
                val ix = ex + lookX * u
                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(glow, Color(0xFF0D47A1)),
                        startY = ey - 7f * u,
                        endY = ey + 7f * u
                    ),
                    topLeft = Offset(ix - 5.5f * u, ey - 7f * u * blink),
                    size = Size(11f * u, 14f * u * blink)
                )
                drawOval(ink, Offset(ix - 2.5f * u, ey - 4f * u * blink), Size(5f * u, 8f * u * blink))
                drawCircle(Color.White, 1.8f * u, Offset(ix - 2f * u, ey - 3f * u * blink))
            }
            drawArc(
                color = ink,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(ex - 7.5f * u, ey - 8.5f * u * blink),
                size = Size(15f * u, 17f * u * blink),
                style = Stroke(1.6f * u, cap = StrokeCap.Round)
            )
        }

        // naak
        drawLine(Color(0x66B5836B), Offset(cx, cy + 9f * u), Offset(cx - 1f * u, cy + 12f * u), strokeWidth = 0.8f * u, cap = StrokeCap.Round)

        // munh
        if (mouthOpen > 0.05f) {
            drawOval(lip, Offset(cx - 4f * u, cy + 16f * u), Size(8f * u, (2f + 6f * mouthOpen) * u))
        } else {
            drawArc(
                color = lip,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - 5f * u, cy + 14f * u),
                size = Size(10f * u, 6f * u),
                style = Stroke(1.2f * u, cap = StrokeCap.Round)
            )
        }

        // bangs (sabse upar)
        val bangs = Path().apply {
            moveTo(cx - 28f * u, cy - 6f * u)
            quadraticBezierTo(cx - 30f * u, cy - 44f * u, cx, cy - 44f * u)
            quadraticBezierTo(cx + 30f * u, cy - 44f * u, cx + 28f * u, cy - 6f * u)
            quadraticBezierTo(cx + 20f * u, cy - 22f * u, cx + 12f * u, cy - 12f * u)
            quadraticBezierTo(cx + 8f * u, cy - 24f * u, cx, cy - 10f * u)
            quadraticBezierTo(cx - 8f * u, cy - 24f * u, cx - 12f * u, cy - 12f * u)
            quadraticBezierTo(cx - 20f * u, cy - 22f * u, cx - 28f * u, cy - 6f * u)
            close()
        }
        drawPath(bangs, hair)
        // baalon ka chamak
        drawArc(
            color = Color(0x44FFFFFF),
            startAngle = 200f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(cx - 22f * u, cy - 40f * u),
            size = Size(44f * u, 30f * u),
            style = Stroke(1.5f * u, cap = StrokeCap.Round)
        )
    }
}
