package com.sateda.keyonekb2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;

public abstract class InputMethodServiceCoreGesture extends InputMethodServiceCoreKeyPress {
    private int MAGIC_KEYBOARD_GESTURE_MOTION_CONST;
    private int MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST ;
    private int ROW_4_BEGIN_Y;
    private int ROW_1_BEGIN_Y;
    protected int TIME_WAIT_GESTURE_UPON_KEY_0;
    private float lastGestureX;
    private float lastGestureY;

    protected boolean mode_gesture_cursor_at_input_mode = false;
    protected boolean mode_gesture_cursor_plus_up_down = false;
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


    protected Processable OnGestureDoubleClick;
    protected Processable OnGestureTripleClick;
    protected Processable OnGestureSecondClickUp;

    protected boolean KeyboardGesturesAtInputModeEnabled;

    protected boolean KeyHoldPlusGestureEnabled;

    protected KeyoneKb2Settings keyoneKb2Settings;

    //public boolean pref_keyboard_gestures_at_views_enable = true;

    //GestureDescription.Builder builder;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onCreate() {
        super.onCreate();
        MAGIC_KEYBOARD_GESTURE_ONE_FINGER_XY_CONST = CoreKeyboardSettings.GestureFingerPressRadius;
        MAGIC_KEYBOARD_GESTURE_MOTION_CONST = CoreKeyboardSettings.GestureMotionBaseSensitivity;
        ROW_4_BEGIN_Y = CoreKeyboardSettings.GestureRow4BeginY;
        ROW_1_BEGIN_Y = CoreKeyboardSettings.GestureRow1BeginY;
        TIME_WAIT_GESTURE_UPON_KEY_0 = CoreKeyboardSettings.TimeWaitGestureUponKey0Hold;

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

        middleYValue = displayMetrics.heightPixels / 2;
        middleXValue = displayMetrics.widthPixels / 2;

        K = (displayMetrics.heightPixels) / (450) * 0.80f;

    }

    int middleYValue;
    int middleXValue;
    float K;
    float Ktime = 1.25f;

    protected abstract boolean IsGestureModeAtViewEnabled();
    public boolean IsVisualKeyboardOpen = false;

    protected boolean ProcessGestureAtMotionEvent(MotionEvent motionEvent) {
        //LogKeyboardTest("GESTURE ACTION: "+motionEvent);

        if (IsNoGesturesMode())
            return true;

        if(IsInputMode()) {
            if(!KeyboardGesturesAtInputModeEnabled)
                return true;
            if(_modeGestureScrollAtInputMode)
                return false;
        } else { //!IsInputMode()

            if (!IsGestureModeAtViewEnabled())
                return true;
            if(_modeGestureAtViewMode == GestureAtViewMode.Scroll)
                return false;
        }

        if (IsInputMode() && (!mode_gesture_cursor_at_input_mode
            || modeDoubleClickGesture)) {
            //Log.d(TAG, "onGenericMotionEvent(): " + motionEvent);
            ProcessDoubleGestureClick(motionEvent);
        }

        if(IsInputMode() && _modeGestureScrollAtInputMode) {
            if(CheckMotionAction(motionEvent,  MotionEvent.ACTION_MOVE) &&
                    motionEvent.getHistorySize() > 0) {
                for (int i = 0; i < motionEvent.getHistorySize(); i++) {
                    ProcessGesturePointersAndPerformAction(motionEvent, i);
                }
            } else {
                ProcessGesturePointersAndPerformAction(motionEvent, -1);
            }
            return true;
        }
        if ( (IsInputMode() && mode_gesture_cursor_at_input_mode)
                || IsAnyGestureAtViewMode()) {
            ProcessGesturePointersAndPerformAction(motionEvent, -1);
        }

        return true;
    }

    private boolean ProcessGesturePointersAndPerformAction(MotionEvent motionEvent, int historyIndex) {
        InputConnection inputConnection = getCurrentInputConnection();
        float motionEventX;
        float motionEventY;
        long eventTime;

        if(historyIndex == -1) {
            eventTime = motionEvent.getEventTime();
        } else {
            eventTime = motionEvent.getHistoricalEventTime(historyIndex);
        }

        if(motionEvent.getPointerCount() == 1) {
            if(historyIndex == -1) {
                motionEventX = motionEvent.getX();
                motionEventY = motionEvent.getY();
            } else {
                motionEventX = motionEvent.getHistoricalX(historyIndex);
                motionEventY = motionEvent.getHistoricalY(historyIndex);
            }
            //TODO: Это проблема т.к. не обрабатывается ACTION_UP/DOWN на нижнем ряду. ЛЕЧИТЬ!

            //if(motionEventY > ROW_4_BEGIN_Y)
            //    return true;
            if (PerformAtomicGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN, eventTime))
                return true;

        } else {
            //Для случая, когда левый палец зажимает KEY_0 и он раньше встал на сенсор, чем второй жестовый палец
            if (motionEvent.getY(0) > ROW_4_BEGIN_Y) {
                if(historyIndex == -1) {
                    motionEventX = motionEvent.getX(1);
                    motionEventY = motionEvent.getY(1);
                } else {
                    motionEventX = motionEvent.getHistoricalX(1, historyIndex);
                    motionEventY = motionEvent.getHistoricalY(1, historyIndex);
                }
                if(motionEvent.getActionIndex() == 0) {
                    if (PerformAtomicGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN, eventTime))
                        return true;
                } else {
                    if (PerformAtomicGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_POINTER_DOWN, eventTime))
                        return true;
                }
            } else {
                //Для случая, когда левый палец зажимает KEY_0 и он позже встал на сенсор, чем второй жестовый палец
                if(historyIndex == -1) {
                    motionEventX = motionEvent.getX(0);
                    motionEventY = motionEvent.getY(0);
                } else {
                    motionEventX = motionEvent.getHistoricalX(0, historyIndex);
                    motionEventY = motionEvent.getHistoricalY(0, historyIndex);
                }
                if(motionEvent.getActionIndex() == 0) {
                    if (PerformAtomicGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_POINTER_DOWN, eventTime))
                        return true;
                } else {
                    if (PerformAtomicGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN, eventTime))
                        return true;
                }
            }
        }
        return false;
    }

    boolean CheckMotionAction(MotionEvent motionEvent, int checkAction) {
        int motionEventAction = motionEvent.getAction();
        int motionEventActionMasked = motionEvent.getActionMasked();
        if(motionEventAction == checkAction) return true;
        if(motionEventActionMasked == checkAction) return true;
        return false;
    }

    long lastEventTime;

    private boolean PerformAtomicGestureAction(MotionEvent motionEvent, InputConnection inputConnection, float motionEventX, float motionEventY, int ACTION_OR_POINTER_UP, int ACTION_OR_POINTER_DOWN, long eventTime ) {
        //Жесть по клавиатуре всегда начинается с ACTION_DOWN
        if (CheckMotionAction(motionEvent,  ACTION_OR_POINTER_DOWN)
            ) {
            //if (debug_gestures)
            //Log.d(TAG2, "onGenericMotionEvent ACTION_DOWN " + motionEvent);
            lastGestureX = motionEventX;
            lastGestureY = motionEventY;
            lastEventTime = eventTime;
            return true;
        }

        if (    CheckMotionAction(motionEvent, MotionEvent.ACTION_MOVE)) {

            if(lastGestureX > 0 && lastGestureY > 0) {
                float deltaX = motionEventX - lastGestureX;
                float absDeltaX = deltaX < 0 ? -1 * deltaX : deltaX;
                float deltaY = motionEventY - lastGestureY;
                float absDeltaY = deltaY < 0 ? -1 * deltaY : deltaY;
                float Kpower = 1;
                /* проезд 3 рядов (сверху-донизу, кроме игнорируемого нижнего ряда) -
                K=1 ~6 событий MoveCursor*/
                /*MAGIC_KEYBOARD_GESTURE_MOTION_CONST=48; pref_gesture_motion_sensitivity=1-положение-слева
                * K=0.5 ~9 событий */
                if(IsAnyGestureAtViewMode())
                    Kpower = 2.5f;
                if(IsInputMode() && _modeGestureScrollAtInputMode)
                    Kpower = 0f;

                int motion_delta_min_x = Math.round((MAGIC_KEYBOARD_GESTURE_MOTION_CONST - pref_gesture_motion_sensitivity)*Kpower);
                int motion_delta_min_y = Math.round((MAGIC_KEYBOARD_GESTURE_MOTION_CONST - pref_gesture_motion_sensitivity)*Kpower);

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
                } else if (mode_gesture_cursor_plus_up_down || IsAnyGestureAtViewMode() || (IsInputMode() && _modeGestureScrollAtInputMode)) {
                    if (absDeltaY < motion_delta_min_y)
                        return true;
                    //int times = Math.round(absDeltaY / motion_delta_min_y);
                    if (deltaY < 0) {
                        if (MoveCursorUpSafe(inputConnection, lastGestureY, motionEventY, eventTime - lastEventTime))
                            Log.d(TAG2, "onGenericMotionEvent KEYCODE_DPAD_UP " + motionEvent);
                    } else {
                        if (MoveCursorDownSafe(inputConnection, lastGestureY, motionEventY, eventTime - lastEventTime))
                            Log.d(TAG2, "onGenericMotionEvent KEYCODE_DPAD_DOWN  " + motionEvent);
                    }
                }
            }
            lastGestureX = motionEventX;
            lastGestureY = motionEventY;
            lastEventTime = eventTime;

            Log.d(TAG2, "lastX: "+lastGestureX+" lastY: "+lastGestureY);
        } else if(CheckMotionAction(motionEvent, ACTION_OR_POINTER_UP)) {
            ResetGestureMovementCoordsToInitial();
            return true;
        }
        return false;
    }

    private boolean IsAnyGestureAtViewMode() {
        return !IsInputMode() && _modeGestureAtViewMode != GestureAtViewMode.Disabled;
    }

    private void ResetGestureMovementCoordsToInitial() {
        lastGestureX = 0;
        lastGestureY = 0;
        lastEventTime = 0;
    }

    private void ProcessDoubleGestureClick(MotionEvent motionEvent) {


        if (    CheckMotionAction(motionEvent, MotionEvent.ACTION_UP)
                || CheckMotionAction(motionEvent, MotionEvent.ACTION_CANCEL)) {
            if(modeDoubleClickGesture) {
                modeDoubleClickGesture = false;
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
        else if (   CheckMotionAction(motionEvent, MotionEvent.ACTION_DOWN)) {

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
                    && curDownTime - prevUpTime <= TIME_LONG_PRESS) {
                Log.d(TAG2, "GESTURE TRIPLE CLICK");
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

    protected abstract boolean IsNoGesturesMode();

    protected boolean IsInputMode() {
        if(getCurrentInputEditorInfo() == null) return false;
        return getCurrentInputEditorInfo().inputType > 0;
    }


    //region CURSOR MOVE

    private boolean MoveCursorDownSafe(InputConnection inputConnection, float lastGestureY, float currentGestureY, long time) {
        if(IsAnyGestureAtViewMode()) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection, 0);
            return true;
        }
        if(inputConnection.getSelectedText(0) == null) {
            CharSequence c = inputConnection.getTextAfterCursor(1, 0);
            if (c.length() > 0) {
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
                ProcessOnCursorMovement(getCurrentInputEditorInfo());
                return true;
            }
        } else {
            ExtractedTextRequest request = new ExtractedTextRequest();
            ExtractedText extractedText = inputConnection.getExtractedText(request, 0);
            int end = extractedText.selectionEnd;
            if (end < extractedText.text.length()) {
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection);
                ProcessOnCursorMovement(getCurrentInputEditorInfo());
                return true;
            }
        }
        return false;
    }

    private boolean MoveCursorUpSafe(InputConnection inputConnection, float lastGestureY, float currentGestureY, long time) {
        if(IsAnyGestureAtViewMode()) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_UP, inputConnection, 0);
            return true;
        }
        if(inputConnection.getSelectedText(0) == null) {
            CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
            if (c.length() > 0) {
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
                ProcessOnCursorMovement(getCurrentInputEditorInfo());
                return true;
            }
        } else {
            ExtractedTextRequest request = new ExtractedTextRequest();
            ExtractedText extractedText = inputConnection.getExtractedText(request, 0);
            int end = extractedText.selectionEnd;
            if (end > 0) {
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_UP, inputConnection);
                ProcessOnCursorMovement(getCurrentInputEditorInfo());
                return true;
            }
        }
        return false;
    }

    protected boolean MoveCursorLeftSafe(InputConnection inputConnection) {
        if(IsAnyGestureAtViewMode()) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _modeGestureScrollAtInputMode) {
            return true;
        }
        if(inputConnection.getSelectedText(0) == null) {
            CharSequence c = inputConnection.getTextBeforeCursor(1, 0);
            if (c.length() > 0) {
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                ProcessOnCursorMovement(getCurrentInputEditorInfo());
                return true;
            }
        } else {
            ExtractedTextRequest request = new ExtractedTextRequest();
            ExtractedText extractedText = inputConnection.getExtractedText(request, 0);
            int end = extractedText.selectionEnd;
            if (end > 0) {
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection);
                ProcessOnCursorMovement(getCurrentInputEditorInfo());
                return true;
            }
        }
        return false;
    }

    protected boolean MoveCursorRightSafe(InputConnection inputConnection) {
        if(IsAnyGestureAtViewMode()) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _modeGestureScrollAtInputMode) {
            return true;
        }
        if(inputConnection.getSelectedText(0) == null) {
            CharSequence c = inputConnection.getTextAfterCursor(1, 0);
            if (c.length() > 0) {
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                ProcessOnCursorMovement(getCurrentInputEditorInfo());
                return true;
            }
        } else {
            ExtractedTextRequest request = new ExtractedTextRequest();
            ExtractedText extractedText = inputConnection.getExtractedText(request, 0);
            int end = extractedText.selectionEnd;
            if (end < extractedText.text.length()) {
                keyDownUpNoMetaKeepTouch(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection);
                ProcessOnCursorMovement(getCurrentInputEditorInfo());
                return true;
            }
        }
        return false;
    }

    //endregion

    //region Actions GESTURE CURSOR (INPUT_MODE)

    protected boolean needUpdateGestureNotificationInsideSingleEvent;

    public boolean ActionSetNeedUpdateGestureNotification() {
        needUpdateGestureNotificationInsideSingleEvent = true;
        return true;
    }

    public boolean ActionTryTurnOffGesturesMode() {
        mode_gesture_cursor_plus_up_down = false;
        if (mode_gesture_cursor_at_input_mode) {
            mode_gesture_cursor_at_input_mode = false;
            return true;
        }
        return false;
    }



    public boolean ActionEnableGestureAtInputMode() {
        mode_gesture_cursor_at_input_mode = true;
        return true;
    }

    protected boolean gestureCursorAtInputEnabledByHold = false;

    public boolean ActionTryEnableGestureCursorModeOnHoldState() {
        if(!mode_gesture_cursor_at_input_mode && KeyHoldPlusGestureEnabled) {
            gestureCursorAtInputEnabledByHold = true;
            mode_gesture_cursor_at_input_mode = true;
            ResetGestureMovementCoordsToInitial();
            return true;
        }
        return false;
    }

    public boolean ActionTryDisableGestureCursorModeUnHoldState() {
        if(gestureCursorAtInputEnabledByHold && KeyHoldPlusGestureEnabled) {
            gestureCursorAtInputEnabledByHold = false;
            mode_gesture_cursor_at_input_mode = false;
            ResetGestureMovementCoordsToInitial();
            return true;
        }
        return false;
    }

    public boolean ActionChangeGestureAtInputModeUpAndDownMode() {
        mode_gesture_cursor_plus_up_down = !mode_gesture_cursor_plus_up_down;
        return true;
    }

    public boolean ActionDisableGestureMode() {
        mode_gesture_cursor_at_input_mode = false;
        return true;
    }

    public boolean ActionDisableAndResetGestureMode() {
        mode_gesture_cursor_at_input_mode = false;
        mode_gesture_cursor_plus_up_down = false;
        return true;
    }

    public boolean ActionTryChangeGestureModeStateAtInputMode() {
        if (IsInputMode()) {
            mode_gesture_cursor_at_input_mode = !mode_gesture_cursor_at_input_mode;
            return true;
        }
        return false;

    }

    public boolean ActionEnableGestureAtInputModeAndUpDownMode() {
        if (IsInputMode()) {
            mode_gesture_cursor_at_input_mode = true;
            mode_gesture_cursor_plus_up_down = true;
        }

        return true;
    }

    public boolean ActionDisableAndResetGesturesAtInputMode() {
        if(_modeGestureScrollAtInputMode) {
            Log.d(TAG2, "_modeGestureScrollAtInputMode=false");
            _modeGestureScrollAtInputMode = false;
            return true;
        }

        if (mode_gesture_cursor_at_input_mode && IsInputMode()) {
            mode_gesture_cursor_at_input_mode = false;
            mode_gesture_cursor_plus_up_down = false;
            return true;
        }
        return false;
    }

    //endregion

    //region Actions GESTURE POINTER/SCROLL (VIEW_MODE)

    boolean _modeGestureAtViewModeDisabledPermanently = false;
    boolean _modeGestureAtViewModePointerAfterEnable = false;

    public enum GestureAtViewMode {
        Disabled,
        Scroll,
        Pointer
    }

    protected String _lastPackageName = "";

    String GESTURE_AT_VIEW_MODE_PREFIX = "GESTURE_AT_VIEW_MODE_";

    GestureAtViewMode GESTURE_MODE_AT_VIEW_MODE_DEFAULT = GestureAtViewMode.Disabled;

    GestureAtViewMode GetGestureStoredOrDefaultPointerMode() {
        if(_lastPackageName == null || _lastPackageName.equals(""))
            return GESTURE_MODE_AT_VIEW_MODE_DEFAULT;
        String prefName = GESTURE_AT_VIEW_MODE_PREFIX +_lastPackageName;
        keyoneKb2Settings.CheckSettingOrSetDefault(prefName, GESTURE_MODE_AT_VIEW_MODE_DEFAULT.name());
        return GestureAtViewMode.valueOf(keyoneKb2Settings.GetStringValue(prefName));
    }

    protected void SetGestureDefaultPointerMode(String packageName, GestureAtViewMode value) {
        keyoneKb2Settings.SetStringValue(GESTURE_AT_VIEW_MODE_PREFIX +packageName, value.name());
    }

    public GestureAtViewMode _modeGestureAtViewMode = GESTURE_MODE_AT_VIEW_MODE_DEFAULT;
    protected boolean _modeGestureScrollAtInputMode = false;
    public boolean ActionTryChangeGestureInputScrollMode() {
        if(!IsInputMode())
            return false;
        if(!KeyboardGesturesAtInputModeEnabled)
            return false;
        _modeGestureScrollAtInputMode = !_modeGestureScrollAtInputMode;
        Log.d(TAG2, "GESTURE_INPUT_SCROLL_MODE SET="+ _modeGestureScrollAtInputMode);
        ResetGestureMovementCoordsToInitial();
        return true;
    }

    public boolean ActionTryEnableGestureInputScrollMode() {
        if(!IsInputMode())
            return false;
        if(!KeyboardGesturesAtInputModeEnabled)
            return false;
        if(_modeGestureAtViewMode == GestureAtViewMode.Disabled)
            return false;
        _modeGestureScrollAtInputMode = true;
        Log.d(TAG2, "GESTURE_INPUT_SCROLL_MODE SET=true");
        return true;
    }

    public boolean ActionTryDisableGestureInputScrollMode() {
        if(_modeGestureScrollAtInputMode) {
            _modeGestureScrollAtInputMode = false;
            Log.d(TAG2, "GESTURE_INPUT_SCROLL_MODE SET=false");
            ResetGestureMovementCoordsToInitial();
            return true;
        }
        return false;
    }

    public boolean ActionChangeGestureModeEnableState() {
        if(_modeGestureAtViewModeDisabledPermanently) {
            _modeGestureAtViewMode = GestureAtViewMode.Disabled;
            return true;
        }
        if(_modeGestureAtViewMode != GestureAtViewMode.Disabled)
            _modeGestureAtViewMode = GestureAtViewMode.Disabled;
        else {
            ResetGestureModeAtViewModeToDefaultAfterEnable();
            _modeGestureAtViewMode = GetGestureStoredOrDefaultPointerMode();
        }

        return true;
    }



    public boolean ActionTryChangeGesturePointerModeAtViewMode() {
        if(IsInputMode())
            return false;
        if(_modeGestureAtViewModeDisabledPermanently) {
            _modeGestureAtViewMode = GestureAtViewMode.Disabled;
            return false;
        }
        if(_modeGestureAtViewMode == GestureAtViewMode.Disabled) {
            return false;
        }
        else if(_modeGestureAtViewMode == GestureAtViewMode.Pointer)
            _modeGestureAtViewMode = GestureAtViewMode.Scroll;
        else if(_modeGestureAtViewMode == GestureAtViewMode.Scroll)
            _modeGestureAtViewMode = GestureAtViewMode.Pointer;
        Log.d(TAG2, "GESTURE_POINTER_MODE SET="+ _modeGestureAtViewMode);
        ResetGestureMovementCoordsToInitial();
        return true;
    }

    private void ResetGestureModeAtViewModeToDefaultAfterEnable() {
        if(_modeGestureAtViewModePointerAfterEnable)
            SetGestureDefaultPointerMode(_lastPackageName, GestureAtViewMode.Pointer);
        else
            SetGestureDefaultPointerMode(_lastPackageName, GestureAtViewMode.Scroll);
    }

    public boolean ActionResetGesturePointerMode() {
        if(_modeGestureAtViewModeDisabledPermanently) {
            _modeGestureAtViewMode = GestureAtViewMode.Disabled;
            return true;
        }
        _modeGestureAtViewMode = GetGestureStoredOrDefaultPointerMode();
        Log.d(TAG2, "GESTURE_POINTER_MODE SET="+ _modeGestureAtViewMode);
        ResetGestureMovementCoordsToInitial();
        return true;
    }





    public boolean ActionResetDoubleClickGestureState() {
        prevDownTime = 0;
        prevUpTime = 0;
        prevPrevDownTime = 0;
        prevPrevUpTime = 0;
        return true;
    }

    //endregion





}
