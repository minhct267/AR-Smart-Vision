package com.google.ar.core.examples.kotlin.common.helpers

import android.app.Activity
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper
import com.google.ar.core.exceptions.CameraNotAvailableException

/** Manages an ARCore Session using the Android Lifecycle API. */
class ARCoreSessionLifecycleHelper(
  val activity: Activity,
  val features: Set<Session.Feature> = setOf()
) : DefaultLifecycleObserver {
  var installRequested = false
  var session: Session? = null
    private set

  var exceptionCallback: ((Exception) -> Unit)? = null
  var beforeSessionResume: ((Session) -> Unit)? = null

  private fun tryCreateSession(): Session? {
    if (!CameraPermissionHelper.hasCameraPermission(activity)) {
      CameraPermissionHelper.requestCameraPermission(activity)
      return null
    }

    return try {
      // Request installation if necessary.
      when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)!!) {
        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
          installRequested = true
          // tryCreateSession will be called again, so we return null for now.
          return null
        }
        ArCoreApk.InstallStatus.INSTALLED -> {
          // Left empty; nothing needs to be done.
        }
      }

      // Create a session if Google Play Services for AR is installed and up to date.
      Session(activity, features)
    } catch (e: Exception) {
      exceptionCallback?.invoke(e)
      null
    }
  }

  /** Called when the LifecycleOwner (Activity/Fragment) resumes. */
  override fun onResume(owner: LifecycleOwner) {
    val session = this.session ?: tryCreateSession() ?: return
    try {
      beforeSessionResume?.invoke(session) // Allows session configuration before resuming
      session.resume() // Resume the session
      this.session = session // Store the session
    } catch (e: CameraNotAvailableException) {
      exceptionCallback?.invoke(e) // Handle error if camera is not available
    }
  }

  /** Called when the LifecycleOwner pauses. */
  override fun onPause(owner: LifecycleOwner) {
    session?.pause()
  }

  /** Closes the session to release native resources. */
  override fun onDestroy(owner: LifecycleOwner) {
    session?.close()
    session = null
  }

  /** Handles the result of the camera permission request. */
  fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    results: IntArray
  ) {
    if (!CameraPermissionHelper.hasCameraPermission(activity)) {
      // Use toast instead of snackbar here since the activity will exit.
      Toast.makeText(
          activity,
          "Camera permission is needed to run this application",
          Toast.LENGTH_LONG
        )
        .show()
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(activity)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(activity)
      }
      activity.finish()
    }
  }
}
