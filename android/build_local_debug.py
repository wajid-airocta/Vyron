#!/usr/bin/env python3
"""Build a private debug APK with the Mac's existing Gemini key as starter."""

import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parent
CONFIG = Path.home() / "Library/Application Support/Vyron/config/api_keys.json"


def main() -> int:
    if not CONFIG.is_file():
        print("VYRON Mac API configuration was not found.", file=sys.stderr)
        return 2
    key = json.loads(CONFIG.read_text()).get("gemini_api_key", "").strip()
    if not key:
        print("Add a Gemini key in VYRON on the Mac first.", file=sys.stderr)
        return 2
    gradle = os.environ.get("VYRON_GRADLE", str(ROOT / "gradlew"))
    environment = os.environ.copy()
    environment["VYRON_STARTER_KEY"] = key
    print("Building a private Android debug APK with the configured starter key.")
    result = subprocess.run([gradle, ":app:assembleDebug", "--console=plain", "--no-daemon"],
                            cwd=ROOT, env=environment, check=False)
    if result.returncode == 0:
        print("APK: " + str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
        print("Keep this test APK private: a bundled API key can be extracted from it.")
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
