package com.google.ar.core.examples.java.common.helpers;

import android.content.Context;
import android.content.SharedPreferences;

public class EisSettings {
  public static final String SHARED_PREFERENCE_ID = "SHARED_PREFERENCE_EIS_OPTIONS";
  public static final String SHARED_PREFERENCE_EIS_ENABLED = "eis_enabled";
  private boolean eisEnabled = false;
  private SharedPreferences sharedPreferences;

  public void onCreate(Context context) {
    sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCE_ID, Context.MODE_PRIVATE);
    eisEnabled = sharedPreferences.getBoolean(SHARED_PREFERENCE_EIS_ENABLED, false);
  }

  public boolean isEisEnabled() {
    return eisEnabled;
  }

  public void setEisEnabled(boolean enable) {
    if (enable == eisEnabled) {
      return;
    }
    eisEnabled = enable;
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putBoolean(SHARED_PREFERENCE_EIS_ENABLED, eisEnabled);
    editor.apply();
  }
}
