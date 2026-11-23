from __future__ import annotations

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = Path(__file__).resolve().parents[1] / "app/src/main/AndroidManifest.xml"


class ManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.root = ET.parse(MANIFEST).getroot()

    def test_risky_permissions_are_declared(self) -> None:
        permissions = {
            node.attrib.get(f"{ANDROID}name")
            for node in self.root.findall("uses-permission")
        }
        for permission in (
            "android.permission.INTERNET",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECEIVE_BOOT_COMPLETED",
        ):
            self.assertIn(permission, permissions)

    def test_backup_is_disabled(self) -> None:
        app = self.root.find("application")
        assert app is not None
        self.assertEqual("false", app.attrib.get(f"{ANDROID}allowBackup"))

    def test_only_expected_components_are_exported(self) -> None:
        app = self.root.find("application")
        assert app is not None
        exported = {
            node.attrib.get(f"{ANDROID}name"): node.attrib.get(f"{ANDROID}exported")
            for node in app
            if node.tag in ("activity", "receiver", "service", "provider")
        }
        self.assertEqual(
            {
                ".ui.MainActivity": "true",
                ".receiver.BootReceiver": "true",
                ".receiver.RefreshReceiver": "false",
            },
            exported,
        )


if __name__ == "__main__":
    unittest.main()
