package com.calorietracker.app.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.calorietracker.app.domain.model.ChatImage
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Downscales a picked photo and encodes it as base64 JPEG so uploads stay small and fast. */
object ImageEncoder {
  private const val MAX_SIDE = 1280
  private const val JPEG_QUALITY = 82

  fun encode(resolver: ContentResolver, uri: Uri): ChatImage? =
    try {
      val bitmap = decode(resolver, uri) ?: return null
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
      ChatImage(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    } catch (_: Exception) {
      null
    } catch (_: OutOfMemoryError) {
      null
    }

  private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      // ImageDecoder applies EXIF rotation, so the model sees the photo upright.
      ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = max(info.size.width, info.size.height)
        if (longest > MAX_SIDE) {
          val scale = MAX_SIDE.toFloat() / longest
          decoder.setTargetSize(
            (info.size.width * scale).toInt().coerceAtLeast(1),
            (info.size.height * scale).toInt().coerceAtLeast(1)
          )
        }
      }
    } else {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      var sample = 1
      while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
      resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
      }
    }
}
