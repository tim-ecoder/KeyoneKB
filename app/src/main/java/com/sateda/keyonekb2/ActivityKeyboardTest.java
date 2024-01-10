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

import static com.sateda.keyonekb2.InputMethodServiceCoreKeyPress.TAG2;

public class ActivityKeyboardTest extends Activity implements InputMethodServiceCoreKeyPress.IDebugUpdate {

    private TextView codeView;
    private TextView codeMetaView;
    private TextView touchInfoView;
    private TextView debugView;
    private ScrollView debugScrollView;



    public void onStop() {
        KeyoneIME.IS_KEYBOARD_TEST = false;
        KeyoneIME.DEBUG_TEXT = "";
        super.onStop();
    }

    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        setContentView(R.layout.activity_keyboard_test);
        KeyoneIME.IS_KEYBOARD_TEST = true;
        KeyoneIME.DEBUG_UPDATE = this;

        this.debugView = (TextView)findViewById(R.id.debug_info_data);
        this.debugView.setMovementMethod(new ScrollingMovementMethod());
        this.debugScrollView = (ScrollView) findViewById(R.id.debug_scroll);
        this.touchInfoView = (TextView)findViewById(R.id.touch_info);
        this.codeView = (TextView)findViewById(R.id.tv_code_value);
        this.codeMetaView = (TextView)findViewById(R.id.tv_key_meta_value);

        CheckBox sbTestKeyboardViewMode = (CheckBox) findViewById(R.id.cb_test_keyboard_view_mode);
        sbTestKeyboardViewMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton param1CompoundButton, boolean param1Boolean) {
                UpdateCheckBoxAtFocus(sbTestKeyboardViewMode);
            }
        });

        sbTestKeyboardViewMode.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View param1View, boolean param1Boolean) {
                UpdateCheckBoxAtFocus(sbTestKeyboardViewMode);
            }
        });

        sbTestKeyboardViewMode.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View param1View, int param1Int, KeyEvent param1KeyEvent) {
                ActivityKeyboardTest.this.updateViews(param1KeyEvent);
                return false;
            }
        });


        EditText inputView = (EditText) findViewById(R.id.input);
        inputView.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View param1View, int param1Int, KeyEvent param1KeyEvent) {
                ActivityKeyboardTest.this.updateViews(param1KeyEvent);
                return false;
            }
        });

        inputView.setOnGenericMotionListener(new View.OnGenericMotionListener() {
                @Override
                public boolean onGenericMotion(View v, MotionEvent event) {
                    String text = "View.onGenericMotionEvent() X = "+event.getX()+", Y ="+event.getY() +" ACT: "+event.getAction();
                    Log.d(TAG2, text);
                    ActivityKeyboardTest.this.touchInfoView.setText(text);
                    return true;
                }
        });


        //this.inputView

        sbTestKeyboardViewMode.requestFocus();

        TextView deviceInfoView = (TextView) findViewById(R.id.device_info);
        deviceInfoView.append("board: " + Build.BOARD);
        deviceInfoView.append("\n");
        deviceInfoView.append("product: " + Build.PRODUCT);
        deviceInfoView.append("\n");
        deviceInfoView.append("device: " + Build.DEVICE);
        deviceInfoView.append("\n");
        deviceInfoView.append("display: " + Build.DISPLAY);
        deviceInfoView.append("\n");
        deviceInfoView.append("brand: " + Build.BRAND);


        RedrawDebug();
    }

    private void updateViews(KeyEvent paramKeyEvent) {
        this.codeView.setText(String.valueOf(paramKeyEvent.getKeyCode()));
        this.codeMetaView.setText(Integer.toBinaryString(paramKeyEvent.getMetaState()));

        RedrawDebug();
    }

    private void RedrawDebug() {
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

    private void UpdateCheckBoxAtFocus(CheckBox sbTestKeyboardViewMode) {
        if(sbTestKeyboardViewMode.isFocused()) {
            sbTestKeyboardViewMode.setChecked(true);
            sbTestKeyboardViewMode.setText(R.string.keyboard_test_view_test_active);
        } else {
            sbTestKeyboardViewMode.setChecked(false);
            sbTestKeyboardViewMode.setText(R.string.keyboard_test_view_test);
        }
    }

    @Override
    public void DebugUpdated() {
        RedrawDebug();
    }
}
