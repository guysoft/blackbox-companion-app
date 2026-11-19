# Blackbox companion app

Read README.md and docs/DEVELOPMENT.md. This is an original GPL-3.0-only Android
controller for guysoft/blackbox-fm1, not a synthesizer or firmware installer.
Do not copy Harbor code/artwork without a licence. Do not clear Android app data.

Run `sh test.sh`, APK build and lint before committing. Never replay controls or
notes on timeout: only the initial same-token HELLO can be retried. Rotation
keeps the controller, releases touches and preserves UI routing/volume state.
Keep MIDI notes distinct from panel matrix keys. Never exercise destructive
shortcuts on the user's music for a test.

Development APKs intentionally keep org.guysoft.blackbox and the existing private
debug signing key. Never commit keystores/passwords. CI uses a private secret;
missing release signing material must fail closed, not create a new release key.
All v* tag releases remain experimental pre-releases. No tagging/publishing or
dependency upgrades without user authorization.
