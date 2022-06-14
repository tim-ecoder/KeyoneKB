package com.sateda.keyonekb2;

import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import static android.content.ContentValues.TAG;

public abstract class GestureKeyboardBase extends KeyPressKeyboardBase {
    private int MAGIC_KEYBOARD_GESTURE_MOTION_CONST;
    private int ROW_4_BEGIN_Y;
    public int TIME_WAIT_GESTURE_UPON_KEY_0;
    protected float lastGestureX;
    protected boolean mode_keyboard_gestures = false;
    protected boolean mode_keyboard_gestures_plus_up_down = false;
    protected int pref_gesture_motion_sensitivity = 10;
    protected boolean pref_keyboard_gestures_at_views_enable = true;
    protected long lastGestureSwipingBeginTime = 0;
    long prevDownTime = 0;
    long prevUpTime = 0;
    long prevPrevDownTime = 0;
    long prevPrevUpTime = 0;
    boolean modeDoubleClickGesture = false;
    private float lastGestureY;
    private boolean enteredGestureMovement = false;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate() {
        super.onCreate();
        MAGIC_KEYBOARD_GESTURE_MOTION_CONST = Integer.parseInt(getString(R.string.KB_CORE_MOTION_BASE_SENSITIVITY));
        ROW_4_BEGIN_Y = Integer.parseInt(getString(R.string.KB_CORE_ROW_4_BEGIN_Y));
        TIME_WAIT_GESTURE_UPON_KEY_0 = Integer.parseInt(getString(R.string.WAIT_GESTURE_UPON_KEY_0));
    }

    protected static void keyDownUpDefaultFlags(int keyEventCode, InputConnection ic) {
        if (ic == null) return;

        ic.sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        ic.sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    private static void keyDownUp(int keyEventCode, InputConnection ic, int meta, int flag) {
        if (ic == null) return;
        long now = SystemClock.uptimeMillis();

        ic.sendKeyEvent(
                new KeyEvent(now - 3, now - 2, KeyEvent.ACTION_DOWN, keyEventCode, 0,
                        meta, -1, 0,
                        flag, 0x101));
        ic.sendKeyEvent(
                new KeyEvent(now - 1, now, KeyEvent.ACTION_UP, keyEventCode, 0,
                        meta, -1, 0,
                        flag, 0x101));
    }

    protected static void keyDownUpKeepTouch(int keyEventCode, InputConnection ic, int meta) {
        GestureKeyboardBase.keyDownUp(keyEventCode, ic, meta,KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
    }

    protected boolean ProcessGestureAtMotionEvent(MotionEvent motionEvent) {
        if (pref_keyboard_gestures_at_views_enable
                && !IsNavMode()
                && !IsInputMode()) {
            return false;
        }

        if (IsNavMode())
            return true;

        if (!mode_keyboard_gestures && motionEvent.getY() <= ROW_4_BEGIN_Y) {
            //if (debug_gestures)
            //    Log.d(TAG, "onGenericMotionEvent(): " + motionEvent);
            ProcessPrepareAtHoldGesture(motionEvent);
        }

        if ((!mode_keyboard_gestures && !mode_keyboard_gestures_plus_up_down)
            || modeDoubleClickGesture) {
            ProcessDoubleGestureClick(motionEvent);
        }
        if (mode_keyboard_gestures) {

            //TODO: Подумать отдельно обрабатывать жесты по горизонтали и отдельно по вертикали ориентируясь на событие ACTION_UP

            InputConnection inputConnection = getCurrentInputConnection();
            float motionEventX = motionEvent.getX();
            float motionEventY = motionEvent.getY();

            //Не ловим движение на нижнем ряду где поблел и переключение языка
            if (motionEventY > ROW_4_BEGIN_Y) return true;

            int motionEventAction = motionEvent.getAction();

            if (PerformGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, motionEventAction))
                return true;

        }

        return true;
    }

    private boolean PerformGestureAction(MotionEvent motionEvent, InputConnection inputConnection, float motionEventX, float motionEventY, int motionEventAction) {
        //Жесть по клавиатуре всегда начинается с ACTION_DOWN
        if (motionEventAction == MotionEvent.ACTION_DOWN
                || motionEventAction == MotionEvent.ACTION_POINTER_DOWN) {
            //if (debug_gestures)
            //    Log.d(TAG, "onGenericMotionEvent ACTION_DOWN " + motionEvent);
            lastGestureX = motionEventX;
            lastGestureY = motionEventY;
            return true;
        }

        if (motionEventAction == MotionEvent.ACTION_MOVE
                || motionEventAction == MotionEvent.ACTION_UP
                || motionEventAction == MotionEvent.ACTION_POINTER_UP) {
            float deltaX = motionEventX - lastGestureX;
            float absDeltaX = deltaX < 0 ? -1 * deltaX : deltaX;
            float deltaY = motionEventY - lastGestureY;
            float absDeltaY = deltaY < 0 ? -1 * deltaY : deltaY;

            int motion_delta_min_x = MAGIC_KEYBOARD_GESTURE_MOTION_CONST - pref_gesture_motion_sensitivity;
            int motion_delta_min_y = MAGIC_KEYBOARD_GESTURE_MOTION_CONST - pref_gesture_motion_sensitivity;

            if (absDeltaX >= absDeltaY) {
                if (absDeltaX < motion_delta_min_x)
                    return true;
                if (deltaX > 0) {
                    if(MoveCursorRightSafe(inputConnection))
                        Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_RIGHT " + motionEvent);
                } else {
                    if(MoveCursorLeftSafe(inputConnection))
                        Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_LEFT " + motionEvent);
                }
            } else if (mode_keyboard_gestures_plus_up_down) {
                if (absDeltaY < motion_delta_min_y)
                    return true;
                //int times = Math.round(absDeltaY / motion_delta_min_y);
                if (deltaY < 0) {
                    if(MoveCursorUpSafe(inputConnection))
                        Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_UP " + motionEvent);
                } else {
                    if(MoveCursorDownSafe(inputConnection))
                        Log.d(TAG, "onGenericMotionEvent KEYCODE_DPAD_DOWN  " + motionEvent);
                }
            }

            lastGestureX = motionEventX;
            lastGestureY = motionEventY;
        }
        return false;
    }

    private boolean MoveCursorDownSafe(InputConnection inputConnection) {
        CharSequence c = inputConnection.getTextAfterCursor(1, 0);
        if (c.length() > 0) {
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
            DetermineFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    private boolean MoveCursorUpSafe(InputConnection inputConnection) {
        CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
        if (c.length() > 0) {
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
            DetermineFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    protected boolean MoveCursorLeftSafe(InputConnection inputConnection) {
        CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
        if (c.length() > 0) {
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
            DetermineFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    protected boolean MoveCursorRightSafe(InputConnection inputConnection) {
        CharSequence c = inputConnection.getTextAfterCursor(1, 0);
        if (c.length() > 0) {
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
            DetermineFirstBigCharStateAndUpdateVisualization(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    protected abstract boolean DetermineFirstBigCharStateAndUpdateVisualization(EditorInfo editorInfo);

    private void ProcessPrepareAtHoldGesture(MotionEvent motionEvent) {


        if (motionEvent.getAction() == MotionEvent.ACTION_UP
                || motionEvent.getAction() == MotionEvent.ACTION_CANCEL
                || motionEvent.getAction() == MotionEvent.ACTION_POINTER_UP
        ) {
            lastGestureSwipingBeginTime = 0;
            enteredGestureMovement = false;
        }
        if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && enteredGestureMovement) {
            lastGestureSwipingBeginTime = SystemClock.uptimeMillis();
            lastGestureX = motionEvent.getX();
            lastGestureY = motionEvent.getY();
        }
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN
                || motionEvent.getAction() == MotionEvent.ACTION_POINTER_DOWN) {

            lastGestureSwipingBeginTime = SystemClock.uptimeMillis();
            lastGestureX = motionEvent.getX();
            lastGestureY = motionEvent.getY();
            enteredGestureMovement = true;
        }
    }

    private void ProcessDoubleGestureClick(MotionEvent motionEvent) {


        if (motionEvent.getAction() == MotionEvent.ACTION_UP
                || motionEvent.getAction() == MotionEvent.ACTION_CANCEL
                || motionEvent.getAction() == MotionEvent.ACTION_POINTER_UP
        ) {
            if(modeDoubleClickGesture) {
                modeDoubleClickGesture = false;
                mode_keyboard_gestures = false;
                mode_keyboard_gestures_plus_up_down = false;
                UpdateGestureModeVisualization();
            } else {
                prevPrevUpTime = prevUpTime;
                prevUpTime = motionEvent.getEventTime();
            }
        }
        else if (motionEvent.getAction() == MotionEvent.ACTION_DOWN
                || motionEvent.getAction() == MotionEvent.ACTION_POINTER_DOWN) {

            long curDownTime = motionEvent.getEventTime();
            if(     prevUpTime - prevDownTime <= TIME_LONG_PRESS
                    && prevPrevUpTime - prevPrevDownTime <= TIME_LONG_PRESS
                    && prevUpTime - prevPrevDownTime <= TIME_DOUBLE_PRESS
                    && curDownTime - prevUpTime <= TIME_DOUBLE_PRESS) {
                Log.d(TAG2, "GESTURE TRIPLE CLICK");
                mode_keyboard_gestures = true;
                mode_keyboard_gestures_plus_up_down = true;
                modeDoubleClickGesture = true;
                UpdateGestureModeVisualization();
            }
            else if(     prevUpTime - prevDownTime <= TIME_LONG_PRESS
                    && curDownTime - prevUpTime <= TIME_DOUBLE_PRESS) {
                Log.d(TAG2, "GESTURE DOUBLE CLICK");
                mode_keyboard_gestures = true;
                modeDoubleClickGesture = true;
                UpdateGestureModeVisualization();
            }

            prevPrevDownTime = prevDownTime;
            prevDownTime = curDownTime;


        }
    }

    protected void TurnOffGesturesMode() {
        //mode_keyboard_gestures_plus_up_down = false;
        if (mode_keyboard_gestures) {
            mode_keyboard_gestures = false;
            UpdateGestureModeVisualization();
        }
    }

    protected abstract void UpdateGestureModeVisualization();

    protected void ResetDoubleClickGestureState() {
        prevDownTime = 0;
        prevUpTime = 0;
        prevPrevDownTime = 0;
        prevPrevUpTime = 0;
    }

    //Если через него отправлять навигационные и прочие команды, не будет активироваться выделение виджета на рабочем столе
    protected void keyDownUpNoMetaKeepTouch(int keyEventCode, InputConnection ic) {
        GestureKeyboardBase.keyDownUp(keyEventCode, ic, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
    }

    protected abstract boolean IsNavMode();

    protected boolean IsInputMode() {
        return getCurrentInputEditorInfo().inputType > 0;
    }
}
