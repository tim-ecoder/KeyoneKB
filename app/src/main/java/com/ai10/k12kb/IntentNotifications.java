package com.ai10.k12kb;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class IntentNotifications extends AppCompatActivity {

    public static final String ACTION = "com.ai10.k12kb.IntentNotifications";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (KeyoneKb2AccessibilityService.Instance != null)
            KeyoneKb2AccessibilityService.Instance.IntentNotifications();

        finish();
    }


}
