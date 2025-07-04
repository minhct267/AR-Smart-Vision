package com.google.ar.core.examples.kotlin.ml

import android.media.Image
import android.util.Log
import com.google.ar.core.examples.kotlin.helloar.HelloArActivity
import com.google.ar.core.examples.kotlin.ml.utils.ImageUtils
import com.google.ar.core.examples.kotlin.ml.utils.ImageUtils.toByteArray
import com.google.ar.core.examples.kotlin.ml.utils.VertexUtils.calculateAverage
import com.google.ar.core.examples.kotlin.ml.utils.VertexUtils.rotateCoordinates
import com.google.ar.core.examples.kotlin.ml.utils.VertexUtils.toAbsoluteCoordinates
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.ImageAnnotatorSettings
import com.google.protobuf.ByteString
import com.google.cloud.vision.v1.Image as GCVImage

class CloudVision(val activity: HelloArActivity) : ObjectDetector(activity) {
  companion object {
    const val TAG = "GoogleCloudVisionDetector"
  }

  init {
    Log.e(TAG, "GoogleCloudVisionDetector init start")
  }

  // Load Google Cloud credentials from raw resource file if available
  val credentials = try {
    val res = activity.resources.getIdentifier("credentials", "raw", activity.packageName)
    if (res == 0) error("Missing GCP credentials in res/raw/credentials.json.")
    GoogleCredentials.fromStream(activity.resources.openRawResource(res))
  } catch (e: Exception) {
    Log.e(TAG, "Unable to create Google credentials from res/raw/credentials.json. Cloud ML will be disabled.", e)
    null
  }

  // Configure the ImageAnnotatorClient with the credentials
  val settings = ImageAnnotatorSettings.newBuilder().setCredentialsProvider { credentials }.build()
  val vision = ImageAnnotatorClient.create(settings)

  override suspend fun analyze(image: Image, imageRotation: Int): List<DetectedObjectResult> {
      try {
          // Convert YUV image to Bitmap
          val convertYuv = convertYuv(image)
          Log.e(TAG, "Convert YUV image to Bitmap!")

          // Rotate image to upright position for better model accuracy
          val rotatedImage = ImageUtils.rotateBitmap(convertYuv, imageRotation)
          Log.e(TAG, "Rotate image to upright position for better model accuracy!")

          // Create request for Google Cloud Vision API
          val request = createAnnotateImageRequest(rotatedImage.toByteArray())
          Log.e(TAG, "Create request for Google Cloud Vision API!")

          val response = vision.batchAnnotateImages(listOf(request))

          // Extract and process detected object annotations
          val objectAnnotationsResult = response.responsesList.first().localizedObjectAnnotationsList
          Log.e(TAG, "Annotations Result: $objectAnnotationsResult")

          return objectAnnotationsResult.map {
            // Compute center point of bounding box
            val center = it.boundingPoly.normalizedVerticesList.calculateAverage()

            // Convert normalized coordinates to absolute pixel coordinates
            val absoluteCoordinates = center.toAbsoluteCoordinates(rotatedImage.width, rotatedImage.height)

            // Rotate coordinates back to match original image orientation
            val rotatedCoordinates = absoluteCoordinates.rotateCoordinates(rotatedImage.width, rotatedImage.height, imageRotation)

            DetectedObjectResult(it.score, it.name, rotatedCoordinates)
          }
      } catch (e: Exception) {
          Log.e(TAG, "Exception in analyze: ${e.message}", e)
          throw e
      }
  }

  /** Creates an [AnnotateImageRequest] from image's byte array. */
  private fun createAnnotateImageRequest(imageBytes: ByteArray): AnnotateImageRequest {
    val image = GCVImage.newBuilder().setContent(ByteString.copyFrom(imageBytes))
    val features = Feature.newBuilder().setType(Feature.Type.OBJECT_LOCALIZATION)
    return AnnotateImageRequest.newBuilder()
      .setImage(image)
      .addFeatures(features)
      .build()
  }
}
