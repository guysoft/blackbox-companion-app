// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ProtocolTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    private static void rejected(Runnable run) {
        try { run.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted invalid input");
    }
    public static void main(String[] args) {
        check(Arrays.equals(Protocol.frame(2, 42, 40, 1),
                new byte[]{(byte) 240, 125, 66, 66, 2, 42, 40, 1, (byte) 247}));
        rejected(() -> Protocol.frame(10));
        rejected(() -> Protocol.frame(1, 128));
        check(Protocol.reply(Protocol.frame(5, 0), 4) == null);
        check(Protocol.reply(Protocol.frame(5, 0), 5).length == 0);
        rejected(() -> Protocol.reply(Protocol.frame(5, 2), 5));
        byte[] hello = new byte[17 + 13 + 65];
        int[] capabilities = {1, 42, 120, 120, 7, 14, 27};
        for (int i = 0; i < capabilities.length; i++) hello[i] = (byte) capabilities[i];
        byte[] name = "BlackBox_950\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, hello, 17, name.length);
        Arrays.fill(hello, 30, 94, (byte) 'a');
        check(Protocol.hello(hello, 42).equals("BlackBox_950"));
        rejected(() -> Protocol.hello(hello, 43));
        Protocol.Parser parser = new Protocol.Parser();
        List<byte[]> frames = new ArrayList<>();
        byte[] message = Protocol.frame(5, 0);
        parser.accept(message, 0, 3, frames::add);
        parser.accept(new byte[]{(byte) 0xf8}, 0, 1, frames::add);
        parser.accept(message, 3, message.length - 3, frames::add);
        check(frames.size() == 1 && Arrays.equals(frames.get(0), message));
        byte[] joined = new byte[message.length * 2];
        System.arraycopy(message, 0, joined, 0, message.length);
        System.arraycopy(message, 0, joined, message.length, message.length);
        parser.accept(joined, 0, joined.length, frames::add);
        check(frames.size() == 3);
        // MIDI status aborts a damaged SysEx; a later fresh frame recovers.
        parser.accept(new byte[]{(byte) 0xf0, 1, (byte) 0x90, 60, 1, (byte) 0xf7}, 0, 6, frames::add);
        check(frames.size() == 3);
        parser.accept(message, 0, message.length, frames::add);
        check(frames.size() == 4);
        byte[] oversized = new byte[2048];
        oversized[0] = (byte) 0xf0;
        oversized[2047] = (byte) 0xf7;
        parser.accept(oversized, 0, oversized.length, frames::add);
        check(frames.size() == 4);
        List<String> edges = new ArrayList<>();
        KeyHolds holds = new KeyHolds((key, down) -> edges.add(key + ":" + down));
        holds.move(0, 3);
        holds.move(1, 3);
        holds.move(0, -1);
        check(holds.held(3) && edges.size() == 1);
        holds.move(1, 4);
        holds.move(2, 8);
        holds.clear();
        check(edges.equals(Arrays.asList("3:true", "3:false", "4:true", "8:true", "4:false", "8:false")));
        check(!holds.held(4) && !holds.held(8));
        // Rebuilding the keybed for rotation releases all fingers once.
        edges.clear();
        holds.move(5, 1);
        holds.move(6, 1);
        holds.move(7, 9);
        holds.clear();
        holds.clear();
        check(edges.equals(Arrays.asList("1:true", "9:true", "1:false", "9:false")));
        check(!holds.held(1) && !holds.held(9));
        System.out.println("PASS: wire codec, identity, stream framing, bounded parser, pointer ownership");
    }
}
