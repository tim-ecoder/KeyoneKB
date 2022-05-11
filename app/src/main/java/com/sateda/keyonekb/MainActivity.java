package com.sateda.keyonekb;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    private Button btn_test_key;
    private Button btn_settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_settings = (Button) findViewById(R.id.btn_settings);
        btn_test_key = (Button) findViewById(R.id.btn_test_key);

        btn_settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSettingsActivity();
            }
        });

        btn_test_key.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setTestKeyActivity();
            }
        });

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

        Log.d(TAG, "sensorManager deviceSensors.get(i).getName() ");
        //List<String> listSensorType = new ArrayList<>();
        for (int i = 0; i < deviceSensors.size(); i++) {
            //listSensorType.add(deviceSensors.get(i).getName());
            Log.d(TAG, "sensorManager "+deviceSensors.get(i).getName()+" "+deviceSensors.get(i).getVendor());
        }

    }

    private void setSettingsActivity() {
        Intent switchActivityIntent = new Intent(this, SettingsActivity.class);
        startActivity(switchActivityIntent);
    }

    private void setTestKeyActivity() {
        Intent switchActivityIntent = new Intent(this, KeyboardTestActivity.class);
        startActivity(switchActivityIntent);
    }

}