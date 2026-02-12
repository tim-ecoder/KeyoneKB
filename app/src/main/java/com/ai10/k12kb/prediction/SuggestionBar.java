package com.ai10.k12kb.prediction;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
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
    private static final int COLOR_TRANSLATION    = 0xFF8AB4F8; // light blue for translations

    private static final int SLOT_CORNER_RADIUS_DP = 6;
    private static final int SLOT_INSET_DP         = 3;
    private static final int SLOT_INSET_TB_DP      = 2;
    private static final int DIVIDER_HEIGHT_DP     = 3;

    private TextView[] slots;
    private int[] slotToSuggestion; // maps slot position to suggestion index
    private int numSlots;
    private boolean showingTranslations = false;
    private OnSuggestionClickListener clickListener;

    public interface OnSuggestionClickListener {
        void onSuggestionClicked(int index, String word);
    }

    public SuggestionBar(Context context, int heightDp, int slotCount) {
        super(context);
        this.numSlots = Math.max(1, slotCount);
        this.slots = new TextView[numSlots];
        this.slotToSuggestion = new int[numSlots];
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        int totalHeightDp = heightDp + DIVIDER_HEIGHT_DP * 2; // top + bottom dividers
        int totalHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, totalHeightDp, getResources().getDisplayMetrics());
        int dividerPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, DIVIDER_HEIGHT_DP, getResources().getDisplayMetrics());

        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, totalHeight));

        // Background: divider color fills all, bar layer inset top+bottom for divider stripes
        ColorDrawable dividerLayer = new ColorDrawable(COLOR_DIVIDER);
        ColorDrawable barLayer = new ColorDrawable(COLOR_BAR_BG);
        LayerDrawable bg = new LayerDrawable(new android.graphics.drawable.Drawable[]{dividerLayer, barLayer});
        bg.setLayerInset(0, 0, 0, 0, 0);                       // divider fills all
        bg.setLayerInset(1, 0, dividerPx, 0, dividerPx);       // bar layer leaves top+bottom for divider
        setBackground(bg);

        // Padding so slots don't overlap the divider stripes
        setPadding(0, dividerPx, 0, dividerPx);

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

        int padHPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());

        for (int i = 0; i < numSlots; i++) {
            final int index = i;
            TextView tv = new TextView(context);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            lp.setMargins(insetPx, insetTBPx, (i == numSlots - 1) ? insetPx : 0, insetTBPx);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setIncludeFontPadding(false);
            tv.setTextColor(COLOR_TEXT);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
            tv.setMaxLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(padHPx, 0, padHPx, 0);
            tv.setBackground(createSlotDrawable(cornerPx));
            tv.setClickable(true);
            tv.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    if (clickListener != null) {
                        String text = ((TextView) v).getText().toString();
                        if (!text.isEmpty()) {
                            clickListener.onSuggestionClicked(slotToSuggestion[index], text);
                        }
                    }
                }
            });
            slots[i] = tv;
            addView(tv);
        }
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
    public void update(List<WordPredictor.Suggestion> suggestions, String prefix) {
        showingTranslations = false;
        String pfx = (prefix != null) ? prefix.toLowerCase() : "";
        int count = (suggestions != null) ? suggestions.size() : 0;

        // Build mapping: suggestion index -> slot position
        // Priority (index 0) in center, rarer words toward left
        int[] suggToSlot = new int[numSlots];
        int center = numSlots / 2;
        suggToSlot[0] = center;
        int right = center + 1;
        int left = center - 1;
        for (int s = 1; s < numSlots; s++) {
            if (right < numSlots) {
                suggToSlot[s] = right++;
            } else if (left >= 0) {
                suggToSlot[s] = left--;
            }
        }
        // Build reverse mapping for click handler
        for (int s = 0; s < numSlots; s++) {
            slotToSuggestion[suggToSlot[s]] = s;
        }

        // Clear all slots first
        for (int i = 0; i < numSlots; i++) {
            slots[i].setText("");
            slots[i].setVisibility(View.VISIBLE);
            slots[i].setTypeface(null, Typeface.NORMAL);
            slots[i].setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams lp = (LayoutParams) slots[i].getLayoutParams();
            lp.weight = 1;
            lp.width = 0;
            slots[i].setLayoutParams(lp);
        }

        // Place suggestions into their mapped slots
        for (int s = 0; s < count && s < numSlots; s++) {
            String word = suggestions.get(s).word;
            int slot = suggToSlot[s];
            slots[slot].setText(word);
            slots[slot].setTextColor(COLOR_TEXT);
            // Weight proportional to text width; priority bonus for earlier suggestions
            float textWidth = slots[slot].getPaint().measureText(word);
            float weight = Math.max(textWidth, 30f) + (numSlots - 1 - s) * 15f;
            LayoutParams lp = (LayoutParams) slots[slot].getLayoutParams();
            lp.weight = weight;
            lp.width = 0;
            slots[slot].setLayoutParams(lp);
            if (s == 0) {
                slots[slot].setTypeface(null, Typeface.BOLD);
                slots[slot].setEllipsize(null);
                // Priority word gets bigger weight so its pillow is never the smallest
                lp.weight = Math.max(textWidth, 30f) + numSlots * 30f;
                lp.width = 0;
                slots[slot].setLayoutParams(lp);
            } else {
                // Non-priority words truncate from start to show unique endings
                slots[slot].setEllipsize(TextUtils.TruncateAt.START);
            }
        }
    }

    /**
     * Update displayed translations (blue text).
     */
    public void updateTranslation(List<String> translations, String sourceWord) {
        showingTranslations = true;
        int count = (translations != null) ? translations.size() : 0;

        // Clear all slots first
        for (int i = 0; i < numSlots; i++) {
            slots[i].setText("");
            slots[i].setVisibility(View.VISIBLE);
            slots[i].setTypeface(null, Typeface.NORMAL);
            slots[i].setEllipsize(TextUtils.TruncateAt.END);
            slots[i].setTextColor(COLOR_TRANSLATION);
            LayoutParams lp = (LayoutParams) slots[i].getLayoutParams();
            lp.weight = 1;
            lp.width = 0;
            slots[i].setLayoutParams(lp);
        }

        // Place translations left-to-right
        for (int i = 0; i < count && i < numSlots; i++) {
            String word = translations.get(i);
            slots[i].setText(word);
            float textWidth = slots[i].getPaint().measureText(word);
            float weight = Math.max(textWidth, 30f) + (numSlots - i) * 10f;
            LayoutParams lp = (LayoutParams) slots[i].getLayoutParams();
            lp.weight = weight;
            lp.width = 0;
            slots[i].setLayoutParams(lp);
            if (i == 0) {
                slots[i].setTypeface(null, Typeface.BOLD);
            }
        }
    }

    public boolean isShowingTranslations() {
        return showingTranslations;
    }

    /**
     * Clear all suggestions.
     */
    public void clear() {
        showingTranslations = false;
        for (int i = 0; i < numSlots; i++) {
            slots[i].setText("");
            slots[i].setVisibility(View.VISIBLE);
            slots[i].setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams lp = (LayoutParams) slots[i].getLayoutParams();
            lp.weight = 1;
            lp.width = 0;
            slots[i].setLayoutParams(lp);
        }
    }
}
