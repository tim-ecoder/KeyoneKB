package com.sateda.keyonekb.input;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import static android.content.ContentValues.TAG;

public class CallStateCallback extends PhoneStateListener {

    private boolean calling;

    public void onCallStateChanged(int state, String incomingNumber) {

        Log.d(TAG, "onCallStateChanged state = "+state);
        calling = state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK;
    }

    public boolean isCalling() {
        return calling;
    }
}