package com.google.ar.core.examples.kotlin.ml

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import com.google.ar.core.examples.kotlin.common.helpers.YuvToRgbConverter


abstract class ObjectDetector(val context: Context) {
  val yuvConverter = YuvToRgbConverter(context)

  abstract suspend fun analyze(image: Image, imageRotation: Int): List<DetectedObjectResult>

  fun convertYuv(image: Image): Bitmap {
    return Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).apply {
      yuvConverter.yuvToRgb(image, this)
    }
  }
}
