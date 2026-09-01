#!/data/data/com.termux/files/usr/bin/bash

s2_lan_ip() {
    ip -4 -o addr show 2>/dev/null | awk '
    {iface=$2; split($4,a,"/"); addr=a[1]}
    iface !~ /^(lo|tun|tap|wg|ppp|rmnet|ccmni|dummy)/ &&
    (addr ~ /^192\.168\./ || addr ~ /^10\./ || addr ~ /^172\.(1[6-9]|2[0-9]|3[01])\./) {print addr; exit}'
}

s2_share_start() {
    local share="$HOME/storage/downloads/Termux/SamanShare" port=8080 ip token url mode_choice mode
    mkdir -p "$share"
    ip="$(s2_lan_ip)"
    if [ -z "$ip" ]; then
        s2_warn "No Wi-Fi/hotspot LAN address detected."
        echo "Open hotspot settings and return here."
        am start -a android.settings.TETHER_SETTINGS >/dev/null 2>&1 || true
        s2_pause
        ip="$(s2_lan_ip)"
    fi
    [ -n "$ip" ] || { s2_err "Could not detect a LAN IP."; s2_pause; return 1; }
    echo "1) Two-way (send + receive)"
    echo "2) Send from phone only"
    echo "3) Receive to phone only"
    read -r -p "Mode [1]: " mode_choice
    case "${mode_choice:-1}" in 2) mode=send;; 3) mode=receive;; *) mode=two-way;; esac
    token="$(python - <<'PY'
import secrets
print(secrets.token_urlsafe(18))
PY
)"
    url="http://$ip:$port/?token=$token"
    s2_clear; s2_title "SAMAN SHARE 2"
    echo "Mode   : $mode"
    echo "Address: $url"
    echo "Folder : $share"
    echo
    if s2_have termux-clipboard-set; then printf '%s' "$url" | termux-clipboard-set 2>/dev/null || true; echo "Address copied to clipboard."; fi
    if s2_have qrencode; then echo; qrencode -t ANSIUTF8 "$url" 2>/dev/null || true; fi
    echo; echo "The access token changes every session."
    echo "Press Ctrl+C to stop sharing."; echo
    termux-wake-lock >/dev/null 2>&1 || true
    cleanup_share() { termux-wake-unlock >/dev/null 2>&1 || true; }
    trap cleanup_share INT TERM EXIT
    python "$SAMAN2_ROOT/share/server.py" "$share" 0.0.0.0 "$port" "$token" "$mode"
    trap - INT TERM EXIT
    cleanup_share
}

s2_share_menu() {
    s2_clear; s2_title "SAMAN SHARE"
    echo "Saman Share 2 uses a random session token and streams uploads to disk."
    echo
    s2_share_start
}
