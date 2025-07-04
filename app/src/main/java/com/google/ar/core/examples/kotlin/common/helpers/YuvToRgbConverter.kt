package com.google.ar.core.examples.kotlin.common.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type

/** Utility class to convert an Android media.Image in YUV_420_888 format to an RGB Bitmap. */
class YuvToRgbConverter(context: Context) {

  // RenderScript & ScriptIntrinsic Initialization
  private val rs = RenderScript.create(context)
  private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

  // Buffer and allocation fields
  private var pixelCount: Int = -1

  // Byte array that holds raw YUV data in NV21 format
  private lateinit var yuvBuffer: ByteArray

  // RenderScript allocations for input (YUV) and output (RGB)
  private lateinit var inputAllocation: Allocation
  private lateinit var outputAllocation: Allocation

  /* Convert YUV Image to RGB Bitmap. */
  @Synchronized
  fun yuvToRgb(image: Image, output: Bitmap) {

    // Allocate or reuse the YUV byte buffer
    if (!::yuvBuffer.isInitialized) {
      pixelCount = image.width * image.height

      // Calculate total buffer size based on bits per pixel for YUV_420_888
      val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
      yuvBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
    }

    // Extract YUV data from Image into the byte array (NV21 format)
    imageToByteArray(image, yuvBuffer)

    if (!::inputAllocation.isInitialized) {
      val elemType = Type.Builder(rs, Element.YUV(rs)).setYuvFormat(ImageFormat.NV21).create()
      inputAllocation = Allocation.createSized(rs, elemType.element, yuvBuffer.size)
    }

    if (!::outputAllocation.isInitialized) {
      outputAllocation = Allocation.createFromBitmap(rs, output)
    }

    // Convert NV21 format YUV -> RGB
    inputAllocation.copyFrom(yuvBuffer)
    scriptYuvToRgb.setInput(inputAllocation)
    scriptYuvToRgb.forEach(outputAllocation)
    outputAllocation.copyTo(output)
  }

  /* Extract YUV Planes into Byte Array. */
  private fun imageToByteArray(image: Image, outputBuffer: ByteArray) {

    // Ensure only YUV_420_888 images are handled
    assert(image.format == ImageFormat.YUV_420_888)

    val imageCrop = Rect(0, 0, image.width, image.height)
    val imagePlanes = image.planes

    imagePlanes.forEachIndexed { planeIndex, plane ->
      val outputStride: Int
      var outputOffset: Int

      when (planeIndex) {
        0 -> {
          outputStride = 1
          outputOffset = 0
        }
        1 -> {
          outputStride = 2
          outputOffset = pixelCount + 1
        }
        2 -> {
          outputStride = 2
          outputOffset = pixelCount
        }
        else -> {
          return@forEachIndexed
        }
      }

      val planeBuffer = plane.buffer
      val rowStride = plane.rowStride
      val pixelStride = plane.pixelStride

      // Divide the width and height by two if it's not the Y plane
      val planeCrop = if (planeIndex == 0) {
        imageCrop
      } else {
        Rect(
          imageCrop.left / 2,
          imageCrop.top / 2,
          imageCrop.right / 2,
          imageCrop.bottom / 2
        )
      }

      val planeWidth = planeCrop.width()
      val planeHeight = planeCrop.height()

      // Intermediate buffer used to store the bytes of each row
      val rowBuffer = ByteArray(plane.rowStride)

      // Size of each row in bytes
      val rowLength = if (pixelStride == 1 && outputStride == 1) {
        planeWidth
      } else {
        (planeWidth - 1) * pixelStride + 1
      }

      for (row in 0 until planeHeight) {

        planeBuffer.position(
          (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride
        )

        if (pixelStride == 1 && outputStride == 1) {
          planeBuffer.get(outputBuffer, outputOffset, rowLength)
          outputOffset += rowLength

        } else {
          planeBuffer.get(rowBuffer, 0, rowLength)

          for (col in 0 until planeWidth) {
            outputBuffer[outputOffset] = rowBuffer[col * pixelStride]
            outputOffset += outputStride
          }
        }
      }
    }
  }
}
