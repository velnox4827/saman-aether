#!/data/data/com.termux/files/usr/bin/bash

s2_check_cmd() {
    local c="$1" required="${2:-yes}" verbose="${3:-no}" path
    if s2_have "$c"; then
        path="$(command -v "$c")"
        if [ "$verbose" = yes ]; then
            printf '  ✓ %-22s %s\n' "$c" "$path"
        else
            printf '  ✓ %s\n' "$c"
        fi
        return 0
    fi

    if [ "$required" = yes ]; then
        printf '  ✗ %s (missing)\n' "$c"
        return 1
    fi
    printf '  ! %s (optional, missing)\n' "$c"
    return 0
}

s2_doctor() {
    local verbose=no bad=0 c
    case "${1:-}" in --verbose|-v|verbose) verbose=yes ;; esac

    echo "Saman Phone Center Doctor"
    echo "Version : $SAMAN2_VERSION"
    echo "Columns : $(s2_terminal_cols)"
    echo

    echo "Core dependencies:"
    for c in bash curl jq python ffmpeg ffprobe aria2c yt-dlp ip ss awk sed tar; do
        s2_check_cmd "$c" yes "$verbose" || bad=$((bad+1))
    done

    echo
    echo "Termux / Android integration:"
    for c in termux-battery-status termux-wifi-connectioninfo termux-clipboard-get termux-notification termux-open; do
        s2_check_cmd "$c" no "$verbose" || true
    done
    if [ -d "$HOME/storage/shared" ]; then
        printf '  ✓ shared storage\n'
    else
        printf '  ! shared storage (run termux-setup-storage)\n'
    fi

    echo
    echo "Saman legacy tools:"
    for c in sdm media netcheck saman-share saman-filebox saman-imagebox; do
        if p="$(s2_legacy "$c" 2>/dev/null)"; then
            if [ "$verbose" = yes ]; then printf '  ✓ %-22s %s\n' "$c" "$p"; else printf '  ✓ %s\n' "$c"; fi
        else
            printf '  ! %s (missing)\n' "$c"
        fi
    done

    echo
    echo "Network detection:"
    printf '  • network : %s\n' "$(s2_network_label '' "$(s2_default_iface)")"
    printf '  • VPN     : %s\n' "$(s2_vpn_state)"
    printf '  • iface visibility: %s\n' "$(s2_net_iface_visibility)"
    if v="$(s2_vpn_iface 2>/dev/null)" && [ -n "$v" ]; then
        printf '  • VPN iface: %s\n' "$v"
    elif [ "$(s2_vpn_state)" = '?' ]; then
        printf '  • note    : Android hides VPN state from Termux\n'
    fi

    echo
    echo "Aether:"
    if [ -x "$PREFIX/bin/saman-aether-core" ]; then
        printf '  ✓ patched core : %s\n' "$("$PREFIX/bin/saman-aether-core" --version 2>/dev/null | head -n1)"
    else
        printf '  ! patched core : missing\n'
    fi
    [ -x "$HOME/.aether-shortcut-runner" ] && printf '  ✓ runner       : found\n' || printf '  ! runner       : missing\n'

    echo
    if [ "$bad" -eq 0 ]; then s2_ok "No missing core dependency detected."; else s2_err "$bad core dependencies are missing."; fi
    if s2_have termux-battery-status; then
        if s2_termux_api_ok; then s2_ok "Termux:API responds."; else s2_warn "Termux:API command exists but did not respond. Check companion app/permissions."; fi
    fi
}

s2_safe_report() {
    local outdir="$HOME/storage/downloads/SamanCenter/Diagnostics" stamp out
    mkdir -p "$outdir"; stamp="$(date +%Y%m%d-%H%M%S)"; out="$outdir/saman2-safe-$stamp.txt"
    {
        echo "Saman Phone Center safe diagnostics"
        echo "==================================="
        echo "Version: $SAMAN2_VERSION"
        echo "Date: $(date)"
        echo "Android: $(getprop ro.build.version.release 2>/dev/null || echo unknown)"
        echo "ABI: $(getprop ro.product.cpu.abi 2>/dev/null || echo unknown)"
        echo
        s2_doctor --verbose
        echo
        echo "Aether status:"
        s2_aether_status
        echo
        echo "Disk:"
        df -h "$HOME" 2>/dev/null || true
    } > "$out" 2>&1
    s2_ok "Safe diagnostics saved"
    echo "$out"
}

s2_diagnostics_menu() {
    while true; do
        s2_clear; s2_title "DIAGNOSTICS"
        echo "1) Quick doctor"
        echo "2) Verbose doctor (show paths)"
        echo "3) Create safe shareable report"
        echo "4) Aether safe diagnostics"
        echo "0) Back"; echo
        x="$(s2_read_choice)"
        case "$x" in
            1) s2_doctor; s2_pause ;;
            2) s2_doctor --verbose; s2_pause ;;
            3) s2_safe_report; s2_pause ;;
            4) if s2_have saman-aether-diagnostics; then saman-aether-diagnostics safe; else s2_err "Aether diagnostics missing"; fi; s2_pause ;;
            0) return ;;
        esac
    done
}
