package com.jarvis.assistant.service

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private class Fly(var x: Float, var y: Float, val phase: Float, val speed: Float, val size: Float)

/** Kaala background, teal glow aur upar udti titliyan. */
class JarvisWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = JarvisEngine()

    private inner class JarvisEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val rnd = Random(7)
        private val flies = List(14) {
            Fly(
                rnd.nextFloat(), rnd.nextFloat(),
                rnd.nextFloat() * 6.28f,
                0.0006f + rnd.nextFloat() * 0.0012f,
                5f + rnd.nextFloat() * 7f
            )
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val wing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 29, 233, 182) }
        private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2f
        }
        private val rect = RectF()
        private var visible = false
        private var t = 0f

        private val frame = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, 33)
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(frame)
            if (v) handler.post(frame)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frame)
            super.onDestroy()
        }

        private fun draw() {
            val holder = surfaceHolder
            val c: Canvas = try {
                holder.lockCanvas()
            } catch (e: Exception) {
                null
            } ?: return
            try {
                t += 0.033f
                val w = c.width.toFloat()
                val h = c.height.toFloat()
                val d = resources.displayMetrics.density

                c.drawColor(Color.parseColor("#03090A"))

                val pulse = 0.85f + 0.15f * sin(t * 1.5f)
                glow.shader = RadialGradient(
                    w / 2f, h * 0.82f, w * 0.85f * pulse,
                    intArrayOf(Color.argb(136, 29, 233, 182), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w, h, glow)

                for (f in flies) {
                    f.y -= f.speed
                    if (f.y < -0.05f) {
                        f.y = 1.05f
                        f.x = rnd.nextFloat()
                    }
                    val px = (f.x + 0.03f * sin(t + f.phase)) * w
                    val py = f.y * h
                    val flap = 0.25f + 0.75f * abs(sin(t * 9f + f.phase))
                    butterfly(c, px, py, f.size * d, flap)
                }
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }

        private fun butterfly(c: Canvas, x: Float, y: Float, s: Float, flap: Float) {
            val ww = s * flap
            rect.set(x - ww, y - s * 0.6f, x, y + s * 0.4f)
            c.drawOval(rect, wing)
            rect.set(x, y - s * 0.6f, x + ww, y + s * 0.4f)
            c.drawOval(rect, wing)
            c.drawLine(x, y - s * 0.5f, x, y + s * 0.5f, body)
        }
    }
}
