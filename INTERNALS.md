# Architecture

MainActivity owns one MidiController and rebuilds views on handled configuration
changes. The worker thread serializes panel requests, HELLO retries and heartbeat.
Protocol parses framed SysEx; KeyHolds tracks touch ownership and suppresses old
fingers across routing changes. KeyboardRouting selects MIDI notes versus panel
keys while quick-layer buttons are held. The phone never synthesizes audio.
