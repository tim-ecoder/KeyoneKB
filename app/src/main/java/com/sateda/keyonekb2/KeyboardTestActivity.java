package com.sateda.keyonekb2;


import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import static android.content.ContentValues.TAG;

public class KeyboardTestActivity extends Activity {
    private View codeTitleView;

    private TextView codeView;

    private TextView deviceInfoView;

    private EditText inputView;

    private TextView metaInfoView;

    private TextView touchInfoView;

    private View scanCodeTitleView;

    private TextView scanCodeView;

    private CheckBox showScanCodeView;

    private TextView debugView;

    private ScrollView debugScrollView;

    private void updateViews(KeyEvent paramKeyEvent) {
        this.codeView.setText(String.valueOf(paramKeyEvent.getKeyCode()));
        this.scanCodeView.setText(String.valueOf(paramKeyEvent.getScanCode()));
        this.metaInfoView.setText("alt:" + paramKeyEvent.isAltPressed() + ", shift:" + paramKeyEvent.isShiftPressed());
        if(!KeyoneIME.DEBUG_TEXT.isEmpty()) {
            this.debugView.setText(KeyoneIME.DEBUG_TEXT);
            debugScrollView.post(new Runnable() {

                @Override
                public void run() {
                    debugScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        }
    }

    public void onStop() {
        KeyoneIME.IS_KEYBOARD_TEST = false;
        KeyoneIME.DEBUG_TEXT = "";
        super.onStop();
    }

    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        setContentView(R.layout.activity_keyboard_test);
        KeyoneIME.IS_KEYBOARD_TEST = true;
        this.debugView = (TextView)findViewById(R.id.debug_info_data);
        this.debugView.setMovementMethod(new ScrollingMovementMethod());
        this.debugScrollView = (ScrollView) findViewById(R.id.debug_scroll);
        this.touchInfoView = (TextView)findViewById(R.id.touch_info);
        this.codeView = (TextView)findViewById(R.id.code);
        this.codeTitleView = findViewById(R.id.codeTitle);
        this.scanCodeView = (TextView)findViewById(R.id.scanCode);
        this.scanCodeTitleView = findViewById(R.id.scanCodeTitle);
        this.showScanCodeView = (CheckBox)findViewById(R.id.showScanCode);
        this.showScanCodeView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton param1CompoundButton, boolean param1Boolean) {
                byte b;
                byte bool = 0;
                TextView textView = KeyboardTestActivity.this.scanCodeView;
                if (param1Boolean) {
                    b = 0;
                } else {
                    b = 8;
                }
                textView.setVisibility(b);
                View view = KeyboardTestActivity.this.scanCodeTitleView;
                if (param1Boolean) {
                    b = bool;
                } else {
                    b = 8;
                }
                view.setVisibility(b);
            }
        });
        this.inputView = (EditText)findViewById(R.id.input);
        this.inputView.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View param1View, int param1Int, KeyEvent param1KeyEvent) {
                KeyboardTestActivity.this.updateViews(param1KeyEvent);
                return false;
            }
        });

        this.inputView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View param1View, boolean param1Boolean) {
                if (!param1Boolean) {
                    //KeyboardTestActivity.this.inputView.requestFocus();
                }
            }
        });


        this.inputView.setOnGenericMotionListener(new View.OnGenericMotionListener() {
                @Override
                public boolean onGenericMotion(View v, MotionEvent event) {

                    String text = "View.onGenericMotionEvent() X = "+event.getX()+", Y ="+event.getY() +" ACT: "+event.getAction();
                    Log.d(TAG, text);
                    KeyboardTestActivity.this.touchInfoView.setText(text);
                    return true;
                }
        });




        //this.inputView

        this.inputView.requestFocus();
        this.metaInfoView = (TextView)findViewById(R.id.meta_info);
        this.deviceInfoView = (TextView)findViewById(R.id.device_info);
        this.deviceInfoView.append("board: " + Build.BOARD);
        this.deviceInfoView.append("\n");
        this.deviceInfoView.append("product: " + Build.PRODUCT);
        this.deviceInfoView.append("\n");
        this.deviceInfoView.append("device: " + Build.DEVICE);
        this.deviceInfoView.append("\n");
        this.deviceInfoView.append("display: " + Build.DISPLAY);
        this.deviceInfoView.append("\n");
        this.deviceInfoView.append("brand: " + Build.BRAND);
    }
}
