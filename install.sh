#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="velnox4827/saman-aether"
BASE="https://raw.githubusercontent.com/velnox4827/saman-aether/main"

ACTION="${1:-install}"

echo "================================"
echo "       SAMAN AETHER"
echo "================================"
echo

if [ "$ACTION" = "uninstall" ]; then

    echo "Removing Saman Aether shortcuts..."

    rm -f "$HOME/.aether-shortcut-runner"

    rm -f         "$HOME/.shortcuts/0-STOP-Aether"         "$HOME/.shortcuts/1-Aether-MASQUE"         "$HOME/.shortcuts/2-Aether-WG"         "$HOME/.shortcuts/3-Aether-GOOL"

    echo
    echo "Saman Aether shortcuts removed."
    echo "Official Aether binary was not removed."
    exit 0
fi


echo "[1/3] Installing requirements..."

pkg install -y     bash     curl     coreutils     procps >/dev/null


echo
echo "[2/3] Installing/updating official Aether..."

TMP_AETHER="$(mktemp)"

cleanup() {
    rm -f "$TMP_AETHER"
}

trap cleanup EXIT

curl -fsSL     https://raw.githubusercontent.com/CluvexStudio/Aether/main/aether.sh     -o "$TMP_AETHER"

chmod +x "$TMP_AETHER"

if command -v aether >/dev/null 2>&1; then
    bash "$TMP_AETHER" update
else
    bash "$TMP_AETHER" install
fi


echo
echo "[3/3] Installing Saman shortcuts..."

mkdir -p "$HOME/.shortcuts"

STAMP="$(date +%Y%m%d-%H%M%S)"

backup_file() {

    FILE="$1"

    if [ -e "$FILE" ]; then
        cp -a "$FILE" "$FILE.backup-$STAMP"
    fi
}

backup_file "$HOME/.aether-shortcut-runner"
backup_file "$HOME/.shortcuts/0-STOP-Aether"
backup_file "$HOME/.shortcuts/1-Aether-MASQUE"
backup_file "$HOME/.shortcuts/2-Aether-WG"
backup_file "$HOME/.shortcuts/3-Aether-GOOL"


curl -fsSL     "$BASE/aether-shortcut-runner"     -o "$HOME/.aether-shortcut-runner"

curl -fsSL     "$BASE/shortcuts/0-STOP-Aether"     -o "$HOME/.shortcuts/0-STOP-Aether"

curl -fsSL     "$BASE/shortcuts/1-Aether-MASQUE"     -o "$HOME/.shortcuts/1-Aether-MASQUE"

curl -fsSL     "$BASE/shortcuts/2-Aether-WG"     -o "$HOME/.shortcuts/2-Aether-WG"

curl -fsSL     "$BASE/shortcuts/3-Aether-GOOL"     -o "$HOME/.shortcuts/3-Aether-GOOL"


chmod +x     "$HOME/.aether-shortcut-runner"     "$HOME/.shortcuts/0-STOP-Aether"     "$HOME/.shortcuts/1-Aether-MASQUE"     "$HOME/.shortcuts/2-Aether-WG"     "$HOME/.shortcuts/3-Aether-GOOL"


echo
echo "================================"
echo "       INSTALL COMPLETE"
echo "================================"
echo
echo "Shortcuts:"
echo "  0-STOP-Aether"
echo "  1-Aether-MASQUE"
echo "  2-Aether-WG"
echo "  3-Aether-GOOL"
echo
echo "SOCKS5:"
echo "  127.0.0.1:1819"
echo
echo "If using Termux:Widget, refresh the widget."
