#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

VERSION="1.4.0"
AETHER_BASE_VERSION="1.8.0"
TERMUX_TAG="termux-v1.4.0"
REPO="velnox4827/saman-aether"
BASE="https://raw.githubusercontent.com/$REPO/main"
RELEASE_BASE="https://github.com/$REPO/releases/download/$TERMUX_TAG"

CORE_BIN="$PREFIX/bin/saman-aether-core"
VERSION_FILE="$PREFIX/etc/saman-aether-termux.version"

ACTION="${1:-install}"

echo "================================"
echo "       SAMAN AETHER"
echo "     Termux v$VERSION"
echo "   Aether Core v$AETHER_BASE_VERSION"
echo "================================"
echo

detect_arch() {
    case "$(uname -m)" in
        aarch64|arm64)
            echo "arm64"
            ;;
        armv7l|armv8l|arm)
            echo "armv7"
            ;;
        x86_64|amd64)
            echo "x86_64"
            ;;
        *)
            echo "unsupported"
            ;;
    esac
}

stop_core() {
    local pid=""

    if [ -f "$HOME/.saman-aether/aether.pid" ]; then
        pid="$(cat "$HOME/.saman-aether/aether.pid" 2>/dev/null || true)"
    fi

    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        kill -TERM "$pid" 2>/dev/null || true

        for _ in $(seq 1 20); do
            kill -0 "$pid" 2>/dev/null || break
            sleep 0.1
        done

        kill -0 "$pid" 2>/dev/null &&
            kill -KILL "$pid" 2>/dev/null || true
    fi

    rm -f "$HOME/.saman-aether/aether.pid"
}

if [ "$ACTION" = "uninstall" ]; then
    echo "Removing Saman Aether Termux files..."

    stop_core

    rm -f "$CORE_BIN"
    rm -f "$VERSION_FILE"
    rm -f "$PREFIX/bin/saman-aether-diagnostics"
    rm -f "$HOME/.aether-shortcut-runner"

    rm -f \
        "$HOME/.shortcuts/0-STOP-Aether" \
        "$HOME/.shortcuts/1-Aether-MASQUE" \
        "$HOME/.shortcuts/2-Aether-WG" \
        "$HOME/.shortcuts/3-Aether-GOOL" \
        "$HOME/.shortcuts/4-Aether-MASQUE-H2" \
        "$HOME/.shortcuts/5-Aether-SAFE-LOG"

    echo
    echo "Saman Aether Termux removed."
    echo "Any separate upstream 'aether' installation was left untouched."
    exit 0
fi

echo "[1/5] Installing requirements..."

pkg install -y \
    bash \
    curl \
    coreutils \
    procps \
    grep \
    sed \
    tar \
    jq \
    iproute2 >/dev/null

ARCH="$(detect_arch)"

if [ "$ARCH" = "unsupported" ]; then
    echo "ERROR: unsupported architecture: $(uname -m)"
    exit 1
fi

ARCHIVE="saman-aether-termux-${ARCH}.tar.gz"

echo "[2/5] Downloading patched Saman Aether Core..."
echo "Architecture: $ARCH"

URL="$RELEASE_BASE/$ARCHIVE"
SUM_URL="$RELEASE_BASE/$ARCHIVE.sha256"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

curl -fL --retry 3 --retry-delay 2 --progress-bar "$URL" -o "$TMP/$ARCHIVE"

if [ -n "$SUM_URL" ] && [ "$SUM_URL" != "null" ]; then
    EXPECTED="$(
        curl -fL --retry 3 --retry-delay 2 --silent --show-error "$SUM_URL" |
            awk '{print $1}' |
            head -n1
    )"

    ACTUAL="$(
        sha256sum "$TMP/$ARCHIVE" |
            awk '{print $1}'
    )"

    if [ -z "$EXPECTED" ] || [ "$EXPECTED" != "$ACTUAL" ]; then
        echo "ERROR: SHA256 verification failed."
        echo "Expected: $EXPECTED"
        echo "Actual:   $ACTUAL"
        exit 1
    fi

    echo "SHA256: verified"
else
    echo "WARNING: checksum asset not found."
fi

tar -xzf "$TMP/$ARCHIVE" -C "$TMP"

test -f "$TMP/saman-aether-core" || {
    echo "ERROR: saman-aether-core missing from archive."
    exit 1
}

chmod +x "$TMP/saman-aether-core"

echo "[3/5] Installing core + runner..."

stop_core

mkdir -p \
    "$PREFIX/bin" \
    "$PREFIX/etc" \
    "$HOME/.shortcuts" \
    "$HOME/.saman-aether"

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="$HOME/.saman-aether-backups/$STAMP"
mkdir -p "$BACKUP_DIR"

backup_file() {
    local f="$1"

    if [ -e "$f" ]; then
        cp -a "$f" "$BACKUP_DIR/$(basename "$f")"
    fi

    return 0
}

for f in \
    "$CORE_BIN" \
    "$PREFIX/bin/saman-aether-diagnostics" \
    "$HOME/.aether-shortcut-runner" \
    "$HOME/.shortcuts/0-STOP-Aether" \
    "$HOME/.shortcuts/1-Aether-MASQUE" \
    "$HOME/.shortcuts/2-Aether-WG" \
    "$HOME/.shortcuts/3-Aether-GOOL" \
    "$HOME/.shortcuts/4-Aether-MASQUE-H2" \
    "$HOME/.shortcuts/5-Aether-SAFE-LOG"
do
    backup_file "$f"
done

cp -f "$TMP/saman-aether-core" "$CORE_BIN"
chmod +x "$CORE_BIN"
printf '%s\n' "$VERSION" > "$VERSION_FILE"

curl -fsSL --retry 3 --retry-delay 2 "$BASE/aether-shortcut-runner" \
    -o "$HOME/.aether-shortcut-runner"

curl -fsSL --retry 3 --retry-delay 2 "$BASE/saman-aether-diagnostics" \
    -o "$PREFIX/bin/saman-aether-diagnostics"

echo "[4/5] Installing Termux:Widget shortcuts..."

for NAME in \
    0-STOP-Aether \
    1-Aether-MASQUE \
    2-Aether-WG \
    3-Aether-GOOL \
    4-Aether-MASQUE-H2 \
    5-Aether-SAFE-LOG
do
    curl -fsSL --retry 3 --retry-delay 2 "$BASE/shortcuts/$NAME" \
        -o "$HOME/.shortcuts/$NAME"
done

chmod +x \
    "$HOME/.aether-shortcut-runner" \
    "$PREFIX/bin/saman-aether-diagnostics" \
    "$HOME/.shortcuts/"*

echo "[5/5] Verifying installation..."

test -x "$CORE_BIN"
"$CORE_BIN" --version 2>/dev/null || true

echo
echo "================================"
echo "       INSTALL COMPLETE"
echo "================================"
echo
echo "Saman Aether Termux: v$VERSION"
echo "Core release: $TERMUX_TAG"
echo
echo "Shortcuts:"
echo "  0-STOP-Aether"
echo "  1-Aether-MASQUE      (H3)"
echo "  2-Aether-WG"
echo "  3-Aether-GOOL"
echo "  4-Aether-MASQUE-H2"
echo "  5-Aether-SAFE-LOG"
echo
echo "Local proxies:"
echo "  SOCKS5       127.0.0.1:1819"
echo "  HTTP CONNECT 127.0.0.1:1820"
echo
echo "Smart Reconnect:"
echo "  WG cache RTT       650ms"
echo "  MASQUE H3 verify   1800ms"
echo "  MASQUE H2 verify   2500ms"
echo "  short-lived path   20s"
echo "  scan remains       balanced"
echo
echo "Diagnostics:"
echo "  saman-aether-diagnostics safe"
echo "  saman-aether-diagnostics full"
echo
echo "The upstream 'aether' binary, if installed separately,"
echo "was not overwritten."
echo
echo "Refresh Termux:Widget after updating."
