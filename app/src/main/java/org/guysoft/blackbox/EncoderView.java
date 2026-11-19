// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.function.IntConsumer;

/** Endless relative encoder. The marker shows gestures, not a device value. */
final class EncoderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final IntConsumer turn;
    private float previousY, remainder, angle;

    EncoderView(Context context, String name, IntConsumer turn) {
        super(context);
        this.turn = turn;
        setContentDescription(name + " encoder; drag up or down; relative control, not value feedback");
    }

    @Override protected void onDraw(Canvas canvas) {
        float x = getWidth() / 2f, y = getHeight() / 2f;
        float radius = Math.min(x, y) - 5;
        paint.setColor(Color.rgb(66, 81, 97));
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(Color.rgb(32, 43, 55));
        canvas.drawCircle(x, y, radius - 3, paint);
        paint.setColor(Color.rgb(49, 192, 173));
        paint.setStrokeWidth(4);
        canvas.drawLine(x + (float) Math.sin(angle) * radius * .5f,
                y - (float) Math.cos(angle) * radius * .5f,
                x + (float) Math.sin(angle) * radius * .85f,
                y - (float) Math.cos(angle) * radius * .85f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            previousY = event.getY();
            remainder = 0;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            remainder += (previousY - event.getY()) / (8 * getResources().getDisplayMetrics().density);
            previousY = event.getY();
            int delta = Math.max(-63, Math.min(63, (int) remainder));
            if (delta != 0) {
                remainder -= delta;
                angle += delta * .12f;
                turn.accept(delta);
                invalidate();
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}
