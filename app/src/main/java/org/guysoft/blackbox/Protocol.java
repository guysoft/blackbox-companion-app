// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

/** Wire codec independent of Android, including fragmented/coalesced MIDI input. */
final class Protocol {
    static byte[] frame(int command, int... args) {
        if (command < 1 || command > 9) throw new IllegalArgumentException("command");
        byte[] result = new byte[args.length + 6];
        result[0] = (byte) 0xf0;
        result[1] = 0x7d;
        result[2] = result[3] = 0x42;
        result[4] = (byte) command;
        for (int i = 0; i < args.length; i++) {
            if (args[i] < 0 || args[i] > 127) throw new IllegalArgumentException("data");
            result[i + 5] = (byte) args[i];
        }
        result[result.length - 1] = (byte) 0xf7;
        return result;
    }

    static byte[] reply(byte[] frame, int command) {
        if (frame.length < 7 || frame[0] != (byte) 0xf0 || frame[1] != 0x7d
                || frame[2] != 0x42 || frame[3] != 0x42 || frame[4] != command
                || frame[frame.length - 1] != (byte) 0xf7) return null;
        for (int i = 5; i < frame.length - 1; i++)
            if (frame[i] < 0) throw new IllegalArgumentException("Invalid reply data");
        if (frame[5] != 0) throw new IllegalArgumentException("Remote rejected command: " + frame[5]);
        return Arrays.copyOfRange(frame, 6, frame.length - 1);
    }

    static String hello(byte[] data, int token) {
        if (data.length < 18 || data[0] != 1 || data[1] != token
                || data[2] != 120 || data[3] != 120
                || data[4] != 7 || data[5] != 14 || data[6] != 27)
            throw new IllegalArgumentException("Incompatible panel");
        String[] strings = new String(data, 17, data.length - 17, StandardCharsets.US_ASCII).split("\0", -1);
        if (strings.length != 3 || !strings[0].equals("BlackBox_950")
                || !strings[1].matches("[0-9a-f]{64}") || !strings[2].isEmpty())
            throw new IllegalArgumentException("Unexpected firmware identity");
        return strings[0];
    }

    static final class Parser {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean active;

        void accept(byte[] data, int offset, int count, Consumer<byte[]> consumer) {
            for (int i = offset; i < offset + count; i++) {
                int b = data[i] & 255;
                if (b >= 0xf8) continue; // Real-time messages may interrupt SysEx.
                if (b == 0xf0) {
                    bytes.reset();
                    active = true;
                } else if (!active) continue;
                else if (b >= 0x80 && b != 0xf7) {
                    active = false;
                    bytes.reset();
                    continue;
                }
                bytes.write(b);
                if (b == 0xf7) {
                    active = false;
                    consumer.accept(bytes.toByteArray());
                    bytes.reset();
                } else if (bytes.size() > 1024) {
                    active = false;
                    bytes.reset();
                }
            }
        }
    }
}
