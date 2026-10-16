"""PyInstaller build for macOS and Windows; run from the repository root."""

import sys
from pathlib import Path

from PyInstaller.utils.hooks import collect_all, collect_submodules

root = Path(SPECPATH)
playwright_data, playwright_binaries, playwright_hidden = collect_all("playwright")
datas = playwright_data + [
    (str(root / "core" / "prompt.txt"), "core"),
    (str(root / "core" / "face_model.obj"), "core"),
    (str(root / "dashboard" / "static"), "dashboard/static"),
    (str(root / "assets" / "vyron.png"), "assets"),
]
datas += [(str(path), "actions") for path in (root / "actions").glob("*.py")]
hiddenimports = playwright_hidden + collect_submodules("actions")

a = Analysis(
    [str(root / "main.py")],
    pathex=[str(root)],
    binaries=playwright_binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    runtime_hooks=[],
    excludes=["pytest", "tkinter"],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="Vyron",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,
    icon=str(root / "assets" / ("vyron.icns" if sys.platform == "darwin" else "vyron.ico")),
)
coll = COLLECT(exe, a.binaries, a.datas, name="Vyron", strip=False, upx=False)

if sys.platform == "darwin":
    app = BUNDLE(
        coll,
        name="Vyron.app",
        icon=str(root / "assets" / "vyron.icns"),
        bundle_identifier="com.airocta.vyron",
        info_plist={
            "CFBundleDisplayName": "VYRON — Your Personal Autonomous Intelligence",
            "CFBundleShortVersionString": "1.0.0",
            "CFBundleVersion": "1.0.0",
            "NSMicrophoneUsageDescription": "VYRON listens when voice interaction is enabled.",
            "NSCameraUsageDescription": "VYRON uses the camera when you request a camera action.",
            "NSScreenCaptureDescription": "VYRON reads the screen when you request screen control.",
        },
    )
