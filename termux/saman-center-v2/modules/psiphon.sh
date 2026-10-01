#!/data/data/com.termux/files/usr/bin/bash
# Official Psiphon Console Client adapter. It never embeds server entries,
# credentials, or a modified Psiphon core.

SAMAN_PSIPHON_DIR="${SAMAN_PSIPHON_DIR:-$HOME/.config/saman/psiphon}"
SAMAN_PSIPHON_SETTINGS="$SAMAN_PSIPHON_DIR/settings.conf"
SAMAN_PSIPHON_STATE="$HOME/.saman-psiphon"
SAMAN_PSIPHON_PID="$SAMAN_PSIPHON_STATE/psiphon.pid"
SAMAN_PSIPHON_LOG="$SAMAN_PSIPHON_STATE/psiphon.log"
SAMAN_PSIPHON_CHAIN_CONFIG="$SAMAN_PSIPHON_DIR/aether-chain.json"
SAMAN_PSIPHON_SOURCE="${SAMAN_PSIPHON_SOURCE:-$HOME/psiphon-tunnel-core}"

saman_psiphon_init() {
    umask 077
    mkdir -p "$SAMAN_PSIPHON_DIR" "$SAMAN_PSIPHON_STATE" || return 1
    chmod 0700 "$SAMAN_PSIPHON_DIR" "$SAMAN_PSIPHON_STATE" 2>/dev/null || true
}

saman_psiphon_bin() {
    local p="${SAMAN_PSIPHON_BIN:-$(command -v psiphon-tunnel-core 2>/dev/null || true)}"
    [ -x "$p" ] || return 1
    readlink -f "$p" 2>/dev/null || printf '%s\n' "$p"
}

saman_psiphon_config() {
    local config=''
    [ -r "$SAMAN_PSIPHON_SETTINGS" ] && config="$(sed -n 's/^CONFIG=//p' "$SAMAN_PSIPHON_SETTINGS" | head -n1)"
    printf '%s\n' "$config"
}

saman_psiphon_set_config() {
    local config="${1:-}" tmp
    [ -r "$config" ] || { printf 'ERROR: readable official Psiphon JSON config required.\n' >&2; return 2; }
    jq -e '.PropagationChannelId | strings | length > 0' "$config" >/dev/null &&
        jq -e '.SponsorId | strings | length > 0' "$config" >/dev/null || {
        printf 'ERROR: config needs non-empty PropagationChannelId and SponsorId.\n' >&2; return 2; }
    saman_psiphon_init || return 1
    tmp="$(mktemp "$SAMAN_PSIPHON_SETTINGS.tmp.XXXXXX")" || return 1
    printf 'CONFIG=%s\n' "$config" > "$tmp" && chmod 0600 "$tmp" && mv -f "$tmp" "$SAMAN_PSIPHON_SETTINGS" || { rm -f "$tmp"; return 1; }
}

saman_psiphon_config_ready() {
    local config
    config="$(saman_psiphon_config)"
    [ -r "$config" ] || return 1
    jq -e '.PropagationChannelId | strings | length > 0' "$config" >/dev/null &&
        jq -e '.SponsorId | strings | length > 0' "$config" >/dev/null
}

saman_psiphon_pid() {
    local pid bin actual
    [ -s "$SAMAN_PSIPHON_PID" ] || return 1
    pid="$(<"$SAMAN_PSIPHON_PID")"
    [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null || return 1
    bin="$(saman_psiphon_bin 2>/dev/null || true)"; actual="$(readlink -f "/proc/$pid/exe" 2>/dev/null || true)"
    [ -n "$bin" ] && [ "$actual" = "$bin" ] || return 1
    printf '%s\n' "$pid"
}

saman_psiphon_status() {
    local bin config pid
    bin="$(saman_psiphon_bin 2>/dev/null || true)"
    config="$(saman_psiphon_config)"
    printf 'Psiphon source : Psiphon-Labs/psiphon-tunnel-core (separate from Aether)\n'
    printf 'Binary         : %s\n' "${bin:-not installed}"
    if [ -n "$bin" ]; then "$bin" --version 2>/dev/null | head -n1 || true; fi
    if saman_psiphon_config_ready; then printf 'Config         : configured (private)\n'; else printf 'CONFIGURATION REQUIRED: choose an official client JSON with SponsorId and PropagationChannelId.\n'; fi
    if pid="$(saman_psiphon_pid 2>/dev/null)"; then printf 'Service        : RUNNING (PID %s)\n' "$pid"; else printf 'Service        : STOPPED\n'; fi
    [ -z "$config" ] || printf 'Config path    : %s\n' "$config"
}

saman_psiphon_prepare_aether_chain() {
    local upstream="${1:-}" config tmp
    [[ "$upstream" =~ ^(socks5|socks4a|http)://127\.0\.0\.1:[0-9]+$ ]] || { printf 'ERROR: Aether chain requires a loopback SOCKS/HTTP upstream URL.\n' >&2; return 2; }
    saman_psiphon_config_ready || { printf 'ERROR: configure an official Psiphon client JSON first.\n' >&2; return 1; }
    config="$(saman_psiphon_config)"; saman_psiphon_init || return 1
    tmp="$(mktemp "$SAMAN_PSIPHON_CHAIN_CONFIG.tmp.XXXXXX")" || return 1
    jq --arg upstream "$upstream" '.UpstreamProxyURL = $upstream' "$config" > "$tmp" && chmod 0600 "$tmp" && mv -f "$tmp" "$SAMAN_PSIPHON_CHAIN_CONFIG" || { rm -f "$tmp"; return 1; }
    printf '%s\n' "$SAMAN_PSIPHON_CHAIN_CONFIG"
}

saman_psiphon_start() {
    local config="${1:-$(saman_psiphon_config)}" bin pid
    saman_psiphon_init || return 1
    pid="$(saman_psiphon_pid 2>/dev/null || true)"; [ -z "$pid" ] || { printf 'ERROR: Psiphon is already managed by Saman (PID %s).\n' "$pid" >&2; return 1; }
    bin="$(saman_psiphon_bin)" || { printf 'ERROR: official Psiphon Console Client is not installed.\n' >&2; return 127; }
    SAMAN_PSIPHON_SETTINGS="$SAMAN_PSIPHON_SETTINGS" saman_psiphon_set_config "$config" || return
    "$bin" -config "$config" >> "$SAMAN_PSIPHON_LOG" 2>&1 &
    pid=$!; printf '%s\n' "$pid" > "$SAMAN_PSIPHON_PID"; chmod 0600 "$SAMAN_PSIPHON_PID"
    sleep 1
    if ! saman_psiphon_pid >/dev/null; then rm -f "$SAMAN_PSIPHON_PID"; printf 'ERROR: Psiphon exited; inspect %s\n' "$SAMAN_PSIPHON_LOG" >&2; return 1; fi
    printf 'Psiphon started; its configured loopback proxies are now owned by the official client.\n'
}

saman_psiphon_stop() {
    local pid
    pid="$(saman_psiphon_pid 2>/dev/null || true)"; [ -n "$pid" ] || { rm -f "$SAMAN_PSIPHON_PID"; printf 'Psiphon is already stopped.\n'; return 0; }
    kill -TERM "$pid" || return 1
    for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || { rm -f "$SAMAN_PSIPHON_PID"; printf 'Psiphon stopped.\n'; return 0; }; sleep 0.1; done
    printf 'ERROR: Psiphon did not stop after TERM; no KILL was sent.\n' >&2; return 1
}

saman_psiphon_update() {
    local source="$SAMAN_PSIPHON_SOURCE" bin stage backup remote
    [ -d "$source/.git" ] || { printf 'ERROR: official Psiphon source checkout not found: %s\n' "$source" >&2; return 1; }
    remote="$(git -C "$source" remote get-url origin 2>/dev/null || true)"
    [[ "$remote" == *'Psiphon-Labs/psiphon-tunnel-core'* ]] || { printf 'ERROR: source origin is not Psiphon-Labs/psiphon-tunnel-core.\n' >&2; return 1; }
    [ -z "$(git -C "$source" status --porcelain)" ] || { printf 'ERROR: official Psiphon source is dirty; it was not changed.\n' >&2; return 1; }
    [ "${1:-}" = --check ] && { git -C "$source" log -1 --format='Psiphon source: %h %cs %s'; return 0; }
    saman_psiphon_pid >/dev/null 2>&1 && { printf 'ERROR: stop Psiphon before replacing its binary.\n' >&2; return 1; }
    command -v go >/dev/null 2>&1 || { printf 'ERROR: Go is required to build the official Console Client.\n' >&2; return 127; }
    git -C "$source" pull --ff-only origin staging-client || return 1
    stage="$(mktemp "${TMPDIR:-/tmp}/psiphon-tunnel-core.XXXXXX")" || return 1
    trap 'rm -f "$stage"' RETURN
    (cd "$source/ConsoleClient" && go build -o "$stage" .) || return 1
    [ -x "$stage" ] || return 1
    bin="${SAMAN_PSIPHON_BIN:-$PREFIX/bin/psiphon-tunnel-core}"
    mkdir -p "${bin%/*}"; backup="$SAMAN_PSIPHON_DIR/backups/psiphon-tunnel-core.$(date +%Y%m%d-%H%M%S)"
    mkdir -p "${backup%/*}"; [ ! -e "$bin" ] || cp -p "$bin" "$backup"
    install -m 0700 "$stage" "$bin"
    printf 'OK: official Psiphon Console Client built from %s. Backup: %s\n' "$(git -C "$source" rev-parse --short HEAD)" "$backup"
}

saman_psiphon_command() {
    case "${1:-status}" in
        status) saman_psiphon_status ;;
        config) [ -n "${2:-}" ] || { printf 'Usage: saman psiphon config /path/to/official-client.json\n' >&2; return 2; }; saman_psiphon_set_config "$2" ;;
        start) saman_psiphon_start "${2:-}" ;;
        stop) saman_psiphon_stop ;;
        chain) saman_psiphon_prepare_aether_chain "${2:-socks5://127.0.0.1:1819}" ;;
        update) saman_psiphon_update "${2:-}" ;;
        *) printf 'Usage: saman psiphon status|config PATH|start [PATH]|stop|chain [LOOPBACK_URL]|update [--check]\n' >&2; return 2 ;;
    esac
}
