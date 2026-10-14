package com.yonidev.ydeck.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.yonidev.ydeck.data.BitmapCache
import com.yonidev.ydeck.data.Logos
import com.yonidev.ydeck.data.Project
import kotlin.math.roundToInt

/** The project logo as a bitmap, since RemoteViews can't clip an image or draw text on a shape by itself. */
object BrandArt {

    /** App-icon style square: the picked logo image, or a white monogram on the flat brand colour. */
    fun logo(context: Context, project: Project, sizeDp: Int): Bitmap {
        val s = (sizeDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(16)
        // Drawn once per look and size; the widget redraws often.
        val key = "tile:${project.color}:${project.mono}:${project.logo}:$s"
        return BitmapCache.get(key) { draw(context, project, s) }!!
    }

    private fun draw(context: Context, project: Project, s: Int): Bitmap {
        val bmp = createBitmap(s, s)
        val c = Canvas(bmp)
        val r = s * 0.29f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = project.color }
        c.drawRoundRect(0f, 0f, s.toFloat(), s.toFloat(), r, r, fill)

        // A picked logo image replaces the monogram, drawn over the brand colour so transparent logos sit on it.
        Logos.load(context, project.logo)?.let { img ->
            fill.shader = BitmapShader(img.scale(s, s), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            c.drawRoundRect(0f, 0f, s.toFloat(), s.toFloat(), r, r, fill)
            return bmp
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = s * 0.46f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
        }
        c.drawText(project.mono, s / 2f, s / 2f - (text.descent() + text.ascent()) / 2f, text)
        return bmp
    }
}
