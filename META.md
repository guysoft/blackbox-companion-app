# Workflow

This app was split from guysoft/blackbox-fm1 into its own public repository by
user request. Public screenshot is a real capture of this app, not generated art.
Package ID stays org.guysoft.blackbox; versionCode 2, versionName 0.2.0-alpha.1.
The existing development key is kept private in the GitHub Actions secret
ANDROID_DEBUG_KEYSTORE_B64. Do not publish a tag/release without user approval.
CI explicitly selects it through BLACKBOX_DEBUG_KEYSTORE and verifies the APK
certificate SHA-256 is 79bace43a9749111312d73f34147102c2fc97df6bfcdddaf0a96ccc4ceffcabb
(matches the installed phone APK). Restoring the key alone proved insufficient:
the runner selected another debug key in run 44556317698. That run's APK is not
update-compatible and must not be distributed as a maintained-key build.

Hardware evidence and original app history are in docs/DEVELOPMENT.md; firmware
health/calibration/update evidence stays in blackbox-fm1 PROGRESS.md. Tests do not
establish analog audio correctness or safe execution of destructive shortcuts.
