# Development rules

- Preserve same-token bounded HELLO retry; never replay non-idempotent controls.
- Release old routing before changing keyboard semantics. Never test CLEAR on
  the user's music.
- Rotation keeps MIDI; actual backgrounding disconnects. Preserve the volume
  ceiling, keyboard mode and connection status while rebuilding views.
- Keep firmware protocol, identity validation and licensing boundaries intact.
- Run `sh test.sh`, `:app:assembleDebug` and `:app:lintDebug`.
- No private keys or local SDK configuration in Git. No dependency upgrades
  without approval.
