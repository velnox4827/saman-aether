#!/data/data/com.termux/files/usr/bin/bash

# shellcheck disable=SC2034 # shared constants consumed by sourced modules
SAMAN2_VERSION="2.0.0-alpha5"
SAMAN_TUNNEL_VERSION="1.5.0"
SAMAN_TERMUX_VERSION="1.5.0"
SAMAN2_ROOT="${SAMAN2_ROOT:-$HOME/.local/share/saman-center-v2}"
SAMAN2_STATE="$HOME/.local/state/saman-center-v2"
SAMAN2_CACHE="$HOME/.cache/saman-center-v2"
SAMAN2_CONFIG="$HOME/.config/saman-center-v2"
SAMAN2_LOG="$SAMAN2_STATE/logs"
SAMAN2_DOWNLOADS="${SAMAN2_DOWNLOADS:-$HOME/storage/downloads/Termux}"

mkdir -p "$SAMAN2_STATE" "$SAMAN2_CACHE" "$SAMAN2_CONFIG" "$SAMAN2_LOG" 2>/dev/null || true

s2_have() { command -v "$1" >/dev/null 2>&1; }

s2_pause() {
    echo
    read -r -p "Press Enter to continue..." _
}

s2_expand_path() {
    local p="$1"
    p="${p#\"}"; p="${p%\"}"
    p="${p#\'}"; p="${p%\'}"
    case "$p" in
        '~') p="$HOME" ;;
        \~/*) p="$HOME/${p#\~/}" ;;
    esac
    printf '%s\n' "$p"
}

s2_legacy() {
    local name="$1"
    if [ -x "$HOME/bin/$name" ]; then
        printf '%s\n' "$HOME/bin/$name"
    elif s2_have "$name"; then
        command -v "$name"
    else
        return 1
    fi
}

s2_termux_api_ok() {
    s2_have termux-battery-status || return 1
    timeout 4 termux-battery-status >/dev/null 2>&1
}

s2_notify() {
    local title="$1" content="$2"
    if s2_have termux-notification; then
        termux-notification --title "$title" --content "$content" >/dev/null 2>&1 || true
    fi
}

s2_human_bytes() {
    awk -v b="${1:-0}" 'BEGIN {
        split("B KB MB GB TB",u," "); i=1;
        while (b>=1024 && i<5) {b/=1024; i++}
        if (i==1) printf "%.0f %s",b,u[i]; else printf "%.1f %s",b,u[i]
    }'
}
