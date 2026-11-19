// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class KeyboardRoutingTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    public static void main(String[] args) {
        KeyboardRouting routing = new KeyboardRouting();
        List<Boolean> releasedAsPanel = new ArrayList<>();
        Runnable release = () -> releasedAsPanel.add(routing.panelKeys());
        check(!routing.panelKeys() && !routing.manual());
        routing.layer(13, true, release); // REC held before touching F3.
        check(routing.panelKeys() && !routing.manual());
        check(releasedAsPanel.equals(Arrays.asList(false)));
        routing.layer(13, true, release); // Duplicate down must not release keys.
        routing.layer(2, true, release);
        routing.layer(13, false, release);
        check(routing.panelKeys() && releasedAsPanel.size() == 1);
        routing.layer(2, false, release);
        check(!routing.panelKeys() && releasedAsPanel.equals(Arrays.asList(false, true)));
        routing.layer(12, true, release); // PLAY is not a keyboard modifier.
        check(!routing.panelKeys() && releasedAsPanel.size() == 2);
        routing.setManual(true, release);
        routing.layer(13, true, release);
        routing.layer(13, false, release);
        check(routing.panelKeys() && routing.manual() && releasedAsPanel.size() == 3);
        routing.setManual(false, release);
        check(!routing.panelKeys() && releasedAsPanel.size() == 4);
        // Switching manual mode off during a held layer stays in panel mode.
        routing.setManual(true, release);
        routing.layer(13, true, release);
        routing.setManual(false, release);
        check(routing.panelKeys() && !routing.manual() && releasedAsPanel.size() == 5);
        routing.layer(13, false, release);
        check(!routing.panelKeys() && releasedAsPanel.size() == 6);
        List<String> edges = new ArrayList<>();
        KeyHolds holds = new KeyHolds((key, down) ->
                edges.add((routing.panelKeys() ? "panel:" : "note:") + key + ":" + down));
        holds.begin(0, 0); // Already playing F3 when REC is pressed.
        routing.layer(13, true, holds::suspend);
        holds.move(0, 0); // Movement must NOT turn that finger into CLEAR.
        check(edges.equals(Arrays.asList("note:0:true", "note:0:false")));
        holds.suspend(); // Repeated release must preserve the suppression.
        holds.move(0, 1);
        check(edges.size() == 2);
        holds.begin(2, 0); // A fresh F3 press while REC is held is a panel key.
        check(edges.get(2).equals("panel:0:true"));
        routing.layer(13, false, holds::suspend);
        check(edges.get(3).equals("panel:0:false"));
        holds.move(2, 0);
        check(edges.size() == 4); // No phantom MIDI note when REC is released.
        holds.end(0);
        holds.end(2);
        holds.begin(0, 0);
        check(edges.get(4).equals("note:0:true"));
        holds.clear();
        System.out.println("Layer keyboard routing and old-route release tests passed");
    }
}
