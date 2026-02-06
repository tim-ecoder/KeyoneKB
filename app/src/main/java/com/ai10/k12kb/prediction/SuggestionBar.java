package com.ai10.k12kb.prediction;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Simple suggestion bar UI showing up to 3 word suggestions.
 * Displayed above the keyboard.
 */
public class SuggestionBar extends LinearLayout {

    private static final int NUM_SLOTS = 3;
    private final TextView[] slots = new TextView[NUM_SLOTS];
    private OnSuggestionClickListener clickListener;

    public interface OnSuggestionClickListener {
        void onSuggestionClicked(int index, String word);
    }

    public SuggestionBar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackgroundColor(0xFF1A1A1A);
        int height = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 36, getResources().getDisplayMetrics());
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, height));
        setGravity(Gravity.CENTER_VERTICAL);

        for (int i = 0; i < NUM_SLOTS; i++) {
            final int index = i;
            TextView tv = new TextView(context);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
            if (i > 0) {
                lp.setMargins(1, 0, 0, 0); // Thin separator
            }
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(0xFFCCCCCC);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
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

    /**
     * Update displayed suggestions.
     * Order: center (index 0) = best, right (index 1) = second, left (index 2) = third.
     * For simplicity we display left-to-right: index 0, 1, 2.
     */
    public void update(List<WordPredictor.Suggestion> suggestions) {
        for (int i = 0; i < NUM_SLOTS; i++) {
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
        // Visibility managed by setCandidatesViewShown() in K12KbIME
    }

    /**
     * Clear all suggestions.
     */
    public void clear() {
        for (int i = 0; i < NUM_SLOTS; i++) {
            slots[i].setText("");
        }
    }
}
