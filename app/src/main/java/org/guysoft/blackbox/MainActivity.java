// SPDX-License-Identifier: GPL-3.0-only
package org.guysoft.blackbox;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.midi.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private MidiManager manager;
    private MidiController midi;
    private TextView status;
    private KeyboardView keyboard;
    private final KeyboardRouting keyboardRouting = new KeyboardRouting();
    private int masterCeiling = 1023;
    private String currentStatus = "Disconnected · no notes sent";
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private AlertDialog scanDialog;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<HoldButton> panelButtons = new ArrayList<>();
    private final Runnable stopScan = this::stopScan;
    private static final ParcelUuid MIDI_SERVICE =
            ParcelUuid.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700");

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        manager = getSystemService(MidiManager.class);
        midi = new MidiController(manager, message -> runOnUiThread(() -> {
            currentStatus = message;
            if (status != null) status.setText(message);
        }));
        buildPanel();
    }

    private void buildPanel() {
        panelButtons.clear();
        LinearLayout root = column();
        root.setPadding(12, 0, 12, 0);
        root.setBackgroundColor(Color.rgb(20, 28, 37));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(12 + insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    12 + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        status = text(currentStatus, 13);
        LinearLayout header = row();
        header.addView(text("BLACKBOX / PANEL", 19), new LinearLayout.LayoutParams(0, -1, 1));
        header.addView(button("MIDI", this::chooseMidi));
        header.addView(button("BLE", this::scanBle));
        header.addView(button("Disconnect", this::disconnect));
        root.addView(header);
        root.addView(status);
        root.addView(text("Audio stays on the pedal · standard BLE-MIDI · screen unavailable", 11));

        LinearLayout controls = column();
        String[] encoderNames = {"SELECT", "ALGORITHM", "KNOB 1", "KNOB 2", "KNOB 3", "KNOB 4", "PRESET"};
        LinearLayout encoders = row();
        for (int i = 0; i < encoderNames.length; i++) {
            final int id = i;
            LinearLayout encoder = column();
            TextView name = text(encoderNames[i], 11);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setTextSize(9);
            encoder.addView(name);
            encoder.addView(new EncoderView(this, encoderNames[i], delta -> midi.encoder(id, delta)),
                    new LinearLayout.LayoutParams(-1, (int) (56 * getResources().getDisplayMetrics().density)));
            LinearLayout steps = row();
            steps.addView(button("−", () -> midi.encoder(id, -1)),
                    new LinearLayout.LayoutParams(0, -2, 1));
            steps.addView(button("+", () -> midi.encoder(id, 1)),
                    new LinearLayout.LayoutParams(0, -2, 1));
            steps.getChildAt(0).setContentDescription(encoderNames[i] + " decrease");
            steps.getChildAt(1).setContentDescription(encoderNames[i] + " increase");
            encoder.addView(steps);
            encoders.addView(encoder, new LinearLayout.LayoutParams(0, -2, 1));
        }
        controls.addView(encoders);
        String[] labels = {"OCT−", "OCT+", "FX", "SCL", "ENV", "LFO", "EDIT",
                "GLO", "HOME", "SAVE", "ARP", "SEQ", "PLAY", "REC"};
        for (int r = 0; r < 2; r++) {
            LinearLayout buttons = row();
            for (int c = 0; c < 7; c++) {
                int id = r * 7 + c;
                HoldButton b = new HoldButton(this, labels[id], down -> {
                    keyboardRouting.layer(id, down, keyboard::release);
                    midi.key(id, down);
                });
                panelButtons.add(b);
                buttons.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
            }
            controls.addView(buttons);
        }
        LinearLayout options = row();
        Switch mode = new Switch(this);
        mode.setText("Panel keys");
        mode.setTextColor(Color.WHITE);
        mode.setContentDescription("Panel keys mode; off sends MIDI notes except while a layer button is held");
        mode.setChecked(keyboardRouting.manual());
        mode.setOnCheckedChangeListener((view, checked) -> {
            keyboardRouting.setManual(checked, keyboard::release);
        });
        options.addView(mode);
        options.addView(text("  MASTER ceiling", 12));
        SeekBar master = new SeekBar(this);
        master.setMax(1023);
        master.setProgress(masterCeiling);
        master.setContentDescription("MASTER ceiling; cannot exceed physical volume");
        master.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) {
                    masterCeiling = value;
                    midi.master(value);
                }
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        options.addView(master, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(options);
        controls.addView(text("Hold a layer button for panel-key shortcuts. REC + lowest F (F3): clear selected sequence. OCT+ / OCT−: Confirm / Cancel.", 11));
        keyboard = new KeyboardView(this, (key, down) -> {
            if (keyboardRouting.panelKeys()) midi.key(key + 14, down);
            else midi.note(key + 53, down);
        });
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LinearLayout body = row();
            ScrollView scroll = new ScrollView(this);
            scroll.addView(controls);
            body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
            body.addView(keyboard, new LinearLayout.LayoutParams(0, -1, 1));
            root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            root.addView(controls);
            root.addView(keyboard, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setMotionEventSplittingEnabled(true);
        return view;
    }
    private LinearLayout row() {
        LinearLayout view = column();
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.rgb(226, 234, 237));
        view.setTextSize(size);
        return view;
    }
    private Button button(String value, Runnable action) {
        Button view = new Button(this);
        view.setText(value);
        view.setTextSize(12);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setPadding(2, 0, 2, 0);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void chooseMidi() {
        List<String> names = new ArrayList<>();
        List<Runnable> choices = new ArrayList<>();
        for (MidiDeviceInfo info : manager.getDevices()) {
            // Explicit port pairs: never select an arbitrary keyboard or other MIDI gear.
            for (MidiDeviceInfo.PortInfo input : info.getPorts()) {
                if (input.getType() != MidiDeviceInfo.PortInfo.TYPE_INPUT) continue;
                for (MidiDeviceInfo.PortInfo output : info.getPorts()) {
                    if (output.getType() != MidiDeviceInfo.PortInfo.TYPE_OUTPUT) continue;
                    names.add(info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME, "MIDI")
                            + " · to " + input.getPortNumber() + " / from " + output.getPortNumber());
                    choices.add(() -> { keyboard.release(); midi.connect(info, input.getPortNumber(), output.getPortNumber()); });
                }
            }
        }
        if (choices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("No bidirectional MIDI devices")
                    .setMessage("Use USB OTG to connect the pedal, or BLE after its receiver firmware is ready. USB/ADB to the PC is not a MIDI route.")
                    .setPositiveButton("OK", null).show();
        } else new AlertDialog.Builder(this).setTitle("Select Blackbox MIDI ports")
                .setItems(names.toArray(new String[0]), (dialog, index) -> choices.get(index).run()).show();
    }

    private void scanBle() {
        String[] permissions = Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, 1);
                return;
            }
        }
        BluetoothManager bluetooth = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bluetooth == null ? null : bluetooth.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            status.setText("Enable Bluetooth, then tap BLE");
            return;
        }
        stopScan();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { status.setText("BLE scanner unavailable"); return; }
        List<BluetoothDevice> devices = new ArrayList<>();
        ArrayAdapter<String> rows = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        scanDialog = new AlertDialog.Builder(this).setTitle("BLE-MIDI devices · scanning 10 seconds")
                .setAdapter(rows, (dialog, index) -> {
                    keyboard.release();
                    midi.connectBluetooth(devices.get(index));
                }).setNegativeButton("Cancel", null).create();
        scanDialog.setOnDismissListener(dialog -> stopScan());
        scanCallback = new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (devices.contains(device)) return;
                devices.add(device);
                try {
                    String name = device.getName();
                    rows.add((name == null ? "BLE-MIDI" : name) + " · " + device.getAddress());
                } catch (SecurityException e) {
                    devices.remove(device);
                    status.setText("Bluetooth permission required");
                    stopScan();
                }
            }
            @Override public void onScanFailed(int code) {
                status.setText("BLE scan failed: " + code);
                stopScan();
            }
        };
        scanDialog.show();
        try {
            scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setServiceUuid(MIDI_SERVICE).build()),
                    new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            main.postDelayed(stopScan, 10000);
        } catch (SecurityException e) { status.setText("Bluetooth permission required"); stopScan(); }
    }

    private void stopScan() {
        main.removeCallbacks(stopScan);
        if (scanner != null && scanCallback != null) {
            try { scanner.stopScan(scanCallback); } catch (SecurityException ignored) {}
        }
        scanner = null;
        scanCallback = null;
        if (scanDialog != null && scanDialog.isShowing())
            scanDialog.setTitle("BLE-MIDI · scan stopped (no device means no receiver found)");
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 1) return;
        for (int result : results) if (result != PackageManager.PERMISSION_GRANTED) {
            status.setText("Bluetooth permission denied");
            return;
        }
        scanBle();
    }

    private void releaseTouches() {
        keyboard.release();
        for (HoldButton button : panelButtons) button.release();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        releaseTouches();
        stopScan();
        if (scanDialog != null) scanDialog.dismiss();
        buildPanel();
    }

    private void disconnect() {
        releaseTouches();
        midi.disconnect();
    }
    @Override protected void onPause() {
        stopScan();
        disconnect();
        super.onPause();
    }
    @Override protected void onDestroy() { midi.shutdown(); super.onDestroy(); }
}
