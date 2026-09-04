#!/data/data/com.termux/files/usr/bin/bash

s2_network_dns() {
    local dns=''
    dns="$(getprop 2>/dev/null | awk -F'[][]' '/net\.dns[1-4]/{if($4!=""){print $4; exit}}')"
    if [ -z "$dns" ] && [ -r /etc/resolv.conf ]; then
        dns="$(awk '/^nameserver[[:space:]]/{print $2; exit}' /etc/resolv.conf 2>/dev/null || true)"
    fi
    printf '%s\n' "$dns"
}

s2_network_route() {
    local route=''
    route="$(ip route 2>/dev/null | awk '/^default/{print; exit}')"
    if [ -z "$route" ] && [ -r /proc/net/route ]; then
        route="$(awk 'NR>1 && $2=="00000000"{print "default dev "$1; exit}' /proc/net/route 2>/dev/null || true)"
    fi
    printf '%s\n' "$route"
}

s2_network_valid_ipv4() {
    awk -F. 'NF==4 {for(i=1;i<=4;i++) if($i !~ /^[0-9]+$/ || $i<0 || $i>255) exit 1; exit 0} {exit 1}' <<< "${1:-}"
}

s2_network_valid_ipv6() {
    [[ "${1:-}" == *:* && "${1:-}" =~ ^[0-9A-Fa-f:.]+$ ]]
}

s2_network_latency_direct() {
    local out code total ms
    out="$(curl -4 -o /dev/null -sS --connect-timeout "${NETWORK_TIMEOUT:-8}" --max-time "${NETWORK_TIMEOUT:-8}" -w '%{http_code} %{time_total}' https://www.cloudflare.com/cdn-cgi/trace 2>/dev/null || true)"
    code="${out%% *}"; total="${out#* }"
    [ -n "$total" ] && [ "$total" != "$out" ] || return 1
    ms="$(awk -v t="$total" 'BEGIN{printf "%.0f", t*1000}')"
    printf '%s ms (HTTP %s)\n' "$ms" "${code:-?}"
}

s2_network_quick() {
    local ip4 ip6 data country city isp asn vpn_state vpn_if route dns iface netlabel latency
    local probe_dir p4 p6 pg pl
    probe_dir="$(mktemp -d)" || return 1
    curl -4 -fsS --connect-timeout "$NETWORK_TIMEOUT" --max-time "$NETWORK_TIMEOUT" https://api.ipify.org >"$probe_dir/ip4" 2>/dev/null & p4=$!
    curl -6 -fsS --connect-timeout "$NETWORK_TIMEOUT" --max-time "$NETWORK_TIMEOUT" https://api64.ipify.org >"$probe_dir/ip6" 2>/dev/null & p6=$!
    curl -fsS --connect-timeout "$NETWORK_TIMEOUT" --max-time "$NETWORK_TIMEOUT" https://ipwho.is/ >"$probe_dir/geo" 2>/dev/null & pg=$!
    s2_network_latency_direct >"$probe_dir/latency" 2>/dev/null & pl=$!
    wait "$p4" 2>/dev/null || true; wait "$p6" 2>/dev/null || true
    wait "$pg" 2>/dev/null || true; wait "$pl" 2>/dev/null || true
    ip4="$(s2_terminal_field < "$probe_dir/ip4")"
    ip6="$(s2_terminal_field < "$probe_dir/ip6")"
    s2_network_valid_ipv4 "$ip4" || ip4=''
    s2_network_valid_ipv6 "$ip6" || ip6=''
    data="$(<"$probe_dir/geo")"
    latency="$(s2_terminal_field < "$probe_dir/latency")"
    rm -rf -- "$probe_dir"
    if s2_have jq && printf '%s' "$data" | jq -e '.success == true' >/dev/null 2>&1; then
        country="$(printf '%s' "$data" | jq -r '.country // "Unknown"' | s2_terminal_field)"
        city="$(printf '%s' "$data" | jq -r '.city // "Unknown"' | s2_terminal_field)"
        isp="$(printf '%s' "$data" | jq -r '.connection.isp // "Unknown"' | s2_terminal_field)"
        asn="$(printf '%s' "$data" | jq -r '.connection.asn // "Unknown"' | s2_terminal_field)"
    else
        country='Unknown'; city='Unknown'; isp='Unknown'; asn='Unknown'
    fi

    iface="$(s2_default_iface)"
    netlabel="$(s2_network_label '' "$iface")"
    vpn_state="$(s2_vpn_state)"
    vpn_if="$(s2_vpn_iface 2>/dev/null || true)"
    route="$(s2_network_route)"
    dns="$(s2_network_dns)"

    echo "Network     : $netlabel"
    echo "Interface   : ${iface:-not visible}"
    echo "Android VPN : $vpn_state${vpn_if:+ ($vpn_if)}"
    echo "Public IPv4 : ${ip4:-Unavailable}"
    echo "Public IPv6 : ${ip6:-Unavailable}"
    echo "Country     : $country"
    echo "City        : $city"
    echo "ISP / ASN   : $isp / $asn"
    echo "DNS         : ${dns:-system/private DNS}"
    echo "Default     : ${route:-not visible}"
    echo "Latency     : ${latency:-Unavailable}"
    echo
    if [ -n "$ip4" ]; then
        s2_ok "Internet reachable"
    else
        s2_err "Internet reachability check failed"
    fi
    if s2_port_listening "$AETHER_SOCKS_PORT"; then
        echo
        echo "Aether proxy:"
        s2_aether_probe compact || true
    fi
}

s2_network_interfaces() {
    echo "Visible interfaces:"
    if ip -brief addr >/dev/null 2>&1; then
        ip -brief addr 2>/dev/null || true
    else
        while IFS= read -r i; do
            printf '  %-18s %s\n' "$i" "$(cat "/sys/class/net/$i/operstate" 2>/dev/null || echo '?')"
        done < <(s2_all_ifaces)
    fi
    echo
    echo "Routes:"
    ip route 2>/dev/null || true
    if ! ip route 2>/dev/null | grep -q .; then
        s2_network_route
    fi
}

s2_network_debug() {
    local ifacevis androidvis
    ifacevis="$(s2_net_iface_visibility)"
    if s2_android_connectivity_visibility; then androidvis='VISIBLE'; else androidvis='HIDDEN/UNAVAILABLE'; fi

    echo "Saman Network detection debug"
    echo "============================="
    echo "Default iface : $(s2_default_iface || true)"
    echo "Physical iface: $(s2_physical_iface 2>/dev/null || echo none)"
    echo "VPN iface     : $(s2_vpn_iface 2>/dev/null || echo none)"
    echo "VPN state     : $(s2_vpn_state)"
    echo "Iface access  : $ifacevis"
    echo "Connectivity  : $androidvis"
    echo "Network label : $(s2_network_label '' "$(s2_default_iface)")"
    echo
    echo "All interfaces visible to Termux:"
    if ! s2_all_ifaces | sed 's/^/  /' | grep -q .; then
        echo "  (none visible)"
    else
        s2_all_ifaces | sed 's/^/  /'
    fi
    echo
    echo "/proc/net/dev:"
    if [ -r /proc/net/dev ]; then
        sed -n '1,20p' /proc/net/dev 2>/dev/null || true
    else
        echo "  unavailable"
    fi
    echo
    if s2_have dumpsys; then
        if s2_android_vpn_dumpsys; then echo "dumpsys VPN   : detected"; else echo "dumpsys VPN   : not detected / unavailable"; fi
    else
        echo "dumpsys VPN   : command unavailable"
    fi
    echo
    if [ "$(s2_vpn_state)" = '?' ]; then
        echo "Note: Android is hiding network/VPN details from the Termux sandbox."
        echo "      Saman reports '?' rather than incorrectly claiming VPN OFF."
    fi
}

s2_network_menu() {
    while true; do
        s2_clear; s2_title "NETWORK ANALYZER"
        echo "1) IP / ISP / VPN summary"
        echo "2) Interfaces & routes"
        echo "3) Aether connection test"
        echo "4) Legacy analyzer"
        echo "5) Speed test"
        echo "6) Detection debug"
        echo "0) Back"; echo
        x="$(s2_read_choice)"
        case "$x" in
            1) echo; s2_network_quick; s2_pause ;;
            2) echo; s2_network_interfaces; s2_pause ;;
            3) echo; s2_aether_probe; s2_pause ;;
            4) if p="$(s2_legacy netcheck 2>/dev/null)"; then "$p"; else s2_err "Legacy netcheck not found"; s2_pause; fi ;;
            5) if s2_have speedtest-cli; then speedtest-cli --simple; else s2_warn "speedtest-cli is not installed."; echo "Install: pip install speedtest-cli"; fi; s2_pause ;;
            6) echo; s2_network_debug; s2_pause ;;
            0) return ;;
        esac
    done
}
