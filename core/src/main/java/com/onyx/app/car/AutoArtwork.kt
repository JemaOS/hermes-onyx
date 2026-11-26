package com.onyx.app.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/** Images nettes dessinées en code, partagées par les aperçus Compose et les CarIcon d'Android Auto. */
object AutoArtwork {
    const val CARD_WIDTH = 292
    const val CARD_HEIGHT = 164

    private const val GRAPHITE_TOP = 0xFF3B4453.toInt()
    private const val GRAPHITE_BOTTOM = 0xFF141A26.toInt()
    private const val MIST = 0xFFDCE3EE.toInt()
    private const val GOLD = 0xFFE8B04B.toInt()
    private const val RED = 0xFFFF3B2F.toInt()
    private const val DASH_TOP = 0xFF1C1D22.toInt()
    private const val DASH_BOTTOM = 0xFF050506.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun startCard(start: AutoStart, look: AutoCardLook, width: Int = CARD_WIDTH, height: Int = CARD_HEIGHT): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.shader = null
        val light = if (start == AutoStart.ASK) 0xFF2E8BF5.toInt() else 0xFF38C29E.toInt()
        when (look) {
            AutoCardLook.COLOR -> {
                val deep = if (start == AutoStart.ASK) 0xFF0A2E66.toInt() else 0xFF0A4A3C.toInt()
                val bright = if (start == AutoStart.ASK) 0xFF3D9BFF.toInt() else 0xFF3ECFA6.toInt()
                canvas.gradient(width, height, deep, bright, diagonal = true)
                drawStartSymbol(canvas, start, width, height, Color.WHITE)
            }
            AutoCardLook.GLOW -> {
                canvas.gradient(width, height, 0xFF07090D.toInt(), 0xFF161B26.toInt(), diagonal = true)
                canvas.glow(width * .73f, height * .44f, height * .75f, light)
                drawStartSymbol(canvas, start, width, height, Color.WHITE)
            }
            AutoCardLook.ORB -> {
                canvas.gradient(width, height, 0xFF06080B.toInt(), 0xFF11151D.toInt(), diagonal = true)
                val x = width * .73f
                val y = height * .44f
                val radius = height * .30f
                canvas.glow(x, y, radius * 1.55f, light, alpha = .34f)
                paint.shader = null
                paint.style = Paint.Style.FILL
                paint.color = 0xFF202937.toInt()
                canvas.drawCircle(x, y, radius, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = maxOf(2.5f, radius * .07f)
                paint.color = light
                canvas.drawCircle(x, y, radius, paint)
                paint.style = Paint.Style.FILL
                if (start == AutoStart.NEW_CHAT) drawPlus(canvas, x, y, radius * .72f, MIST)
                else drawBars(canvas, x, y, radius * .9f, MIST)
            }
            AutoCardLook.GOLD -> {
                canvas.gradient(width, height, 0xFF2B3240.toInt(), 0xFF0C1017.toInt())
                drawStartSymbol(canvas, start, width, height, GOLD)
            }
            AutoCardLook.GRAPHITE -> {
                canvas.gradient(width, height, GRAPHITE_TOP, GRAPHITE_BOTTOM)
                drawStartSymbol(canvas, start, width, height, MIST)
            }
            AutoCardLook.MARKS -> {
                canvas.gradient(width, height, GRAPHITE_TOP, GRAPHITE_BOTTOM)
                val x = width * .73f
                val y = height * .44f
                if (start == AutoStart.ASK) drawBars(canvas, x, y, height * .50f, MIST)
                else drawSpark(canvas, x, y, height * .25f, GOLD)
            }
        }
        return bitmap
    }

    fun voiceState(state: AutoVoiceState, look: AutoVoiceLook, side: Int = 120): Bitmap {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.shader = null
        val size = side.toFloat()
        val center = size / 2f
        val disc = RectF(0f, 0f, size, size)
        paint.shader = LinearGradient(
            0f, 0f, 0f, size,
            if (look == AutoVoiceLook.KITT) DASH_TOP else GRAPHITE_TOP,
            if (look == AutoVoiceLook.KITT) DASH_BOTTOM else GRAPHITE_BOTTOM,
            Shader.TileMode.CLAMP,
        )
        canvas.drawOval(disc, paint)
        paint.shader = null
        when (look) {
            AutoVoiceLook.BARS, AutoVoiceLook.LAMPS, AutoVoiceLook.KITT -> {
                val color = if (look == AutoVoiceLook.KITT) RED else if (state == AutoVoiceState.THINKING) GOLD else MIST
                if (state == AutoVoiceState.THINKING) {
                    if (look == AutoVoiceLook.BARS) drawScanner(canvas, size, color, stacked = false)
                    else drawScanner(canvas, size, color, stacked = true)
                } else {
                    drawVoiceBars(canvas, size, color, stacked = look != AutoVoiceLook.BARS)
                }
            }
            AutoVoiceLook.ORB -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = size * .045f
                paint.color = if (state == AutoVoiceState.THINKING) GOLD else MIST
                canvas.drawCircle(center, center, size * .34f, paint)
                paint.style = Paint.Style.FILL
                drawSpark(canvas, center, center, size * .15f, GOLD)
            }
            AutoVoiceLook.ONYX -> {
                paint.color = GOLD
                paint.style = Paint.Style.FILL
                val eyeY = if (state == AutoVoiceState.THINKING) size * .36f else size * .43f
                canvas.drawCircle(size * .38f, eyeY, size * .035f, paint)
                canvas.drawCircle(size * .62f, eyeY, size * .035f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = size * .035f
                paint.strokeCap = Paint.Cap.ROUND
                if (state == AutoVoiceState.SPEAKING) {
                    canvas.drawOval(RectF(size * .41f, size * .55f, size * .59f, size * .68f), paint)
                } else {
                    canvas.drawLine(size * .43f, size * .61f, size * .57f, size * .61f, paint)
                }
                paint.style = Paint.Style.FILL
                paint.strokeCap = Paint.Cap.BUTT
            }
        }
        return bitmap
    }

    private fun drawStartSymbol(canvas: Canvas, start: AutoStart, width: Int, height: Int, color: Int) {
        val x = width * .73f
        val y = height * .44f
        val symbolHeight = height * .50f
        if (start == AutoStart.NEW_CHAT) {
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(2f, height * .025f)
            canvas.drawRoundRect(
                RectF(x - symbolHeight * .50f, y - symbolHeight * .38f, x + symbolHeight * .50f, y + symbolHeight * .38f),
                symbolHeight * .18f,
                symbolHeight * .18f,
                paint,
            )
            paint.style = Paint.Style.FILL
            drawPlus(canvas, x, y, symbolHeight * .44f, color)
        } else {
            drawMicrophone(canvas, x, y, symbolHeight, color)
        }
    }

    private fun drawMicrophone(canvas: Canvas, x: Float, y: Float, height: Float, color: Int) {
        paint.shader = null
        paint.color = color
        paint.style = Paint.Style.FILL
        val width = height * .40f
        canvas.drawRoundRect(RectF(x - width / 2, y - height * .52f, x + width / 2, y + height * .16f), width / 2, width / 2, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = height * .07f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(x - height * .34f, y - height * .12f, x + height * .34f, y + height * .40f), 0f, 180f, false, paint)
        canvas.drawLine(x, y + height * .40f, x, y + height * .55f, paint)
        canvas.drawLine(x - height * .17f, y + height * .55f, x + height * .17f, y + height * .55f, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawPlus(canvas: Canvas, x: Float, y: Float, height: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = height * .16f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x - height / 2, y, x + height / 2, y, paint)
        canvas.drawLine(x, y - height / 2, x, y + height / 2, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawBars(canvas: Canvas, x: Float, y: Float, height: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        val barWidth = height * .12f
        val gap = height * .21f
        val heights = listOf(.38f, .62f, 1f, .62f, .38f)
        heights.forEachIndexed { index, fraction ->
            val left = x + (index - 2) * gap - barWidth / 2
            val barHeight = height * fraction
            canvas.drawRoundRect(RectF(left, y - barHeight / 2, left + barWidth, y + barHeight / 2), barWidth / 2, barWidth / 2, paint)
        }
    }

    private fun drawVoiceBars(canvas: Canvas, size: Float, color: Int, stacked: Boolean) {
        val heights = listOf(.24f, .46f, .68f, .46f, .24f)
        val barWidth = size * .075f
        val gap = size * .145f
        heights.forEachIndexed { index, fraction ->
            val x = size / 2 + (index - 2) * gap
            val height = size * fraction
            if (stacked) drawLampStack(canvas, x, size / 2, barWidth, height, color)
            else {
                paint.color = color
                canvas.drawRoundRect(RectF(x - barWidth / 2, size / 2 - height / 2, x + barWidth / 2, size / 2 + height / 2), barWidth / 2, barWidth / 2, paint)
            }
        }
    }

    private fun drawScanner(canvas: Canvas, size: Float, color: Int, stacked: Boolean) {
        val y = size * .57f
        if (stacked) drawLampStack(canvas, size * .57f, y, size * .09f, size * .46f, color)
        else {
            paint.color = color
            canvas.drawRoundRect(RectF(size * .28f, y - size * .045f, size * .72f, y + size * .045f), size * .045f, size * .045f, paint)
        }
        paint.color = GOLD
        canvas.drawCircle(size * .37f, y, size * .055f, paint)
    }

    private fun drawLampStack(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, color: Int) {
        paint.color = color
        val count = 5
        val gap = height / count
        repeat(count) { index ->
            val lampY = y - height / 2 + index * gap + gap * .12f
            canvas.drawRoundRect(RectF(x - width / 2, lampY, x + width / 2, lampY + gap * .68f), width * .18f, width * .18f, paint)
        }
    }

    private fun drawSpark(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        val path = Path()
        repeat(16) { index ->
            val angle = -Math.PI / 2 + index * Math.PI / 8
            val r = if (index % 2 == 0) radius else radius * .30f
            val px = x + cos(angle).toFloat() * r
            val py = y + sin(angle).toFloat() * r
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun Canvas.gradient(width: Int, height: Int, from: Int, to: Int, diagonal: Boolean = false) {
        paint.shader = LinearGradient(0f, 0f, if (diagonal) width.toFloat() else 0f, height.toFloat(), from, to, Shader.TileMode.CLAMP)
        drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun Canvas.glow(x: Float, y: Float, radius: Float, color: Int, alpha: Float = .55f) {
        paint.shader = RadialGradient(x, y, radius, withAlpha(color, alpha), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        drawCircle(x, y, radius, paint)
        paint.shader = null
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
