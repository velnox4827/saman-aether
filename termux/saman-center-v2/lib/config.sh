#!/data/data/com.termux/files/usr/bin/bash

S2_CONFIG_FILE="${S2_CONFIG_FILE:-$SAMAN2_CONFIG/settings.conf}"
S2_CONFIG_ERRORS=()

s2_config_defaults() {
    AETHER_SOCKS_PORT=1819
    AETHER_HTTP_PORT=1820
    SHARE_PORT=8080
    SHARE_MAX_BYTES=4294967296
    SHARE_MIN_FREE_BYTES=536870912
    SHARE_READ_TIMEOUT=30
    SHARE_MAX_CONNECTIONS=32
    NETWORK_TIMEOUT=8
    TERMUX_API_TIMEOUT=4
    DASHBOARD_CACHE_TTL=30
    LOG_MAX_CURRENT_BYTES=5242880
    LOG_MAX_PREVIOUS_BYTES=1048576
    AETHER_READY_TIMEOUT_SECS=120
    AETHER_PORT_HANDOFF_TIMEOUT_SECS=3
    AETHER_LOG_GUARD_INTERVAL_SECS=60
    AETHER_SMART_WG_MAX_RTT_MS=650
    AETHER_SMART_MASQUE_H3_MAX_VERIFY_MS=1800
    AETHER_SMART_MASQUE_H2_MAX_VERIFY_MS=2500
    AETHER_SMART_SHORT_LIVED_SECS=20
}

s2_config_assign() {
    local key="$1" value="$2"
    case "$key" in
        AETHER_SOCKS_PORT|AETHER_HTTP_PORT|SHARE_PORT)
            if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -ge 1024 ] && [ "$value" -le 65535 ]; then
                printf -v "$key" '%s' "$value"
            else
                S2_CONFIG_ERRORS+=("$key must be an integer from 1024 to 65535")
            fi
            ;;
        SHARE_MAX_BYTES|SHARE_MIN_FREE_BYTES|LOG_MAX_CURRENT_BYTES|LOG_MAX_PREVIOUS_BYTES)
            if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -ge 65536 ]; then
                printf -v "$key" '%s' "$value"
            else
                S2_CONFIG_ERRORS+=("$key must be an integer of at least 65536")
            fi
            ;;
        NETWORK_TIMEOUT|TERMUX_API_TIMEOUT|SHARE_READ_TIMEOUT|AETHER_PORT_HANDOFF_TIMEOUT_SECS)
            if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -ge 1 ] && [ "$value" -le 60 ]; then
                printf -v "$key" '%s' "$value"
            else
                S2_CONFIG_ERRORS+=("$key must be an integer from 1 to 60")
            fi
            ;;
        SHARE_MAX_CONNECTIONS)
            if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -ge 1 ] && [ "$value" -le 128 ]; then
                printf -v "$key" '%s' "$value"
            else
                S2_CONFIG_ERRORS+=("$key must be an integer from 1 to 128")
            fi
            ;;
        AETHER_READY_TIMEOUT_SECS|AETHER_LOG_GUARD_INTERVAL_SECS)
            if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -ge 10 ] && [ "$value" -le 600 ]; then
                printf -v "$key" '%s' "$value"
            else
                S2_CONFIG_ERRORS+=("$key must be an integer from 10 to 600")
            fi
            ;;
        AETHER_SMART_SHORT_LIVED_SECS)
            if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -ge 1 ] && [ "$value" -le 300 ]; then
                printf -v "$key" '%s' "$value"
            else
                S2_CONFIG_ERRORS+=("$key must be an integer from 1 to 300")
            fi
            ;;
        AETHER_SMART_WG_MAX_RTT_MS|AETHER_SMART_MASQUE_H3_MAX_VERIFY_MS|AETHER_SMART_MASQUE_H2_MAX_VERIFY_MS)
            if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -ge 100 ] && [ "$value" -le 10000 ]; then
                printf -v "$key" '%s' "$value"
            else
                S2_CONFIG_ERRORS+=("$key must be an integer from 100 to 10000")
            fi
            ;;
        DASHBOARD_CACHE_TTL)
            if [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -ge 5 ] && [ "$value" -le 3600 ]; then
                printf -v "$key" '%s' "$value"
            else
                S2_CONFIG_ERRORS+=("$key must be an integer from 5 to 3600")
            fi
            ;;
        ''|'#'*) ;;
        *) ;; # Unknown keys are preserved on disk but never evaluated or exposed.
    esac
}

s2_config_cross_validate() {
    if [ "$AETHER_SOCKS_PORT" = "$AETHER_HTTP_PORT" ] ||
       [ "$AETHER_SOCKS_PORT" = "$SHARE_PORT" ] ||
       [ "$AETHER_HTTP_PORT" = "$SHARE_PORT" ]; then
        S2_CONFIG_ERRORS+=("AETHER_SOCKS_PORT, AETHER_HTTP_PORT, and SHARE_PORT must be different")
    fi
}

s2_config_load() {
    local line key value
    s2_config_defaults
    S2_CONFIG_ERRORS=()
    [ -r "$S2_CONFIG_FILE" ] || return 0
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in ''|'#'*) continue ;; esac
        if [[ "$line" != *=* ]]; then
            S2_CONFIG_ERRORS+=("malformed line (expected KEY=VALUE)")
            continue
        fi
        key="${line%%=*}"; value="${line#*=}"
        if [[ ! "$key" =~ ^[A-Z][A-Z0-9_]*$ ]] || [[ "$value" == *$'\n'* ]]; then
            S2_CONFIG_ERRORS+=("malformed key or value")
            continue
        fi
        s2_config_assign "$key" "$value"
    done < "$S2_CONFIG_FILE"
    s2_config_cross_validate
}

s2_config_show() {
    printf '%s\n' \
        "AETHER_SOCKS_PORT=$AETHER_SOCKS_PORT" \
        "AETHER_HTTP_PORT=$AETHER_HTTP_PORT" \
        "SHARE_PORT=$SHARE_PORT" \
        "SHARE_MAX_BYTES=$SHARE_MAX_BYTES" \
        "SHARE_MIN_FREE_BYTES=$SHARE_MIN_FREE_BYTES" \
        "SHARE_READ_TIMEOUT=$SHARE_READ_TIMEOUT" \
        "SHARE_MAX_CONNECTIONS=$SHARE_MAX_CONNECTIONS" \
        "NETWORK_TIMEOUT=$NETWORK_TIMEOUT" \
        "TERMUX_API_TIMEOUT=$TERMUX_API_TIMEOUT" \
        "DASHBOARD_CACHE_TTL=$DASHBOARD_CACHE_TTL" \
        "LOG_MAX_CURRENT_BYTES=$LOG_MAX_CURRENT_BYTES" \
        "LOG_MAX_PREVIOUS_BYTES=$LOG_MAX_PREVIOUS_BYTES" \
        "AETHER_READY_TIMEOUT_SECS=$AETHER_READY_TIMEOUT_SECS" \
        "AETHER_PORT_HANDOFF_TIMEOUT_SECS=$AETHER_PORT_HANDOFF_TIMEOUT_SECS" \
        "AETHER_LOG_GUARD_INTERVAL_SECS=$AETHER_LOG_GUARD_INTERVAL_SECS" \
        "AETHER_SMART_WG_MAX_RTT_MS=$AETHER_SMART_WG_MAX_RTT_MS" \
        "AETHER_SMART_MASQUE_H3_MAX_VERIFY_MS=$AETHER_SMART_MASQUE_H3_MAX_VERIFY_MS" \
        "AETHER_SMART_MASQUE_H2_MAX_VERIFY_MS=$AETHER_SMART_MASQUE_H2_MAX_VERIFY_MS" \
        "AETHER_SMART_SHORT_LIVED_SECS=$AETHER_SMART_SHORT_LIVED_SECS"
}

s2_config_known_key() {
    case "$1" in
        AETHER_SOCKS_PORT|AETHER_HTTP_PORT|SHARE_PORT|SHARE_MAX_BYTES|SHARE_MIN_FREE_BYTES|SHARE_READ_TIMEOUT|SHARE_MAX_CONNECTIONS|NETWORK_TIMEOUT|TERMUX_API_TIMEOUT|DASHBOARD_CACHE_TTL|LOG_MAX_CURRENT_BYTES|LOG_MAX_PREVIOUS_BYTES|AETHER_READY_TIMEOUT_SECS|AETHER_PORT_HANDOFF_TIMEOUT_SECS|AETHER_LOG_GUARD_INTERVAL_SECS|AETHER_SMART_WG_MAX_RTT_MS|AETHER_SMART_MASQUE_H3_MAX_VERIFY_MS|AETHER_SMART_MASQUE_H2_MAX_VERIFY_MS|AETHER_SMART_SHORT_LIVED_SECS) return 0 ;;
        *) return 1 ;;
    esac
}

s2_config_set() {
    local key="${1:-}" value="${2:-}" line line_key tmp seen=0
    [ "$#" -eq 2 ] || { printf 'ERROR: usage: saman config set KEY VALUE\n' >&2; return 2; }
    s2_config_known_key "$key" || { printf 'ERROR: unsupported configuration key: %s\n' "$key" >&2; return 2; }
    S2_CONFIG_ERRORS=()
    s2_config_assign "$key" "$value"
    s2_config_cross_validate
    if [ "${#S2_CONFIG_ERRORS[@]}" -gt 0 ]; then s2_config_validate >&2; return 2; fi
    s2_init_runtime || return 1
    tmp="$(mktemp "$SAMAN2_CONFIG/settings.conf.tmp.XXXXXX")" || return 1
    if [ -r "$S2_CONFIG_FILE" ]; then
        while IFS= read -r line || [ -n "$line" ]; do
            line_key="${line%%=*}"
            if [ "$line_key" = "$key" ]; then
                if [ "$seen" -eq 0 ]; then printf '%s=%s\n' "$key" "$value" >> "$tmp"; seen=1; fi
            else
                printf '%s\n' "$line" >> "$tmp"
            fi
        done < "$S2_CONFIG_FILE"
    fi
    [ "$seen" -eq 1 ] || printf '%s=%s\n' "$key" "$value" >> "$tmp"
    if chmod 0600 "$tmp" && mv -f "$tmp" "$S2_CONFIG_FILE"; then
        s2_config_load
        printf 'OK: %s updated in %s\n' "$key" "$S2_CONFIG_FILE"
        return 0
    fi
    rm -f -- "$tmp"
    printf 'ERROR: could not update %s\n' "$S2_CONFIG_FILE" >&2
    return 1
}

s2_config_validate() {
    local error
    if [ "${#S2_CONFIG_ERRORS[@]}" -eq 0 ]; then
        printf 'OK: configuration is valid (%s)\n' "$S2_CONFIG_FILE"
        return 0
    fi
    for error in "${S2_CONFIG_ERRORS[@]}"; do printf 'ERROR: %s\n' "$error"; done
    printf 'ACTION REQUIRED: correct %s; invalid values are currently using safe defaults.\n' "$S2_CONFIG_FILE"
    return 1
}
