#!/usr/bin/env python3
"""Build a signed, private VYRON APK with the Mac's Gemini key included."""

import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parent
CONFIG = Path.home() / "Library/Application Support/Vyron/config/api_keys.json"
PRIVATE = ROOT / ".local-release"
SIGNING = PRIVATE / "signing.json"
KEYSTORE = PRIVATE / "vyron-release.p12"


def signing_password() -> str:
    PRIVATE.mkdir(mode=0o700, exist_ok=True)
    PRIVATE.chmod(0o700)
    if SIGNING.exists() != KEYSTORE.exists():
        raise RuntimeError("Release signing files are incomplete; do not regenerate the key without recovering them.")
    if SIGNING.exists():
        return json.loads(SIGNING.read_text())["password"]

    password = secrets.token_urlsafe(36)
    environment = os.environ.copy()
    environment["VYRON_RELEASE_STORE_PASSWORD"] = password
    environment["VYRON_RELEASE_KEY_PASSWORD"] = password
    java_home = environment.get("JAVA_HOME")
    keytool = str(Path(java_home) / "bin/keytool") if java_home else "keytool"
    command = [keytool, "-genkeypair", "-noprompt", "-storetype", "PKCS12",
               "-keystore", str(KEYSTORE), "-alias", "vyron", "-keyalg", "RSA",
               "-keysize", "3072", "-validity", "3650",
               "-dname", "CN=VYRON, OU=Airocta, O=Airocta, C=PK",
               "-storepass:env", "VYRON_RELEASE_STORE_PASSWORD",
               "-keypass:env", "VYRON_RELEASE_KEY_PASSWORD"]
    result = subprocess.run(command, env=environment, capture_output=True, text=True)
    if result.returncode:
        KEYSTORE.unlink(missing_ok=True)
        raise RuntimeError("Could not create the local release signing key.")
    descriptor = os.open(SIGNING, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as stream:
        json.dump({"password": password}, stream)
    KEYSTORE.chmod(0o600)
    return password


def main() -> int:
    if not CONFIG.is_file():
        print("VYRON Mac API configuration was not found.", file=sys.stderr)
        return 2
    key = json.loads(CONFIG.read_text()).get("gemini_api_key", "").strip()
    if not key:
        print("Add a Gemini key in VYRON on the Mac first.", file=sys.stderr)
        return 2
    try:
        password = signing_password()
    except Exception as error:
        print(str(error), file=sys.stderr)
        return 2
    environment = os.environ.copy()
    environment.update({
        "VYRON_STARTER_KEY": key,
        "VYRON_RELEASE_STORE_FILE": str(KEYSTORE),
        "VYRON_RELEASE_STORE_PASSWORD": password,
        "VYRON_RELEASE_KEY_ALIAS": "vyron",
        "VYRON_RELEASE_KEY_PASSWORD": password,
    })
    gradle = environment.get("VYRON_GRADLE", str(ROOT / "gradlew"))
    print("Building a signed private release APK with the configured Gemini key.")
    result = subprocess.run([gradle, ":app:assembleRelease", "--console=plain", "--no-daemon"],
                            cwd=ROOT, env=environment, check=False)
    if result.returncode:
        return result.returncode
    source = ROOT / "app/build/outputs/apk/release/app-release.apk"
    if not source.is_file():
        print("Signed release APK was not produced.", file=sys.stderr)
        return 3
    destination = ROOT / "dist/Vyron-Android-1.2.0.apk"
    destination.parent.mkdir(exist_ok=True)
    shutil.copy2(source, destination)
    destination.chmod(0o600)
    print("Private installable APK: " + str(destination))
    print("Keep the APK and signing files private; the bundled API key is extractable.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
