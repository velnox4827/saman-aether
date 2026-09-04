#!/data/data/com.termux/files/usr/bin/bash

# Shared immutable metadata and low-cost helpers. Sourcing this file must not
# create files or directories; commands initialize writable state explicitly.
# shellcheck disable=SC2034 # constants are consumed by dynamically loaded modules
SAMAN2_VERSION="2.1.0"
SAMAN_TUNNEL_VERSION="1.5.0"
SAMAN_TERMUX_VERSION="1.5.0"
SAMAN2_ROOT="${SAMAN2_ROOT:-$HOME/.local/share/saman-center-v2}"
SAMAN2_STATE="${SAMAN2_STATE:-$HOME/.local/state/saman-center-v2}"
SAMAN2_CACHE="${SAMAN2_CACHE:-$HOME/.cache/saman-center-v2}"
SAMAN2_CONFIG="${SAMAN2_CONFIG:-$HOME/.config/saman-center-v2}"
SAMAN2_LOG="${SAMAN2_LOG:-$SAMAN2_STATE/logs}"
SAMAN2_DOWNLOADS="${SAMAN2_DOWNLOADS:-$HOME/storage/downloads/Termux}"

s2_have() { command -v "$1" >/dev/null 2>&1; }

s2_init_runtime() {
    umask 077
    mkdir -p "$SAMAN2_STATE" "$SAMAN2_CACHE" "$SAMAN2_CONFIG" "$SAMAN2_LOG" || {
        printf 'ERROR: cannot initialize Saman writable directories under %s\n' "$HOME" >&2
        return 1
    }
    chmod 0700 "$SAMAN2_STATE" "$SAMAN2_CACHE" "$SAMAN2_CONFIG" "$SAMAN2_LOG" 2>/dev/null || true
}

s2_atomic_write() {
    local destination="$1" tmp
    mkdir -p "${destination%/*}" || return 1
    tmp="$(mktemp "${destination}.tmp.XXXXXX")" || return 1
    if cat > "$tmp" && chmod 0600 "$tmp" && mv -f "$tmp" "$destination"; then
        return 0
    fi
    rm -f -- "$tmp"
    return 1
}

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

# Render untrusted network/log text as one terminal-safe field.
s2_terminal_field() {
    if [ "$#" -gt 0 ]; then printf '%s' "$1"; else cat; fi |
        LC_ALL=C tr '\000-\010\013\014\016-\037\177' ' ' | tr '\r\n' '  ' | \
        awk '{$1=$1; print}'
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
    timeout "${TERMUX_API_TIMEOUT:-4}" termux-battery-status >/dev/null 2>&1
}

s2_notify() {
    local title="$1" content="$2"
    if s2_have termux-notification; then
        timeout "${TERMUX_API_TIMEOUT:-4}" termux-notification --title "$title" --content "$content" >/dev/null 2>&1 || true
    fi
}

s2_log_event() {
    local level="$1" event="$2"
    s2_init_runtime >/dev/null 2>&1 || return 0
    printf '%(%Y-%m-%dT%H:%M:%S%z)T [%s] %s\n' -1 "$level" "$event" >> "$SAMAN2_LOG/saman.log" 2>/dev/null || true
}

s2_human_bytes() {
    awk -v b="${1:-0}" 'BEGIN {
        split("B KB MB GB TB",u," "); i=1;
        while (b>=1024 && i<5) {b/=1024; i++}
        if (i==1) printf "%.0f %s",b,u[i]; else printf "%.1f %s",b,u[i]
    }'
}
