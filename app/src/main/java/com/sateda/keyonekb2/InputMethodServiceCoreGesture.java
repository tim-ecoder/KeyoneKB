package com.sateda.keyonekb2;

import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public abstract class InputMethodServiceCoreGesture extends InputMethodServiceCoreKeyPress {
    private int MAGIC_KEYBOARD_GESTURE_MOTION_CONST;
    private int MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST ;
    private int ROW_4_BEGIN_Y;
    private int ROW_1_BEGIN_Y;
    protected int TIME_WAIT_GESTURE_UPON_KEY_0;
    private float lastGestureX;
    protected boolean mode_keyboard_gestures = false;
    protected boolean mode_keyboard_gestures_plus_up_down = false;
    protected int pref_gesture_motion_sensitivity = 10;
    protected long lastGestureSwipingBeginTime = 0;
    long prevDownTime = 0;
    long prevUpTime = 0;
    long prevPrevDownTime = 0;
    long prevPrevUpTime = 0;

    float prevX = 0;
    float prevY = 0;
    float prevPrevX = 0;
    float prevPrevY = 0;
    boolean modeDoubleClickGesture = false;
    private float lastGestureY;
    private boolean enteredGestureMovement = false;

    protected Processable OnGestureDoubleClick;
    protected Processable OnGestureTripleClick;
    protected Processable OnGestureSecondClickUp;

    protected boolean KeyboardGesturesAtInputModeEnabled;

    protected boolean KeyHoldPlusGestureEnabled;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate() {
        super.onCreate();
        MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST = CoreKeyboardSettings.GestureFingerPressRadius;
        MAGIC_KEYBOARD_GESTURE_MOTION_CONST = CoreKeyboardSettings.GestureMotionBaseSensitivity;
        ROW_4_BEGIN_Y = CoreKeyboardSettings.GestureRow4BeginY;
        ROW_1_BEGIN_Y = CoreKeyboardSettings.GestureRow1BeginY;
        TIME_WAIT_GESTURE_UPON_KEY_0 = CoreKeyboardSettings.TimeWaitGestureUponKey0Hold;
    }

    protected abstract boolean IsGestureModeEnabled();

    protected boolean ProcessGestureAtMotionEvent(MotionEvent motionEvent) {
        //LogKeyboardTest("GESTURE ACTION: "+motionEvent.getAction());
        if (IsGestureModeEnabled()
                && !IsNoGesturesMode()
                && !IsInputMode()) {
            return false;
        }

        if (IsNoGesturesMode())
            return true;

        if(!KeyboardGesturesAtInputModeEnabled)
            return true;

        //Не ловим движение на нижнем ряду где поблел и переключение языка (иначе при зажатии KEY_0 курсор будет прыгать и придется фильтровать эти события
        if (motionEvent.getY() > ROW_4_BEGIN_Y || motionEvent.getY() < ROW_1_BEGIN_Y) {
            return true;
        }

        if (!mode_keyboard_gestures && KeyHoldPlusGestureEnabled) {
            ProcessPrepareAtHoldGesture(motionEvent);
        }

        //&& !mode_keyboard_gestures_plus_up_down
        if ((!mode_keyboard_gestures )
            || modeDoubleClickGesture) {
            //Log.d(TAG, "onGenericMotionEvent(): " + motionEvent);
            ProcessDoubleGestureClick(motionEvent);
        }
        if (mode_keyboard_gestures) {

            //TODO: Подумать отдельно обрабатывать жесты по горизонтали и отдельно по вертикали ориентируясь на событие ACTION_UP

            InputConnection inputConnection = getCurrentInputConnection();
            float motionEventX = motionEvent.getX();
            float motionEventY = motionEvent.getY();

            int motionEventAction = motionEvent.getAction();

            if (PerformGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, motionEventAction))
                return true;

        }

        return true;
    }

    boolean CheckMotionAction(MotionEvent motionEvent, int checkAction) {
        int motionEventAction = motionEvent.getAction();
        int motionEventActionMasked = motionEvent.getActionMasked();
        if(motionEventAction == checkAction) return true;
        if(motionEventActionMasked == checkAction) return true;
        return false;
    }

    private boolean PerformGestureAction(MotionEvent motionEvent, InputConnection inputConnection, float motionEventX, float motionEventY, int motionEventAction) {
        //Жесть по клавиатуре всегда начинается с ACTION_DOWN
        if (CheckMotionAction(motionEvent,  MotionEvent.ACTION_DOWN)
            || CheckMotionAction(motionEvent, MotionEvent.ACTION_POINTER_DOWN)) {
            //if (debug_gestures)
            //    Log.d(TAG, "onGenericMotionEvent ACTION_DOWN " + motionEvent);
            lastGestureX = motionEventX;
            lastGestureY = motionEventY;
            return true;
        }

        if (    CheckMotionAction(motionEvent, MotionEvent.ACTION_MOVE)) {

            if(lastGestureX > 0 && lastGestureY > 0) {
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
                        if (MoveCursorRightSafe(inputConnection))
                            Log.d(TAG2, "onGenericMotionEvent KEYCODE_DPAD_RIGHT " + motionEvent);
                    } else {
                        if (MoveCursorLeftSafe(inputConnection))
                            Log.d(TAG2, "onGenericMotionEvent KEYCODE_DPAD_LEFT " + motionEvent);
                    }
                } else if (mode_keyboard_gestures_plus_up_down) {
                    if (absDeltaY < motion_delta_min_y)
                        return true;
                    //int times = Math.round(absDeltaY / motion_delta_min_y);
                    if (deltaY < 0) {
                        if (MoveCursorUpSafe(inputConnection))
                            Log.d(TAG2, "onGenericMotionEvent KEYCODE_DPAD_UP " + motionEvent);
                    } else {
                        if (MoveCursorDownSafe(inputConnection))
                            Log.d(TAG2, "onGenericMotionEvent KEYCODE_DPAD_DOWN  " + motionEvent);
                    }
                }
            }
            lastGestureX = motionEventX;
            lastGestureY = motionEventY;
        } else if(CheckMotionAction(motionEvent, MotionEvent.ACTION_POINTER_UP)
                || CheckMotionAction(motionEvent, MotionEvent.ACTION_UP)) {
            lastGestureX = 0;
            lastGestureY = 0;
        }
        return false;
    }





    private void ProcessPrepareAtHoldGesture(MotionEvent motionEvent) {


        if (    CheckMotionAction(motionEvent, MotionEvent.ACTION_UP)
                || CheckMotionAction(motionEvent, MotionEvent.ACTION_CANCEL)
                || CheckMotionAction(motionEvent, MotionEvent.ACTION_POINTER_UP)) {

            lastGestureSwipingBeginTime = 0;
            enteredGestureMovement = false;
        }
        if (CheckMotionAction(motionEvent, MotionEvent.ACTION_MOVE) && enteredGestureMovement) {
            lastGestureSwipingBeginTime = SystemClock.uptimeMillis();
            lastGestureX = motionEvent.getX();
            lastGestureY = motionEvent.getY();
        }
        if (CheckMotionAction(motionEvent, MotionEvent.ACTION_DOWN)
                || CheckMotionAction(motionEvent, MotionEvent.ACTION_DOWN))  {

            lastGestureSwipingBeginTime = SystemClock.uptimeMillis();
            lastGestureX = motionEvent.getX();
            lastGestureY = motionEvent.getY();
            enteredGestureMovement = true;
        }
    }

    private void ProcessDoubleGestureClick(MotionEvent motionEvent) {


        if (    CheckMotionAction(motionEvent, MotionEvent.ACTION_UP)
                || CheckMotionAction(motionEvent, MotionEvent.ACTION_CANCEL)
                || CheckMotionAction(motionEvent, MotionEvent.ACTION_POINTER_UP)) {
            if(modeDoubleClickGesture) {
                modeDoubleClickGesture = false;
                //mode_keyboard_gestures = false;
                // //mode_keyboard_gestures_plus_up_down = false;
                //UpdateGestureModeVisualization();
                if(OnGestureSecondClickUp != null) {
                    OnGestureSecondClickUp.Process(null);
                }
            } else {
                prevPrevUpTime = prevUpTime;
                prevUpTime = motionEvent.getEventTime();
                prevX = motionEvent.getX();
                prevY = motionEvent.getY();
            }
        } else if( CheckMotionAction(motionEvent, MotionEvent.ACTION_MOVE)) {
            float curX = motionEvent.getX();
            float curY = motionEvent.getY();
            //Случай когда два пальца работают вместе (получается мултитач) и для второго пальца нет сигнала DOWN
            if(Math.abs(curY - prevY) < MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST
            || Math.abs(curX - prevX) < MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST) {
                prevY = 0;
                prevX = 0;
            }
        }
        else if (   CheckMotionAction(motionEvent, MotionEvent.ACTION_DOWN)
                || CheckMotionAction(motionEvent, MotionEvent.ACTION_POINTER_DOWN)) {

            long curDownTime = motionEvent.getEventTime();
            float curX = motionEvent.getX();
            float curY = motionEvent.getY();

            if(     Math.abs(curX - prevX) < MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST
                    && Math.abs(curY - prevY) < MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST
                    && Math.abs(prevPrevX - prevX) < MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST
                    && Math.abs(prevPrevY - prevY) < MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST
                    && prevUpTime - prevDownTime <= TIME_LONG_PRESS
                    && prevPrevUpTime - prevPrevDownTime <= TIME_LONG_PRESS
                    && prevUpTime - prevPrevDownTime <= TIME_DOUBLE_PRESS
                    && curDownTime - prevUpTime <= TIME_DOUBLE_PRESS) {
                Log.d(TAG2, "GESTURE TRIPLE CLICK");
                //ActionEnableGestureMode();
                //ActionChangeGestureAtInputModeUpAndDownMode();
                if(OnGestureTripleClick != null) {
                    OnGestureTripleClick.Process(null);
                }
                modeDoubleClickGesture = true;
            }
            else if(Math.abs(curX - prevX) < MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST
                    && Math.abs(curY - prevY) < MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST
                    && prevUpTime - prevDownTime <= TIME_LONG_PRESS
                    && curDownTime - prevUpTime <= TIME_DOUBLE_PRESS) {
                Log.d(TAG2, "GESTURE DOUBLE CLICK");
                //ActionEnableGestureMode();
                if(OnGestureDoubleClick != null) {
                    OnGestureDoubleClick.Process(null);
                }
                modeDoubleClickGesture = true;
            }

            prevPrevDownTime = prevDownTime;
            prevDownTime = curDownTime;

            prevPrevX = prevX;
            prevPrevY = prevY;
            prevX = motionEvent.getX();
            prevY = motionEvent.getY();



        }
    }

    protected abstract boolean ProcessOnCursorMovement(EditorInfo editorInfo);

    //private abstract void UpdateGestureModeVisualization(boolean isInput);

    //private abstract void UpdateGestureModeVisualization();

    protected abstract boolean IsNoGesturesMode();

    //region CURSOR MOVE

    private boolean MoveCursorDownSafe(InputConnection inputConnection) {
        CharSequence c = inputConnection.getTextAfterCursor(1, 0);
        if (c.length() > 0) {
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
            ProcessOnCursorMovement(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    private boolean MoveCursorUpSafe(InputConnection inputConnection) {
        CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
        if (c.length() > 0) {
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
            ProcessOnCursorMovement(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    protected boolean MoveCursorLeftSafe(InputConnection inputConnection) {
        CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
        if (c.length() > 0) {
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
            ProcessOnCursorMovement(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    protected boolean MoveCursorRightSafe(InputConnection inputConnection) {
        CharSequence c = inputConnection.getTextAfterCursor(1, 0);
        if (c.length() > 0) {
            keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
            ProcessOnCursorMovement(getCurrentInputEditorInfo());
            return true;
        }
        return false;
    }

    //endregion

    //region ACTIONS

    protected boolean IsInputMode() {
        if(getCurrentInputEditorInfo() == null) return false;
        //Log.d(TAG2, "IsInputMode() "+getCurrentInputEditorInfo().inputType);
        return getCurrentInputEditorInfo().inputType > 0;
    }

    protected boolean ActionTryTurnOffGesturesMode() {
        //mode_keyboard_gestures_plus_up_down = false;
        if (mode_keyboard_gestures) {
            mode_keyboard_gestures = false;
            //UpdateGestureModeVisualization();
            return true;
        }
        return false;
    }

    protected boolean ActionResetDoubleClickGestureState() {
        prevDownTime = 0;
        prevUpTime = 0;
        prevPrevDownTime = 0;
        prevPrevUpTime = 0;
        return true;
    }

    //endregion
}
