// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

import android.bluetooth.BluetoothDevice;
import android.media.midi.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.Consumer;

/** One worker owns MIDI, the remote lease and the single outstanding request. */
final class MidiController {
    private final MidiManager manager;
    private final HandlerThread thread = new HandlerThread("Blackbox MIDI");
    private final Handler worker;
    private final Consumer<String> status;
    private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
    private MidiDevice device;
    private MidiInputPort send;
    private MidiOutputPort receive;
    private Protocol.Parser parser = new Protocol.Parser();
    private byte[] pending;
    private int token, generation;
    private boolean ready;
    private final boolean[] notes = new boolean[128];
    private final HelloRetry helloRetry = new HelloRetry();
    private final Runnable timeout = this::onTimeout;
    private final Runnable heartbeat = new Runnable() {
        public void run() {
            if (!ready) return;
            if (pending == null && queue.isEmpty()) enqueue(Protocol.frame(5, token));
            worker.postDelayed(this, 500);
        }
    };

    MidiController(MidiManager manager, Consumer<String> status) {
        this.manager = manager;
        this.status = status;
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    void connect(MidiDeviceInfo info, int input, int output) {
        worker.post(() -> {
            close("Connecting…");
            int attempt = generation;
            try {
                manager.openDevice(info, d -> opened(d, input, output, attempt), worker);
            } catch (SecurityException | IllegalArgumentException e) {
                fail(e.getMessage());
            }
        });
    }

    void connectBluetooth(BluetoothDevice bluetooth) {
        worker.post(() -> {
            close("Opening BLE-MIDI…");
            int attempt = generation;
            try {
                manager.openBluetoothDevice(bluetooth, d -> opened(d, 0, 0, attempt), worker);
            } catch (SecurityException | IllegalArgumentException e) {
                fail(e.getMessage());
            }
        });
    }

    private void opened(MidiDevice opened, int input, int output, int attempt) {
        if (attempt != generation) {
            if (opened != null) try { opened.close(); } catch (IOException ignored) {}
            return;
        }
        if (opened == null) { fail("Cannot open MIDI device"); return; }
        device = opened;
        send = device.openInputPort(input);
        receive = device.openOutputPort(output);
        if (send == null || receive == null) { fail("Bidirectional MIDI ports unavailable"); return; }
        parser = new Protocol.Parser();
        receive.connect(new MidiReceiver() {
            @Override public void onSend(byte[] data, int offset, int count, long timestamp) {
                byte[] copy = Arrays.copyOfRange(data, offset, offset + count);
                worker.post(() -> {
                    if (attempt == generation) parser.accept(copy, 0, copy.length, MidiController.this::reply);
                });
            }
        });
        token = new SecureRandom().nextInt(127) + 1;
        helloRetry.begin(SystemClock.uptimeMillis());
        enqueue(Protocol.frame(1, token));
    }

    private void onTimeout() {
        if (pending != null && helloRetry.canRetry(pending[4], SystemClock.uptimeMillis())) {
            // Only HELLO is retried, with the same token, before any controls
            // are enabled. Never replay a key, encoder, MASTER or note action.
            if (write(pending)) worker.postDelayed(timeout, HelloRetry.DELAY_MS);
        } else fail("Panel reply timed out; disconnected");
    }

    private void reply(byte[] frame) {
        if (pending == null) return;
        try {
            byte[] data = Protocol.reply(frame, pending[4]);
            if (data == null) return;
            if (pending[4] == 1) {
                String identity = Protocol.hello(data, token);
                ready = true;
                helloRetry.clear();
                status.accept(identity + " · panel connected · screen unavailable");
                worker.postDelayed(heartbeat, 500);
            }
            worker.removeCallbacks(timeout);
            pending = null;
            pump();
        } catch (IllegalArgumentException e) { fail(e.getMessage()); }
    }

    private void enqueue(byte[] message) {
        if (send == null) return;
        if (queue.size() >= 128) { fail("Control queue overloaded; disconnected"); return; }
        queue.add(message);
        pump();
    }

    private void pump() {
        if (pending != null || queue.isEmpty() || send == null) return;
        pending = queue.remove();
        if (write(pending)) worker.postDelayed(timeout, pending[4] == 1 ? HelloRetry.DELAY_MS : 900);
    }

    private boolean write(byte[] data) {
        try {
            if (send == null) return false;
            send.send(data, 0, data.length);
            return true;
        } catch (IOException e) { fail("MIDI disconnected: " + e.getMessage()); return false; }
    }

    void key(int id, boolean down) {
        if (id < 0 || id > 40) throw new IllegalArgumentException("key");
        worker.post(() -> { if (ready) enqueue(Protocol.frame(2, token, id, down ? 1 : 0)); });
    }

    void encoder(int id, int delta) {
        if (id < 0 || id > 6 || delta < -63 || delta > 63) throw new IllegalArgumentException("encoder");
        worker.post(() -> { if (ready) enqueue(Protocol.frame(3, token, id, delta + 64)); });
    }

    void master(int value) {
        if (value < 0 || value > 1023) throw new IllegalArgumentException("master");
        worker.post(() -> {
            if (ready) {
                queue.removeIf(frame -> frame[4] == 9);
                enqueue(Protocol.frame(9, token, 1, value & 127, value >> 7));
            }
        });
    }

    void note(int note, boolean down) {
        if (note < 0 || note > 127) throw new IllegalArgumentException("note");
        worker.post(() -> {
            if (!ready || notes[note] == down) return;
            notes[note] = down;
            write(new byte[]{(byte) (down ? 0x90 : 0x80), (byte) note, (byte) (down ? 96 : 0)});
        });
    }

    void disconnect() { worker.post(() -> close("Disconnected · no notes sent")); }
    void shutdown() { worker.post(() -> { close("Disconnected"); thread.quitSafely(); }); }
    private void fail(String reason) { close(reason); }

    private void close(String reason) {
        boolean canRelease = ready && pending == null;
        generation++;
        ready = false;
        helloRetry.clear();
        worker.removeCallbacks(heartbeat);
        worker.removeCallbacks(timeout);
        queue.clear();
        pending = null;
        // Best effort on an already-broken link; the firmware lease clears panel holds.
        if (send != null) {
            try {
                for (int i = 0; i < notes.length; i++)
                    if (notes[i]) send.send(new byte[]{(byte) 0x80, (byte) i, 0}, 0, 3);
                send.send(new byte[]{(byte) 0xb0, 64, 0, (byte) 0xb0, 123, 0}, 0, 6);
                byte[] release = Protocol.frame(6, token);
                // Never pipeline RELEASE behind an outstanding request. In that
                // case the lease clears holds within two seconds after link loss.
                if (canRelease) send.send(release, 0, release.length);
            } catch (IOException ignored) {}
        }
        Arrays.fill(notes, false);
        try { if (receive != null) receive.close(); } catch (IOException ignored) {}
        try { if (send != null) send.close(); } catch (IOException ignored) {}
        try { if (device != null) device.close(); } catch (IOException ignored) {}
        receive = null;
        send = null;
        device = null;
        token = 0;
        status.accept(reason);
    }
}
