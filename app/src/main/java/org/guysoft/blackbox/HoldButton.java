// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.Button;
import java.util.function.Consumer;

/** Touch preserves holds; accessibility clicks send a complete press/release. */
final class HoldButton extends Button {
    private final Consumer<Boolean> edge;
    private boolean held, touchClick;

    HoldButton(Context context, String label, Consumer<Boolean> edge) {
        super(context);
        this.edge = edge;
        setText(label);
        setTextSize(12);
        setMinWidth(0);
        setMinimumWidth(0);
        setPadding(2, 0, 2, 0);
        setContentDescription(label + " panel button; hold supported");
        setClickable(true);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            held = true;
            setPressed(true);
            edge.accept(true);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            release();
            touchClick = true;
            performClick();
            touchClick = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) release();
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    void release() {
        if (held) edge.accept(false);
        held = false;
        setPressed(false);
    }

    @Override public boolean performClick() {
        super.performClick();
        if (!touchClick) { edge.accept(true); edge.accept(false); }
        return true;
    }
}
