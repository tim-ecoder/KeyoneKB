package com.ai10.k12kb;

import android.app.Activity;
import android.os.Bundle;

public class IntentNotifications extends Activity {

    // См. комментарий в IntentQuickSettings — action должен быть уникален
    // для каждой установленной сборки.
    public static final String ACTION = BuildConfig.APPLICATION_ID + ".IntentNotifications";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (K12KbAccessibilityService.Instance != null)
            K12KbAccessibilityService.Instance.IntentNotifications();

        finish();
    }


}
