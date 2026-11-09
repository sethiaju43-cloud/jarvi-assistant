package com.jarvis.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private fun DrawScope.butterfly(c: Offset, s: Float, flap: Float, color: Color) {
    val w = s * flap
    drawOval(color.copy(alpha = 0.85f), Offset(c.x - w, c.y - s * 0.6f), Size(w, s))
    drawOval(color.copy(alpha = 0.85f), Offset(c.x, c.y - s * 0.6f), Size(w, s))
    drawOval(color.copy(alpha = 0.55f), Offset(c.x - w * 0.7f, c.y + s * 0.1f), Size(w * 0.7f, s * 0.6f))
    drawOval(color.copy(alpha = 0.55f), Offset(c.x, c.y + s * 0.1f), Size(w * 0.7f, s * 0.6f))
    drawLine(Color.White, Offset(c.x, c.y - s * 0.5f), Offset(c.x, c.y + s * 0.6f), strokeWidth = s * 0.12f)
}

/** Teal glow wali anime character: glowing book aur titliyan. Sab kuch code se bana hai. */
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
    val orbit by inf.animateFloat(
        initialValue = 0f, targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit"
    )
    val flap by inf.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(240), RepeatMode.Reverse),
        label = "flap"
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
        CharState.IDLE -> Color(0xFF1DE9B6)
        CharState.LISTENING -> Color(0xFF00E5FF)
        CharState.THINKING -> Color(0xFFB388FF)
        CharState.SPEAKING -> Color(0xFF64FFDA)
    }
    val lookX = if (state == CharState.THINKING) 1.6f else 0f

    val skin = Color(0xFFE3F3EF)
    val hair = Color(0xFF16363A)
    val hairDark = Color(0xFF0A1B1E)
    val shirt = Color(0xFF0E5C5A)
    val ink = Color(0xFF07191B)
    val lip = Color(0xFF7A3A47)

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val u = size.width / 112f
        val cx = size.width / 2f
        val cy = 48f * u + bob * 1.5f * u

        // peeche ka glow
        val gr = size.width * 0.55f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(cx, cy),
                radius = gr
            ),
            radius = gr,
            center = Offset(cx, cy)
        )
        if (state == CharState.LISTENING) {
            drawCircle(glow, radius = 50f * u * pulse, center = Offset(cx, cy), style = Stroke(0.8f * u))
        }

        // peeche ke baal
        drawOval(hairDark, Offset(cx - 34f * u, cy - 42f * u), Size(68f * u, 84f * u))
        drawRoundRect(hairDark, Offset(cx - 38f * u, cy - 12f * u), Size(16f * u, 50f * u), CornerRadius(8f * u))
        drawRoundRect(hairDark, Offset(cx + 22f * u, cy - 12f * u), Size(16f * u, 50f * u), CornerRadius(8f * u))

        // body aur gardan
        drawRoundRect(shirt, Offset(cx - 30f * u, cy + 34f * u), Size(60f * u, 30f * u), CornerRadius(16f * u))
        drawRect(skin, Offset(cx - 6f * u, cy + 24f * u), Size(12f * u, 14f * u))
        drawLine(glow, Offset(cx - 14f * u, cy + 36f * u), Offset(cx, cy + 44f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
        drawLine(glow, Offset(cx + 14f * u, cy + 36f * u), Offset(cx, cy + 44f * u), strokeWidth = 1.2f * u, cap = StrokeCap.Round)

        // glowing book
        val by = cy + 46f * u
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.7f), Color.Transparent),
                center = Offset(cx, by + 4f * u),
                radius = 26f * u
            ),
            radius = 26f * u,
            center = Offset(cx, by + 4f * u)
        )
        val left = Path().apply {
            moveTo(cx - 1f * u, by)
            lineTo(cx - 24f * u, by - 5f * u)
            lineTo(cx - 24f * u, by + 11f * u)
            lineTo(cx - 1f * u, by + 16f * u)
            close()
        }
        val right = Path().apply {
            moveTo(cx + 1f * u, by)
            lineTo(cx + 24f * u, by - 5f * u)
            lineTo(cx + 24f * u, by + 11f * u)
            lineTo(cx + 1f * u, by + 16f * u)
            close()
        }
        val pageBrush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.9f), glow.copy(alpha = 0.5f)),
            startY = by - 5f * u,
            endY = by + 16f * u
        )
        drawPath(left, pageBrush)
        drawPath(right, pageBrush)

        // chehra
        drawOval(skin, Offset(cx - 24f * u, cy - 30f * u), Size(48f * u, 58f * u))

        // headset
        drawCircle(glow, 4f * u, Offset(cx - 25f * u, cy + 2f * u))
        drawCircle(glow, 4f * u, Offset(cx + 25f * u, cy + 2f * u))

        // gaal ki halki laali
        drawOval(Color(0x44FF8A80), Offset(cx - 20f * u, cy + 10f * u), Size(9f * u, 4f * u))
        drawOval(Color(0x44FF8A80), Offset(cx + 11f * u, cy + 10f * u), Size(9f * u, 4f * u))

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
                        colors = listOf(glow, Color(0xFF00695C)),
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
        drawLine(Color(0x668AA8A2), Offset(cx, cy + 9f * u), Offset(cx - 1f * u, cy + 12f * u), strokeWidth = 0.8f * u, cap = StrokeCap.Round)

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
        drawArc(
            color = glow.copy(alpha = 0.35f),
            startAngle = 200f,
            sweepAngle = 60f,
            useCenter = false,
            topLeft = Offset(cx - 22f * u, cy - 40f * u),
            size = Size(44f * u, 30f * u),
            style = Stroke(1.5f * u, cap = StrokeCap.Round)
        )

        // titliyan
        for (i in 0 until 3) {
            val a = orbit + i * 2.1f
            val bx = cx + (36f + i * 5f) * u + cos(a) * 6f * u
            val byy = cy - 14f * u + i * 16f * u - sin(a) * 5f * u
            butterfly(Offset(bx, byy), 4.5f * u, flap, glow)
        }
    }
}
