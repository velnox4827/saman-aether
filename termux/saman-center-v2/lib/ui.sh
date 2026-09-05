#!/data/data/com.termux/files/usr/bin/bash

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    S2_RESET='\033[0m'
    S2_BOLD='\033[1m'
    S2_DIM='\033[2m'
    S2_CYAN='\033[36m'
    S2_GREEN='\033[32m'
    S2_YELLOW='\033[33m'
    S2_RED='\033[31m'
    S2_BLUE='\033[34m'
else
    # shellcheck disable=SC2034 # palette is shared by optional modules
    S2_RESET='' S2_BOLD='' S2_DIM='' S2_CYAN='' S2_GREEN='' S2_YELLOW='' S2_RED='' S2_BLUE=''
fi

s2_clear() { clear 2>/dev/null || printf '\033[2J\033[H'; }
s2_ok() { printf "%b✓%b %s\n" "$S2_GREEN" "$S2_RESET" "$*"; }
s2_warn() { printf "%b!%b %s\n" "$S2_YELLOW" "$S2_RESET" "$*"; }
s2_err() { printf "%b✗%b %s\n" "$S2_RED" "$S2_RESET" "$*"; }

s2_read_choice() {
    local prompt="${1:-Select: }" x=''
    while true; do
        printf '%s' "$prompt" >&2
        IFS= read -r x || return 1
        case "$x" in
            $'\033'*|'^['*)
                # Ignore accidental Termux extra-key escape sequences (PgUp/PgDn/arrows).
                continue
                ;;
        esac
        printf '%s\n' "$x"
        return 0
    done
}

s2_terminal_cols() {
    local c="${COLUMNS:-}"
    if [ -z "$c" ] || ! [[ "$c" =~ ^[0-9]+$ ]]; then
        c="$(tput cols 2>/dev/null || true)"
    fi
    if [ -z "$c" ] || ! [[ "$c" =~ ^[0-9]+$ ]]; then
        c=44
    fi
    [ "$c" -lt 30 ] && c=30
    [ "$c" -gt 72 ] && c=72
    printf '%s\n' "$c"
}

s2_repeat() {
    local ch="$1" n="$2" pad
    [ "$n" -le 0 ] 2>/dev/null && return 0
    printf -v pad '%*s' "$n" ''
    printf '%s' "${pad// /$ch}"
}

s2_rule() {
    local cols
    cols="$(s2_terminal_cols)"
    s2_repeat '-' "$cols"
    echo
}

s2_title() {
    printf "%b%b%s%b\n" "$S2_BOLD" "$S2_CYAN" "$1" "$S2_RESET"
    s2_rule
}

s2_section() {
    printf "%b%s%b\n" "$S2_DIM" "$1" "$S2_RESET"
}

s2_cache_read() {
    local f="$1" fallback="${2:-...}"
    [ -s "$SAMAN2_CACHE/$f" ] && cat "$SAMAN2_CACHE/$f" || printf '%s\n' "$fallback"
}

s2_sys_ifaces() {
    local d n
    if [ -d /sys/class/net ]; then
        for d in /sys/class/net/*; do
            [ -e "$d" ] || continue
            n="${d##*/}"
            [ "$n" = lo ] && continue
            printf '%s\n' "$n"
        done
    fi
}

s2_ip_ifaces() {
    ip -o link 2>/dev/null | awk -F': ' '{n=$2; sub(/@.*/,"",n); if(n!="lo") print n}' 2>/dev/null || true
}

s2_proc_ifaces() {
    [ -r /proc/net/dev ] || return 0
    awk -F: 'NR>2 {gsub(/[[:space:]]/,"",$1); if($1!="" && $1!="lo") print $1}' /proc/net/dev 2>/dev/null || true
}

s2_all_ifaces() {
    { s2_ip_ifaces; s2_sys_ifaces; s2_proc_ifaces; } | awk 'NF && !seen[$0]++'
}

s2_iface_is_up() {
    local iface="$1" state=''
    if [ -r "/sys/class/net/$iface/operstate" ]; then
        state="$(cat "/sys/class/net/$iface/operstate" 2>/dev/null || true)"
        case "$state" in up|unknown) return 0 ;; esac
    fi
    ip link show dev "$iface" 2>/dev/null | grep -qE 'state (UP|UNKNOWN)|<[^>]*UP[^>]*>'
}

s2_default_iface() {
    local iface=''
    iface="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="dev"){print $(i+1); exit}}')"
    [ -z "$iface" ] && iface="$(ip route 2>/dev/null | awk '/^default/{for(i=1;i<=NF;i++) if($i=="dev"){print $(i+1); exit}}')"

    if [ -z "$iface" ]; then
        while IFS= read -r iface; do
            case "$iface" in
                wlan*|wifi*|rmnet*|ccmni*|pdp*|wwan*|cell*|v4-rmnet*|v6-rmnet*)
                    if s2_iface_is_up "$iface"; then printf '%s\n' "$iface"; return 0; fi
                    ;;
            esac
        done < <(s2_all_ifaces)
        iface=''
    fi
    printf '%s\n' "$iface"
}

s2_physical_iface() {
    local iface
    while IFS= read -r iface; do
        case "$iface" in
            wlan*|wifi*|rmnet*|ccmni*|pdp*|wwan*|cell*|v4-rmnet*|v6-rmnet*)
                if s2_iface_is_up "$iface"; then printf '%s\n' "$iface"; return 0; fi
                ;;
        esac
    done < <(s2_all_ifaces)
    return 1
}

s2_network_label() {
    local ssid="$1" iface="$2" physical=''
    case "$ssid" in
        ''|'null'|'<unknown ssid>'|'unknown ssid'|'Unknown'|'UNKNOWN'|'0x') ssid='' ;;
    esac

    if [ -n "$ssid" ]; then
        printf 'Wi-Fi: %s\n' "$ssid"
        return 0
    fi

    case "$iface" in
        tun*|tap*|wg*|vpn*|ppp*|ipsec*)
            physical="$(s2_physical_iface 2>/dev/null || true)"
            [ -n "$physical" ] && iface="$physical"
            ;;
    esac

    if [ -z "$iface" ]; then
        physical="$(s2_physical_iface 2>/dev/null || true)"
        [ -n "$physical" ] && iface="$physical"
    fi

    case "$iface" in
        wlan*|wifi*) printf 'Wi-Fi\n' ;;
        rmnet*|ccmni*|pdp*|wwan*|cell*|v4-rmnet*|v6-rmnet*) printf 'Cellular\n' ;;
        tun*|tap*|wg*|vpn*|ppp*|ipsec*) printf 'Network + VPN\n' ;;
        '')
            # On recent Android versions Termux may not be allowed to enumerate
            # interfaces. With no Wi-Fi SSID but working internet, "Cellular"
            # is the useful phone-facing label; debug still marks it as inferred.
            if [ -s "$SAMAN2_CACHE/ip" ]; then printf 'Cellular\n'; else printf 'Network\n'; fi
            ;;
        *) printf '%s\n' "$iface" ;;
    esac
}

s2_vpn_iface() {
    local iface
    while IFS= read -r iface; do
        case "$iface" in
            tun[0-9]*|tun|tap[0-9]*|tap|wg[0-9]*|wg|vpn[0-9]*|vpn|ipsec[0-9]*|ipsec|ppp[0-9]*)
                printf '%s\n' "$iface"
                return 0
                ;;
        esac
    done < <(s2_all_ifaces)
    return 1
}

s2_android_vpn_dumpsys() {
    s2_have dumpsys || return 1
    local out
    out="$(timeout 2 dumpsys connectivity 2>/dev/null || true)"
    [ -n "$out" ] || return 1
    printf '%s\n' "$out" | grep -Eqi 'TRANSPORT_VPN|type.?VPN|VPN.*CONNECTED|VpnNetworkAgent|VPN.*VALIDATED'
}

s2_net_iface_visibility() {
    # Prints VISIBLE when Termux can enumerate at least one non-loopback iface.
    # Prints HIDDEN when Android's app sandbox exposes none.
    if s2_all_ifaces | grep -q .; then
        printf 'VISIBLE\n'
    else
        printf 'HIDDEN\n'
    fi
}

s2_android_connectivity_visibility() {
    s2_have dumpsys || return 1
    local out
    out="$(timeout 2 dumpsys connectivity 2>/dev/null || true)"
    [ -n "$out" ]
}

s2_vpn_state() {
    local iface='' visibility=''
    if iface="$(s2_vpn_iface 2>/dev/null)" && [ -n "$iface" ]; then
        printf 'ON\n'
        return 0
    fi
    if s2_android_vpn_dumpsys; then
        printf 'ON\n'
        return 0
    fi

    visibility="$(s2_net_iface_visibility)"
    # If Android hides interfaces and dumpsys is unavailable, OFF cannot be
    # established. This is exactly what Android 14/15 often does to Termux.
    if [ "$visibility" = HIDDEN ] && ! s2_android_connectivity_visibility; then
        printf '?\n'
        return 0
    fi

    printf 'OFF\n'
}

s2_vpn_quick() { s2_vpn_state; }

s2_dashboard_refresh_due() {
    local marker="$SAMAN2_CACHE/.last-refresh" now mtime
    [ -f "$marker" ] || return 0
    now="$(printf '%(%s)T' -1)"
    mtime="$(stat -c %Y "$marker" 2>/dev/null || printf 0)"
    [[ "$mtime" =~ ^[0-9]+$ ]] || return 0
    [ $((now-mtime)) -ge "$DASHBOARD_CACHE_TTL" ]
}


# shellcheck disable=SC2120 # optional force argument is intentionally supported
s2_refresh_dashboard() {
    local force="${1:-}"
    s2_init_runtime >/dev/null 2>&1 || return 1
    [ "$force" = force ] || s2_dashboard_refresh_due || return 0
    mkdir "$SAMAN2_CACHE/.refresh-lock" 2>/dev/null || return 0
    (
        trap 'rmdir "$SAMAN2_CACHE/.refresh-lock" 2>/dev/null || true' EXIT
        (
            local j tmp
            tmp="$SAMAN2_CACHE/battery.$BASHPID.tmp"
            j="$(timeout "$TERMUX_API_TIMEOUT" termux-battery-status 2>/dev/null || true)"
            if [ -n "$j" ] && s2_have jq && printf '%s' "$j" | jq -e . >/dev/null 2>&1; then
                printf '%s%%\n' "$(printf '%s' "$j" | jq -r '.percentage // "?"')" > "$tmp"
                mv -f "$tmp" "$SAMAN2_CACHE/battery"
                tmp="$SAMAN2_CACHE/temp.$BASHPID.tmp"
                printf '%s°C\n' "$(printf '%s' "$j" | jq -r '.temperature // "?"')" > "$tmp"
                mv -f "$tmp" "$SAMAN2_CACHE/temp"
            fi
        ) &
        (
            local j ssid iface label tmp
            iface="$(s2_default_iface)"
            j="$(timeout "$TERMUX_API_TIMEOUT" termux-wifi-connectioninfo 2>/dev/null || true)"
            ssid=''
            if [ -n "$j" ] && s2_have jq && printf '%s' "$j" | jq -e . >/dev/null 2>&1; then ssid="$(printf '%s' "$j" | jq -r '.ssid // empty')"; fi
            label="$(s2_network_label "$ssid" "$iface")"
            tmp="$SAMAN2_CACHE/network.$BASHPID.tmp"; printf '%s\n' "$label" > "$tmp"; mv -f "$tmp" "$SAMAN2_CACHE/network"
        ) &
        (
            local tmp
            tmp="$SAMAN2_CACHE/vpn.$BASHPID.tmp"; printf '%s\n' "$(s2_vpn_state)" > "$tmp"; mv -f "$tmp" "$SAMAN2_CACHE/vpn"
        ) &
        (
            local trace ip cc tmp
            trace="$(curl -4 -sS --max-time "$NETWORK_TIMEOUT" https://www.cloudflare.com/cdn-cgi/trace 2>/dev/null || true)"
            ip="$(sed -n 's/^ip=//p' <<< "$trace" | head -n1)"; cc="$(sed -n 's/^loc=//p' <<< "$trace" | head -n1)"
            if [ -n "$ip" ]; then tmp="$SAMAN2_CACHE/ip.$BASHPID.tmp"; printf '%s\n' "$ip" > "$tmp"; mv -f "$tmp" "$SAMAN2_CACHE/ip"; fi
            if [ -n "$cc" ]; then tmp="$SAMAN2_CACHE/country.$BASHPID.tmp"; printf '%s\n' "$cc" > "$tmp"; mv -f "$tmp" "$SAMAN2_CACHE/country"; fi
        ) &
        wait
        : > "$SAMAN2_CACHE/.last-refresh"
    ) >/dev/null 2>&1 &
    # Consumed by saman2 after this module is sourced dynamically.
    # shellcheck disable=SC2034
    S2_REFRESH_PID=$!
}

s2_dashboard_warmup() {
    if [ ! -s "$SAMAN2_CACHE/battery" ] || [ ! -s "$SAMAN2_CACHE/network" ] || [ ! -s "$SAMAN2_CACHE/ip" ]; then
        s2_refresh_dashboard
        for _ in 1 2 3 4 5 6 7 8; do
            [ -s "$SAMAN2_CACHE/battery" ] && [ -s "$SAMAN2_CACHE/network" ] && break
            sleep 0.1
        done
    fi
}

s2_aether_quick() {
    local pf="$HOME/.saman-aether/aether.pid" pid=''
    [ -f "$pf" ] && pid="$(cat "$pf" 2>/dev/null || true)"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        local cmd='' mode='ON'
        [ -r "/proc/$pid/cmdline" ] && cmd="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
        case " $cmd " in
            *' --gool '*) mode='GOOL' ;;
            *' --wg '*) mode='WG' ;;
            *' --masque '*' --h2 '*) mode='MASQUE H2' ;;
            *' --masque '*) mode='MASQUE H3' ;;
            *)
                if declare -F s2_aether_mode >/dev/null 2>&1; then mode="$(s2_aether_mode "$pid" 2>/dev/null || echo ON)"; fi
                ;;
        esac
        printf '%s' "$mode"
    else
        printf 'OFF'
    fi
}

s2_box_line() {
    local text="$1" inner="$2"
    if [ "${#text}" -gt "$inner" ]; then
        if [ "$inner" -ge 4 ]; then text="${text:0:$((inner-3))}..."; else text="${text:0:$inner}"; fi
    fi
    printf "%b│%b %-*s%b│%b\n" "$S2_CYAN" "$S2_RESET" "$inner" "$text" "$S2_CYAN" "$S2_RESET"
}

s2_dashboard() {
    local bat temp net vpn ip cc free aether cols inner title border
    s2_dashboard_warmup
    bat="$(s2_cache_read battery)"
    temp="$(s2_cache_read temp)"
    net="$(s2_cache_read network)"
    vpn="$(s2_cache_read vpn '?')"
    ip="$(s2_cache_read ip)"
    cc="$(s2_cache_read country '?')"
    free="$(df -h "$HOME" 2>/dev/null | awk 'NR==2{print $4}')"
    aether="$(s2_aether_quick)"

    cols="$(s2_terminal_cols)"
    [ "$cols" -gt 56 ] && cols=56
    inner=$((cols-2))
    [ "$inner" -lt 28 ] && inner=28
    border="$(s2_repeat '─' "$inner")"

    printf "%b╭%s╮%b\n" "$S2_CYAN" "$border" "$S2_RESET"
    title="SAMAN PHONE CENTER 2 | $SAMAN2_VERSION"
    s2_box_line "$title" "$inner"
    printf "%b├%s┤%b\n" "$S2_CYAN" "$border" "$S2_RESET"
    s2_box_line "Battery $bat | Temp $temp" "$inner"
    s2_box_line "Free $free | Aether $aether" "$inner"
    s2_box_line "$net | VPN $vpn" "$inner"
    s2_box_line "IP $ip | $cc" "$inner"
    printf "%b╰%s╯%b\n" "$S2_CYAN" "$border" "$S2_RESET"
    echo
    s2_refresh_dashboard
}

s2_progress() {
    local pct="${1:-0}" label="${2:-Working}" width=22 filled empty
    [ "$pct" -lt 0 ] 2>/dev/null && pct=0
    [ "$pct" -gt 100 ] 2>/dev/null && pct=100
    filled=$((pct * width / 100)); empty=$((width-filled))
    printf '\r%-18s [' "$label"
    printf '%*s' "$filled" '' | tr ' ' '#'
    printf '%*s' "$empty" '' | tr ' ' '-'
    printf '] %3d%%' "$pct"
}
