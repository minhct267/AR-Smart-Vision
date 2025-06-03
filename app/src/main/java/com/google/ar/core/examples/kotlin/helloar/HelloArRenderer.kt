package com.google.ar.core.examples.kotlin.helloar

import android.graphics.RectF
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Coordinates2d
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.LightEstimate
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper
import com.google.ar.core.examples.java.common.samplerender.Framebuffer
import com.google.ar.core.examples.java.common.samplerender.GLError
import com.google.ar.core.examples.java.common.samplerender.Mesh
import com.google.ar.core.examples.java.common.samplerender.SampleRender
import com.google.ar.core.examples.java.common.samplerender.Shader
import com.google.ar.core.examples.java.common.samplerender.Texture
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer
import com.google.ar.core.examples.java.common.samplerender.arcore.PlaneRenderer
import com.google.ar.core.examples.java.common.samplerender.arcore.SpecularCubemapFilter
import com.google.ar.core.examples.kotlin.ml.DetectedObjectResult
import com.google.ar.core.examples.kotlin.ml.GoogleCloudVisionDetector
import com.google.ar.core.examples.kotlin.ml.render.LabelRender
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Collections

// =============================================================
//                   Rendering ARCore features
// =============================================================
class HelloArRenderer(val activity: HelloArActivity) :
  SampleRender.Renderer, DefaultLifecycleObserver, CoroutineScope by MainScope() {

  // -------------------------------
  // Constants & Companion Object
  // -------------------------------
  companion object {
    const val TAG = "HelloArRenderer"

    // Spherical harmonics factors for environmental lighting
    private val sphericalHarmonicFactors =
      floatArrayOf(
        0.282095f,
        -0.325735f,
        0.325735f,
        -0.325735f,
        0.273137f,
        -0.273137f,
        0.078848f,
        -0.273137f,
        0.136569f
      )

    private val Z_NEAR = 0.1f
    private val Z_FAR = 100f

    val APPROXIMATE_DISTANCE_METERS = 2.0f

    val CUBEMAP_RESOLUTION = 16
    val CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32
  }


  // -------------------------------
  // Rendering Resources
  // -------------------------------
  lateinit var render: SampleRender
  lateinit var planeRenderer: PlaneRenderer
  lateinit var backgroundRenderer: BackgroundRenderer
  lateinit var virtualSceneFramebuffer: Framebuffer
  var hasSetTextureNames = false

  // Point Cloud rendering
  lateinit var pointCloudVertexBuffer: VertexBuffer
  lateinit var pointCloudMesh: Mesh
  lateinit var pointCloudShader: Shader
  var lastPointCloudTimestamp: Long = 0

  // Virtual object (ARCore pawn/anchor)
  lateinit var virtualObjectMesh: Mesh
  lateinit var virtualObjectShader: Shader
  lateinit var virtualObjectAlbedoTexture: Texture
  lateinit var virtualObjectAlbedoInstantPlacementTexture: Texture
  private val maxAnchor = 6

  // Flicker effect resources
  lateinit var flickerMesh: Mesh
  lateinit var flickerShader: Shader
  lateinit var flickerTexture: Texture

  // Flicker effect parameters
  private val flickerOffsetY = 0.26f  // Translate y units regarding anchor
  private val flickerScale = 0.07f // Radius of flicker
  private val flickerFrequencies = listOf(7.0, 8.0, 9.0, 11.0, 7.5, 8.5)
  private val wrappedAnchors = mutableListOf<WrappedAnchor>()

  // Restrict region for flicker highlighting (normalized screen coordinates)
  private val restrictRegion = RectF(0.35f, 0.35f, 0.65f, 0.65f)

  // Label rendering for Detection
  val labelRenderer = LabelRender()


  // -------------------------------
  // Object Detection State
  // -------------------------------
  val objectDetector = GoogleCloudVisionDetector(activity)
  var scanButtonWasPressed = false
  var objectResults: List<DetectedObjectResult>? = null
  val detectedAnchors = Collections.synchronizedList(mutableListOf<DetectedAnchor>())


  // -------------------------------
  // Environmental HDR Lighting
  // -------------------------------
  lateinit var dfgTexture: Texture
  lateinit var cubemapFilter: SpecularCubemapFilter


  // ---------------------------------
  // Matrix Buffers (reused per frame)
  // ---------------------------------
  val modelMatrix = FloatArray(16)
  val viewMatrix = FloatArray(16)
  val projectionMatrix = FloatArray(16)
  val modelViewMatrix = FloatArray(16)
  val modelViewProjectionMatrix = FloatArray(16)

  val sphericalHarmonicsCoefficients = FloatArray(9 * 3)
  val viewInverseMatrix = FloatArray(16)
  val worldLightDirection = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
  val viewLightDirection = FloatArray(4)


  // -------------------------------
  // ARCore Session & Helpers
  // -------------------------------
  lateinit var view: HelloArView
  val session
    get() = activity.arCoreSessionHelper.session
  val displayRotationHelper = DisplayRotationHelper(activity)
  val trackingStateHelper = TrackingStateHelper(activity)

  private fun hideSnackbar() = activity.view.snackbarHelper.hide(activity)
  private fun showSnackbar(message: String): Unit = activity.view.snackbarHelper.showError(activity, message)


  // -------------------------------
  // Lifecycle Methods
  // -------------------------------
  override fun onResume(owner: LifecycleOwner) {
    displayRotationHelper.onResume()
    hasSetTextureNames = false
  }

  override fun onPause(owner: LifecycleOwner) {
    displayRotationHelper.onPause()
  }


  /**
   * Runs once when a fresh OpenGL ES context is ready.
   * Use it to (re)create shaders, textures, and any other GPU resources,
   * because everything loaded before is lost when the context is rebuilt.
   */
  override fun onSurfaceCreated(render: SampleRender) {
    // Prepare rendering resources: shaders, textures, meshes, etc
    try {
      // Plane and background renders
      planeRenderer = PlaneRenderer(render)
      backgroundRenderer = BackgroundRenderer(render)
      virtualSceneFramebuffer = Framebuffer(render, 1, 1)

      // Environmental lighting cubemap filter
      cubemapFilter = SpecularCubemapFilter(
        render,
        CUBEMAP_RESOLUTION,
        CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES
      )

      // Load DFG LUT texture for environmental lighting
      dfgTexture = Texture(
        render,
        Texture.Target.TEXTURE_2D,
        Texture.WrapMode.CLAMP_TO_EDGE,
        false
      )

      // The dfg.raw file is a raw half-float texture with two channels
      val dfgResolution = 64
      val dfgChannels = 2
      val halfFloatSize = 2
      val buffer: ByteBuffer =
        ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize)
      activity.assets.open("models/dfg.raw").use { it.read(buffer.array()) }

      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.textureId)
      GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture")
      GLES30.glTexImage2D(
        GLES30.GL_TEXTURE_2D,
        0,
        GLES30.GL_RG16F,
        dfgResolution,
        dfgResolution,
        0,
        GLES30.GL_RG,
        GLES30.GL_HALF_FLOAT,
        buffer
      )
      GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D")

      // Point cloud rendering setup
      pointCloudShader =
        Shader.createFromAssets(
            render,
            "shaders/point_cloud.vert",
            "shaders/point_cloud.frag",
            null
          )
          .setVec4("u_Color", floatArrayOf(31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f))
          .setFloat("u_PointSize", 5.0f)

      // Point cloud vertex buffer: 4 entries per vertex (X, Y, Z, confidence)
      pointCloudVertexBuffer = VertexBuffer(render, 4, null)
      val pointCloudVertexBuffers = arrayOf(pointCloudVertexBuffer)
      pointCloudMesh = Mesh(
        render,
        Mesh.PrimitiveMode.POINTS,
        null,
        pointCloudVertexBuffers
      )

      // +++++ Virtual object (pawn) textures and mesh +++++
      virtualObjectAlbedoTexture =
        Texture.createFromAsset(
          render,
          "models/pawn_albedo.png",
          Texture.WrapMode.CLAMP_TO_EDGE,
          Texture.ColorFormat.SRGB
        )

      virtualObjectAlbedoInstantPlacementTexture =
        Texture.createFromAsset(
          render,
          "models/pawn_albedo_instant_placement.png",
          Texture.WrapMode.CLAMP_TO_EDGE,
          Texture.ColorFormat.SRGB
        )

      val virtualObjectPbrTexture =
        Texture.createFromAsset(
          render,
          "models/pawn_roughness_metallic_ao.png",
          Texture.WrapMode.CLAMP_TO_EDGE,
          Texture.ColorFormat.LINEAR
        )

      virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj")

      virtualObjectShader =
        Shader.createFromAssets(
            render,
            "shaders/environmental_hdr.vert",
            "shaders/environmental_hdr.frag",
            mapOf("NUMBER_OF_MIPMAP_LEVELS" to cubemapFilter.numberOfMipmapLevels.toString())
          )
          .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
          .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
          .setTexture("u_Cubemap", cubemapFilter.filteredCubemapTexture)
          .setTexture("u_DfgTexture", dfgTexture)


      // +++++ Flicker effect mesh and shader +++++
      flickerMesh = Mesh.createFromAsset(render, "models/flicker.obj")
      flickerShader = Shader.createFromAssets(
        render,
        "shaders/flicker.vert",
        "shaders/flicker.frag",
        null
      )

      // Initialize label renderer
      labelRenderer.onSurfaceCreated(render)

    } catch (e: IOException) {
      Log.e(TAG, "Failed to read a required asset file", e)
      showError("Failed to read a required asset file: $e")
    }
  }


  // Update viewport and framebuffer size when surface changes
  override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
    displayRotationHelper.onSurfaceChanged(width, height)
    virtualSceneFramebuffer.resize(width, height)
  }


  /**
   * Called once per display frame (≈60 FPS).
   * Update AR tracking data, refresh scene state, and draw everything to the screen here.
   */
  override fun onDrawFrame(render: SampleRender) {
    Log.e("Mimo", "onDrawFrame called")

    val session = session ?: return

    // // Set camera texture names (only once per GL thread)
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
      hasSetTextureNames = true
    }

    // -----------------------------
    // -- Update per-frame state ---
    // -----------------------------

    // Update ARCore session if view size changed
    displayRotationHelper.updateSessionIfNeeded(session)

    // Obtain the current frame
    val frame =
      try {
        session.update()
      } catch (e: CameraNotAvailableException) {
        Log.e(TAG, "Camera not available during onDrawFrame", e)
        showError("Camera not available. Try restarting the app.")
        return
      }

    val camera = frame.camera

    // Update background renderer state for depth/occlusion
    try {
      backgroundRenderer.setUseDepthVisualization(
        render,
        activity.depthSettings.depthColorVisualizationEnabled()
      )
      backgroundRenderer.setUseOcclusion(render, activity.depthSettings.useDepthForOcclusion())
    } catch (e: IOException) {
      Log.e(TAG, "Failed to read a required asset file", e)
      showError("Failed to read a required asset file: $e")
      return
    }

    // Update display geometry for background renderer
    backgroundRenderer.updateDisplayGeometry(frame)
    val shouldGetDepthImage =
      activity.depthSettings.useDepthForOcclusion() ||
        activity.depthSettings.depthColorVisualizationEnabled()
    if (camera.trackingState == TrackingState.TRACKING && shouldGetDepthImage) {
      try {
        val depthImage = frame.acquireDepthImage16Bits()
        backgroundRenderer.updateCameraDepthTexture(depthImage)
        depthImage.close()
      } catch (e: NotYetAvailableException) {
        // Depth data not available yet, ignore
      }
    }


    // +++++ Handle user tap (placing anchors) +++++
    handleTap(frame, camera)

    // Keep screen on while tracking
    trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

    // Show user guidance message based on tracking state
    val message: String? =
      when {
        camera.trackingState == TrackingState.PAUSED &&
          camera.trackingFailureReason == TrackingFailureReason.NONE ->
          activity.getString(R.string.searching_planes)
        camera.trackingState == TrackingState.PAUSED ->
          TrackingStateHelper.getTrackingFailureReasonString(camera)
        session.hasTrackingPlane() && wrappedAnchors.isEmpty() ->
          activity.getString(R.string.waiting_taps)
        session.hasTrackingPlane() && wrappedAnchors.isNotEmpty() -> null
        else -> activity.getString(R.string.searching_planes)
      }
    if (message == null) {
      activity.view.snackbarHelper.hide(activity)
    } else {
      activity.view.snackbarHelper.showMessage(activity, message)
    }

    // --- Draw background camera image ---
    if (frame.timestamp != 0L) {
      // Only draw if camera produced a frame
      backgroundRenderer.drawBackground(render)
    }

    // If not tracking, skip 3D rendering
    if (camera.trackingState == TrackingState.PAUSED) {
      return
    }


    // -------------------------------------------------------------
    // --- Draw non-occluded virtual objects (point cloud, etc.) ---
    // -------------------------------------------------------------

    // Get projection and view matrices
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR)
    camera.getViewMatrix(viewMatrix, 0)

    // Draw point cloud
    frame.acquirePointCloud().use { pointCloud ->
      if (pointCloud.timestamp > lastPointCloudTimestamp) {
        pointCloudVertexBuffer.set(pointCloud.points)
        lastPointCloudTimestamp = pointCloud.timestamp
      }
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
      pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
      render.draw(pointCloudMesh, pointCloudShader)
    }

    /** Display/Hide planes (the white mesh on the surface) as needed. */
//    // Visualize planes
//    planeRenderer.drawPlanes(
//      render,
//      session.getAllTrackables<Plane>(Plane::class.java),
//      camera.displayOrientedPose,
//      projectionMatrix
//    )


    // +++++++++++++++++++++++++++++++++++
    // ++++++++ Object detection +++++++++
    // +++++++++++++++++++++++++++++++++++
    if (scanButtonWasPressed) {
      scanButtonWasPressed = false
      val cameraImage = frame.tryAcquireCameraImage()
      if (cameraImage != null) {
        Log.e("Mimo", "Lấy được ảnh!")
        launch(Dispatchers.IO) {
          try {
              val cameraId = session.cameraConfig.cameraId
              val imageRotation = displayRotationHelper.getCameraSensorToDisplayRotation(cameraId)
              Log.e("Mimo", "Bắt đầu analyze object detection")

              objectResults = objectDetector.analyze(cameraImage, imageRotation)
              Log.e("Mimo", "Kết quả objectResults: $objectResults")

              cameraImage.close()
          } catch (e: Exception) {
              Log.e("Mimo", "Lỗi khi detect object: ${e.message}", e)
              view.post {
                view.setScanningActive(false)
                showSnackbar("Object detection failed: ${e.message}")
              }
          }
        }
      } else {
        Log.e("Mimo", "Could not acquire camera image for object detection!")
        view.post { view.setScanningActive(false) }
        showSnackbar("Fail to receive camera image for object detection!")
      }
    }

    // If results were completed this frame, create anchors from model results
    val objects = objectResults
    if (objects != null) {
      objectResults = null
      Log.i("Mimi", "$objectDetector got objects: $objects")

      val anchors = objects.mapNotNull { obj ->
        val (atX, atY) = obj.centerCoordinate
        Log.e("Mimi", "Trying to create anchor at ($atX, $atY)")

        val anchor = createAnchor(atX.toFloat(), atY.toFloat(), frame) ?: return@mapNotNull null
        Log.i("Mimi", "Created anchor ${anchor.pose} from hit test!")

        DetectedAnchor(anchor, obj.label)
      }

      detectedAnchors.addAll(anchors)

      view.post {
        try {
            view.resetButton.isEnabled = detectedAnchors.isNotEmpty()
            view.setScanningActive(false)
            when {
              objects.isEmpty() ->
                showSnackbar("No objects were detected!")
              anchors.size != objects.size ->
                showSnackbar("Try moving your device around to obtain a better understanding of the environment!")
            }
        } catch (e: Exception) {
            Log.e("Mimi", "Exception when updating UI: ${e.message}", e)
        }
      }
    }

    // Render labels for detected objects
    for (detectedAnchor in detectedAnchors) {
      val anchor = detectedAnchor.anchor
      if (anchor.trackingState != TrackingState.TRACKING) continue
      labelRenderer.draw(
        render,
        modelViewProjectionMatrix,
        anchor.pose,
        camera.pose,
        detectedAnchor.label
      )
    }


    // -----------------------------------------------------------------
    // --- Draw occluded virtual objects (anchors, flickers, labels) ---
    // -----------------------------------------------------------------

    // Update lighting parameters for shaders
    updateLightEstimation(frame.lightEstimate, viewMatrix)

    // Draw virtual objects (pawn) at each anchor
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)
    for ((anchor, trackable) in wrappedAnchors.filter { it.anchor.trackingState == TrackingState.TRACKING }) {
      anchor.pose.toMatrix(modelMatrix, 0)
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

      // Update shader properties and draw
      virtualObjectShader.setMat4("u_ModelView", modelViewMatrix)
      virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
      val texture =
        if ((trackable as? InstantPlacementPoint)?.trackingMethod ==
            InstantPlacementPoint.TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE
        ) {
          virtualObjectAlbedoInstantPlacementTexture
        } else {
          virtualObjectAlbedoTexture
        }
      virtualObjectShader.setTexture("u_AlbedoTexture", texture)
      render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)
    }


    // +++++ Add virtual objects (pawn) at each detected label (Pawn on Label) +++++
    for (detectedAnchor in detectedAnchors) {
      val anchor = detectedAnchor.anchor
      if (anchor.trackingState != TrackingState.TRACKING) continue

      anchor.pose.toMatrix(modelMatrix, 0)
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

      // Update shader properties and draw
      virtualObjectShader.setMat4("u_ModelView", modelViewMatrix)
      virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
      virtualObjectShader.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
      render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)
    }


    // ----------------------------
    // --- Flicker effect logic ---
    // ----------------------------
    data class FlickerInfo(
      val index: Int,
      val wrappedAnchor: WrappedAnchor,
      val screenPos: Pair<Float, Float>?,
      val distanceToCamera: Float,
      val isInRestrictRegion: Boolean,
      val flickerOn: Boolean
    )

    val flickerInfos = mutableListOf<FlickerInfo>()

    for ((i, wrappedAnchor) in wrappedAnchors.withIndex()) {
      val anchor = wrappedAnchor.anchor
      if (anchor.trackingState != TrackingState.TRACKING) continue

      val flickerFrequencyHz = wrappedAnchor.flickerFrequencyHz
      val flickerPeriodSec = 1.0 / flickerFrequencyHz
      val elapsedSec = (System.nanoTime() - wrappedAnchor.createdTimestamp) / 1_000_000_000.0
      val flickerOn = (elapsedSec % flickerPeriodSec) < (flickerPeriodSec / 2)

      // Calculate flicker position above anchor
      val flickerPose = anchor.pose.compose(Pose.makeTranslation(0f, flickerOffsetY, 0f))
      val worldPosition = floatArrayOf(
        flickerPose.tx(), flickerPose.ty(), flickerPose.tz(), 1f
      )

      // Project to screen space (0..1)
      val screenPos = worldToScreen(
        worldPosition,
        viewMatrix,
        projectionMatrix,
        virtualSceneFramebuffer.getWidth(),
        virtualSceneFramebuffer.getHeight()
      )

      val isInRestrictRegion = screenPos?.let { (x, y) ->
        restrictRegion.contains(x, y)
      } ?: false

      // Calculate distance from camera to flicker
      val cameraPos = floatArrayOf(camera.pose.tx(), camera.pose.ty(), camera.pose.tz())
      val dx = flickerPose.tx() - cameraPos[0]
      val dy = flickerPose.ty() - cameraPos[1]
      val dz = flickerPose.tz() - cameraPos[2]
      val distanceToCamera = Math.sqrt((dx*dx + dy*dy + dz*dz).toDouble()).toFloat()

      flickerInfos.add(
        FlickerInfo(
          index = i,
          wrappedAnchor = wrappedAnchor,
          screenPos = screenPos,
          distanceToCamera = distanceToCamera,
          isInRestrictRegion = isInRestrictRegion,
          flickerOn = flickerOn
        )
      )
    }

    // Find the closest flicker in restrict region (for green highlight)
    val closestFlickerInRegion = flickerInfos
      .filter { it.isInRestrictRegion }
      .minByOrNull { it.distanceToCamera }


    // ---------------------------------
    // ------- Render flickers ---------
    // ---------------------------------
    for (info in flickerInfos) {
      if (!info.flickerOn) continue
      val wrappedAnchor = info.wrappedAnchor
      val anchor = wrappedAnchor.anchor

      // Calculate flicker pose above anchor
      val flickerPose = anchor.pose.compose(Pose.makeTranslation(0f, flickerOffsetY, 0f))
      val modelMatrix = FloatArray(16)
      flickerPose.toMatrix(modelMatrix, 0)

      // Scale sphere
      val scaleMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
      Matrix.scaleM(scaleMatrix, 0, flickerScale, flickerScale, flickerScale)
      val finalModelMatrix = FloatArray(16)
      Matrix.multiplyMM(finalModelMatrix, 0, modelMatrix, 0, scaleMatrix, 0)
      val modelViewProjectionMatrix = FloatArray(16)
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, modelViewProjectionMatrix, 0, finalModelMatrix, 0)

      val isCloset = closestFlickerInRegion != null && info.index == closestFlickerInRegion.index

      // If closest in restrict region: green, else white
      if (isCloset) {
        flickerShader.setVec4("u_Color", floatArrayOf(0f, 1f, 0f, 1f)) // Green
      } else {
        flickerShader.setVec4("u_Color", floatArrayOf(1f, 1f, 1f, 1f)) // White
      }
      flickerShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)

      render.draw(flickerMesh, flickerShader, virtualSceneFramebuffer)
    }

    // --- Compose the virtual scene with the background ---
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)
  }


  /** Checks if we detected at least one plane. */
  private fun Session.hasTrackingPlane() =
    getAllTrackables(Plane::class.java).any { it.trackingState == TrackingState.TRACKING }


  /** Update state based on the current frame's light estimation. */
  private fun updateLightEstimation(lightEstimate: LightEstimate, viewMatrix: FloatArray) {
    if (lightEstimate.state != LightEstimate.State.VALID) {
      virtualObjectShader.setBool("u_LightEstimateIsValid", false)
      return
    }
    virtualObjectShader.setBool("u_LightEstimateIsValid", true)
    Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0)
    virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix)
    updateMainLight(
      lightEstimate.environmentalHdrMainLightDirection,
      lightEstimate.environmentalHdrMainLightIntensity,
      viewMatrix
    )
    updateSphericalHarmonicsCoefficients(lightEstimate.environmentalHdrAmbientSphericalHarmonics)
    cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap())
  }


  /** Update main light direction and intensity for environmental lighting. */
  private fun updateMainLight(
    direction: FloatArray,
    intensity: FloatArray,
    viewMatrix: FloatArray
  ) {
    // Transform world light direction to view space
    worldLightDirection[0] = direction[0]
    worldLightDirection[1] = direction[1]
    worldLightDirection[2] = direction[2]
    Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0)
    virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection)
    virtualObjectShader.setVec3("u_LightIntensity", intensity)
  }


  /** Update spherical harmonics coefficients for environmental lighting. */
  private fun updateSphericalHarmonicsCoefficients(coefficients: FloatArray) {
    require(coefficients.size == 9 * 3) {
      "The given coefficients array must be of length 27 (3 components per 9 coefficients"
    }
    for (i in 0 until 9 * 3) {
      sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3]
    }
    virtualObjectShader.setVec3Array(
      "u_SphericalHarmonicsCoefficients",
      sphericalHarmonicsCoefficients
    )
  }

  // ------------------------------------------------------------------
  // --- User interaction: handle tap to place anchors in the scene ---
  // ------------------------------------------------------------------
  private fun handleTap(frame: Frame, camera: Camera) {
    if (camera.trackingState != TrackingState.TRACKING) return
    val tap = activity.view.tapHelper.poll() ?: return

    val hitResultList =
      if (activity.instantPlacementSettings.isInstantPlacementEnabled) {
        frame.hitTestInstantPlacement(tap.x, tap.y, APPROXIMATE_DISTANCE_METERS)
      } else {
        frame.hitTest(tap)
      }

    // ---------------------------------------------------------------
    // Hits are sorted by depth. Consider only closest hit on a plane.
    // ---------------------------------------------------------------
    val firstHitResult =
      hitResultList.firstOrNull { hit ->
        when (val trackable = hit.trackable!!) {
          is Plane ->
            trackable.isPoseInPolygon(hit.hitPose) &&
              PlaneRenderer.calculateDistanceToPlane(hit.hitPose, camera.pose) > 0
          is Point -> trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
          is InstantPlacementPoint -> true
          is DepthPoint -> true
          else -> false
        }
      }

    if (firstHitResult != null) {
      // Cap the number of objects created to avoid overloading
      if (wrappedAnchors.size >= maxAnchor) {
        wrappedAnchors.forEach { it.anchor.detach() }
        wrappedAnchors.clear()
      }

      // Add anchor at the hit position
      val anchorIndex = wrappedAnchors.size
      val flickerFrequency = flickerFrequencies[anchorIndex]

      wrappedAnchors.add(
        WrappedAnchor(
          firstHitResult.createAnchor(),
          firstHitResult.trackable,
          System.nanoTime(),
          flickerFrequency
          )
      )

      // Show occlusion dialog if needed (on UI thread)
      activity.runOnUiThread { activity.view.showOcclusionDialogIfNeeded() }
    }
  }


  // ---------------------------------------------------------------------
  // --- Utility: project 3D world position to normalized screen space ---
  // ---------------------------------------------------------------------
  private fun worldToScreen(
    worldPosition: FloatArray, // [x, y, z, 1]
    viewMatrix: FloatArray,
    projectionMatrix: FloatArray,
    viewportWidth: Int,
    viewportHeight: Int
  ): Pair<Float, Float>? {
    // Multiply by view matrix
    val tempVec = FloatArray(4)
    Matrix.multiplyMV(tempVec, 0, viewMatrix, 0, worldPosition, 0)

    // Multiply by projection matrix
    val clipVec = FloatArray(4)
    Matrix.multiplyMV(clipVec, 0, projectionMatrix, 0, tempVec, 0)

    // Perspective divide
    if (clipVec[3] == 0f) return null
    val ndcX = clipVec[0] / clipVec[3]
    val ndcY = clipVec[1] / clipVec[3]

    // Convert NDC (-1..1) to screen (0..1)
    val screenX = 0.5f * (ndcX + 1f)
    val screenY = 0.5f * (1f - ndcY)

    return Pair(screenX, screenY)
  }


  // --------------------------------------------------------------------
  // --- Object Detection Pipeline: handle detection and anchor logic ---
  // --------------------------------------------------------------------

  // Call this to trigger object detection in the next frame
  fun bindView(view: HelloArView) {
    this.view = view

    // When scan button is clicked, set flag to process camera image in onDrawFrame
    view.scanButton.setOnClickListener {
      scanButtonWasPressed = true
      view.setScanningActive(true)
      hideSnackbar()
    }

    view.resetButton.setOnClickListener {
      detectedAnchors.clear()
      view.resetButton.isEnabled = false
      hideSnackbar()
    }
  }

  // Acquire the current camera image, return null if not available
  fun Frame.tryAcquireCameraImage() = try {
    acquireCameraImage()
  } catch (e: NotYetAvailableException) {
    null
  } catch (e: Throwable) {
    throw e
  }

  // Buffers for coordinate conversion (used in anchor creation)
  private val convertFloats = FloatArray(4)
  private val convertFloatsOut = FloatArray(4)

  /** Create an anchor using (x, y) coordinates. */
  fun createAnchor(xImage: Float, yImage: Float, frame: Frame): Anchor? {
    // IMAGE_PIXELS -> VIEW
    convertFloats[0] = xImage
    convertFloats[1] = yImage
    frame.transformCoordinates2d(
      Coordinates2d.IMAGE_PIXELS,
      convertFloats,
      Coordinates2d.VIEW,
      convertFloatsOut
    )

    // Conduct a hit test using the VIEW coordinates
    val hits = frame.hitTest(convertFloatsOut[0], convertFloatsOut[1])
    val result = hits.getOrNull(0) ?: return null
    return result.trackable.createAnchor(result.hitPose)
  }

  /** Show error message on the UI. */
  private fun showError(errorMessage: String) =
    activity.view.snackbarHelper.showError(activity, errorMessage)
}

/** Associates an Anchor with the trackable it was attached to. */
private data class WrappedAnchor(
  val anchor: Anchor,
  val trackable: Trackable,
  val createdTimestamp: Long = System.nanoTime(),
  val flickerFrequencyHz: Double
)

/** Stores an anchor and its associated label (for object detection). */
data class DetectedAnchor(
  val anchor: Anchor,
  val label: String
)
