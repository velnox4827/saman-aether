#!/data/data/com.termux/files/usr/bin/bash

s2_battery_details() {
    if ! s2_have termux-battery-status; then s2_err "termux-api commands are not installed"; return 1; fi
    local j
    j="$(timeout "${TERMUX_API_TIMEOUT:-4}" termux-battery-status 2>/dev/null || true)"
    if s2_have jq && printf '%s' "$j" | jq -e . >/dev/null 2>&1; then printf '%s' "$j" | jq .; else s2_err "Termux:API app may be unavailable or permission denied"; fi
}

s2_phone_api_test() {
    echo "Termux:API command : $(s2_have termux-battery-status && echo installed || echo missing)"
    if s2_termux_api_ok; then s2_ok "Battery API responds"; else s2_err "Battery API did not respond"; fi
    if s2_have termux-clipboard-get; then timeout "$TERMUX_API_TIMEOUT" termux-clipboard-get >/dev/null 2>&1 && s2_ok "Clipboard API responds" || s2_warn "Clipboard API unavailable/denied"; fi
    if s2_have termux-notification; then s2_ok "Notification command installed"; else s2_warn "Notification command missing"; fi
}

s2_phone_validate_ssh() {
    local host="${1:-}" user="${2:-}" port="${3:-}"
    [[ "$host" =~ ^[A-Za-z0-9][A-Za-z0-9._:-]*$ ]] || return 1
    [ -z "$user" ] || [[ "$user" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || return 1
    [[ "$port" =~ ^[0-9]+$ ]] && [ "$port" -ge 1 ] && [ "$port" -le 65535 ]
}

s2_phone_menu() {
    while true; do
        s2_clear; s2_title "PHONE TOOLS"
        echo "1) Battery details"
        echo "2) Flashlight ON"
        echo "3) Flashlight OFF"
        echo "4) Show clipboard"
        echo "5) Copy text to clipboard"
        echo "6) Send test notification"
        echo "7) Termux API test"
        echo "8) SSH quick connect"
        echo "0) Back"; echo
        x="$(s2_read_choice)"
        case "$x" in
            1) s2_battery_details; s2_pause ;;
            2) timeout "$TERMUX_API_TIMEOUT" termux-torch on 2>/dev/null && s2_ok "Flashlight ON" || s2_err "Could not turn flashlight on"; sleep 1 ;;
            3) timeout "$TERMUX_API_TIMEOUT" termux-torch off 2>/dev/null && s2_ok "Flashlight OFF" || s2_err "Could not turn flashlight off"; sleep 1 ;;
            4) echo; clip="$(timeout "$TERMUX_API_TIMEOUT" termux-clipboard-get 2>/dev/null)" && s2_terminal_field "$clip" || s2_err "Clipboard unavailable"; s2_pause ;;
            5) read -r -p "Text: " txt; printf '%s' "$txt" | timeout "$TERMUX_API_TIMEOUT" termux-clipboard-set 2>/dev/null && s2_ok "Copied" || s2_err "Clipboard unavailable"; sleep 1 ;;
            6) s2_notify "Saman Phone Center" "Termux API is working ✓"; timeout "$TERMUX_API_TIMEOUT" termux-vibrate -d 150 >/dev/null 2>&1 || true; s2_ok "Notification requested"; sleep 1 ;;
            7) s2_phone_api_test; s2_pause ;;
            8) read -r -p "Server IP / hostname: " host; [ -z "$host" ] && continue; read -r -p "Username: " user; read -r -p "Port [22]: " port; port="${port:-22}"; if ! s2_phone_validate_ssh "$host" "$user" "$port"; then s2_err "Invalid SSH hostname, username, or port"; s2_pause; continue; fi; if [ -n "$user" ]; then ssh -p "$port" -- "$user@$host"; else ssh -p "$port" -- "$host"; fi ;;
            0) return ;;
        esac
    done
}
