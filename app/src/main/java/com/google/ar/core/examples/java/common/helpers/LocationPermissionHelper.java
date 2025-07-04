package com.google.ar.core.examples.java.common.helpers;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public final class LocationPermissionHelper {
  private static final int LOCATION_PERMISSION_CODE = 1;
  private static final String LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;

  public static boolean hasFineLocationPermission(Activity activity) {
    return ContextCompat.checkSelfPermission(activity, LOCATION_PERMISSION)
        == PackageManager.PERMISSION_GRANTED;
  }

  public static void requestFineLocationPermission(Activity activity) {
    ActivityCompat.requestPermissions(
        activity, new String[] {LOCATION_PERMISSION}, LOCATION_PERMISSION_CODE);
  }

  public static boolean hasFineLocationPermissionsResponseInResult(String[] permissions) {
    for (String permission : permissions) {
      if (LOCATION_PERMISSION.equals(permission)) {
        return true;
      }
    }
    return false;
  }

  public static boolean shouldShowRequestPermissionRationale(Activity activity) {
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCATION_PERMISSION);
  }

  public static void launchPermissionSettings(Activity activity) {
    Intent intent = new Intent();
    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
    activity.startActivity(intent);
  }
}
