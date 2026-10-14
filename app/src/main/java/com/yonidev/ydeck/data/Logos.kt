package com.yonidev.ydeck.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.scale
import java.io.File
import kotlin.math.min

/** Project logos picked from the gallery: stored square in filesDir/logos, referenced by relative path. */
object Logos {
    private const val SIZE = 256

    fun import(context: Context, uri: Uri, projectId: String): String? = runCatching {
        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 1024) decoder.setTargetSampleSize(longest / 1024)
        }
        val side = min(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side).scale(SIZE, SIZE)
        val dir = File(context.filesDir, "logos").apply { mkdirs() }
        // A new name per pick, so widgets and caches never show the previous image.
        val name = "logos/$projectId-${System.currentTimeMillis().toString(36)}.png"
        File(context.filesDir, name).outputStream().use { square.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dir.listFiles { f -> f.name.startsWith("$projectId-") && "logos/${f.name}" != name }?.forEach { it.delete() }
        name
    }.getOrNull()

    /** Every pick gets a new file name, so the path alone is a safe cache key. */
    fun load(context: Context, path: String?): Bitmap? = path?.let { p ->
        BitmapCache.get("logo:$p") { File(context.filesDir, p).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
    }

    fun delete(context: Context, path: String?) {
        path?.let { File(context.filesDir, it).delete() }
    }

    /** The logo's most prominent saturated colour, for "colour from logo". */
    fun dominantColor(bitmap: Bitmap): Int? {
        val small = bitmap.scale(32, 32)
        val hsv = FloatArray(3)
        val weight = DoubleArray(12)
        val sums = Array(12) { DoubleArray(3) }
        for (x in 0 until 32) for (y in 0 until 32) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 200) continue
            android.graphics.Color.colorToHSV(c, hsv)
            val w = (hsv[1] * hsv[2]).toDouble()
            if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue
            val bucket = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            weight[bucket] += w
            sums[bucket][0] += android.graphics.Color.red(c) * w
            sums[bucket][1] += android.graphics.Color.green(c) * w
            sums[bucket][2] += android.graphics.Color.blue(c) * w
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return null
        if (weight[best] <= 0.0) return null
        val s = sums[best]
        val w = weight[best]
        return android.graphics.Color.rgb((s[0] / w).toInt(), (s[1] / w).toInt(), (s[2] / w).toInt())
    }
}
