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

    protected boolean pref_keyboard_gestures_at_views_enable = true;

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
            if(!_modeGesturePointerAtViewMode)
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
                || (!IsInputMode() && _modeGesturePointerAtViewMode)) {
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
                if(!IsInputMode() && _modeGesturePointerAtViewMode)
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
                } else if (mode_gesture_cursor_plus_up_down || (!IsInputMode() && _modeGesturePointerAtViewMode) || (IsInputMode() && _modeGestureScrollAtInputMode)) {
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

    protected boolean IsInputMode() {
        if(getCurrentInputEditorInfo() == null) return false;
        return getCurrentInputEditorInfo().inputType > 0;
    }


    //region IMITATE SCREEN GESTURE

    Boolean directionTop;

    private void ImitateScreenGesture(boolean isToTop, float lastGestureY, float currentGestureY, long time) {
        //Log.d(TAG2, "K="+K);
        Ktime = 1.3f;
        //Log.d(TAG2, "Ktime="+Ktime);

        int coordFrom = Math.min(Math.round((lastGestureY + 125) * K), 1400);
        int coordTo = Math.min(Math.round((currentGestureY + 125) * K), 1400);
        long time2 = Math.round(time*Ktime);
        long startTime = 0L;

        //Log.d(TAG2, "coordFrom="+coordFrom);
        //Log.d(TAG2, "coordTo="+coordTo);
        //Log.d(TAG2, "time="+time2);


        if (isToTop) {
            Log.d(TAG2, "GESTURE_SCROLL_UP");
        } else {
            Log.d(TAG2, "GESTURE_SCROLL_DOWN");
        }

        if(directionTop != null && directionTop != isToTop) {
            Log.d(TAG2, "GESTURE_CHANGE_DIRECTION");
            MovementsInQueue.clear();
            TimesInQueue.clear();
            //ResetScrollGestureStateToInitial();
        }

        directionTop = isToTop;

        if(lastStroke == null) {
            ResetScrollGestureStateToInitial();

            Path path = new Path();
            path.moveTo(middleXValue, coordFrom);
            path.lineTo(middleXValue, coordTo);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription strokeDescription = new GestureDescription.StrokeDescription(path, startTime, 25, true);
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
            //Log.d(TAG2, "ADDING_GESTURE_TO_RANGE");
            MovementsInQueue.add(coordTo - coordFrom);
            TimesInQueue.add(time2);
        }
    }

    private void ResetScrollGestureStateToInitial() {
        Log.d(TAG2, "RESET_SCROLL_GESTURE");
        lastStroke = null;
        nextFrom = 0;
        MovementsInQueue.clear();
        TimesInQueue.clear();
        directionTop = null;
    }

    ArrayList<Integer> MovementsInQueue = new ArrayList<>();
    ArrayList<Long> TimesInQueue = new ArrayList<>();
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
                long time2 = 0;
                for(int i = 0; i < MovementsInQueue.size(); i++) {
                    lineTo += MovementsInQueue.get(i);
                    //Log.d(TAG2, "ADD: "+MovementsInQueue.get(i));
                    time2 += TimesInQueue.get(i);
                }
                MovementsInQueue.clear();
                TimesInQueue.clear();
                path.lineTo(middleXValue, lineTo);
                Log.d(TAG2, "TO "+lineTo);
                nextFrom = lineTo;
                Log.d(TAG2, "TIME "+time2);
                GestureDescription.StrokeDescription stroke = lastStroke.continueStroke(path, 0, time2, true);
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
                if(directionTop == null)
                    return;
                Path path = new Path();
                path.moveTo(middleXValue, nextFrom);
                if(directionTop)
                    path.lineTo(middleXValue, nextFrom+50);
                else
                    path.lineTo(middleXValue, nextFrom-50);


                GestureDescription.StrokeDescription stroke = lastStroke.continueStroke(path, 0, 250, false);
                GestureDescription.Builder builder = new GestureDescription.Builder();
                builder = builder.addStroke(stroke);
                GestureDescription  gesture = builder.build();
                KeyoneKb2AccessibilityService.Instance.dispatchGesture(
                        gesture,
                        null,
                        null);
                ResetScrollGestureStateToInitial();
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
        if(!IsInputMode() && _modeGesturePointerAtViewMode) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_DOWN, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _modeGestureScrollAtInputMode) {
            //if(ActiveNode != null) {
                ImitateScreenGesture(true, lastGestureY, currentGestureY, time);
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
        if(!IsInputMode() && _modeGesturePointerAtViewMode) {
            keyDownUpMeta(KeyEvent.KEYCODE_DPAD_UP, inputConnection, 0);
            return true;
        }
        if(IsInputMode() && _modeGestureScrollAtInputMode) {
            //if(ActiveNode != null) {
                //AccessibilityNodeInfo info = FindScrollableRecurs(ActiveNode);
                ImitateScreenGesture(false, lastGestureY, currentGestureY, time);
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
        if(!IsInputMode() && _modeGesturePointerAtViewMode) {
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
        if(!IsInputMode() && _modeGesturePointerAtViewMode) {
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
            return true;
        }
        return false;
    }

    public boolean ActionTryDisableGestureCursorModeUnHoldState() {
        if(gestureCursorAtInputEnabledByHold && KeyHoldPlusGestureEnabled) {
            gestureCursorAtInputEnabledByHold = false;
            mode_gesture_cursor_at_input_mode = false;
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

    public boolean ActionChangeGestureModeEnableState() {
        pref_keyboard_gestures_at_views_enable = !pref_keyboard_gestures_at_views_enable;
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
        if (mode_gesture_cursor_at_input_mode && IsInputMode()) {
            mode_gesture_cursor_at_input_mode = false;
            mode_gesture_cursor_plus_up_down = false;
            return true;
        }
        return false;
    }

    //endregion

    //region Actions GESTURE POINTER/SCROLL (VIEW_MODE)

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

    public boolean _modeGesturePointerAtViewMode = GESTURE_POINTER_MODE_DEFAULT;
    protected boolean _modeGestureScrollAtInputMode = false;
    public boolean ActionTryChangeGestureInputScrollMode() {
        if(!IsInputMode())
            return false;
        _modeGestureScrollAtInputMode = !_modeGestureScrollAtInputMode;
        Log.d(TAG2, "GESTURE_INPUT_SCROLL_MODE SET="+ _modeGestureScrollAtInputMode);
        ResetGestureMovementCoordsToInitial();
        return true;
    }

    public boolean ActionTryDisableGestureInputScrollMode() {
        if(_modeGestureScrollAtInputMode) {
            _modeGestureScrollAtInputMode = false;
            Log.d(TAG2, "GESTURE_INPUT_SCROLL_MODE SET="+ _modeGestureScrollAtInputMode);
            ResetGestureMovementCoordsToInitial();
            return true;
        }
        return false;
    }

    public boolean ActionTryChangeGesturePointerModeAtViewMode() {
        if(IsInputMode())
            return false;
        _modeGesturePointerAtViewMode = !_modeGesturePointerAtViewMode;
        Log.d(TAG2, "GESTURE_POINTER_MODE SET="+ _modeGesturePointerAtViewMode);
        ResetGestureMovementCoordsToInitial();
        return true;
    }

    public boolean ActionResetGesturePointerMode() {
        _modeGesturePointerAtViewMode = GetGestureDefaultPointerMode();
        Log.d(TAG2, "GESTURE_POINTER_MODE SET="+ _modeGesturePointerAtViewMode);
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
