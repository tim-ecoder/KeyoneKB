package com.ai10.k12kb;

import android.app.Activity;
import android.os.Bundle;

public class IntentQuickSettings extends Activity {

    // С applicationId, иначе dev-сборка и основная слушали бы один и тот же
    // action и перехватывали намерения друг друга.
    public static final String ACTION = BuildConfig.APPLICATION_ID + ".IntentQuickSettings";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //final Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        //final Intent intent = new Intent(Settings.System.);
        //sendBroadcast();

        if(K12KbAccessibilityService.Instance != null)
            K12KbAccessibilityService.Instance.IntentQuickSettings();


        //startActivity(intent);
        finish();
    }


}


