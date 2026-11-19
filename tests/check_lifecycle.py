# SPDX-License-Identifier: GPL-3.0-only
"""Source contract for the rotation fix; actual lifecycle is tested on Android."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "app/src/main"


def test_rotation_keeps_controller_and_releases_touches():
    activity = ET.parse(ANDROID / "AndroidManifest.xml").find("application/activity")
    changes = activity.attrib["{http://schemas.android.com/apk/res/android}configChanges"].split("|")
    assert {"orientation", "screenSize", "screenLayout", "smallestScreenSize"} <= set(changes)
    source = (ANDROID / "java/org/guysoft/blackbox/MainActivity.java").read_text()
    rotation = source.split("void onConfigurationChanged(", 1)[1].split("\n    }", 1)[0]
    assert "releaseTouches();" in rotation and "buildPanel();" in rotation
    assert "disconnect(" not in rotation and "shutdown(" not in rotation
    layout = source.split("void buildPanel()", 1)[1].split("private LinearLayout column()", 1)[0]
    assert "new MidiController" not in layout
    assert "mode.setChecked(keyboardRouting.manual())" in layout
    assert "master.setProgress(masterCeiling)" in layout
    pause = source.split("void onPause()", 1)[1].split("\n    }", 1)[0]
    assert "disconnect();" in pause  # Backgrounding is not a rotation exception.


if __name__ == "__main__":
    test_rotation_keeps_controller_and_releases_touches()
    print("Rotation/controller lifecycle contract passed")
