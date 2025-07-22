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
    const val TAG = "CloudVision"
  }

  init {
    Log.e(TAG, "GoogleCloudVisionDetector init start")
  }

  val credentials = try {
    val res = activity.resources.getIdentifier("credentials", "raw", activity.packageName)
    if (res == 0) error("Missing GCP credentials in res/raw/credentials.json.")
    GoogleCredentials.fromStream(activity.resources.openRawResource(res))
  } catch (e: Exception) {
    Log.e(TAG, "Unable to create Google credentials from res/raw/credentials.json. Cloud ML will be disabled.", e)
    null
  }

  val settings = ImageAnnotatorSettings.newBuilder().setCredentialsProvider { credentials }.build()
  val vision = ImageAnnotatorClient.create(settings)

  override suspend fun analyze(image: Image, imageRotation: Int): List<DetectedObjectResult> {
      try {

          val convertYuv = convertYuv(image)
          val rotatedImage = ImageUtils.rotateBitmap(convertYuv, imageRotation)
          val request = createAnnotateImageRequest(rotatedImage.toByteArray())
          val response = vision.batchAnnotateImages(listOf(request))
          val objectAnnotationsResult = response.responsesList.first().localizedObjectAnnotationsList

          return objectAnnotationsResult.map {
            val center = it.boundingPoly.normalizedVerticesList.calculateAverage()
            val absoluteCoordinates = center.toAbsoluteCoordinates(rotatedImage.width, rotatedImage.height)
            val rotatedCoordinates = absoluteCoordinates.rotateCoordinates(rotatedImage.width, rotatedImage.height, imageRotation)
            DetectedObjectResult(it.score, it.name, rotatedCoordinates)
          }
      } catch (e: Exception) {
          Log.e(TAG, "Exception in analyze: ${e.message}", e)
          throw e
      }
  }

  private fun createAnnotateImageRequest(imageBytes: ByteArray): AnnotateImageRequest {
    val image = GCVImage.newBuilder().setContent(ByteString.copyFrom(imageBytes))
    val features = Feature.newBuilder().setType(Feature.Type.OBJECT_LOCALIZATION)
    return AnnotateImageRequest.newBuilder()
      .setImage(image)
      .addFeatures(features)
      .build()
  }
}
