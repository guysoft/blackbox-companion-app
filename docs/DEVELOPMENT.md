# Development history and evidence

This document retains the original in-tree development history; paths beginning
with `android/` describe that earlier layout. Current root build/install commands
are in [README.md](../README.md). Firmware and protocol documentation:
https://github.com/guysoft/blackbox-fm1/tree/blackbox-fm1/docs

Original GPL-3.0-only remote controller, not a synth or emulator. No Harbor code,
artwork, firmware packages or native emulator are included. Harbor's full-panel
approach inspired the feature set; its inspected revision has no identified reuse
licence.

## Use

- Install `app/build/outputs/apk/debug/app-debug.apk`. App: **Blackbox Panel**,
  package `org.guysoft.blackbox`.
- **MIDI** lists explicit bidirectional Android MIDI port pairs. Choose the pedal.
  Direct USB requires the phone to host the pedal through OTG, not both devices
  plugged into a PC. USB/ADB alone is not a MIDI route.
- **BLE** requests Nearby Devices permission and scans the standard BLE-MIDI
  service for ten seconds. Choose the intended device; no automatic connection.
  Android handles BLE-MIDI framing through `MidiManager.openBluetoothDevice`.
- After HELLO validates protocol v1, panel dimensions/counts, `BlackBox_950` and
  a source fingerprint, notes and controls become active. The app never sends
  stock/OTA/editor commands.
- Drag encoders vertically, or tap their ± buttons. Knob markers show local
  gestures, **not** parameter feedback from the pedal.
- Buttons support holds and simultaneous touches. OCT+/OCT− are the real logical
  panel controls (including Confirm/Cancel); no HOME modifier is needed on the
  phone. They do not transpose the phone's fixed-pitch MIDI keyboard.
- Keyboard: F3–G5, channel 1, velocity 96, chords and sliding.
  **Panel keys off:** ordinary MIDI notes, except while holding FX, SCL, EDIT,
  GLO, SEQ or REC: these temporarily use matrix keys for layer shortcuts.
  **Panel keys on:** matrix IDs 14–40 throughout, for step editing/layers.
  Routing changes release existing touches using their old mode; those fingers
  must lift before acting again, so an already sounding note cannot turn into
  a clear command just because REC was pressed.
- To clear the selected track's sequence: **hold REC first, tap the lowest F
  (F3), then release REC**. This is not CLEAR SONG ORDER. SAVE held is undo.
- MASTER sets a ceiling below the physical pot; it cannot raise physical volume.
  The initial slider position does not claim ownership or send a volume change.
- Disconnect and leaving the app close the session. Reconnect manually.
  Rotation keeps the MIDI connection and rebuilds the layout, releasing held
  touches and preserving keyboard mode and the MASTER slider position.
  Cleanup sends held note-offs, sustain-off and all-notes-off. RELEASE is sent only
  if no panel reply is outstanding; otherwise the device's two-second lease
  expires. Cleanup on a broken link is necessarily best effort.

No screen/LED readback is implemented in the app. Firmware USB screen capture
now works; there is no simulated display pretending to represent the pedal.
Q mini input routing, variable velocity, pitch bend and modulation UI are not yet
implemented.

## Build and tests

JDK 17+, Gradle 8.11.1, Android SDK platform 35 and build-tools 35.0.0.
AGP is pinned to 8.6.1. No external UI/runtime dependencies.

```sh
sh android/test.sh
ANDROID_HOME="$HOME/Android/Sdk" gradle -p android :app:assembleDebug :app:lintDebug
adb -s YOUR_DEVICE install -r android/app/build/outputs/apk/debug/app-debug.apk
```

This workstation used its cached Gradle distribution. Its offline build used:

```sh
ANDROID_HOME="$HOME/Android/Sdk" \
  "$HOME/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle" \
  -p android --offline --no-daemon \
  -Pandroid.aapt2FromMavenOverride="$HOME/Android/Sdk/build-tools/35.0.0/aapt2" \
  :app:assembleDebug :app:lintDebug
```

Host tests cover wire bytes, identity validation, fragmented/coalesced SysEx,
real-time interleaving, parser recovery/bounds, and overlapping finger ownership.
They do not exercise an Android MIDI transport or prove real-time performance.

## Evidence and limits

2026-11-18: built and installed on Galaxy S10e, Android 15. Mobile MCP verified
portrait/landscape UI, MIDI no-device dialog, Nearby Devices permission, BLE scan
start/stop, keyboard-mode toggle and disconnected key touch. No BLE-MIDI devices
were listed. No app crash exit was recorded (only APK replacement).

**Not tested:** actual USB/BLE note or panel delivery, round-trip lease behavior,
physical multitouch chords, audio or latency from this Android app. The firmware's
BLE receiver is now tested with Linux, separately from the Android transport.
That receiver must release incoming MIDI voices on link loss as well as panel
holds—panel lease expiry alone does not clear ordinary MIDI notes.

2026-11-19 update: community self-calibration and a boot-guard fix enable real
BLE-MIDI. Linux verifies advertising, full HELLO, panel keys, traffic and reconnect.
Use the app's BLE scan and select **Blackbox MIDI 8CE3** on this unit. Only one
central may connect; disconnect the laptop first (done after the Linux trial).
The phone was absent from ADB, so Android delivery is still untested.
See `../docs/BLACKBOX-BLE.md` for evidence and remaining limits.

Later the same day: Android scan/open/full HELLO and ongoing heartbeats passed
on the Galaxy S10e. Android opens MIDI ports before GATT is ready; the app now
retries only the initial HELLO for up to ten seconds, not control/note actions.
The app was left connected for the user's keyboard/audio test, with volume
unchanged. Actual Android notes/audio remain unmeasured.

Rotation fix: the original activity recreation closed MIDI in pause/destroy.
Orientation/size changes are now handled within the same activity; the controller
is not recreated. Four real portrait/landscape switches on the Galaxy S10e kept
the app connected and heartbeats advancing, with keyboard mode preserved and no
new pedal deadline misses or reboot. Held-key cleanup is covered by host tests;
no audible note was generated during that rotation check.

Layer-chord update: automatic held-layer routing and old-touch suppression pass
Java host tests; updated APK installed and BLE reconnected. REC + F3 was
deliberately not invoked on the user's music. One reconnect returned a session
rejection before a subsequent explicit reconnect worked. A host-only probe
reproduces duplicate BLE HELLO scheduling its own session cleanup in the pedal;
that separate firmware issue is not fixed by this app-only update.
