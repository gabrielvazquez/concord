package com.gabrielvazquez.concord

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream

object Images {
    private fun decode(c: Context, uri: Uri, max: Int): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { d, info, _ ->
            val w = info.size.width; val h = info.size.height; val s = maxOf(w, h)
            if (s > max * 2) { val f = (max * 2).toFloat() / s; d.setTargetSize((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1)) }
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: Exception) { null }

    fun squareJpeg(c: Context, uri: Uri, size: Int): ByteArray? {
        val b = decode(c, uri, size) ?: return null
        val side = minOf(b.width, b.height)
        val crop = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(crop, size, size, true)
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
    }

    fun scaledJpeg(c: Context, uri: Uri, max: Int): ByteArray? {
        val b = decode(c, uri, max) ?: return null
        val s = maxOf(b.width, b.height)
        val out = if (s > max) Bitmap.createScaledBitmap(b, b.width * max / s, b.height * max / s, true) else b
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }
}
