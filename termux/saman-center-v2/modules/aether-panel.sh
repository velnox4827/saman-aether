#!/data/data/com.termux/files/usr/bin/bash
# shellcheck disable=SC1090,SC2034,SC2318
# Saman-side control panel for the untouched official Aether binary.
# This file never implements or modifies Aether networking logic.

SAMAN_AETHER_CONFIG_DIR="${SAMAN_AETHER_CONFIG_DIR:-$HOME/.config/saman/aether}"
SAMAN_AETHER_SETTINGS="$SAMAN_AETHER_CONFIG_DIR/settings.conf"
SAMAN_AETHER_CAP_CACHE="$SAMAN_AETHER_CONFIG_DIR/capabilities.cache"
SAMAN_AETHER_PRESET_DIR="$SAMAN_AETHER_CONFIG_DIR/presets"
SAMAN_AETHER_BACKUP_DIR="$SAMAN_AETHER_CONFIG_DIR/backups"
SAMAN_AETHER_UPSTREAM="CluvexStudio/Aether"

saman_aether_bin() {
    local p="${SAMAN_AETHER_BIN:-$(command -v aether 2>/dev/null || true)}"
    [ -x "$p" ] || return 1
    readlink -f "$p" 2>/dev/null || printf '%s\n' "$p"
}

saman_aether_init() {
    local line key value
    umask 077
    mkdir -p "$SAMAN_AETHER_CONFIG_DIR" "$SAMAN_AETHER_PRESET_DIR" "$SAMAN_AETHER_BACKUP_DIR" || return 1
    chmod 0700 "$SAMAN_AETHER_CONFIG_DIR" "$SAMAN_AETHER_PRESET_DIR" "$SAMAN_AETHER_BACKUP_DIR" 2>/dev/null || true
    if [ ! -e "$SAMAN_AETHER_SETTINGS" ]; then
        saman_aether_defaults
        # Migrate only the old Saman-owned listener preferences; never copy
        # upstream identities, profiles, keys, or credential-bearing values.
        if [ -r "${S2_CONFIG_FILE:-}" ]; then
            while IFS= read -r line || [ -n "$line" ]; do
                key="${line%%=*}"; value="${line#*=}"
                case "$key" in
                    AETHER_SOCKS_PORT) [[ "$value" =~ ^[0-9]+$ ]] && AETHER_PANEL_BIND="127.0.0.1:$value" ;;
                    AETHER_HTTP_PORT) [[ "$value" =~ ^[0-9]+$ ]] && AETHER_PANEL_HTTP="127.0.0.1:$value" ;;
                esac
            done < "$S2_CONFIG_FILE"
        fi
        saman_aether_save
    fi
}

saman_aether_defaults() {
    AETHER_PANEL_MODE="masque"
    AETHER_PANEL_PRESET="upstream-default"
    AETHER_PANEL_SCAN=""
    AETHER_PANEL_NOIZE=""
    AETHER_PANEL_IP="4"
    AETHER_PANEL_H2=0
    AETHER_PANEL_H3=0
    AETHER_PANEL_ECH=""
    AETHER_PANEL_FRAGMENT=0
    AETHER_PANEL_FRAGMENT_SIZE=""
    AETHER_PANEL_FRAGMENT_DELAY=""
    AETHER_PANEL_BIND="127.0.0.1:1819"
    AETHER_PANEL_HTTP="127.0.0.1:1820"
    AETHER_PANEL_UPSTREAM=""
    AETHER_PANEL_DNS=""
    AETHER_PANEL_ROUTE_BLOCK=""
    AETHER_PANEL_ROUTE_DIRECT=""
    AETHER_PANEL_ROUTES=""
    AETHER_PANEL_QUICK=0
    AETHER_PANEL_RECONNECT_SECS=""
    AETHER_PANEL_STARTUP_SECS=""
    AETHER_PANEL_VALIDATE_SECS=""
    AETHER_PANEL_KEEPALIVE=""
    AETHER_PANEL_PEER=""
    AETHER_PANEL_WG_PEER=""
    AETHER_PANEL_WIW_OUTER=""
    AETHER_PANEL_WIW_INNER=""
    AETHER_PANEL_WIW_PEERS=""
    AETHER_PANEL_MIM_OUTER=""
    AETHER_PANEL_MIM_INNER=""
    AETHER_PANEL_MIM_PEERS=""
    AETHER_PANEL_PERF=""
    AETHER_PANEL_TLS_GROUPS=""
    AETHER_PANEL_LOG_LEVEL=""
    AETHER_PANEL_NO_QUIC_V2=0
    AETHER_PANEL_NO_DATA_CHECK=0
    AETHER_PANEL_NO_PROFILE_RETRY=0
    AETHER_PANEL_H2_PEER=""
    AETHER_PANEL_TOR_MODE="off"
    # Preserve the official Aether default.  Argument construction suppresses
    # HTTP CONNECT when its saved endpoint is the same listener.
    AETHER_PANEL_TOR_BIND="127.0.0.1:1820"
    AETHER_PANEL_TOR_DIR=""
    AETHER_PANEL_TOR_BRIDGES="auto"
    AETHER_PANEL_TOR_BRIDGE=""
    AETHER_PANEL_TOR_PT=""
    AETHER_PANEL_TOR_PT_DIR=""
}

saman_aether_save_defaults() {
    saman_aether_defaults
    saman_aether_save
}

saman_aether_key_var() { printf 'AETHER_PANEL_%s\n' "$1"; }

saman_aether_valid_value() {
    local k="$1" v="$2"
    case "$k" in
        MODE) [[ "$v" =~ ^(masque|wg|gool|mim)$ ]] ;;
        TOR_MODE) [[ "$v" =~ ^(off|inside|reverse|only)$ ]] ;;
        TOR_BRIDGES) [[ "$v" =~ ^(auto|force|disabled)$ ]] ;;
        PRESET) [[ "$v" =~ ^[a-z0-9-]+$ ]] ;;
        SCAN) [[ "$v" =~ ^[a-z0-9-]+$ ]] ;;
        NOIZE|IP|ECH|PERF|LOG_LEVEL) [[ "$v" =~ ^[A-Za-z0-9_.:-]*$ ]] ;;
        H2|H3|FRAGMENT|QUICK|NO_QUIC_V2|NO_DATA_CHECK|NO_PROFILE_RETRY) [[ "$v" =~ ^[01]$ ]] ;;
        *_SECS|KEEPALIVE) [[ "$v" =~ ^[0-9]{1,5}$ ]] ;;
        *) [ "${#v}" -le 2048 ] && [[ "$v" != *$'\n'* ]] ;;
    esac
}

saman_aether_assign() {
    local k="$1" v="$2" var
    saman_aether_valid_value "$k" "$v" || return 1
    var="$(saman_aether_key_var "$k")"
    printf -v "$var" '%s' "$v"
}

saman_aether_load() {
    local line k v
    saman_aether_defaults
    [ -r "$SAMAN_AETHER_SETTINGS" ] || return 0
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in ''|'#'*) continue;; esac
        [[ "$line" == *=* ]] || continue
        k="${line%%=*}"; v="${line#*=}"
        case "$k" in
            MODE|PRESET|SCAN|NOIZE|IP|H2|H3|ECH|FRAGMENT|FRAGMENT_SIZE|FRAGMENT_DELAY|BIND|HTTP|UPSTREAM|DNS|ROUTE_BLOCK|ROUTE_DIRECT|ROUTES|QUICK|RECONNECT_SECS|STARTUP_SECS|VALIDATE_SECS|KEEPALIVE|PEER|WG_PEER|WIW_OUTER|WIW_INNER|WIW_PEERS|MIM_OUTER|MIM_INNER|MIM_PEERS|PERF|TLS_GROUPS|LOG_LEVEL|NO_QUIC_V2|NO_DATA_CHECK|NO_PROFILE_RETRY|H2_PEER|TOR_MODE|TOR_BIND|TOR_DIR|TOR_BRIDGES|TOR_BRIDGE|TOR_PT|TOR_PT_DIR)
                saman_aether_assign "$k" "$v" || true ;;
        esac
    done < "$SAMAN_AETHER_SETTINGS"
}

saman_aether_save() {
    local tmp k var
    tmp="$(mktemp "$SAMAN_AETHER_SETTINGS.tmp.XXXXXX")" || return 1
    {
        printf '# Saman Aether preferences; official Aether owns identity/config files.\n'
        for k in MODE PRESET SCAN NOIZE IP H2 H3 ECH FRAGMENT FRAGMENT_SIZE FRAGMENT_DELAY BIND HTTP UPSTREAM DNS ROUTE_BLOCK ROUTE_DIRECT ROUTES QUICK RECONNECT_SECS STARTUP_SECS VALIDATE_SECS KEEPALIVE PEER WG_PEER WIW_OUTER WIW_INNER WIW_PEERS MIM_OUTER MIM_INNER MIM_PEERS PERF TLS_GROUPS LOG_LEVEL NO_QUIC_V2 NO_DATA_CHECK NO_PROFILE_RETRY H2_PEER TOR_MODE TOR_BIND TOR_DIR TOR_BRIDGES TOR_BRIDGE TOR_PT TOR_PT_DIR; do
            var="$(saman_aether_key_var "$k")"; printf '%s=%s\n' "$k" "${!var-}"
        done
    } > "$tmp" && chmod 0600 "$tmp" && mv -f "$tmp" "$SAMAN_AETHER_SETTINGS" || { rm -f "$tmp"; return 1; }
}

saman_aether_setting_label() {
    case "$1" in
        MODE) printf 'Connection Mode';; PRESET) printf 'Preset';; SCAN) printf 'Scan Mode';; NOIZE) printf 'Obfuscation';; IP) printf 'IP Mode';; H2) printf 'MASQUE Carrier';; TOR_MODE) printf 'Tor Routing';; TOR_BRIDGES) printf 'Tor Bridges';; *) printf '%s' "$1";;
    esac
}

saman_aether_set() {
    local k="$1" v="$2" var old label
    saman_aether_load
    var="$(saman_aether_key_var "$k")"; old="${!var-}"
    label="$(saman_aether_setting_label "$k")"
    if [ "$old" = "$v" ]; then
        printf 'Already selected: %s = %s\n' "$label" "$v"
        return 0
    fi
    saman_aether_assign "$k" "$v" || { printf 'ERROR: invalid Aether setting %s=%s\n' "$k" "$v" >&2; return 2; }
    [ "$k" = PRESET ] || saman_aether_mark_custom "$k"
    saman_aether_save || return 1
    saman_aether_load
    var="$(saman_aether_key_var "$k")"
    [ "${!var-}" = "$v" ] || { printf 'ERROR: saved Aether setting verification failed: %s\n' "$k" >&2; return 1; }
    printf 'Saved: %s = %s\n' "$label" "$v"
}

saman_aether_mark_custom() {
    [ "${AETHER_PANEL_PRESET:-}" = custom ] || AETHER_PANEL_PRESET=custom
}

saman_aether_flag_supported() {
    local flag="$1"
    [ -r "${SAMAN_AETHER_HELP_FILE:-}" ] && grep -Fq -- "$flag" "$SAMAN_AETHER_HELP_FILE"
}

saman_aether_detect_capabilities() {
    local bin version tmp strings_file
    bin="$(saman_aether_bin 2>/dev/null || true)"
    [ -n "$bin" ] || { printf 'ERROR: official Aether is not executable.\n' >&2; return 127; }
    tmp="$SAMAN_AETHER_CONFIG_DIR/help.txt.tmp.$$"
    "$bin" --help > "$tmp" 2>&1 || true
    chmod 0600 "$tmp" && mv -f "$tmp" "$SAMAN_AETHER_CONFIG_DIR/help.txt" || return 1
    SAMAN_AETHER_HELP_FILE="$SAMAN_AETHER_CONFIG_DIR/help.txt"
    version="$($bin --version 2>/dev/null || true)"
    SAMAN_AETHER_CAP_VERSION="$(printf '%s\n' "$version" | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+' | head -n1 || printf unknown)"
    SAMAN_AETHER_CAP_PATH="$bin"
    for p in MASQUE WG GOOL MIM H2 ECH FRAGMENT DNS ROUTING PERF TOR TOR_HELP IRONCLAD; do
        eval "SAMAN_AETHER_CAP_HAS_$p=0"
    done
    saman_aether_flag_supported --masque && SAMAN_AETHER_CAP_HAS_MASQUE=1
    saman_aether_flag_supported --wg && SAMAN_AETHER_CAP_HAS_WG=1
    saman_aether_flag_supported --gool && SAMAN_AETHER_CAP_HAS_GOOL=1
    saman_aether_flag_supported --mim && SAMAN_AETHER_CAP_HAS_MIM=1
    saman_aether_flag_supported --h2 && SAMAN_AETHER_CAP_HAS_H2=1
    saman_aether_flag_supported --ech && SAMAN_AETHER_CAP_HAS_ECH=1
    saman_aether_flag_supported --fragment && SAMAN_AETHER_CAP_HAS_FRAGMENT=1
    saman_aether_flag_supported --dns && SAMAN_AETHER_CAP_HAS_DNS=1
    saman_aether_flag_supported --route-block && SAMAN_AETHER_CAP_HAS_ROUTING=1
    saman_aether_flag_supported --perf && SAMAN_AETHER_CAP_HAS_PERF=1
    if saman_aether_flag_supported --tor &&
       saman_aether_flag_supported --tor-reverse &&
       saman_aether_flag_supported --tor-only; then
        SAMAN_AETHER_CAP_HAS_TOR_HELP=1
    fi
    # Help is not proof: non-Tor builds retain the same CLI listing.  Rust crate
    # provenance gives us a deterministic, non-network compiled-feature probe.
    strings_file="$SAMAN_AETHER_CONFIG_DIR/strings.tmp.$$"
    if command -v strings >/dev/null 2>&1 && strings "$bin" > "$strings_file" 2>/dev/null &&
       grep -Eq 'arti-client|arti_client|tor-proto|tor_proto|tor-rtcompat|tor_rtcompat' "$strings_file"; then
        [ "$SAMAN_AETHER_CAP_HAS_TOR_HELP" -eq 1 ] && SAMAN_AETHER_CAP_HAS_TOR=1
    fi
    rm -f -- "$strings_file"
    SAMAN_AETHER_CAP_SCANS=""
    for x in turbo balanced thorough stealth ironclad; do grep -Eq "(^|[|[:space:]])$x([|[:space:]]|$)" "$SAMAN_AETHER_HELP_FILE" && SAMAN_AETHER_CAP_SCANS+="$x "; done
    SAMAN_AETHER_CAP_NOIZE=""
    for x in off light firewall balanced gfw aggressive; do grep -Eq "(^|[|[:space:]])$x([|[:space:]]|$)" "$SAMAN_AETHER_HELP_FILE" && SAMAN_AETHER_CAP_NOIZE+="$x "; done
    [[ " $SAMAN_AETHER_CAP_SCANS " == *" ironclad "* ]] && SAMAN_AETHER_CAP_HAS_IRONCLAD=1
    {
        printf 'VERSION=%s\nPATH=%s\n' "$SAMAN_AETHER_CAP_VERSION" "$SAMAN_AETHER_CAP_PATH"
        printf 'SCANS=%s\nNOIZE=%s\n' "$SAMAN_AETHER_CAP_SCANS" "$SAMAN_AETHER_CAP_NOIZE"
        for p in MASQUE WG GOOL MIM H2 ECH FRAGMENT DNS ROUTING PERF TOR TOR_HELP IRONCLAD; do eval "printf '%s=%s\\n' HAS_$p \"\${SAMAN_AETHER_CAP_HAS_$p}\""; done
    } > "$SAMAN_AETHER_CAP_CACHE.tmp" && chmod 0600 "$SAMAN_AETHER_CAP_CACHE.tmp" && mv -f "$SAMAN_AETHER_CAP_CACHE.tmp" "$SAMAN_AETHER_CAP_CACHE"
    # Keep this private help snapshot for argument gating during this process.
}

saman_aether_require_caps() {
    [ -n "${SAMAN_AETHER_CAP_VERSION:-}" ] || saman_aether_detect_capabilities || return
}

saman_aether_validate_settings() {
    local changed=0
    saman_aether_load
    saman_aether_detect_capabilities >/dev/null 2>&1 || return 1
    case "$AETHER_PANEL_MODE" in
        masque) [ "$SAMAN_AETHER_CAP_HAS_MASQUE" -eq 1 ] || { AETHER_PANEL_MODE=wg; changed=1; };;
        wg) [ "$SAMAN_AETHER_CAP_HAS_WG" -eq 1 ] || { AETHER_PANEL_MODE=masque; changed=1; };;
        gool) [ "$SAMAN_AETHER_CAP_HAS_GOOL" -eq 1 ] || { AETHER_PANEL_MODE=masque; changed=1; };;
        mim) [ "$SAMAN_AETHER_CAP_HAS_MIM" -eq 1 ] || { AETHER_PANEL_MODE=masque; changed=1; };;
    esac
    [ -z "$AETHER_PANEL_SCAN" ] || case " $SAMAN_AETHER_CAP_SCANS " in *" $AETHER_PANEL_SCAN "*) ;; *) AETHER_PANEL_SCAN=balanced; changed=1;; esac
    [ -z "$AETHER_PANEL_NOIZE" ] || case " $SAMAN_AETHER_CAP_NOIZE " in *" $AETHER_PANEL_NOIZE "*) ;; *) AETHER_PANEL_NOIZE=""; changed=1;; esac
    [ "$changed" -eq 0 ] || saman_aether_save
    return 0
}

saman_aether_validate_launch() {
    local item path
    case "${AETHER_PANEL_TOR_MODE:-off}" in
        off) return 0 ;;
        inside|reverse|only) ;;
        *) printf 'ERROR: invalid Tor routing mode.\n' >&2; return 2 ;;
    esac
    [ "${SAMAN_AETHER_CAP_HAS_TOR:-0}" -eq 1 ] || {
        printf 'ERROR: installed official Aether was not compiled with Tor support.\n' >&2
        return 1
    }
    if [ "$AETHER_PANEL_TOR_BIND" = "$AETHER_PANEL_BIND" ]; then
        printf 'ERROR: Tor proxy and main SOCKS cannot share %s.\n' "$AETHER_PANEL_BIND" >&2
        return 1
    fi
    if [ "$AETHER_PANEL_TOR_MODE" = reverse ] && [ "$AETHER_PANEL_MODE" != masque ]; then
        printf 'ERROR: Tor reverse requires MASQUE HTTP/2; WireGuard, GOOL, and MIM are incompatible.\n' >&2
        return 1
    fi
    if [ -n "$AETHER_PANEL_TOR_PT" ]; then
        while IFS= read -r item; do
            [ -n "$item" ] || continue
            path="${item#*=}"
            [ -x "$path" ] || { printf 'ERROR: Tor pluggable transport is not executable.\n' >&2; return 1; }
        done < <(printf '%s\n' "$AETHER_PANEL_TOR_PT" | tr ';' '\n')
    fi
    if [ -n "$AETHER_PANEL_TOR_PT_DIR" ]; then
        while IFS= read -r item; do
            [ -d "$item" ] || { printf 'ERROR: Tor PT directory is unavailable.\n' >&2; return 1; }
        done < <(printf '%s\n' "$AETHER_PANEL_TOR_PT_DIR" | tr ';' '\n')
    fi
}

saman_aether_build_args_array() {
    local mode="$1"; shift || true
    local ip="$AETHER_PANEL_IP" item
    saman_aether_validate_launch || return
    if [ "${AETHER_PANEL_TOR_MODE:-off}" = only ]; then
        SAMAN_AETHER_ARGS=(--tor-only --bind "$AETHER_PANEL_TOR_BIND")
        mode=tor-only
    else
        SAMAN_AETHER_ARGS=(--bind "$AETHER_PANEL_BIND")
        [ "$AETHER_PANEL_TOR_MODE" = off ] || [ "$AETHER_PANEL_TOR_BIND" != "$AETHER_PANEL_HTTP" ] && SAMAN_AETHER_ARGS+=(--http-proxy "$AETHER_PANEL_HTTP")
    fi
    case "$mode" in
        masque) SAMAN_AETHER_ARGS+=(--masque);;
        wg) SAMAN_AETHER_ARGS+=(--wg);;
        gool) SAMAN_AETHER_ARGS+=(--gool);;
        mim) SAMAN_AETHER_ARGS+=(--mim);;
        tor-only) ;;
    esac
    case "$ip" in 4) SAMAN_AETHER_ARGS+=(-4);; 6) SAMAN_AETHER_ARGS+=(-6);; both) SAMAN_AETHER_ARGS+=(--dual);; esac
    [ -n "$AETHER_PANEL_SCAN" ] && saman_aether_flag_supported --scan && SAMAN_AETHER_ARGS+=(--scan "$AETHER_PANEL_SCAN")
    [ -n "$AETHER_PANEL_NOIZE" ] && saman_aether_flag_supported --noize && SAMAN_AETHER_ARGS+=(--noize "$AETHER_PANEL_NOIZE")
    [ "$AETHER_PANEL_QUICK" = 1 ] && saman_aether_flag_supported --quick-reconnect && SAMAN_AETHER_ARGS+=(--quick-reconnect)
    [ "$mode" = masque ] && { [ "$AETHER_PANEL_H2" = 1 ] || [ "${AETHER_PANEL_TOR_MODE:-off}" = reverse ]; } && saman_aether_flag_supported --h2 && SAMAN_AETHER_ARGS+=(--h2)
    [ "$mode" = mim ] && [ "$AETHER_PANEL_H2" = 1 ] && saman_aether_flag_supported --h2 && SAMAN_AETHER_ARGS+=(--h2)
    [ "$AETHER_PANEL_H3" = 1 ] && [ "$mode" = masque ] && [ "${AETHER_PANEL_TOR_MODE:-off}" != reverse ] && saman_aether_flag_supported --h3 && SAMAN_AETHER_ARGS+=(--h3)
    [ "$AETHER_PANEL_NO_QUIC_V2" = 1 ] && saman_aether_flag_supported --no-quic-v2 && SAMAN_AETHER_ARGS+=(--no-quic-v2)
    [ "$AETHER_PANEL_ECH" ] && saman_aether_flag_supported --ech && SAMAN_AETHER_ARGS+=(--ech "$AETHER_PANEL_ECH")
    [ "$AETHER_PANEL_FRAGMENT" = 1 ] && saman_aether_flag_supported --fragment && SAMAN_AETHER_ARGS+=(--fragment)
    [ "$AETHER_PANEL_FRAGMENT_SIZE" ] && saman_aether_flag_supported --fragment-size && SAMAN_AETHER_ARGS+=(--fragment-size "$AETHER_PANEL_FRAGMENT_SIZE")
    [ "$AETHER_PANEL_FRAGMENT_DELAY" ] && saman_aether_flag_supported --fragment-delay && SAMAN_AETHER_ARGS+=(--fragment-delay "$AETHER_PANEL_FRAGMENT_DELAY")
    [ "$AETHER_PANEL_UPSTREAM" ] && saman_aether_flag_supported --upstream && SAMAN_AETHER_ARGS+=(--upstream "$AETHER_PANEL_UPSTREAM")
    [ "$AETHER_PANEL_DNS" ] && saman_aether_flag_supported --dns && SAMAN_AETHER_ARGS+=(--dns "$AETHER_PANEL_DNS")
    [ "$AETHER_PANEL_ROUTE_BLOCK" ] && saman_aether_flag_supported --route-block && SAMAN_AETHER_ARGS+=(--route-block "$AETHER_PANEL_ROUTE_BLOCK")
    [ "$AETHER_PANEL_ROUTE_DIRECT" ] && saman_aether_flag_supported --route-direct && SAMAN_AETHER_ARGS+=(--route-direct "$AETHER_PANEL_ROUTE_DIRECT")
    [ "$AETHER_PANEL_ROUTES" ] && saman_aether_flag_supported --routes && SAMAN_AETHER_ARGS+=(--routes "$AETHER_PANEL_ROUTES")
    [ "$AETHER_PANEL_RECONNECT_SECS" ] && saman_aether_flag_supported --reconnect-secs && SAMAN_AETHER_ARGS+=(--reconnect-secs "$AETHER_PANEL_RECONNECT_SECS")
    [ "$AETHER_PANEL_STARTUP_SECS" ] && saman_aether_flag_supported --startup-secs && SAMAN_AETHER_ARGS+=(--startup-secs "$AETHER_PANEL_STARTUP_SECS")
    [ "$AETHER_PANEL_VALIDATE_SECS" ] && saman_aether_flag_supported --validate-secs && SAMAN_AETHER_ARGS+=(--validate-secs "$AETHER_PANEL_VALIDATE_SECS")
    [ "$AETHER_PANEL_KEEPALIVE" ] && { [ "$mode" = wg ] || [ "$mode" = gool ]; } && saman_aether_flag_supported --keepalive && SAMAN_AETHER_ARGS+=(--keepalive "$AETHER_PANEL_KEEPALIVE")
    [ "$AETHER_PANEL_PEER" ] && saman_aether_flag_supported --peer && SAMAN_AETHER_ARGS+=(--peer "$AETHER_PANEL_PEER")
    [ "$AETHER_PANEL_WG_PEER" ] && saman_aether_flag_supported --wg-peer && SAMAN_AETHER_ARGS+=(--wg-peer "$AETHER_PANEL_WG_PEER")
    [ "$AETHER_PANEL_WIW_OUTER" ] && [ "$mode" = gool ] && saman_aether_flag_supported --wiw-outer && SAMAN_AETHER_ARGS+=(--wiw-outer "$AETHER_PANEL_WIW_OUTER")
    [ "$AETHER_PANEL_WIW_INNER" ] && [ "$mode" = gool ] && saman_aether_flag_supported --wiw-inner && SAMAN_AETHER_ARGS+=(--wiw-inner "$AETHER_PANEL_WIW_INNER")
    [ "$AETHER_PANEL_WIW_PEERS" ] && [ "$mode" = gool ] && saman_aether_flag_supported --wiw-peers && SAMAN_AETHER_ARGS+=(--wiw-peers "$AETHER_PANEL_WIW_PEERS")
    [ "$AETHER_PANEL_MIM_OUTER" ] && [ "$mode" = mim ] && saman_aether_flag_supported --mim-outer && SAMAN_AETHER_ARGS+=(--mim-outer "$AETHER_PANEL_MIM_OUTER")
    [ "$AETHER_PANEL_MIM_INNER" ] && [ "$mode" = mim ] && saman_aether_flag_supported --mim-inner && SAMAN_AETHER_ARGS+=(--mim-inner "$AETHER_PANEL_MIM_INNER")
    [ "$AETHER_PANEL_MIM_PEERS" ] && [ "$mode" = mim ] && saman_aether_flag_supported --mim-peers && SAMAN_AETHER_ARGS+=(--mim-peers "$AETHER_PANEL_MIM_PEERS")
    [ "$AETHER_PANEL_H2_PEER" ] && [ "$mode" = masque ] && [ "$AETHER_PANEL_H2" = 1 ] && saman_aether_flag_supported --h2-peer && SAMAN_AETHER_ARGS+=(--h2-peer "$AETHER_PANEL_H2_PEER")
    [ "$AETHER_PANEL_PERF" ] && saman_aether_flag_supported --perf && SAMAN_AETHER_ARGS+=(--perf "$AETHER_PANEL_PERF")
    [ "$AETHER_PANEL_TLS_GROUPS" ] && saman_aether_flag_supported --tls-groups && SAMAN_AETHER_ARGS+=(--tls-groups "$AETHER_PANEL_TLS_GROUPS")
    [ "$AETHER_PANEL_LOG_LEVEL" ] && saman_aether_flag_supported --log-level && SAMAN_AETHER_ARGS+=(--log-level "$AETHER_PANEL_LOG_LEVEL")
    [ "$AETHER_PANEL_NO_DATA_CHECK" = 1 ] && saman_aether_flag_supported --no-data-check && SAMAN_AETHER_ARGS+=(--no-data-check)
    [ "$AETHER_PANEL_NO_PROFILE_RETRY" = 1 ] && { [ "$mode" = wg ] || [ "$mode" = gool ]; } && saman_aether_flag_supported --no-profile-retry && SAMAN_AETHER_ARGS+=(--no-profile-retry)
    case "${AETHER_PANEL_TOR_MODE:-off}" in
        inside) SAMAN_AETHER_ARGS+=(--tor --tor-bind "$AETHER_PANEL_TOR_BIND") ;;
        reverse) SAMAN_AETHER_ARGS+=(--tor-reverse --tor-bind "$AETHER_PANEL_TOR_BIND") ;;
    esac
    if [ "${AETHER_PANEL_TOR_MODE:-off}" != off ]; then
        [ -n "$AETHER_PANEL_TOR_DIR" ] && saman_aether_flag_supported --tor-dir && SAMAN_AETHER_ARGS+=(--tor-dir "$AETHER_PANEL_TOR_DIR")
        case "$AETHER_PANEL_TOR_BRIDGES" in
            force) saman_aether_flag_supported --tor-bridges && SAMAN_AETHER_ARGS+=(--tor-bridges) ;;
            disabled) saman_aether_flag_supported --no-tor-bridges && SAMAN_AETHER_ARGS+=(--no-tor-bridges) ;;
        esac
        if [ -n "$AETHER_PANEL_TOR_BRIDGE" ] && saman_aether_flag_supported --tor-bridge; then
            while IFS= read -r item; do [ -n "$item" ] && SAMAN_AETHER_ARGS+=(--tor-bridge "$item"); done < <(printf '%s\n' "$AETHER_PANEL_TOR_BRIDGE" | tr ';' '\n')
        fi
        if [ -n "$AETHER_PANEL_TOR_PT" ] && saman_aether_flag_supported --tor-pt; then
            while IFS= read -r item; do [ -n "$item" ] && SAMAN_AETHER_ARGS+=(--tor-pt "$item"); done < <(printf '%s\n' "$AETHER_PANEL_TOR_PT" | tr ';' '\n')
        fi
        if [ -n "$AETHER_PANEL_TOR_PT_DIR" ] && saman_aether_flag_supported --tor-pt-dir; then
            while IFS= read -r item; do [ -n "$item" ] && SAMAN_AETHER_ARGS+=(--tor-pt-dir "$item"); done < <(printf '%s\n' "$AETHER_PANEL_TOR_PT_DIR" | tr ';' '\n')
        fi
    fi
}

saman_aether_build_args() {
    saman_aether_require_caps || return 1
    saman_aether_build_args_array "${AETHER_PANEL_MODE:-masque}"
    printf '%q ' "${SAMAN_AETHER_ARGS[@]}"
    printf '\n'
}

saman_aether_apply_preset() {
    local p="$1"
    saman_aether_defaults
    case "$p" in
        upstream-default) AETHER_PANEL_PRESET=upstream-default; AETHER_PANEL_MODE=masque; AETHER_PANEL_SCAN=""; AETHER_PANEL_NOIZE=""; AETHER_PANEL_QUICK=0;;
        balanced) AETHER_PANEL_PRESET=balanced; AETHER_PANEL_MODE=masque; AETHER_PANEL_SCAN=balanced; AETHER_PANEL_NOIZE=firewall; AETHER_PANEL_QUICK=1;;
        fast) AETHER_PANEL_PRESET=fast; AETHER_PANEL_MODE=masque; AETHER_PANEL_SCAN=turbo; AETHER_PANEL_NOIZE=light; AETHER_PANEL_QUICK=1;;
        stable) AETHER_PANEL_PRESET=stable; AETHER_PANEL_MODE=masque; AETHER_PANEL_SCAN=balanced; AETHER_PANEL_NOIZE=firewall; AETHER_PANEL_QUICK=1; AETHER_PANEL_RECONNECT_SECS=2;;
        restricted) AETHER_PANEL_PRESET=restricted; AETHER_PANEL_MODE=masque; AETHER_PANEL_SCAN=ironclad; AETHER_PANEL_NOIZE=gfw; AETHER_PANEL_H2=1; AETHER_PANEL_H3=0; AETHER_PANEL_QUICK=1;;
        stealth) AETHER_PANEL_PRESET=stealth; AETHER_PANEL_MODE=masque; AETHER_PANEL_SCAN=stealth; AETHER_PANEL_NOIZE=light; AETHER_PANEL_QUICK=1;;
        compatibility) AETHER_PANEL_PRESET=compatibility; AETHER_PANEL_MODE=masque; AETHER_PANEL_SCAN=balanced; AETHER_PANEL_NOIZE=firewall; AETHER_PANEL_IP=4; AETHER_PANEL_H2=0; AETHER_PANEL_H3=1; AETHER_PANEL_QUICK=1;;
        custom) AETHER_PANEL_PRESET=custom;;
        *) printf 'ERROR: unknown preset: %s\n' "$p" >&2; return 2;;
    esac
    saman_aether_save
}

saman_aether_show_config() {
    saman_aether_load; saman_aether_detect_capabilities >/dev/null 2>&1 || true
    printf '%s\n' 'Aether configuration (Saman preferences; secrets omitted)' \
        "Aether Version    : ${SAMAN_AETHER_CAP_VERSION:-unknown}" \
        "Executable        : ${SAMAN_AETHER_CAP_PATH:-$(saman_aether_bin 2>/dev/null || echo unavailable)}" \
        "Connection Mode   : $AETHER_PANEL_MODE" \
        "Tor Routing       : $AETHER_PANEL_TOR_MODE" \
        "Tor Proxy         : $([ "$AETHER_PANEL_TOR_MODE" = only ] && printf '%s' "$AETHER_PANEL_TOR_BIND" || { [ "$AETHER_PANEL_TOR_MODE" = off ] && printf 'off' || printf '%s' "$AETHER_PANEL_TOR_BIND"; })" \
        "Tor Bridges       : $AETHER_PANEL_TOR_BRIDGES" \
        "Custom bridges    : $([ -n "$AETHER_PANEL_TOR_BRIDGE" ] && printf 'configured (private)' || printf 'none')" \
        "Custom PT         : $([ -n "$AETHER_PANEL_TOR_PT" ] && printf 'configured (private)' || printf 'automatic')" \
        "Actual Running    : $(if declare -F s2_aether_pid_running >/dev/null 2>&1 && s2_aether_pid_running; then s2_aether_mode; else printf 'stopped'; fi)" \
        "Preset            : $AETHER_PANEL_PRESET" \
        "Scan Mode         : ${AETHER_PANEL_SCAN:-upstream default}" \
        "Obfuscation       : ${AETHER_PANEL_NOIZE:-upstream default}" \
        "IP Mode           : $AETHER_PANEL_IP" \
        "Quick Reconnect   : $([ "$AETHER_PANEL_QUICK" = 1 ] && echo ON || echo OFF)" \
        "MASQUE Carrier    : $([ "$AETHER_PANEL_H2" = 1 ] && echo HTTP/2 || echo HTTP/3)" \
        "ECH               : ${AETHER_PANEL_ECH:-upstream default}" \
        "Fragmentation     : $([ "$AETHER_PANEL_FRAGMENT" = 1 ] && echo ON || echo OFF)" \
        "SOCKS             : $AETHER_PANEL_BIND" \
        "HTTP Proxy        : $AETHER_PANEL_HTTP" \
        "Settings          : $SAMAN_AETHER_SETTINGS"
}

saman_aether_capabilities() {
    saman_aether_detect_capabilities || return
    printf 'Official Aether: %s\nPath: %s\n' "$SAMAN_AETHER_CAP_VERSION" "$SAMAN_AETHER_CAP_PATH"
    printf 'Modes: masque=%s wg=%s gool=%s mim=%s\n' "$SAMAN_AETHER_CAP_HAS_MASQUE" "$SAMAN_AETHER_CAP_HAS_WG" "$SAMAN_AETHER_CAP_HAS_GOOL" "$SAMAN_AETHER_CAP_HAS_MIM"
    if [ "$SAMAN_AETHER_CAP_HAS_TOR" -eq 1 ]; then
        printf 'Tor: available (compiled)\n'
    elif [ "${SAMAN_AETHER_CAP_HAS_TOR_HELP:-0}" -eq 1 ]; then
        printf 'Tor: unavailable (not compiled)\n'
    else
        printf 'Tor: unavailable (CLI unsupported)\n'
    fi
    printf 'Scan modes: %s\nNoize profiles: %s\n' "$SAMAN_AETHER_CAP_SCANS" "$SAMAN_AETHER_CAP_NOIZE"
}

saman_aether_backup_settings() {
    local stamp dest
    stamp="$1"
    dest="$SAMAN_AETHER_BACKUP_DIR/$stamp"
    mkdir -p "$dest" || return 1
    [ -e "$SAMAN_AETHER_SETTINGS" ] && cp -p "$SAMAN_AETHER_SETTINGS" "$dest/settings.conf"
    printf '%s\n' "$dest"
}

saman_aether_reset() {
    local yes="${1:-}"
    [ "$yes" = --yes ] || { read -r -p 'Type RESET to restore upstream defaults (identities/keys are kept): ' yes; [ "$yes" = RESET ] || return 1; }
    saman_aether_init || return 1
    saman_aether_backup_settings "reset-$(date +%Y%m%d-%H%M%S)" >/dev/null
    saman_aether_save_defaults
    printf 'OK: Saman Aether overrides reset; official identities and keys were kept.\n'
}

saman_aether_archive_safe() {
    local archive="$1" listing="$2" verbose="$3" name type
    tar -tzf "$archive" > "$listing" 2>/dev/null || return 1
    tar -tvzf "$archive" > "$verbose" 2>/dev/null || return 1
    grep -Eq '^\./?aether$|^aether$' "$listing" || return 1
    while IFS= read -r name; do
        name="${name#./}"
        case "$name" in
            aether|pt|pt/) ;;
            pt/*)
                [[ "$name" != *'/../'* && "$name" != ../* && "$name" != */.. && "$name" != *//* ]] || return 1
                [[ "$name" =~ ^pt/[A-Za-z0-9._+/@=-]+/?$ ]] || return 1
                ;;
            *) return 1 ;;
        esac
    done < "$listing"
    while IFS= read -r name; do
        type="${name:0:1}"
        case "$type" in -|d) ;; *) return 1 ;; esac
    done < "$verbose"
}

saman_aether_update() (
    set -o pipefail
    local bin arch api json tag asset checksum_url asset_url tmp new install_dir live_pt
    local backup expected actual version staged_bin staged_pt rollback_bin rollback_pt had_pt=0
    bin="$(saman_aether_bin)" || return 127
    arch="${SAMAN_AETHER_ARCH:-$(uname -m)}"; case "$arch" in aarch64|arm64) arch=arm64;; armv7l|armv7|arm) arch=armv7;; x86_64|amd64) arch=x86_64;; *) printf 'ERROR: unsupported architecture: %s\n' "$arch" >&2; return 2;; esac
    for dep in curl jq tar sha256sum readlink; do command -v "$dep" >/dev/null 2>&1 || { printf 'ERROR: %s is required.\n' "$dep" >&2; return 127; }; done
    api="https://api.github.com/repos/$SAMAN_AETHER_UPSTREAM/releases/latest"
    json="$(curl --proto '=https' --tlsv1.2 -fsSL --connect-timeout 15 --max-time 60 -H 'Accept: application/vnd.github+json' "$api")" || return 1
    tag="$(printf '%s' "$json" | jq -r 'select(.draft==false and .prerelease==false) | .tag_name')"
    asset="aether-android-$arch.tar.gz"
    asset_url="$(printf '%s' "$json" | jq -r --arg n "$asset" '.assets[]|select(.name==$n)|.browser_download_url')"
    checksum_url="$(printf '%s' "$json" | jq -r --arg n "$asset.sha256" '.assets[]|select(.name==$n)|.browser_download_url')"
    [ -n "$tag" ] && [ "$tag" != null ] && [ -n "$asset_url" ] && [ -n "$checksum_url" ] || { printf 'ERROR: official release lacks %s or checksum.\n' "$asset" >&2; return 1; }
    version="$($bin --version 2>/dev/null || printf unknown)"
    printf 'Installed: %s\nLatest: %s (%s)\n' "$version" "$tag" "$arch"
    [ "${1:-}" = --check ] && return 0

    tmp="$(mktemp -d "$SAMAN_AETHER_CONFIG_DIR/update.XXXXXX")" || return 1
    install_dir="${bin%/*}"; live_pt="$install_dir/pt"
    staged_bin="$install_dir/.aether.update.$$"; staged_pt="$install_dir/.pt.update.$$"
    rollback_bin="$install_dir/.aether.rollback.$$"; rollback_pt="$install_dir/.pt.rollback.$$"
    trap 'rm -rf -- "$tmp" "$staged_bin" "$staged_pt" "$rollback_bin" "$rollback_pt"' EXIT INT TERM
    curl --proto '=https' --tlsv1.2 -fL --connect-timeout 15 --max-time 180 "$asset_url" -o "$tmp/$asset" || return 1
    curl --proto '=https' --tlsv1.2 -fL --connect-timeout 15 --max-time 60 "$checksum_url" -o "$tmp/$asset.sha256" || return 1
    (cd "$tmp" && sha256sum -c "$asset.sha256") || { printf 'ERROR: checksum mismatch.\n' >&2; return 1; }
    saman_aether_archive_safe "$tmp/$asset" "$tmp/archive.list" "$tmp/archive.verbose" || { printf 'ERROR: unsafe or incomplete official archive.\n' >&2; return 1; }
    mkdir -p "$tmp/extract"; tar -xzf "$tmp/$asset" -C "$tmp/extract" || return 1
    new="$tmp/extract/aether"; [ -f "$new" ] && [ -x "$new" ] && [ -d "$tmp/extract/pt" ] || { printf 'ERROR: archive must contain executable aether and pt/.\n' >&2; return 1; }
    "$new" --version >/dev/null 2>&1 && "$new" --help >/dev/null 2>&1 || { printf 'ERROR: staged binary validation failed.\n' >&2; return 1; }

    backup="$SAMAN_AETHER_BACKUP_DIR/update-$(date +%Y%m%d-%H%M%S)-$$"
    mkdir -p "$backup"; chmod 0700 "$backup"
    cp -p "$bin" "$backup/aether" || return 1
    [ ! -d "$live_pt" ] || { cp -a "$live_pt" "$backup/pt" || return 1; had_pt=1; }
    [ ! -f "$SAMAN_AETHER_SETTINGS" ] || cp -p "$SAMAN_AETHER_SETTINGS" "$backup/settings.conf" || return 1

    install -m 0700 "$new" "$staged_bin" || return 1
    cp -a "$tmp/extract/pt" "$staged_pt" || return 1
    mv "$bin" "$rollback_bin" || return 1
    if [ "$had_pt" -eq 1 ]; then mv "$live_pt" "$rollback_pt" || { mv "$rollback_bin" "$bin"; return 1; }; fi
    if ! mv "$staged_bin" "$bin" || ! mv "$staged_pt" "$live_pt" ||
       ! "$bin" --version >/dev/null 2>&1 || ! "$bin" --help >/dev/null 2>&1; then
        rm -rf -- "$bin" "$live_pt"
        mv "$rollback_bin" "$bin" 2>/dev/null || true
        [ "$had_pt" -eq 0 ] || mv "$rollback_pt" "$live_pt" 2>/dev/null || true
        printf 'ERROR: live validation failed; binary and PT directory rolled back.\n' >&2
        return 1
    fi
    rm -rf -- "$rollback_bin" "$rollback_pt"
    version="$($bin --version 2>/dev/null || printf unknown)"
    printf 'OK: installed %s with official pt/. Backup: %s\n' "$version" "$backup"
)

saman_aether_test_current() {
    local bin pid rc
    saman_aether_load; saman_aether_detect_capabilities >/dev/null 2>&1 || return 1
    if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -Eq ":(1819|1820)[[:space:]]"; then printf 'TIMEOUT/BLOCKED: configured proxy port is already occupied; unrelated processes were not killed.\n'; return 124; fi
    saman_aether_build_args_array "$AETHER_PANEL_MODE"
    bin="$(saman_aether_bin)" || return 127
    printf 'Testing official Aether (%s) with saved settings...\n' "$AETHER_PANEL_MODE"
    local log="$SAMAN_AETHER_CONFIG_DIR/test-current.log"
    "$bin" "${SAMAN_AETHER_ARGS[@]}" >"$log" 2>&1 & pid=$!
    sleep 3
    if kill -0 "$pid" 2>/dev/null; then kill -TERM "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; printf 'TIMEOUT: process started but did not finish within 3 seconds; stopped cleanly.\n'; return 124; fi
    wait "$pid"; rc=$?; printf 'Aether exited with code %s.\n' "$rc"; return "$rc"
}

saman_aether_diagnostics_panel() {
    saman_aether_show_config
    printf '\nCapabilities:\n'; saman_aether_capabilities
    printf '\nProcess/ports:\n'; s2_aether_status 2>/dev/null || true
    printf '\nDependencies:\n'; for x in bash curl jq tar sha256sum readlink ss timeout; do command -v "$x" >/dev/null 2>&1 && printf '  OK %s\n' "$x" || printf '  MISSING %s\n' "$x"; done
}

saman_aether_prompt_set() {
    local key="$1" label="$2" value var current
    var="$(saman_aether_key_var "$key")"; current="${!var-}"
    read -r -p "$label [current: $current]: " value
    [ -n "$value" ] || return 0
    saman_aether_set "$key" "$value"
}

saman_aether_mode_menu() {
    local c rc current
    while :; do
        saman_aether_load; s2_clear
        current="$AETHER_PANEL_MODE"
        [ "$current" = masque ] && { [ "$AETHER_PANEL_H2" = 1 ] && current=masque-h2 || current=masque-h3; }
        printf '%s\n' 'CONNECTION TRANSPORTS' '---------------------'
        [ "${SAMAN_AETHER_CAP_HAS_MASQUE:-0}" -eq 1 ] && printf '1) %s MASQUE HTTP/3\n' "$(saman_aether_mark "$current" masque-h3)"
        [ "${SAMAN_AETHER_CAP_HAS_H2:-0}" -eq 1 ] && printf '2) %s MASQUE HTTP/2\n' "$(saman_aether_mark "$current" masque-h2)"
        [ "${SAMAN_AETHER_CAP_HAS_WG:-0}" -eq 1 ] && printf '3) %s WireGuard\n' "$(saman_aether_mark "$current" wg)"
        [ "${SAMAN_AETHER_CAP_HAS_GOOL:-0}" -eq 1 ] && printf '4) %s GOOL\n' "$(saman_aether_mark "$current" gool)"
        [ "${SAMAN_AETHER_CAP_HAS_MIM:-0}" -eq 1 ] && printf '5) %s MASQUE-in-MASQUE\n' "$(saman_aether_mark "$current" mim)"
        printf '0) Back\n'
        if saman_aether_read_number 5; then rc=0; else rc=$?; fi; c="${SAMAN_AETHER_CHOICE:-}"
        [ "$rc" -eq 1 ] && return 0; [ "$rc" -eq 2 ] && continue; [ "$c" = 0 ] && return 0
        case "$c" in
            1) [ "${SAMAN_AETHER_CAP_HAS_MASQUE:-0}" -eq 1 ] || continue; next_mode=masque; next_h2=0; next_h3=1; next_label='MASQUE H3' ;;
            2) [ "${SAMAN_AETHER_CAP_HAS_H2:-0}" -eq 1 ] || continue; next_mode=masque; next_h2=1; next_h3=0; next_label='MASQUE H2' ;;
            3) [ "${SAMAN_AETHER_CAP_HAS_WG:-0}" -eq 1 ] || continue; next_mode=wg; next_h2=0; next_h3=0; next_label='WG' ;;
            4) [ "${SAMAN_AETHER_CAP_HAS_GOOL:-0}" -eq 1 ] || continue; next_mode=gool; next_h2=0; next_h3=0; next_label='GOOL' ;;
            5) [ "${SAMAN_AETHER_CAP_HAS_MIM:-0}" -eq 1 ] || continue; next_mode=mim; next_h2=0; next_h3=0; next_label='MIM' ;;
        esac
        [ "$AETHER_PANEL_TOR_MODE" = reverse ] && [ "$next_mode" != masque ] && { printf 'Tor reverse requires MASQUE H2.\n'; saman_aether_ack; continue; }
        next_current="$next_mode"; [ "$next_mode" = masque ] && { [ "$next_h2" = 1 ] && next_current=masque-h2 || next_current=masque-h3; }
        [ "$current" = "$next_current" ] && { printf 'Already selected: Connection Mode = %s\n' "$next_label"; return; }
        AETHER_PANEL_MODE="$next_mode" AETHER_PANEL_H2="$next_h2" AETHER_PANEL_H3="$next_h3"
        saman_aether_mark_custom; saman_aether_save
        printf 'Saved: Connection Mode = %s\n' "$next_label"
        return
    done
}

saman_aether_select_menu() {
    local key="$1" title="$2" values="$3" c i v
    while :; do
        printf '\n%s\n' "$title"; i=1; for v in $values; do printf '%s) %s\n' "$i" "$v"; i=$((i+1)); done; printf '0) Back\n'
        read -r -p 'Choice: ' c || return; [ "$c" = 0 ] && return
        i=1; for v in $values; do if [ "$i" = "$c" ]; then saman_aether_set "$key" "$v"; return; fi; i=$((i+1)); done
    done
}

saman_aether_preset_menu() {
    local c i=1 v selected current
    saman_aether_load
    printf '\nPresets (official Aether arguments only)\n'
    for v in upstream-default balanced fast stable restricted stealth compatibility custom; do printf '%s) %s\n' "$i" "$v"; i=$((i+1)); done
    printf '0) Back\n'; read -r -p 'Choice: ' c || return; [ "$c" = 0 ] && return
    i=1; for v in upstream-default balanced fast stable restricted stealth compatibility custom; do if [ "$i" = "$c" ]; then selected="$v"; break; fi; i=$((i+1)); done
    [ -n "${selected:-}" ] || return
    current="$AETHER_PANEL_PRESET"
    [ "$current" = "$selected" ] && { printf 'Already selected: Preset = %s\n' "$selected"; return; }
    saman_aether_apply_preset "$selected" || return
    saman_aether_load
    [ "$AETHER_PANEL_PRESET" = "$selected" ] || { printf 'ERROR: saved Preset verification failed.\n' >&2; return 1; }
    printf 'Saved: Preset = %s\n' "$selected"
}
saman_aether_scan_menu() { saman_aether_select_menu SCAN 'Scan modes: turbo=first response, balanced=fastest few, thorough=whole ranges, stealth=fewer probes, ironclad=real tunnel/HTTP validation' "$SAMAN_AETHER_CAP_SCANS"; }
saman_aether_noize_menu() { saman_aether_select_menu NOIZE 'Obfuscation profiles (official Noize)' "$SAMAN_AETHER_CAP_NOIZE"; }

saman_aether_network_menu() {
    local c
    while :; do
        saman_aether_load; printf '\nNetwork / IP\n1) IP mode [%s]\n2) SOCKS bind [%s]\n3) HTTP proxy [%s]\n4) DNS [%s]\n5) Upstream proxy [%s]\n6) Routing lists\n7) Quick reconnect [%s]\n8) Reconnect seconds [%s]\n0) Back\n' "$AETHER_PANEL_IP" "$AETHER_PANEL_BIND" "$AETHER_PANEL_HTTP" "${AETHER_PANEL_DNS:-default}" "${AETHER_PANEL_UPSTREAM:-none}" "$AETHER_PANEL_QUICK" "${AETHER_PANEL_RECONNECT_SECS:-default}"
        read -r -p 'Choice: ' c
        case "$c" in 1) saman_aether_select_menu IP 'IP mode' '4 6 both';; 2) read -r -p 'SOCKS address: ' v; [ -n "$v" ] && saman_aether_set BIND "$v";; 3) read -r -p 'HTTP address: ' v; [ -n "$v" ] && saman_aether_set HTTP "$v";; 4) read -r -p 'DNS list: ' v; saman_aether_set DNS "$v";; 5) read -r -p 'Upstream URL: ' v; saman_aether_set UPSTREAM "$v";; 6) read -r -p 'Route-block list: ' v; saman_aether_set ROUTE_BLOCK "$v"; read -r -p 'Route-direct list: ' v; saman_aether_set ROUTE_DIRECT "$v";; 7) [ "$AETHER_PANEL_QUICK" = 1 ] && saman_aether_set QUICK 0 || saman_aether_set QUICK 1;; 8) read -r -p 'Reconnect seconds: ' v; saman_aether_set RECONNECT_SECS "$v";; 0) return;; esac
    done
}

saman_aether_masque_menu() {
    local c v
    while :; do saman_aether_load; printf '\nMASQUE Settings\n1) Carrier [%s]\n2) ECH [%s]\n3) Fragmentation [%s]\n4) Fragment size [%s]\n5) Fragment delay [%s]\n6) H2 peer [%s]\n7) Startup seconds [%s]\n8) Validation seconds [%s]\n0) Back\n' "$([ "$AETHER_PANEL_H2" = 1 ] && echo HTTP/2 || echo HTTP/3)" "${AETHER_PANEL_ECH:-default}" "$AETHER_PANEL_FRAGMENT" "${AETHER_PANEL_FRAGMENT_SIZE:-default}" "${AETHER_PANEL_FRAGMENT_DELAY:-default}" "${AETHER_PANEL_H2_PEER:-auto}" "${AETHER_PANEL_STARTUP_SECS:-default}" "${AETHER_PANEL_VALIDATE_SECS:-default}"; read -r -p 'Choice: ' c; case "$c" in 1) [ "$AETHER_PANEL_H2" = 1 ] && AETHER_PANEL_H2=0 || AETHER_PANEL_H2=1; AETHER_PANEL_H3=$((1-AETHER_PANEL_H2)); saman_aether_mark_custom; saman_aether_save;; 2) read -r -p 'ECH (auto/base64, empty clears): ' v; saman_aether_set ECH "$v";; 3) [ "$AETHER_PANEL_FRAGMENT" = 1 ] && saman_aether_set FRAGMENT 0 || saman_aether_set FRAGMENT 1;; 4) read -r -p 'Fragment size: ' v; saman_aether_set FRAGMENT_SIZE "$v";; 5) read -r -p 'Fragment delay: ' v; saman_aether_set FRAGMENT_DELAY "$v";; 6) read -r -p 'H2 peer: ' v; saman_aether_set H2_PEER "$v";; 7) read -r -p 'Startup seconds: ' v; saman_aether_set STARTUP_SECS "$v";; 8) read -r -p 'Validation seconds: ' v; saman_aether_set VALIDATE_SECS "$v";; 0) return;; esac; done
}

saman_aether_wireguard_menu() { local c v; while :; do saman_aether_load; printf '\nWireGuard Settings\n1) Manual peer [%s]\n2) Keepalive [%s]\n3) No profile retry [%s]\n4) Scan mode\n5) Reconnect seconds [%s]\n0) Back\n' "${AETHER_PANEL_WG_PEER:-auto}" "${AETHER_PANEL_KEEPALIVE:-default}" "$AETHER_PANEL_NO_PROFILE_RETRY" "${AETHER_PANEL_RECONNECT_SECS:-default}"; read -r -p 'Choice: ' c; case "$c" in 1) read -r -p 'WG peer ip:port (empty clears): ' v; saman_aether_set WG_PEER "$v";; 2) read -r -p 'Keepalive seconds: ' v; saman_aether_set KEEPALIVE "$v";; 3) [ "$AETHER_PANEL_NO_PROFILE_RETRY" = 1 ] && saman_aether_set NO_PROFILE_RETRY 0 || saman_aether_set NO_PROFILE_RETRY 1;; 4) saman_aether_scan_menu;; 5) read -r -p 'Reconnect seconds: ' v; saman_aether_set RECONNECT_SECS "$v";; 0) return;; esac; done; }
saman_aether_gool_menu() { local c v; while :; do saman_aether_load; printf '\nGOOL Settings\n1) Automatic scan (clear manual peers)\n2) Outer peer [%s]\n3) Inner peer [%s]\n4) Pair [%s]\n5) Rescan\n0) Back\n' "${AETHER_PANEL_WIW_OUTER:-auto}" "${AETHER_PANEL_WIW_INNER:-auto}" "${AETHER_PANEL_WIW_PEERS:-auto}"; read -r -p 'Choice: ' c; case "$c" in 1) saman_aether_set WIW_OUTER ''; saman_aether_set WIW_INNER ''; saman_aether_set WIW_PEERS '';; 2) read -r -p 'Outer ip:port: ' v; saman_aether_set WIW_OUTER "$v";; 3) read -r -p 'Inner ip:port: ' v; saman_aether_set WIW_INNER "$v";; 4) read -r -p 'Outer,Inner: ' v; saman_aether_set WIW_PEERS "$v";; 5) saman_aether_set WIW_PEERS auto;; 0) return;; esac; done; }

saman_aether_advanced_menu() { local c v; while :; do saman_aether_load; printf '\nAdvanced Settings\n1) Performance [%s]\n2) TLS groups [%s]\n3) Log level [%s]\n4) No QUIC v2 [%s]\n5) Skip data check [%s]\n6) Keepalive [%s]\n0) Back\n' "${AETHER_PANEL_PERF:-auto}" "${AETHER_PANEL_TLS_GROUPS:-auto}" "${AETHER_PANEL_LOG_LEVEL:-default}" "$AETHER_PANEL_NO_QUIC_V2" "$AETHER_PANEL_NO_DATA_CHECK" "${AETHER_PANEL_KEEPALIVE:-default}"; read -r -p 'Choice: ' c; case "$c" in 1) read -r -p 'Performance low/medium/high: ' v; saman_aether_set PERF "$v";; 2) read -r -p 'TLS groups: ' v; saman_aether_set TLS_GROUPS "$v";; 3) read -r -p 'Log level: ' v; saman_aether_set LOG_LEVEL "$v";; 4) [ "$AETHER_PANEL_NO_QUIC_V2" = 1 ] && saman_aether_set NO_QUIC_V2 0 || saman_aether_set NO_QUIC_V2 1;; 5) [ "$AETHER_PANEL_NO_DATA_CHECK" = 1 ] && saman_aether_set NO_DATA_CHECK 0 || saman_aether_set NO_DATA_CHECK 1;; 6) read -r -p 'Keepalive seconds: ' v; saman_aether_set KEEPALIVE "$v";; 0) return;; esac; done; }

saman_aether_mark() { [ "$1" = "$2" ] && printf '[x]' || printf '[ ]'; }

saman_aether_render_tor_menu() {
    saman_aether_load
    printf '%s\n' 'TOR ROUTING' '-----------'
    printf '1) %s Off\n' "$(saman_aether_mark "$AETHER_PANEL_TOR_MODE" off)"
    printf '2) %s Inside selected transport\n' "$(saman_aether_mark "$AETHER_PANEL_TOR_MODE" inside)"
    printf '3) %s Reverse via MASQUE H2\n' "$(saman_aether_mark "$AETHER_PANEL_TOR_MODE" reverse)"
    printf '4) %s Tor only\n' "$(saman_aether_mark "$AETHER_PANEL_TOR_MODE" only)"
    printf '0) Back\n'
}

saman_aether_ack() {
    local ignored
    printf 'Press Enter to continue...' >&2
    IFS= read -r ignored || true
}

saman_aether_read_number() {
    local max="$1" c=''
    printf 'Choice: ' >&2
    IFS= read -r c || return 1
    if [ -z "$c" ]; then
        printf 'Please enter a number.\n'
        saman_aether_ack
        return 2
    fi
    if ! [[ "$c" =~ ^[0-9]+$ ]] || [ "$c" -gt "$max" ]; then
        printf 'Invalid choice: %s\n' "$c"
        saman_aether_ack
        return 2
    fi
    SAMAN_AETHER_CHOICE="$c"
}

saman_aether_tor_mode_menu() {
    local c rc
    while :; do
        s2_clear
        saman_aether_render_tor_menu
        if saman_aether_read_number 4; then rc=0; else rc=$?; fi; c="${SAMAN_AETHER_CHOICE:-}"
        [ "$rc" -eq 1 ] && return 0
        [ "$rc" -eq 2 ] && continue
        [ "$c" = 0 ] && return 0
        [ "${SAMAN_AETHER_CAP_HAS_TOR:-0}" -eq 1 ] || { printf 'Tor is unavailable in this Aether build.\n'; saman_aether_ack; continue; }
        case "$c" in
            1) saman_aether_set TOR_MODE off ;;
            2) saman_aether_set TOR_MODE inside ;;
            3)
                saman_aether_load
                AETHER_PANEL_TOR_MODE=reverse AETHER_PANEL_MODE=masque AETHER_PANEL_H2=1 AETHER_PANEL_H3=0
                saman_aether_mark_custom; saman_aether_save
                printf 'Saved: Tor Routing = reverse (MASQUE H2 forced)\n'
                ;;
            4) saman_aether_set TOR_MODE only ;;
        esac
        return 0
    done
}

saman_aether_tor_settings_menu() {
    local c rc v
    while :; do
        saman_aether_load; s2_clear
        printf '%s\n' 'TOR SETTINGS' '------------'
        printf '1) Bind [%s]\n2) Data dir [%s]\n3) Bridges [%s]\n4) Custom bridge [private]\n5) PT path [private]\n6) PT search dir [private]\n0) Back\n' \
            "$AETHER_PANEL_TOR_BIND" "${AETHER_PANEL_TOR_DIR:-default}" "$AETHER_PANEL_TOR_BRIDGES"
        if saman_aether_read_number 6; then rc=0; else rc=$?; fi; c="${SAMAN_AETHER_CHOICE:-}"
        [ "$rc" -eq 1 ] && return 0; [ "$rc" -eq 2 ] && continue; [ "$c" = 0 ] && return 0
        case "$c" in
            1) read -r -p 'Tor bind: ' v || return; [ -n "$v" ] && saman_aether_set TOR_BIND "$v" ;;
            2) read -r -p 'Tor data dir (empty clears): ' v || return; saman_aether_set TOR_DIR "$v" ;;
            3) saman_aether_select_menu TOR_BRIDGES 'Tor bridges' 'auto force disabled' ;;
            4) read -r -s -p 'Private bridge line(s; separated): ' v || return; printf '\n'; saman_aether_set TOR_BRIDGE "$v" ;;
            5) read -r -s -p 'Private [name=]PT path(s;): ' v || return; printf '\n'; saman_aether_set TOR_PT "$v" ;;
            6) read -r -s -p 'Private PT search dir(s;): ' v || return; printf '\n'; saman_aether_set TOR_PT_DIR "$v" ;;
        esac
        saman_aether_ack
    done
}

saman_aether_render_main_menu() {
    local actual='stopped' selected
    saman_aether_load
    selected="$AETHER_PANEL_MODE"
    [ "$selected" = masque ] && { [ "$AETHER_PANEL_H2" = 1 ] && selected='masque-h2' || selected='masque-h3'; }
    [ "$AETHER_PANEL_TOR_MODE" = off ] || selected="$selected + tor-$AETHER_PANEL_TOR_MODE"
    if declare -F s2_aether_pid_running >/dev/null 2>&1 && s2_aether_pid_running; then actual="$(s2_aether_mode)"; fi
    printf '%s\n' 'AETHER CONTROL PANEL' '--------------------' "Selected: $selected" "Running : $actual" '' \
        '1) Start selected' \
        '2) Stop' \
        '3) Restart selected' \
        '4) Status' \
        '5) Connection transports' \
        '6) Tor routing' \
        '7) Tor settings' \
        '8) Network/profiles/settings' \
        '9) Updates' \
        '10) Logs / help' \
        '0) Back' \
        '99) Exit Saman'
}

s2_aether_menu() {
    local c rc
    saman_aether_init || return 1
    saman_aether_detect_capabilities >/dev/null 2>&1 || true
    while :; do
        s2_clear; saman_aether_render_main_menu
        if saman_aether_read_number 99; then rc=0; else rc=$?; fi; c="${SAMAN_AETHER_CHOICE:-}"
        [ "$rc" -eq 1 ] && return 0; [ "$rc" -eq 2 ] && continue
        case "$c" in
            1) saman_aether_load; s2_aether_start "$AETHER_PANEL_MODE"; saman_aether_ack ;;
            2) s2_aether_stop; saman_aether_ack ;;
            3) saman_aether_load; s2_aether_restart "$AETHER_PANEL_MODE"; saman_aether_ack ;;
            4) s2_aether_status; saman_aether_ack ;;
            5) saman_aether_mode_menu ;;
            6) saman_aether_tor_mode_menu ;;
            7) saman_aether_tor_settings_menu ;;
            8) saman_aether_network_menu ;;
            9) saman_aether_update; saman_aether_ack ;;
            10) saman_aether_show_config; printf '\nUse: saman aether logs\n'; saman_aether_ack ;;
            0) return 0 ;;
            99) S2_EXIT_REQUESTED=1; return 0 ;;
            *) printf 'Invalid choice: %s\n' "$c"; saman_aether_ack ;;
        esac
    done
}

saman_aether_panel_command() {
    saman_aether_init || return 1
    case "${1:-menu}" in
        menu) s2_aether_menu;; config|show) saman_aether_show_config;; capabilities|detect) saman_aether_capabilities;; preset) [ -n "${2:-}" ] || return 2; saman_aether_apply_preset "$2";; reset) saman_aether_reset "${2:-}";; update) saman_aether_update "${2:-}";; test) saman_aether_test_current;; validate) saman_aether_validate_settings; printf 'OK: settings validated against installed Aether capabilities.\n';; args) saman_aether_load; saman_aether_detect_capabilities; saman_aether_build_args;; diagnostics) saman_aether_diagnostics_panel;; *) printf 'Usage: saman aether panel|config|capabilities|preset NAME|reset [--yes]|update [--check]|test|validate|args|diagnostics\n' >&2; return 2;; esac
}
