package com.sateda.keyonekb2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
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

    protected KeyoneKb2Settings keyoneKb2Settings;

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

        if (IsNoGesturesMode())
            return true;

        if(IsInputMode()) {
            if(!KeyboardGesturesAtInputModeEnabled)
                return true;
            //if(_gestureInputScrollViewMode)
            //    return false;
        } else { //!IsInputMode()

            if (!IsGestureModeEnabled())
                return true;
            if(!GesturePointerMode)
                return false;
        }

        if (!mode_keyboard_gestures && !GesturePointerMode && KeyHoldPlusGestureEnabled) {
            ProcessPrepareAtHoldGesture(motionEvent);
        }

        //&& !mode_keyboard_gestures_plus_up_down
        if ((!mode_keyboard_gestures )
            || modeDoubleClickGesture) {
            //Log.d(TAG, "onGenericMotionEvent(): " + motionEvent);
            ProcessDoubleGestureClick(motionEvent);
        }
        //if (mode_keyboard_gestures || (!IsInputMode() && GesturePointerMode)) {
        if (mode_keyboard_gestures || (!IsInputMode() && GesturePointerMode) || _gestureInputScrollViewMode) {

            //TODO: Подумать отдельно обрабатывать жесты по горизонтали и отдельно по вертикали ориентируясь на событие ACTION_UP

            InputConnection inputConnection = getCurrentInputConnection();
            float motionEventX;
            float motionEventY;

            if(motionEvent.getPointerCount() == 1) {
                motionEventX = motionEvent.getX();
                motionEventY = motionEvent.getY();
                if(motionEventY > ROW_4_BEGIN_Y)
                    return true;
                if (PerformGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN))
                    return true;
            } else {
                if (motionEvent.getY(0) > ROW_4_BEGIN_Y) {
                    motionEventX = motionEvent.getX(1);
                    motionEventY = motionEvent.getY(1);
                    if(motionEvent.getActionIndex() == 0) {
                        if (PerformGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN))
                            return true;
                    } else {
                        if (PerformGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_POINTER_DOWN))
                            return true;
                    }
                } else {
                    motionEventX = motionEvent.getX(0);
                    motionEventY = motionEvent.getY(0);
                    if(motionEvent.getActionIndex() == 0) {
                        if (PerformGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_POINTER_DOWN))
                            return true;
                    } else {
                        if (PerformGestureAction(motionEvent, inputConnection, motionEventX, motionEventY, MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN))
                            return true;
                    }
                }
            }



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

    private boolean PerformGestureAction(MotionEvent motionEvent, InputConnection inputConnection, float motionEventX, float motionEventY, int ACTION_OR_POINTER_UP, int ACTION_OR_POINTER_DOWN ) {
        //Жесть по клавиатуре всегда начинается с ACTION_DOWN
        if (CheckMotionAction(motionEvent,  ACTION_OR_POINTER_DOWN)
            //|| CheckMotionAction(motionEvent, MotionEvent.ACTION_POINTER_DOWN)
            ) {
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
                float Kpower = 1;
                if(!IsInputMode() && GesturePointerMode)
                    Kpower = 3f;
                if(IsInputMode() && _gestureInputScrollViewMode)
                    Kpower = 1.5f;

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
                } else if (mode_keyboard_gestures_plus_up_down || (!IsInputMode() && GesturePointerMode) || _gestureInputScrollViewMode) {
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
            Log.d(TAG2, "lastX: "+lastGestureX+" lastY: "+lastGestureY);
        //} else if(CheckMotionAction(motionEvent, MotionEvent.ACTION_POINTER_UP) || CheckMotionAction(motionEvent, MotionEvent.ACTION_UP)) {
        } else if(CheckMotionAction(motionEvent, ACTION_OR_POINTER_UP)) {
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

    //region CURSOR MOVE

    public AccessibilityNodeInfo ActiveNode;

    private AccessibilityNodeInfo FindScrollableRecurs(AccessibilityNodeInfo info) {
        if(info == null)
            return null;
        for (int j = 0; j < info.getActionList().size(); j++) {
            if(info.getActionList().get(j) == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
                return info;
            if(info.getActionList().get(j) == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
                return info;
            if(info.getActionList().get(j) == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
                return info;
            if(info.getActionList().get(j) == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP)
                return info;
        }
        for (int i = 0; i < info.getChildCount(); i++)  {
            AccessibilityNodeInfo res = FindScrollableRecurs(info.getChild(i));
            if(res != null)
                return res;
        }
        return null;
    }

    private void moveByGesture(boolean isToTop) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

        int middleYValue = displayMetrics.heightPixels / 2;
        int middleXValue = displayMetrics.widthPixels / 2;
        //final int leftSideOfScreen = displayMetrics.widthPixels / 4;
        //final int rightSizeOfScreen = middleXValue + leftSideOfScreen;
        int topSideOfScreen = middleYValue - displayMetrics.heightPixels / 4;
        int bottomSideOfScreen = middleYValue + displayMetrics.heightPixels / 4;

        Path path = new Path();

        if (!isToTop) {
            path.moveTo(middleXValue, middleYValue);
            path.lineTo(middleXValue, topSideOfScreen);
            Log.d(TAG2, "GESTURE_SCROLL_TOP");
        } else {
            path.moveTo(middleXValue, middleYValue);
            path.lineTo(middleXValue, bottomSideOfScreen);
            Log.d(TAG2, "GESTURE_SCROLL_BOTTOM");
        }

        //gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 150, true));
        //KeyoneKb2AccessibilityService.Instance.dispatchGesture(gestureBuilder.build(), null, null);
        //swipeScreenWithDelay(path, 0l, 200l, 250l);
        GestureDescription.StrokeDescription gesture1 = new GestureDescription.StrokeDescription(path, 0, 150, true);
        if(gesture == null) {
            gesture = gesture1;
            KeyoneKb2AccessibilityService.Instance.dispatchGesture(
                    new GestureDescription.Builder().addStroke(gesture1).build(),
                    new GestureCompletedCallback(), null
            );
        } else {
            Gestures.add(gesture1);
        }

        PathMeasure pM = new PathMeasure(path, false);
        float breakPoint  = pM.getLength() * 0.75f;
        EndPath = new Path();
        pM.getSegment(breakPoint, pM.getLength(), EndPath, true);
    }

    Path EndPath;

    ArrayList<GestureDescription.StrokeDescription> Gestures = new ArrayList<>();
    GestureDescription.StrokeDescription gesture;

    class GestureCompletedCallback extends AccessibilityService.GestureResultCallback {
        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            super.onCompleted(gestureDescription);

            if (Gestures.size() > 0) {
                GestureDescription.StrokeDescription gesture1 = Gestures.get(0);
                Gestures.remove(gesture1);
                KeyoneKb2AccessibilityService.Instance.dispatchGesture(
                        new GestureDescription.Builder().addStroke(gesture1).build(),
                        new GestureCompletedCallback(),
                        null);
            } else {
                gesture = null;
                KeyoneKb2AccessibilityService.Instance.dispatchGesture(
                        new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(EndPath, 0, 300, false)).build(),
                        null,
                        null);
            }
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            super.onCancelled(gestureDescription);
            gesture = null;
        }
    }

    private boolean MoveCursorDownSafe(InputConnection inputConnection) {
        if(!IsInputMode() && GesturePointerMode) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _gestureInputScrollViewMode) {
            //if(ActiveNode != null) {
                moveByGesture(true);
            //    AccessibilityNodeInfo info = FindScrollableRecurs(ActiveNode);
                //if(info != null)
                //    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);

                    //info.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            //}
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

    private boolean MoveCursorUpSafe(InputConnection inputConnection) {
        if(!IsInputMode() && GesturePointerMode) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_UP, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _gestureInputScrollViewMode) {
            //if(ActiveNode != null) {
                //AccessibilityNodeInfo info = FindScrollableRecurs(ActiveNode);
                moveByGesture(false);
                //if(info != null)
                //    info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                    //info.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            //}
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
        if(!IsInputMode() && GesturePointerMode) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_LEFT, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _gestureInputScrollViewMode) {
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
        if(!IsInputMode() && GesturePointerMode) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_RIGHT, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _gestureInputScrollViewMode) {
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

    //region ACTIONS

    protected String _lastPackageName = "";

    String GESTURE_POINTER_MODE_PREFIX = "GESTURE_POINTER_MODE_";

    boolean GESTURE_POINTER_MODE_DEFAULT = true;

    boolean GetGestureDefaultPointerMode() {
        if(_lastPackageName == null || _lastPackageName.equals(""))
            return GESTURE_POINTER_MODE_DEFAULT;
        String prefName = GESTURE_POINTER_MODE_PREFIX+_lastPackageName;
        keyoneKb2Settings.CheckSettingOrSetDefault(prefName, GESTURE_POINTER_MODE_DEFAULT);
        return keyoneKb2Settings.GetBooleanValue(prefName);
    }

    protected void SetGestureDefaultPointerMode(String packageName, boolean value) {
        keyoneKb2Settings.SetBooleanValue(GESTURE_POINTER_MODE_PREFIX+packageName, value);
    }

    public boolean GesturePointerMode = GESTURE_POINTER_MODE_DEFAULT;
    protected boolean _gestureInputScrollViewMode = false;
    public boolean ActionTryChangeGestureInputScrollMode() {
        if(!IsInputMode())
            return false;
        _gestureInputScrollViewMode = !_gestureInputScrollViewMode;
        Log.d(TAG2, "GESTURE_INPUT_SCROLL_MODE SET="+ _gestureInputScrollViewMode);
        return true;
    }

    public boolean ActionDisableGestureInputScrollMode() {
        _gestureInputScrollViewMode = false;
        Log.d(TAG2, "GESTURE_INPUT_SCROLL_MODE SET="+ _gestureInputScrollViewMode);
        return true;
    }

    public boolean ActionTryChangeGesturePointerModeAtViewMode() {
        if(IsInputMode())
            return false;
        GesturePointerMode = !GesturePointerMode;
        Log.d(TAG2, "GESTURE_POINTER_MODE SET="+ GesturePointerMode);
        return true;
    }

    public boolean ActionResetGesturePointerMode() {
        GesturePointerMode = GetGestureDefaultPointerMode();
        Log.d(TAG2, "GESTURE_POINTER_MODE SET="+ GesturePointerMode);
        return true;
    }

    protected boolean IsInputMode() {
        if(getCurrentInputEditorInfo() == null) return false;
        //Log.d(TAG2, "IsInputMode() "+getCurrentInputEditorInfo().inputType);
        return getCurrentInputEditorInfo().inputType > 0;
    }

    protected boolean ActionTryTurnOffGesturesMode() {
        mode_keyboard_gestures_plus_up_down = false;
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
