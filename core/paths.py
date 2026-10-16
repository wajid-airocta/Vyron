"""Locations for bundled resources and per-user writable data."""

from __future__ import annotations

import os
import sys
from pathlib import Path


def resource_root() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys._MEIPASS)
    return Path(__file__).resolve().parent.parent


def data_root() -> Path:
    if not getattr(sys, "frozen", False):
        return resource_root()
    if sys.platform == "darwin":
        base = Path.home() / "Library" / "Application Support"
    elif sys.platform == "win32":
        base = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
    else:
        base = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))
    return base / "Vyron"


def user_plugins_dir() -> Path:
    path = data_root() / "plugins"
    path.mkdir(parents=True, exist_ok=True)
    return path
