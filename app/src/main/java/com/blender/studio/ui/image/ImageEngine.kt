package com.blender.studio.ui.image

import android.content.Context
import android.graphics.*
import android.net.Uri
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Tous les réglages : l'image d'origine n'est jamais modifiée (édition non destructive). */
data class ImageState(
    val brightness: Float = 0f, val contrast: Float = 1f, val saturation: Float = 1f,
    val warmth: Float = 0f, val sharpness: Float = 0f, val vignette: Float = 0f,
    val rotation: Int = 0, val flipH: Boolean = false, val aspect: Float = 0f,
    val text: String = "", val textSize: Float = 0.08f, val textX: Float = 0.5f, val textY: Float = 0.85f,
    val textColor: Int = Color.WHITE, val preset: String = "Original",
) {
    fun toJson(): String = JSONObject().put("b", brightness).put("c", contrast).put("s", saturation).put("w", warmth)
        .put("sh", sharpness).put("v", vignette).put("r", rotation).put("f", flipH).put("a", aspect)
        .put("t", text).put("ts", textSize).put("tx", textX).put("ty", textY).put("tc", textColor).put("p", preset).toString()

    companion object {
        fun fromJson(s: String): ImageState = runCatching {
            val j = JSONObject(s)
            ImageState(j.getDouble("b").toFloat(), j.getDouble("c").toFloat(), j.getDouble("s").toFloat(), j.getDouble("w").toFloat(),
                j.getDouble("sh").toFloat(), j.getDouble("v").toFloat(), j.getInt("r"), j.getBoolean("f"), j.getDouble("a").toFloat(),
                j.getString("t"), j.getDouble("ts").toFloat(), j.getDouble("tx").toFloat(), j.getDouble("ty").toFloat(), j.getInt("tc"), j.getString("p"))
        }.getOrDefault(ImageState())
    }
}

object ImageEngine {
    val presets = linkedMapOf(
        "Original" to ImageState(),
        "Vif" to ImageState(contrast = 1.15f, saturation = 1.35f),
        "Noir & Blanc" to ImageState(contrast = 1.2f, saturation = 0f),
        "Chaud" to ImageState(warmth = 0.6f, saturation = 1.1f),
        "Froid" to ImageState(warmth = -0.6f),
        "Cinéma" to ImageState(contrast = 1.25f, saturation = 0.85f, warmth = 0.2f, vignette = 0.5f),
        "Vintage" to ImageState(brightness = 0.04f, contrast = 0.9f, saturation = 0.7f, warmth = 0.45f, vignette = 0.35f),
    )

    fun applyPreset(s: ImageState, name: String): ImageState {
        val p = presets[name] ?: return s
        return s.copy(brightness = p.brightness, contrast = p.contrast, saturation = p.saturation,
            warmth = p.warmth, vignette = p.vignette, preset = name)
    }

    fun load(ctx: Context, uri: Uri, maxSide: Int): Bitmap {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            val k = maxSide.toFloat() / max(info.size.width, info.size.height)
            if (k < 1f) dec.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun colorMatrix(s: ImageState): ColorMatrix {
        val cm = ColorMatrix().apply { setSaturation(s.saturation) }
        val c = s.contrast
        val t = (1 - c) * 128f + s.brightness * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f)))
        val w = s.warmth * 0.2f
        cm.postConcat(ColorMatrix(floatArrayOf(
            1f + w, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - w, 0f, 0f,
            0f, 0f, 0f, 1f, 0f)))
        return cm
    }

    fun render(src: Bitmap, s: ImageState): Bitmap {
        val m = Matrix().apply { postRotate(s.rotation.toFloat()); if (s.flipH) postScale(-1f, 1f) }
        var b = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (s.aspect > 0f) {
            var w = b.width; var h = b.height
            if (w.toFloat() / h > s.aspect) w = (h * s.aspect).roundToInt() else h = (w / s.aspect).roundToInt()
            b = Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w, h)
        }
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(b, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix(s)) })
        if (s.sharpness > 0f) sharpen(out, s.sharpness)
        val w = out.width.toFloat(); val h = out.height.toFloat()
        if (s.vignette > 0f) {
            val p = Paint().apply {
                shader = RadialGradient(w / 2, h / 2, hypot(w, h) / 2,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((210 * s.vignette).toInt(), 0, 0, 0)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, w, h, p)
        }
        if (s.text.isNotBlank()) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = s.textColor; textSize = min(w, h) * s.textSize * 1.2f; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(textSize / 10f, 0f, textSize / 14f, Color.argb(160, 0, 0, 0))
            }
            canvas.drawText(s.text, w * s.textX, h * s.textY, p)
        }
        return out
    }

    private fun chan(c: Int, l: Int, r: Int, u: Int, d: Int, sh: Int, a: Float): Int {
        val v = ((c shr sh) and 255) * (1 + 4 * a) -
            a * (((l shr sh) and 255) + ((r shr sh) and 255) + ((u shr sh) and 255) + ((d shr sh) and 255))
        return v.roundToInt().coerceIn(0, 255)
    }

    private fun sharpen(bmp: Bitmap, a: Float) {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val c = px[i]; val l = px[i - 1]; val r = px[i + 1]; val u = px[i - w]; val d = px[i + w]
            out[i] = (c and 0xFF000000.toInt()) or (chan(c, l, r, u, d, 16, a) shl 16) or (chan(c, l, r, u, d, 8, a) shl 8) or chan(c, l, r, u, d, 0, a)
        }
        bmp.setPixels(out, 0, w, 0, 0, w, h)
    }
}
