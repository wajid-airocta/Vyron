#!/bin/zsh
set -euo pipefail
cd "${0:A:h}/.."
export PYINSTALLER_CONFIG_DIR="$PWD/.pyinstaller-cache"
.venv/bin/python -m PyInstaller --clean --noconfirm Vyron.spec
mkdir -p installers
hdiutil create -volname VYRON -srcfolder dist/Vyron.app -ov -format UDZO installers/Vyron-macOS-arm64.dmg
