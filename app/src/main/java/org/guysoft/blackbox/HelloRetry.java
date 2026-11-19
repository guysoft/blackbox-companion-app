// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

/** Android can open MIDI ports before Bluetooth GATT is ready to send. */
final class HelloRetry {
    static final int DELAY_MS = 500;
    private long deadline;
    private boolean active;

    void begin(long now) { deadline = now + 10000; active = true; }
    void clear() { active = false; }
    boolean canRetry(int command, long now) {
        return active && command == 1 && now < deadline;
    }
}
