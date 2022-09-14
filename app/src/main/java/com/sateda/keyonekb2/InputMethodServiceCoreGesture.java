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


    protected Processable OnGestureDoubleClick;
    protected Processable OnGestureTripleClick;
    protected Processable OnGestureSecondClickUp;

    protected boolean KeyboardGesturesAtInputModeEnabled;

    protected boolean KeyHoldPlusGestureEnabled;

    protected KeyoneKb2Settings keyoneKb2Settings;

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

        K = (displayMetrics.heightPixels) / (450) * 0.85f;

    }

    int middleYValue;
    int middleXValue;
    float K;

    protected abstract boolean IsGestureModeEnabled();

    protected boolean ProcessGestureAtMotionEvent(MotionEvent motionEvent) {
        //LogKeyboardTest("GESTURE ACTION: "+motionEvent);

        if (IsNoGesturesMode())
            return true;

        if(IsInputMode()) {
            if(!KeyboardGesturesAtInputModeEnabled)
                return true;
        } else { //!IsInputMode()

            if (!IsGestureModeEnabled())
                return true;
            if(!GesturePointerMode)
                return false;
        }

        //&& !mode_keyboard_gestures_plus_up_down
        if ((!mode_keyboard_gestures )
            || modeDoubleClickGesture) {
            //Log.d(TAG, "onGenericMotionEvent(): " + motionEvent);
            ProcessDoubleGestureClick(motionEvent);
        }
        //if (mode_keyboard_gestures || (!IsInputMode() && GesturePointerMode)) {
        if (mode_keyboard_gestures || (!IsInputMode() && GesturePointerMode) || (IsInputMode() && _gestureInputScrollViewMode)) {

            if(CheckMotionAction(motionEvent,  MotionEvent.ACTION_MOVE) &&
                    motionEvent.getHistorySize() > 0) {
                //ProcessGestureAtomicMovement(motionEvent, -1);
                for (int i = 0; i < motionEvent.getHistorySize(); i++) {
                    ProcessGesturePointersAndPerformAction(motionEvent, i);
                }
            } else {
                ProcessGesturePointersAndPerformAction(motionEvent, -1);
            }
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
            ResetScrollGestureStateToInitial();
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
                if(!IsInputMode() && GesturePointerMode)
                    Kpower = 2.5f;
                if(IsInputMode() && _gestureInputScrollViewMode)
                    Kpower = 0.75f;

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
                } else if (mode_keyboard_gestures_plus_up_down || (!IsInputMode() && GesturePointerMode) || (IsInputMode() && _gestureInputScrollViewMode)) {
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
            //Это тут точно не нужно иначе прокрутка перестенет доплывать/доезжать после окончания действия
            //ResetScrollGestureStateToInitial();
            return true;
        }
        return false;
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

    //region GESTURE

    long lastTime = 0;
    Boolean directionTop;

    private void moveByGesture(boolean isToTop, float lastGestureY, float currentGestureY, long time) {
        Log.d(TAG2, "K="+K);

        int coordFrom = Math.min(Math.round((lastGestureY + 75) * K), 1450);
        int coordTo = Math.min(Math.round((currentGestureY + 75) * K), 1450);
        //long time2 = Math.max(150, time);
        long time2 = 150L;
        long startTime = 0L;

        Log.d(TAG2, "coordFrom="+coordFrom);
        Log.d(TAG2, "coordTo="+coordTo);
        Log.d(TAG2, "time="+time2);



        if (isToTop) {
            Log.d(TAG2, "GESTURE_SCROLL_UP");
        } else {
            Log.d(TAG2, "GESTURE_SCROLL_DOWN");
        }

        if(directionTop != null && directionTop != isToTop) {
            ResetScrollGestureStateToInitial();
        }

        directionTop = isToTop;

        if(lastStroke == null) {
            ResetScrollGestureStateToInitial();

            Path path = new Path();
            path.moveTo(middleXValue, coordFrom);
            path.lineTo(middleXValue, coordTo);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription strokeDescription = new GestureDescription.StrokeDescription(path, startTime, time2, true);
            lastStroke = strokeDescription;
            nextFrom = coordTo;
            builder = builder.addStroke(strokeDescription);
            GestureDescription gesture = builder.build();
            Log.d(TAG2, "FIRING_FIRST_GESTURE_IN_RANGE");
            KeyoneKb2AccessibilityService.Instance.dispatchGesture(
                    gesture,
                    new GestureCompletedCallback(), null
            );
        } else {
            Log.d(TAG2, "ADDING_GESTURE_TO_RANGE");
            MovementsInQueue.add(coordTo - coordFrom);
        }
        lastTime = time2;
    }

    private void ResetScrollGestureStateToInitial() {
        Log.d(TAG2, "RESET_SCROLL_GESTURE");
        lastStroke = null;
        //gestureNext = null;
        nextFrom = 0;
        MovementsInQueue.clear();
        directionTop = null;
    }

    ArrayList<Integer> MovementsInQueue = new ArrayList<>();
    GestureDescription.StrokeDescription lastStroke;

    int nextFrom = 0;

    class GestureCompletedCallback extends AccessibilityService.GestureResultCallback {
        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            super.onCompleted(gestureDescription);

            if(MovementsInQueue.size() > 0) {
                Path path = new Path();
                path.moveTo(middleXValue, nextFrom);
                Log.d(TAG2, "FROM "+nextFrom);
                int lineTo = nextFrom;
                for(int i = 0; i < MovementsInQueue.size(); i++) {
                    lineTo += MovementsInQueue.get(i);
                    Log.d(TAG2, "ADD: "+MovementsInQueue.get(i));
                }
                MovementsInQueue.clear();
                path.lineTo(middleXValue, lineTo);
                Log.d(TAG2, "TO "+lineTo);
                nextFrom = lineTo;


                GestureDescription.StrokeDescription stroke = lastStroke.continueStroke(path, 0, 150, true);
                GestureDescription.Builder builder = new GestureDescription.Builder();
                builder = builder.addStroke(stroke);
                GestureDescription  gesture = builder.build();
                lastStroke = stroke;
                Log.d(TAG2, "FIRING_GESTURE_FROM_RANGE");
                KeyoneKb2AccessibilityService.Instance.dispatchGesture(
                        gesture,
                        new GestureCompletedCallback(),
                        null);
            } else {
                Log.d(TAG2, "STOP_GESTURE");
                ResetScrollGestureStateToInitial();
                //KeyoneKb2AccessibilityService.Instance.dispatchGesture(
                //        builder.addStroke(new GestureDescription.StrokeDescription(null, 0, 300, false)).build(),
                //        null,
                //        null);
            }
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            super.onCancelled(gestureDescription);
            Log.d(TAG2, "CANCELLED_GESTURE");
            ResetScrollGestureStateToInitial();
        }
    }

    //endregion


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




    private boolean MoveCursorDownSafe(InputConnection inputConnection, float lastGestureY, float currentGestureY, long time) {
        if(!IsInputMode() && GesturePointerMode) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _gestureInputScrollViewMode) {
            //if(ActiveNode != null) {
                moveByGesture(true, lastGestureY, currentGestureY, time);
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

    private boolean MoveCursorUpSafe(InputConnection inputConnection, float lastGestureY, float currentGestureY, long time) {
        Log.d(TAG2, "MOVE_CURSOR!");
        if(!IsInputMode() && GesturePointerMode) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_UP, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _gestureInputScrollViewMode) {
            //if(ActiveNode != null) {
                //AccessibilityNodeInfo info = FindScrollableRecurs(ActiveNode);
                moveByGesture(false, lastGestureY, currentGestureY, time);
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

    //region Actions GESTURE (VIEW_MODE) POINTER/SCROLL

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
        ResetGestureMovementCoordsToInitial();
        return true;
    }

    public boolean ActionDisableGestureInputScrollMode() {
        _gestureInputScrollViewMode = false;
        Log.d(TAG2, "GESTURE_INPUT_SCROLL_MODE SET="+ _gestureInputScrollViewMode);
        ResetGestureMovementCoordsToInitial();
        return true;
    }

    public boolean ActionTryChangeGesturePointerModeAtViewMode() {
        if(IsInputMode())
            return false;
        GesturePointerMode = !GesturePointerMode;
        Log.d(TAG2, "GESTURE_POINTER_MODE SET="+ GesturePointerMode);
        ResetGestureMovementCoordsToInitial();
        return true;
    }

    public boolean ActionResetGesturePointerMode() {
        GesturePointerMode = GetGestureDefaultPointerMode();
        Log.d(TAG2, "GESTURE_POINTER_MODE SET="+ GesturePointerMode);
        ResetGestureMovementCoordsToInitial();
        return true;
    }

    protected boolean IsInputMode() {
        if(getCurrentInputEditorInfo() == null) return false;
        return getCurrentInputEditorInfo().inputType > 0;
    }

    protected boolean ActionTryTurnOffGesturesMode() {
        mode_keyboard_gestures_plus_up_down = false;
        if (mode_keyboard_gestures) {
            mode_keyboard_gestures = false;
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
