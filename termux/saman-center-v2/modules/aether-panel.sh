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
            MODE|PRESET|SCAN|NOIZE|IP|H2|H3|ECH|FRAGMENT|FRAGMENT_SIZE|FRAGMENT_DELAY|BIND|HTTP|UPSTREAM|DNS|ROUTE_BLOCK|ROUTE_DIRECT|ROUTES|QUICK|RECONNECT_SECS|STARTUP_SECS|VALIDATE_SECS|KEEPALIVE|PEER|WG_PEER|WIW_OUTER|WIW_INNER|WIW_PEERS|MIM_OUTER|MIM_INNER|MIM_PEERS|PERF|TLS_GROUPS|LOG_LEVEL|NO_QUIC_V2|NO_DATA_CHECK|NO_PROFILE_RETRY|H2_PEER)
                saman_aether_assign "$k" "$v" || true ;;
        esac
    done < "$SAMAN_AETHER_SETTINGS"
}

saman_aether_save() {
    local tmp k var
    tmp="$(mktemp "$SAMAN_AETHER_SETTINGS.tmp.XXXXXX")" || return 1
    {
        printf '# Saman Aether preferences; official Aether owns identity/config files.\n'
        for k in MODE PRESET SCAN NOIZE IP H2 H3 ECH FRAGMENT FRAGMENT_SIZE FRAGMENT_DELAY BIND HTTP UPSTREAM DNS ROUTE_BLOCK ROUTE_DIRECT ROUTES QUICK RECONNECT_SECS STARTUP_SECS VALIDATE_SECS KEEPALIVE PEER WG_PEER WIW_OUTER WIW_INNER WIW_PEERS MIM_OUTER MIM_INNER MIM_PEERS PERF TLS_GROUPS LOG_LEVEL NO_QUIC_V2 NO_DATA_CHECK NO_PROFILE_RETRY H2_PEER; do
            var="$(saman_aether_key_var "$k")"; printf '%s=%s\n' "$k" "${!var-}"
        done
    } > "$tmp" && chmod 0600 "$tmp" && mv -f "$tmp" "$SAMAN_AETHER_SETTINGS" || { rm -f "$tmp"; return 1; }
}

saman_aether_set() {
    local k="$1" v="$2"
    saman_aether_assign "$k" "$v" || { printf 'ERROR: invalid Aether setting %s=%s\n' "$k" "$v" >&2; return 2; }
    [ "$k" = PRESET ] || saman_aether_mark_custom "$k"
    saman_aether_save
}

saman_aether_mark_custom() {
    [ "${AETHER_PANEL_PRESET:-}" = custom ] || AETHER_PANEL_PRESET=custom
}

saman_aether_flag_supported() {
    local flag="$1"
    [ -r "${SAMAN_AETHER_HELP_FILE:-}" ] && grep -Fq -- "$flag" "$SAMAN_AETHER_HELP_FILE"
}

saman_aether_detect_capabilities() {
    local bin version tmp
    bin="$(saman_aether_bin 2>/dev/null || true)"
    [ -n "$bin" ] || { printf 'ERROR: official Aether is not executable.\n' >&2; return 127; }
    tmp="$SAMAN_AETHER_CONFIG_DIR/help.txt.tmp.$$"
    "$bin" --help > "$tmp" 2>&1 || true
    chmod 0600 "$tmp" && mv -f "$tmp" "$SAMAN_AETHER_CONFIG_DIR/help.txt" || return 1
    SAMAN_AETHER_HELP_FILE="$SAMAN_AETHER_CONFIG_DIR/help.txt"
    version="$($bin --version 2>/dev/null || true)"
    SAMAN_AETHER_CAP_VERSION="$(printf '%s\n' "$version" | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+' | head -n1 || printf unknown)"
    SAMAN_AETHER_CAP_PATH="$bin"
    for p in MASQUE WG GOOL MIM H2 ECH FRAGMENT DNS ROUTING PERF TOR IRONCLAD; do
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
    saman_aether_flag_supported --tor && SAMAN_AETHER_CAP_HAS_TOR=1
    SAMAN_AETHER_CAP_SCANS=""
    for x in turbo balanced thorough stealth ironclad; do grep -Eq "(^|[|[:space:]])$x([|[:space:]]|$)" "$SAMAN_AETHER_HELP_FILE" && SAMAN_AETHER_CAP_SCANS+="$x "; done
    SAMAN_AETHER_CAP_NOIZE=""
    for x in off light firewall balanced gfw aggressive; do grep -Eq "(^|[|[:space:]])$x([|[:space:]]|$)" "$SAMAN_AETHER_HELP_FILE" && SAMAN_AETHER_CAP_NOIZE+="$x "; done
    [[ " $SAMAN_AETHER_CAP_SCANS " == *" ironclad "* ]] && SAMAN_AETHER_CAP_HAS_IRONCLAD=1
    {
        printf 'VERSION=%s\nPATH=%s\n' "$SAMAN_AETHER_CAP_VERSION" "$SAMAN_AETHER_CAP_PATH"
        printf 'SCANS=%s\nNOIZE=%s\n' "$SAMAN_AETHER_CAP_SCANS" "$SAMAN_AETHER_CAP_NOIZE"
        for p in MASQUE WG GOOL MIM H2 ECH FRAGMENT DNS ROUTING PERF TOR IRONCLAD; do eval "printf '%s=%s\\n' HAS_$p \"\${SAMAN_AETHER_CAP_HAS_$p}\""; done
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

saman_aether_build_args_array() {
    local mode="$1"; shift || true
    local ip="$AETHER_PANEL_IP"
    SAMAN_AETHER_ARGS=(--bind "$AETHER_PANEL_BIND" --http-proxy "$AETHER_PANEL_HTTP")
    case "$mode" in
        masque) SAMAN_AETHER_ARGS+=(--masque);;
        wg) SAMAN_AETHER_ARGS+=(--wg);;
        gool) SAMAN_AETHER_ARGS+=(--gool);;
        mim) SAMAN_AETHER_ARGS+=(--mim);;
    esac
    case "$ip" in 4) SAMAN_AETHER_ARGS+=(-4);; 6) SAMAN_AETHER_ARGS+=(-6);; both) SAMAN_AETHER_ARGS+=(--dual);; esac
    [ -n "$AETHER_PANEL_SCAN" ] && saman_aether_flag_supported --scan && SAMAN_AETHER_ARGS+=(--scan "$AETHER_PANEL_SCAN")
    [ -n "$AETHER_PANEL_NOIZE" ] && saman_aether_flag_supported --noize && SAMAN_AETHER_ARGS+=(--noize "$AETHER_PANEL_NOIZE")
    [ "$AETHER_PANEL_QUICK" = 1 ] && saman_aether_flag_supported --quick-reconnect && SAMAN_AETHER_ARGS+=(--quick-reconnect)
    [ "$mode" = masque ] && [ "$AETHER_PANEL_H2" = 1 ] && saman_aether_flag_supported --h2 && SAMAN_AETHER_ARGS+=(--h2)
    [ "$mode" = mim ] && [ "$AETHER_PANEL_H2" = 1 ] && saman_aether_flag_supported --h2 && SAMAN_AETHER_ARGS+=(--h2)
    [ "$AETHER_PANEL_H3" = 1 ] && [ "$mode" = masque ] && saman_aether_flag_supported --h3 && SAMAN_AETHER_ARGS+=(--h3)
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

saman_aether_update() {
    local bin arch api json tag asset checksum_url asset_url tmp new old backup expected actual
    bin="$(saman_aether_bin)" || return 127
    arch="$(uname -m)"; case "$arch" in aarch64|arm64) arch=arm64;; armv7l|armv7|arm) arch=armv7;; x86_64|amd64) arch=x86_64;; *) printf 'ERROR: unsupported architecture: %s\n' "$arch" >&2; return 2;; esac
    command -v curl >/dev/null 2>&1 || { printf 'ERROR: curl is required.\n' >&2; return 127; }
    command -v jq >/dev/null 2>&1 || { printf 'ERROR: jq is required.\n' >&2; return 127; }
    api="https://api.github.com/repos/$SAMAN_AETHER_UPSTREAM/releases/latest"
    json="$(curl --proto '=https' --tlsv1.2 -fsSL --connect-timeout 15 --max-time 60 -H 'Accept: application/vnd.github+json' "$api")" || return 1
    tag="$(printf '%s' "$json" | jq -r 'select(.draft==false and .prerelease==false) | .tag_name')"
    asset="aether-android-$arch.tar.gz"
    asset_url="$(printf '%s' "$json" | jq -r --arg n "$asset" '.assets[]|select(.name==$n)|.browser_download_url')"
    checksum_url="$(printf '%s' "$json" | jq -r --arg n "$asset.sha256" '.assets[]|select(.name==$n)|.browser_download_url')"
    [ -n "$tag" ] && [ "$tag" != null ] && [ -n "$asset_url" ] && [ -n "$checksum_url" ] || { printf 'ERROR: official release lacks %s or checksum.\n' "$asset" >&2; return 1; }
    printf 'Installed: %s\nLatest: %s (%s)\n' "$($bin --version 2>/dev/null | head -n1)" "$tag" "$arch"
    [ "${1:-}" = --check ] && return 0
    tmp="$(mktemp -d "$SAMAN_AETHER_CONFIG_DIR/update.XXXXXX")" || return 1

    curl --proto '=https' --tlsv1.2 -fL --connect-timeout 15 --max-time 180 "$asset_url" -o "$tmp/$asset"
    curl --proto '=https' --tlsv1.2 -fL --connect-timeout 15 --max-time 60 "$checksum_url" -o "$tmp/$asset.sha256"
    (cd "$tmp" && sha256sum -c "$asset.sha256") || { printf 'ERROR: checksum mismatch.\n' >&2; return 1; }
    mkdir -p "$tmp/extract"; tar -xzf "$tmp/$asset" -C "$tmp/extract" || return 1
    new="$tmp/extract/aether"; [ -x "$new" ] || { printf 'ERROR: official archive missing aether.\n' >&2; return 1; }
    "$new" --version >/dev/null 2>&1 && "$new" --help >/dev/null 2>&1 || { printf 'ERROR: staged binary validation failed.\n' >&2; rm -rf "$tmp"; return 1; }
    old="$bin"; backup="$(saman_aether_backup_settings "binary-$(date +%Y%m%d-%H%M%S)")" || return 1
    cp -p "$old" "$backup/aether" || return 1
    install -m 0700 "$new" "$old.update.$$" && mv -f "$old.update.$$" "$old" || return 1
    if ! "$old" --version >/dev/null 2>&1 || ! "$old" --help >/dev/null 2>&1; then cp -p "$backup/aether" "$old"; printf 'ERROR: live validation failed; rolled back.\n' >&2; return 1; fi
    saman_aether_detect_capabilities >/dev/null 2>&1 || true
    saman_aether_validate_settings >/dev/null 2>&1 || true
    printf 'OK: official Aether updated to %s; settings validated.\n' "$($old --version 2>/dev/null || true)"
    rm -rf "$tmp"
}

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
    local c i=1 option mode
    saman_aether_detect_capabilities >/dev/null 2>&1 || true
    while :; do
        saman_aether_load
        printf '\nConnection Mode [%s]\n' "$AETHER_PANEL_MODE"
        options=()
        [ "${SAMAN_AETHER_CAP_HAS_MASQUE:-0}" -eq 1 ] && options+=("MASQUE HTTP/3")
        [ "${SAMAN_AETHER_CAP_HAS_H2:-0}" -eq 1 ] && options+=("MASQUE HTTP/2")
        [ "${SAMAN_AETHER_CAP_HAS_WG:-0}" -eq 1 ] && options+=("WireGuard")
        [ "${SAMAN_AETHER_CAP_HAS_GOOL:-0}" -eq 1 ] && options+=("GOOL / WARP-in-WARP")
        [ "${SAMAN_AETHER_CAP_HAS_MIM:-0}" -eq 1 ] && options+=("MASQUE-in-MASQUE")
        i=1; for option in "${options[@]}"; do printf '%s) %s\n' "$i" "$option"; i=$((i+1)); done
        printf '0) Back\n'; read -r -p 'Choice: ' c
        [ "$c" = 0 ] && return
        case "$c" in
            1) mode=masque; AETHER_PANEL_H2=0; AETHER_PANEL_H3=1;;
            2) mode=masque; AETHER_PANEL_H2=1; AETHER_PANEL_H3=0;;
            3) mode=wg;; 4) mode=gool;; 5) mode=mim;; *) continue;;
        esac
        saman_aether_set MODE "$mode"; saman_aether_save
    done
}

saman_aether_select_menu() {
    local key="$1" title="$2" values="$3" c i v
    while :; do
        printf '\n%s\n' "$title"; i=1; for v in $values; do printf '%s) %s\n' "$i" "$v"; i=$((i+1)); done; printf '0) Back\n'
        read -r -p 'Choice: ' c; [ "$c" = 0 ] && return
        i=1; for v in $values; do if [ "$i" = "$c" ]; then saman_aether_set "$key" "$v"; return; fi; i=$((i+1)); done
    done
}

saman_aether_preset_menu() { saman_aether_select_menu PRESET 'Presets (official Aether arguments only)' 'upstream-default balanced fast stable restricted stealth compatibility custom'; }
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

s2_aether_menu() {
    saman_aether_init; saman_aether_load; saman_aether_detect_capabilities >/dev/null 2>&1 || true
    local c
    while :; do saman_aether_load; printf '\nAETHER CONTROL PANEL\n--------------------\nMode [%s]  Preset [%s]  Scan [%s]  Noize [%s]\n1) Connect / Start\n2) Connection Mode\n3) Preset\n4) Scan Mode\n5) Obfuscation / Noize\n6) Network / IP\n7) MASQUE Settings\n8) WireGuard Settings\n9) GOOL Settings\n10) DNS / Proxy / Routing\n11) Advanced Settings\n12) Show Current Configuration\n13) Test Current Configuration\n14) Update Aether\n15) Reset Aether Settings\n16) Version / Diagnostics\n0) Back\n' "$AETHER_PANEL_MODE" "$AETHER_PANEL_PRESET" "${AETHER_PANEL_SCAN:-default}" "${AETHER_PANEL_NOIZE:-default}"; read -r -p 'Choice: ' c; case "$c" in 1) s2_aether_start "$AETHER_PANEL_MODE";; 2) saman_aether_mode_menu;; 3) saman_aether_preset_menu; saman_aether_load; saman_aether_apply_preset "$AETHER_PANEL_PRESET";; 4) saman_aether_scan_menu;; 5) saman_aether_noize_menu;; 6|10) saman_aether_network_menu;; 7) saman_aether_masque_menu;; 8) saman_aether_wireguard_menu;; 9) saman_aether_gool_menu;; 11) saman_aether_advanced_menu;; 12) saman_aether_show_config; s2_pause;; 13) saman_aether_test_current; s2_pause;; 14) saman_aether_update; s2_pause;; 15) saman_aether_reset; s2_pause;; 16) saman_aether_diagnostics_panel; s2_pause;; 0) return;; esac; done
}

saman_aether_panel_command() {
    saman_aether_init || return 1
    case "${1:-menu}" in
        menu) s2_aether_menu;; config|show) saman_aether_show_config;; capabilities|detect) saman_aether_capabilities;; preset) [ -n "${2:-}" ] || return 2; saman_aether_apply_preset "$2";; reset) saman_aether_reset "${2:-}";; update) saman_aether_update "${2:-}";; test) saman_aether_test_current;; validate) saman_aether_validate_settings; printf 'OK: settings validated against installed Aether capabilities.\n';; args) saman_aether_load; saman_aether_detect_capabilities; saman_aether_build_args;; diagnostics) saman_aether_diagnostics_panel;; *) printf 'Usage: saman aether panel|config|capabilities|preset NAME|reset [--yes]|update [--check]|test|validate|args|diagnostics\n' >&2; return 2;; esac
}
