package com.google.ar.core.examples.kotlin.ml.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

object ImageUtils {
  /** Creates a new bitmap by rotating the input bitmap degrees. */
  fun rotateBitmap(bitmap: Bitmap, rotation: Int): Bitmap {
    if (rotation == 0) return bitmap

    val matrix = Matrix()
    matrix.postRotate(rotation.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
  }

  /** Converts a bitmap to ByteArray. */
  fun Bitmap.toByteArray(): ByteArray = ByteArrayOutputStream().use { stream ->
    this.compress(Bitmap.CompressFormat.JPEG, 100, stream)
    stream.toByteArray()
  }
}
