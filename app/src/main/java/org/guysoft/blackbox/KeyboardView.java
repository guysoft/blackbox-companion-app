// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.function.BiConsumer;

/** F3–G5 keybed. Panel-key mode deliberately bypasses MIDI note semantics. */
final class KeyboardView extends View {
    private final RectF[] keys = new RectF[27];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawKey = new RectF();
    private final String[] labels = new String[27];
    private final KeyHolds holds;

    KeyboardView(Context context, BiConsumer<Integer, Boolean> edge) {
        super(context);
        holds = new KeyHolds(edge);
        setContentDescription("27-key keyboard F3 to G5; supports chords and sliding");
        setFocusable(true);
        String[] names = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};
        for (int i = 0; i < labels.length; i++) {
            int note = 53 + i;
            labels[i] = names[note % 12] + (note / 12 - 1);
        }
    }

    private boolean black(int index) {
        int n = (53 + index) % 12;
        return n == 1 || n == 3 || n == 6 || n == 8 || n == 10;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float width = w / 16f; // F3–G5 has sixteen white keys.
        int white = 0;
        for (int i = 0; i < 27; i++) {
            if (black(i)) keys[i] = new RectF((white - .32f) * width, 0,
                    (white + .32f) * width, h * .61f);
            else keys[i] = new RectF(white++ * width, 0, white * width, h);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        for (int layer = 0; layer < 2; layer++) {
            for (int i = 0; i < keys.length; i++) {
                if (black(i) != (layer == 1)) continue;
                paint.setColor(holds.held(i) ? Color.rgb(49, 192, 173)
                        : black(i) ? Color.rgb(27, 32, 39) : Color.rgb(239, 232, 212));
                RectF key = drawKey;
                key.set(keys[i]);
                key.inset(1, 1);
                canvas.drawRoundRect(key, 4, 4, paint);
                if (!black(i)) {
                    paint.setColor(Color.DKGRAY);
                    paint.setTextSize(Math.min(24, getWidth() / 44f));
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(labels[i],
                            key.centerX(), key.bottom - 12, paint);
                }
            }
        }
    }

    private int hit(float x, float y) {
        for (int layer = 1; layer >= 0; layer--)
            for (int i = 0; i < keys.length; i++)
                if (black(i) == (layer == 1) && keys[i].contains(x, y)) return i;
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) holds.clear();
        else {
            if (action == MotionEvent.ACTION_DOWN) holds.clear();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                int i = event.getActionIndex();
                holds.begin(event.getPointerId(i), hit(event.getX(i), event.getY(i)));
            }
            int lifted = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                    ? event.getActionIndex() : -1;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == lifted) holds.end(event.getPointerId(i));
                else holds.move(event.getPointerId(i), hit(event.getX(i), event.getY(i)));
            }
        }
        getParent().requestDisallowInterceptTouchEvent(true);
        if (action == MotionEvent.ACTION_UP) performClick();
        invalidate();
        return true;
    }

    void release() { holds.suspend(); invalidate(); }
    @Override public boolean performClick() { super.performClick(); return true; }
}
