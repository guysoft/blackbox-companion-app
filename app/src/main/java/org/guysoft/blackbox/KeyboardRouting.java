// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

/** Physical FM-1 key semantics while a quick-layer button is held. */
final class KeyboardRouting {
    private boolean manual;
    private int layers;

    boolean manual() { return manual; }
    boolean panelKeys() { return manual || layers != 0; }

    void setManual(boolean value, Runnable release) {
        if (panelKeys() != (value || layers != 0)) release.run();
        manual = value;
    }

    void layer(int id, boolean down, Runnable release) {
        // Remote matrix IDs: FX, SCL, EDIT, GLO, SEQ, REC.
        if (id != 2 && id != 3 && id != 6 && id != 7 && id != 11 && id != 13) return;
        int next = down ? layers | (1 << id) : layers & ~(1 << id);
        // Release using the OLD routing; never reinterpret an already held
        // musical key as a destructive layer shortcut.
        if (panelKeys() != (manual || next != 0)) release.run();
        layers = next;
    }
}
