package com.google.ar.core.examples.java.common.helpers;

import android.content.Context;
import android.content.SharedPreferences;

public class DepthSettings {
  public static final String SHARED_PREFERENCES_ID = "SHARED_PREFERENCES_OCCLUSION_OPTIONS";
  public static final String SHARED_PREFERENCES_SHOW_DEPTH_ENABLE_DIALOG_OOBE = "show_depth_enable_dialog_oobe";
  public static final String SHARED_PREFERENCES_USE_DEPTH_FOR_OCCLUSION = "use_depth_for_occlusion";

  private boolean depthColorVisualizationEnabled = false;
  private boolean useDepthForOcclusion = false;
  private SharedPreferences sharedPreferences;

  public void onCreate(Context context) {
    sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_ID, Context.MODE_PRIVATE);
    useDepthForOcclusion =
        sharedPreferences.getBoolean(SHARED_PREFERENCES_USE_DEPTH_FOR_OCCLUSION, false);
  }

  public boolean useDepthForOcclusion() {
    return useDepthForOcclusion;
  }

  public void setUseDepthForOcclusion(boolean enable) {
    if (enable == useDepthForOcclusion) {
      return;
    }
    useDepthForOcclusion = enable;
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putBoolean(SHARED_PREFERENCES_USE_DEPTH_FOR_OCCLUSION, useDepthForOcclusion);
    editor.apply();
  }

  public boolean depthColorVisualizationEnabled() {
    return depthColorVisualizationEnabled;
  }

  public void setDepthColorVisualizationEnabled(boolean depthColorVisualizationEnabled) {
    this.depthColorVisualizationEnabled = depthColorVisualizationEnabled;
  }

  public boolean shouldShowDepthEnableDialog() {
    boolean showDialog =
        sharedPreferences.getBoolean(SHARED_PREFERENCES_SHOW_DEPTH_ENABLE_DIALOG_OOBE, true);

    if (showDialog) {
      SharedPreferences.Editor editor = sharedPreferences.edit();
      editor.putBoolean(SHARED_PREFERENCES_SHOW_DEPTH_ENABLE_DIALOG_OOBE, false);
      editor.apply();
    }

    return showDialog;
  }
}
