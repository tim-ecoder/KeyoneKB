package com.ai10.k12kb;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PillBadgeHelper {

    private static final Pattern BADGE_PATTERN = Pattern.compile("^([A-Za-z0-9]+)\\.\\s*(.*)$", Pattern.DOTALL);
    private static final String BADGE_TAG = "pill_badge";
    private static final String HINT_ARROW_TAG = "pill_hint_arrow";
    private static final String HINT_TEXT_TAG = "pill_hint_text";

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
                // Skip standalone CompoundButtons (Switch) — they handle their own toggle
                if (tv instanceof CompoundButton) continue;
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
        boolean hasSwitch = false;
        CompoundButton switchChild = null;
        for (int j = 0; j < pill.getChildCount(); j++) {
            View c = pill.getChildAt(j);
            if (c instanceof CompoundButton) {
                hasSwitch = true;
                switchChild = (CompoundButton) c;
            }
            if (c instanceof TextView && !BADGE_TAG.equals(c.getTag())) {
                TextView tv = (TextView) c;
                // Skip chevrons and badges (short text)
                if (tv.getText().length() > 2) {
                    // Don't apply ellipsis to Switch — we'll extract its text
                    if (!(c instanceof CompoundButton)) {
                        applyEllipsisToTextView(tv);
                    }
                    hasLongText = true;
                }
            }
        }
        if (hasLongText) {
            if (hasSwitch && switchChild != null
                    && switchChild.getText().length() > 0) {
                // Extract Switch's text into a separate TextView so that
                // tapping text expands pill, tapping switch toggles it
                String switchText = switchChild.getText().toString();

                final TextView textView = new TextView(pill.getContext());
                textView.setText(switchText);
                textView.setTag(R.id.pill_set_text,
                        switchChild.getTag(R.id.pill_set_text));
                textView.setTextColor(switchChild.getTextColors());
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        switchChild.getTextSize());
                LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                tvParams.gravity = Gravity.CENTER_VERTICAL;
                textView.setLayoutParams(tvParams);
                applyEllipsisToTextView(textView);

                // Shrink Switch to just the toggle control
                switchChild.setText("");
                switchChild.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                // Insert text before the Switch
                int switchIndex = pill.indexOfChild(switchChild);
                pill.addView(textView, switchIndex);
            }
            if (hasSwitch) {
                // Click-to-expand on all non-Switch children (text + badge)
                for (int j = 0; j < pill.getChildCount(); j++) {
                    View c = pill.getChildAt(j);
                    if (!(c instanceof CompoundButton)) {
                        c.setClickable(true);
                        c.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                togglePillExpand(pill);
                            }
                        });
                    }
                }
            } else {
                pill.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        togglePillExpand(pill);
                    }
                });
            }
        }
    }

    private static void applyEllipsisToTextView(TextView tv) {
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
    }

    private static void toggleExpand(TextView tv) {
        if (tv.getMaxLines() == 1) {
            tv.setSingleLine(false);
            tv.setMaxLines(Integer.MAX_VALUE);
            tv.setEllipsize(null);
        } else {
            tv.setSingleLine(true);
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
                if (pill.getOrientation() == LinearLayout.VERTICAL) {
                    // Vertical pill (e.g. seekbar): wrap badge + title in a horizontal row
                    int textIndex = pill.indexOfChild(textChild);
                    LinearLayout row = new LinearLayout(pill.getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                    pill.removeView(textChild);
                    row.addView(badge);
                    row.addView(textChild);
                    pill.addView(row, textIndex);
                } else {
                    pill.addView(badge, 0);
                }
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
        // Prefer non-CompoundButton TextViews (e.g. extracted text label)
        TextView fallback = null;
        for (int i = 0; i < pill.getChildCount(); i++) {
            View child = pill.getChildAt(i);
            if (BADGE_TAG.equals(child.getTag())) continue;
            if (child instanceof TextView) {
                if (!(child instanceof CompoundButton)) {
                    return (TextView) child;
                }
                if (fallback == null) {
                    fallback = (TextView) child;
                }
            }
        }
        return fallback;
    }

    private static TextView findBadge(LinearLayout pill) {
        return pill.findViewWithTag(BADGE_TAG);
    }

    /**
     * Show or hide the badge on the pill that contains the given view.
     */
    public static void setBadgeVisible(ViewGroup container, int viewId, boolean visible) {
        View target = container.findViewById(viewId);
        if (target == null) return;
        LinearLayout pill = findPillParent(target, container);
        if (pill == null) return;
        TextView badge = findBadge(pill);
        if (badge != null) {
            badge.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
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

    /**
     * Add expandable hint boxes to pills that have associated hint strings.
     * Call after applyToContainer() so pills are already restructured.
     *
     * @param container the settings container
     * @param hintMap   array of {viewId, hintStringResId} pairs
     */
    public static void applyHints(ViewGroup container, int[][] hintMap) {
        for (int[] entry : hintMap) {
            int viewId = entry[0];
            int hintResId = entry[1];
            View target = container.findViewById(viewId);
            if (target == null) continue;

            // Walk up to the pill parent (LinearLayout with background)
            LinearLayout pill = findPillParent(target, container);
            if (pill == null) continue;

            String hintText = container.getContext().getString(hintResId);
            if (hintText == null || hintText.trim().isEmpty()) continue;

            applyHintToPill(pill, hintText);
        }
    }

    private static LinearLayout findPillParent(View target, ViewGroup container) {
        View current = target;
        while (current != null && current != container) {
            if (current instanceof LinearLayout
                    && ((LinearLayout) current).getBackground() != null
                    && current.getParent() == container) {
                return (LinearLayout) current;
            }
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        // target itself might be inside a wrapper pill that is a direct child of container
        if (target instanceof LinearLayout && ((LinearLayout) target).getBackground() != null) {
            return (LinearLayout) target;
        }
        return null;
    }

    private static void applyHintToPill(final LinearLayout pill, String hintText) {
        Context context = pill.getContext();

        final LinearLayout headerRow;

        if (pill.getOrientation() == LinearLayout.VERTICAL) {
            // Pill is already vertical (spinner, seekbar).
            // Find the existing horizontal title row to add the arrow into.
            LinearLayout existingRow = null;
            for (int i = 0; i < pill.getChildCount(); i++) {
                View child = pill.getChildAt(i);
                if (child instanceof LinearLayout
                        && ((LinearLayout) child).getOrientation() == LinearLayout.HORIZONTAL) {
                    existingRow = (LinearLayout) child;
                    break;
                }
            }
            if (existingRow != null) {
                headerRow = existingRow;
            } else {
                // No horizontal row found — skip this pill
                return;
            }
        } else {
            // Pill is horizontal — collect children into a new header row
            List<View> children = new ArrayList<>();
            for (int i = 0; i < pill.getChildCount(); i++) {
                children.add(pill.getChildAt(i));
            }
            pill.removeAllViews();

            headerRow = new LinearLayout(context);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            for (View child : children) {
                headerRow.addView(child);
            }

            pill.setOrientation(LinearLayout.VERTICAL);
            pill.addView(headerRow);
        }

        // Create the ▾ arrow indicator
        final TextView arrow = new TextView(context);
        arrow.setTag(HINT_ARROW_TAG);
        arrow.setText("\u25BE"); // ▾
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.chevronTextColor, tv, true);
        arrow.setTextColor(tv.data);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        arrowParams.setMarginStart(dpToPx(context, 4));
        arrowParams.setMarginEnd(dpToPx(context, 4));
        arrow.setLayoutParams(arrowParams);

        // Ensure text children use weight so the arrow gets space
        for (int i = 0; i < headerRow.getChildCount(); i++) {
            View c = headerRow.getChildAt(i);
            if (c instanceof TextView && !(c instanceof CompoundButton)
                    && !BADGE_TAG.equals(c.getTag())) {
                ViewGroup.LayoutParams lp = c.getLayoutParams();
                if (lp instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) lp;
                    if (llp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                        llp.width = 0;
                        llp.weight = 1;
                        c.setLayoutParams(llp);
                    }
                }
            }
        }

        // Insert arrow before Switch if present, otherwise at end of header row
        int arrowIndex = headerRow.getChildCount();
        for (int i = 0; i < headerRow.getChildCount(); i++) {
            if (headerRow.getChildAt(i) instanceof CompoundButton) {
                arrowIndex = i;
                break;
            }
        }
        headerRow.addView(arrow, arrowIndex);

        // Create the hint text box
        final TextView hintView = new TextView(context);
        hintView.setTag(HINT_TEXT_TAG);
        hintView.setText(hintText.trim());
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hintView.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
        hintView.setLineSpacing(0, 1.45f);
        hintView.setBackgroundResource(R.drawable.bg_hint_box);
        int pad = dpToPx(context, 12);
        hintView.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int hMargin = dpToPx(context, 2);
        int topMargin = dpToPx(context, 8);
        hintParams.setMargins(hMargin, topMargin, hMargin, 0);
        hintView.setLayoutParams(hintParams);
        hintView.setVisibility(View.GONE);

        // Append hint at the bottom of the pill
        pill.addView(hintView);

        // Click handler: toggle hint + text ellipsis on header row
        View.OnClickListener hintToggle = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean expanding = hintView.getVisibility() == View.GONE;
                hintView.setVisibility(expanding ? View.VISIBLE : View.GONE);
                arrow.setText(expanding ? "\u25B4" : "\u25BE"); // ▴ or ▾
                // Also expand/collapse truncated text in the header row
                for (int i = 0; i < headerRow.getChildCount(); i++) {
                    View c = headerRow.getChildAt(i);
                    if (c instanceof TextView && !(c instanceof CompoundButton)
                            && !HINT_ARROW_TAG.equals(c.getTag())
                            && !BADGE_TAG.equals(c.getTag())) {
                        TextView t = (TextView) c;
                        if (expanding) {
                            t.setSingleLine(false);
                            t.setMaxLines(Integer.MAX_VALUE);
                            t.setEllipsize(null);
                        } else {
                            t.setSingleLine(true);
                            t.setEllipsize(TextUtils.TruncateAt.END);
                        }
                    }
                }
            }
        };

        // Check if header has interactive controls (Switch, or views with existing click listeners)
        boolean hasInteractiveControl = false;
        for (int i = 0; i < headerRow.getChildCount(); i++) {
            View c = headerRow.getChildAt(i);
            if (c instanceof CompoundButton || (c.hasOnClickListeners() && !(c instanceof TextView))) {
                hasInteractiveControl = true;
                break;
            }
        }

        if (hasInteractiveControl) {
            // Set hintToggle on non-interactive children only
            for (int i = 0; i < headerRow.getChildCount(); i++) {
                View child = headerRow.getChildAt(i);
                if (child instanceof CompoundButton) continue;
                if (child.hasOnClickListeners() && !(child instanceof TextView)) continue;
                child.setClickable(true);
                child.setOnClickListener(hintToggle);
            }
        } else {
            pill.setOnClickListener(hintToggle);
        }
    }
}
