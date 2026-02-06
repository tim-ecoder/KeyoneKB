package com.ai10.k12kb;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class IntentQuickSettings extends AppCompatActivity {

    public static final String ACTION = "com.ai10.k12kb.IntentQuickSettings";

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


