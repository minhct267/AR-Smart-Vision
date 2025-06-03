package com.google.ar.core.examples.kotlin.ml

data class DetectedObjectResult(
  val confidence: Float,
  val label: String,
  val centerCoordinate: Pair<Int, Int>
)
