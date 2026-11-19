// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/** Pointer ownership prevents one finger releasing another finger's key. */
final class KeyHolds {
    private final Map<Integer, Integer> pointers = new HashMap<>();
    private final Set<Integer> blocked = new HashSet<>();
    private final BiConsumer<Integer, Boolean> edge;

    KeyHolds(BiConsumer<Integer, Boolean> edge) { this.edge = edge; }

    void move(int pointer, int key) {
        if (blocked.contains(pointer)) return;
        int previous = pointers.getOrDefault(pointer, -1);
        if (previous == key) return;
        pointers.remove(pointer);
        if (previous >= 0 && !pointers.containsValue(previous)) edge.accept(previous, false);
        if (key >= 0) {
            boolean held = pointers.containsValue(key);
            pointers.put(pointer, key);
            if (!held) edge.accept(key, true);
        }
    }

    boolean held(int key) { return pointers.containsValue(key); }
    void begin(int pointer, int key) {
        blocked.remove(pointer);
        move(pointer, key);
    }
    void end(int pointer) {
        blocked.remove(pointer);
        move(pointer, -1);
    }
    void clear() {
        for (int pointer : pointers.keySet().toArray(new Integer[0])) move(pointer, -1);
        blocked.clear();
    }
    void suspend() {
        Set<Integer> fingers = new HashSet<>(blocked);
        fingers.addAll(pointers.keySet());
        clear();
        blocked.addAll(fingers);
    }
}
