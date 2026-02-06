package com.ai10.k12kb.prediction;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Configurable suggestion bar UI showing word predictions above the keyboard.
 */
public class SuggestionBar extends LinearLayout {

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
        setOrientation(HORIZONTAL);
        setBackgroundColor(0xFF1A1A1A);
        int height = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, heightDp, getResources().getDisplayMetrics());
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, height));
        setGravity(Gravity.CENTER_VERTICAL);

        // Auto-compute font size: roughly 40% of height in dp
        float fontSizeSp = heightDp * 0.4f;
        if (fontSizeSp < 10) fontSizeSp = 10;
        if (fontSizeSp > 24) fontSizeSp = 24;

        for (int i = 0; i < numSlots; i++) {
            final int index = i;
            TextView tv = new TextView(context);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
            if (i > 0) {
                lp.setMargins(1, 0, 0, 0);
            }
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(0xFFCCCCCC);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
            tv.setMaxLines(1);
            tv.setPadding(4, 0, 4, 0);
            tv.setBackgroundColor(0xFF2A2A2A);
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
            addView(tv);
        }
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
                slots[i].setTextColor(0xFFCCCCCC);
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
