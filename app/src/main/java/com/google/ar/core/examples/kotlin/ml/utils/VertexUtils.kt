package com.google.ar.core.examples.kotlin.ml.utils

import com.google.cloud.vision.v1.NormalizedVertex

object VertexUtils {
  fun NormalizedVertex.toAbsoluteCoordinates(imageWidth: Int, imageHeight: Int, ): Pair<Int, Int> {
    return (x * imageWidth).toInt() to (y * imageHeight).toInt()
  }

  fun Pair<Int, Int>.rotateCoordinates(imageWidth: Int, imageHeight: Int, imageRotation: Int, ): Pair<Int, Int> {
    val (x, y) = this
    return when (imageRotation) {
      0 -> x to y
      180 -> imageWidth - x to imageHeight - y
      90 -> y to imageWidth - x
      270 -> imageHeight - y to x
      else -> error("Invalid imageRotation $imageRotation")
    }
  }

  fun List<NormalizedVertex>.calculateAverage(): NormalizedVertex {
    var averageX = 0f
    var averageY = 0f
    for (vertex in this) {
      averageX += vertex.x / size
      averageY += vertex.y / size
    }
    return NormalizedVertex.newBuilder().setX(averageX).setY(averageY).build()
  }
}
