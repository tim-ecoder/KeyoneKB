package com.ai10.k12kb.input;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import static com.ai10.k12kb.InputMethodServiceCoreKeyPress.TAG2;

public class CallStateCallback extends PhoneStateListener {

    private boolean calling;

    public void onCallStateChanged(int state, String incomingNumber) {

        Log.d(TAG2, "onCallStateChanged state = "+state);
        if(state == TelephonyManager.CALL_STATE_IDLE)
            calling = false;
        else
            calling = true;
        //if(state == TelephonyManager.CALL_STATE_RINGING)
        //else if(state == TelephonyManager.CALL_STATE_OFFHOOK)
        //    calling = true;

    }

    public boolean isCalling() {
        return calling;
    }
}