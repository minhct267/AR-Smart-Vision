package com.google.ar.core.examples.java.common.helpers;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

public final class SnackbarHelper {
  private static final int BACKGROUND_COLOR = 0xbf323232;
  private Snackbar messageSnackbar;
  private enum DismissBehavior { HIDE, SHOW, FINISH };
  private int maxLines = 2;
  private String lastMessage = "";
  private View snackbarView;

  public boolean isShowing() {
    return messageSnackbar != null;
  }

  public void showMessage(Activity activity, String message) {
    if (!message.isEmpty() && (!isShowing() || !lastMessage.equals(message))) {
      lastMessage = message;
      show(activity, message, DismissBehavior.HIDE);
    }
  }

  public void showMessageWithDismiss(Activity activity, String message) {
    show(activity, message, DismissBehavior.SHOW);
  }

  public void showMessageForShortDuration(Activity activity, String message) {
    show(activity, message, DismissBehavior.SHOW, Snackbar.LENGTH_SHORT);
  }

  public void showMessageForLongDuration(Activity activity, String message) {
    show(activity, message, DismissBehavior.SHOW, Snackbar.LENGTH_LONG);
  }

  public void showError(Activity activity, String errorMessage) {
    show(activity, errorMessage, DismissBehavior.FINISH);
  }

  public void hide(Activity activity) {
    if (!isShowing()) {
      return;
    }
    lastMessage = "";
    Snackbar messageSnackbarToHide = messageSnackbar;
    messageSnackbar = null;
    activity.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            messageSnackbarToHide.dismiss();
          }
        });
  }

  public void setMaxLines(int lines) {
    maxLines = lines;
  }

  public boolean isDurationIndefinite() {
    return isShowing() && messageSnackbar.getDuration() == Snackbar.LENGTH_INDEFINITE;
  }

  public void setParentView(View snackbarView) {
    this.snackbarView = snackbarView;
  }

  private void show(Activity activity, String message, DismissBehavior dismissBehavior) {
    show(activity, message, dismissBehavior, Snackbar.LENGTH_INDEFINITE);
  }

  private void show(
      final Activity activity,
      final String message,
      final DismissBehavior dismissBehavior,
      int duration) {
    activity.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            messageSnackbar =
                Snackbar.make(
                    snackbarView == null
                        ? activity.findViewById(android.R.id.content)
                        : snackbarView,
                    message,
                    duration);
            messageSnackbar.getView().setBackgroundColor(BACKGROUND_COLOR);
            if (dismissBehavior != DismissBehavior.HIDE && duration == Snackbar.LENGTH_INDEFINITE) {
              messageSnackbar.setAction(
                  "Dismiss",
                  new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                      messageSnackbar.dismiss();
                    }
                  });
              if (dismissBehavior == DismissBehavior.FINISH) {
                messageSnackbar.addCallback(
                    new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                      @Override
                      public void onDismissed(Snackbar transientBottomBar, int event) {
                        super.onDismissed(transientBottomBar, event);
                        activity.finish();
                      }
                    });
              }
            }
            ((TextView)
                    messageSnackbar
                        .getView()
                        .findViewById(com.google.android.material.R.id.snackbar_text))
                .setMaxLines(maxLines);
            messageSnackbar.show();
          }
        });
  }
}
