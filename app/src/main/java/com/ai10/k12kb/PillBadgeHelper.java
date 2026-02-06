package com.ai10.k12kb;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PillBadgeHelper {

    private static final Pattern BADGE_PATTERN = Pattern.compile("^([A-Za-z0-9]+)\\.\\s*(.*)$", Pattern.DOTALL);
    private static final String BADGE_TAG = "pill_badge";

    /**
     * Process all direct children of a container, applying badges
     * to any pill whose text starts with "A.", "1.", etc.
     * Handles both LinearLayout pills and standalone Switch/TextView pills.
     */
    public static void applyToContainer(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) child;
                // Only process pills (have a background), skip bare containers
                if (ll.getBackground() != null && hasTextChild(ll)) {
                    applyToPill(ll);
                }
            } else if (child instanceof TextView && child.getVisibility() != View.GONE) {
                // Standalone Switch/Button/TextView (e.g. SwitchPill in settings)
                TextView tv = (TextView) child;
                String text = tv.getText().toString();
                if (BADGE_PATTERN.matcher(text).matches()) {
                    // Wrap in a LinearLayout so we can insert a badge beside it
                    LinearLayout wrapper = wrapInPill(container, tv, i);
                    applyToPill(wrapper);
                }
            }
        }
        // Second pass: apply ellipsis truncation + click-to-expand on all pills
        applyEllipsisToContainer(container);
    }

    /**
     * Walk all pill children, set single-line truncation with ellipsis on text views,
     * and add click-to-expand/collapse toggle on the pill.
     */
    private static void applyEllipsisToContainer(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout) {
                final LinearLayout pill = (LinearLayout) child;
                if (pill.getBackground() == null) continue;
                applyEllipsisToPill(pill);
            } else if (child instanceof TextView) {
                final TextView tv = (TextView) child;
                if (tv.getBackground() == null) continue;
                applyEllipsisToTextView(tv);
                tv.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        toggleExpand(tv);
                    }
                });
            }
        }
    }

    private static void applyEllipsisToPill(final LinearLayout pill) {
        boolean hasLongText = false;
        for (int j = 0; j < pill.getChildCount(); j++) {
            View c = pill.getChildAt(j);
            if (c instanceof TextView && !BADGE_TAG.equals(c.getTag())) {
                TextView tv = (TextView) c;
                // Skip chevrons and badges (short text)
                if (tv.getText().length() > 2) {
                    applyEllipsisToTextView(tv);
                    hasLongText = true;
                }
            }
        }
        if (hasLongText) {
            pill.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    togglePillExpand(pill);
                }
            });
        }
    }

    private static void applyEllipsisToTextView(TextView tv) {
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
    }

    private static void toggleExpand(TextView tv) {
        if (tv.getMaxLines() == 1) {
            tv.setMaxLines(Integer.MAX_VALUE);
            tv.setEllipsize(null);
        } else {
            tv.setMaxLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
        }
    }

    private static void togglePillExpand(LinearLayout pill) {
        for (int j = 0; j < pill.getChildCount(); j++) {
            View c = pill.getChildAt(j);
            if (c instanceof TextView && !BADGE_TAG.equals(c.getTag())
                    && ((TextView) c).getText().length() > 2) {
                toggleExpand((TextView) c);
            }
        }
    }

    /**
     * Apply badge to a single LinearLayout pill.
     * If text starts with "A.", "B.", "1.", etc. — show a blue circle badge.
     * If text does not (or changed to not) — hide the badge.
     */
    public static void applyToPill(LinearLayout pill) {
        TextView textChild = findTextChild(pill);
        if (textChild == null) return;

        String currentText = textChild.getText().toString();
        String weSet = (String) textChild.getTag(R.id.pill_set_text);
        TextView badge = findBadge(pill);

        // Reconstruct full text if we previously stripped the prefix
        String fullText;
        if (weSet != null && currentText.equals(weSet)
                && badge != null && badge.getVisibility() == View.VISIBLE) {
            fullText = badge.getText().toString() + ". " + currentText;
        } else {
            fullText = currentText;
        }

        Matcher m = BADGE_PATTERN.matcher(fullText);
        if (m.matches()) {
            String prefix = m.group(1);
            String rest = m.group(2);

            if (badge == null) {
                badge = createBadge(pill.getContext());
                pill.addView(badge, 0);
            }
            badge.setText(prefix);
            badge.setVisibility(View.VISIBLE);
            textChild.setText(rest);
            textChild.setTag(R.id.pill_set_text, rest);
        } else {
            if (badge != null) {
                badge.setVisibility(View.GONE);
            }
            textChild.setTag(R.id.pill_set_text, null);
        }
    }

    /**
     * Wrap a standalone view (Switch, Button) in a horizontal LinearLayout,
     * transferring background, padding, and margins to the wrapper.
     * On subsequent applyToContainer calls the wrapper is found as a LinearLayout pill.
     */
    private static LinearLayout wrapInPill(ViewGroup parent, TextView child, int index) {
        ViewGroup.LayoutParams originalParams = child.getLayoutParams();

        LinearLayout wrapper = new LinearLayout(child.getContext());
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);

        // Copy size and margins from child to wrapper
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                originalParams.width, originalParams.height);
        if (originalParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) originalParams;
            wrapperParams.setMargins(mp.leftMargin, mp.topMargin,
                    mp.rightMargin, mp.bottomMargin);
            wrapperParams.setMarginStart(mp.getMarginStart());
            wrapperParams.setMarginEnd(mp.getMarginEnd());
        }
        wrapper.setLayoutParams(wrapperParams);

        // Transfer background and padding from child to wrapper
        wrapper.setBackground(child.getBackground());
        wrapper.setPadding(child.getPaddingLeft(), child.getPaddingTop(),
                child.getPaddingRight(), child.getPaddingBottom());

        // Child fills wrapper, no own background/padding
        child.setBackground(null);
        child.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams childParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        child.setLayoutParams(childParams);

        parent.removeViewAt(index);
        wrapper.addView(child);
        parent.addView(wrapper, index);

        return wrapper;
    }

    private static TextView findTextChild(LinearLayout pill) {
        for (int i = 0; i < pill.getChildCount(); i++) {
            View child = pill.getChildAt(i);
            if (BADGE_TAG.equals(child.getTag())) continue;
            if (child instanceof TextView) {
                return (TextView) child;
            }
        }
        return null;
    }

    private static TextView findBadge(LinearLayout pill) {
        return pill.findViewWithTag(BADGE_TAG);
    }

    private static boolean hasTextChild(LinearLayout ll) {
        for (int i = 0; i < ll.getChildCount(); i++) {
            View child = ll.getChildAt(i);
            if (child instanceof TextView && !BADGE_TAG.equals(child.getTag())) {
                return true;
            }
        }
        return false;
    }

    private static TextView createBadge(Context context) {
        TextView badge = new TextView(context);
        badge.setTag(BADGE_TAG);

        int size = dpToPx(context, 26);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMarginEnd(dpToPx(context, 8));
        badge.setLayoutParams(params);

        badge.setBackgroundResource(R.drawable.bg_badge_circle);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);

        return badge;
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}
