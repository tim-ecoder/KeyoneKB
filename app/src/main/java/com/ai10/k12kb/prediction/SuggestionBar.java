package com.ai10.k12kb.prediction;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Configurable suggestion bar UI showing word predictions above the keyboard.
 * Styled to match the on-screen keyboard (Gboard dark theme).
 */
public class SuggestionBar extends LinearLayout {

    // Keyboard-matching colors (from colors.xml)
    private static final int COLOR_BAR_BG        = 0xFF202124; // keyboard_background_color
    private static final int COLOR_SLOT_BG       = 0xFF3C4043; // keyboard_key_bg
    private static final int COLOR_SLOT_PRESSED  = 0xFF5F6368; // keyboard_pressed
    private static final int COLOR_TEXT           = 0xFFE8EAED; // keyboard_text_color
    private static final int COLOR_DIVIDER        = 0xFF101012;

    private static final int SLOT_CORNER_RADIUS_DP = 6;
    private static final int SLOT_INSET_DP         = 3;
    private static final int SLOT_INSET_TB_DP      = 1; // top/bottom margin inside slots (3 - 2)
    private static final int DIVIDER_HEIGHT_DP     = 3;

    private TextView[] slots;
    private int numSlots;
    private OnSuggestionClickListener clickListener;

    public interface OnSuggestionClickListener {
        void onSuggestionClicked(int index, String word);
    }

    public SuggestionBar(Context context, int heightDp, int slotCount) {
        super(context);
        this.numSlots = Math.max(1, slotCount);
        this.slots = new TextView[numSlots];
        setOrientation(VERTICAL);
        setBackgroundColor(COLOR_BAR_BG);

        int totalHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, heightDp + DIVIDER_HEIGHT_DP,
                getResources().getDisplayMetrics());
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, totalHeight));

        // Horizontal row for suggestion slots
        int rowHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, heightDp, getResources().getDisplayMetrics());
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));

        // Auto-compute font size: roughly 40% of height in dp
        float fontSizeSp = heightDp * 0.4f;
        if (fontSizeSp < 10) fontSizeSp = 10;
        if (fontSizeSp > 24) fontSizeSp = 24;

        int insetPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, SLOT_INSET_DP, getResources().getDisplayMetrics());
        int insetTBPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, SLOT_INSET_TB_DP, getResources().getDisplayMetrics());
        int cornerPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, SLOT_CORNER_RADIUS_DP, getResources().getDisplayMetrics());

        for (int i = 0; i < numSlots; i++) {
            final int index = i;
            TextView tv = new TextView(context);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
            lp.setMargins(insetPx, insetTBPx, (i == numSlots - 1) ? insetPx : 0, insetTBPx);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(COLOR_TEXT);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
            tv.setMaxLines(1);
            tv.setPadding(4, 0, 4, 0);
            tv.setBackground(createSlotDrawable(cornerPx));
            tv.setClickable(true);
            tv.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (clickListener != null) {
                        String text = ((TextView) v).getText().toString();
                        if (!text.isEmpty()) {
                            clickListener.onSuggestionClicked(index, text);
                        }
                    }
                }
            });
            slots[i] = tv;
            row.addView(tv);
        }

        addView(row);

        // 2dp dark divider at bottom
        View divider = new View(context);
        int dividerHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, DIVIDER_HEIGHT_DP, getResources().getDisplayMetrics());
        divider.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dividerHeight));
        divider.setBackgroundColor(COLOR_DIVIDER);
        addView(divider);
    }

    private static StateListDrawable createSlotDrawable(int cornerPx) {
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(COLOR_SLOT_PRESSED);
        pressed.setCornerRadius(cornerPx);

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(COLOR_SLOT_BG);
        normal.setCornerRadius(cornerPx);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    public void setOnSuggestionClickListener(OnSuggestionClickListener listener) {
        this.clickListener = listener;
    }

    public int getNumSlots() {
        return numSlots;
    }

    /**
     * Update displayed suggestions.
     */
    public void update(List<WordPredictor.Suggestion> suggestions) {
        for (int i = 0; i < numSlots; i++) {
            if (suggestions != null && i < suggestions.size()) {
                String word = suggestions.get(i).word;
                slots[i].setText(word);
                slots[i].setTextColor(COLOR_TEXT);
                if (i == 0) {
                    slots[i].setTypeface(null, Typeface.BOLD);
                } else {
                    slots[i].setTypeface(null, Typeface.NORMAL);
                }
            } else {
                slots[i].setText("");
            }
        }
    }

    /**
     * Clear all suggestions.
     */
    public void clear() {
        for (int i = 0; i < numSlots; i++) {
            slots[i].setText("");
        }
    }
}
