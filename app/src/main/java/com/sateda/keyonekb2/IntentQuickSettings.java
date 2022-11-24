package com.sateda.keyonekb2;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class IntentQuickSettings extends AppCompatActivity {

    public static final String ACTION = "com.sateda.keyonekb2.IntentQuickSettings";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //final Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        //final Intent intent = new Intent(Settings.System.);
        //sendBroadcast();

        if(KeyoneKb2AccessibilityService.Instance != null)
            KeyoneKb2AccessibilityService.Instance.IntentQuickSettings();


        //startActivity(intent);
        finish();
    }


}


