package com.google.ar.core.examples.java.common.helpers;

import android.content.Context;
import android.content.SharedPreferences;

public class InstantPlacementSettings {
  public static final String SHARED_PREFERENCES_ID = "SHARED_PREFERENCES_INSTANT_PLACEMENT_OPTIONS";
  public static final String SHARED_PREFERENCES_INSTANT_PLACEMENT_ENABLED = "instant_placement_enabled";
  private boolean instantPlacementEnabled = true;
  private SharedPreferences sharedPreferences;

  public void onCreate(Context context) {
    sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_ID, Context.MODE_PRIVATE);
    instantPlacementEnabled =
        sharedPreferences.getBoolean(SHARED_PREFERENCES_INSTANT_PLACEMENT_ENABLED, false);
  }

  public boolean isInstantPlacementEnabled() {
    return instantPlacementEnabled;
  }

  public void setInstantPlacementEnabled(boolean enable) {
    if (enable == instantPlacementEnabled) {
      return;
    }
    instantPlacementEnabled = enable;
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putBoolean(SHARED_PREFERENCES_INSTANT_PLACEMENT_ENABLED, instantPlacementEnabled);
    editor.apply();
  }
}
