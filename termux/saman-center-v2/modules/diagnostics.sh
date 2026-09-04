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
    local verbose=no bad=0 warn=0 c path vpn_state iface_visibility pid_file="$HOME/.saman-aether/aether.pid" pid=''
    case "${1:-}" in '' ) ;; --verbose|-v|verbose) verbose=yes ;; *) printf 'ERROR: usage: saman doctor [--verbose]\n' >&2; return 2 ;; esac

    echo "Saman Phone Center Doctor"
    echo "Version : $SAMAN2_VERSION"
    echo

    echo "Required runtime:"
    for c in bash awk sed grep curl ip ss timeout readlink; do
        s2_check_cmd "$c" yes "$verbose" || bad=$((bad+1))
    done

    echo
    echo "Optional feature dependencies:"
    for c in jq python ffmpeg ffprobe aria2c yt-dlp tar qrencode; do
        s2_check_cmd "$c" no "$verbose" || true
    done

    echo
    echo "Installation integrity:"
    for path in "$HOME/bin/saman" "$SAMAN2_ROOT/saman2" "$SAMAN2_ROOT/lib/common.sh" \
        "$SAMAN2_ROOT/lib/config.sh" "$SAMAN2_ROOT/modules/aether.sh" \
        "$PREFIX/bin/saman-aether-core" "$HOME/.aether-shortcut-runner"; do
        if [ -x "$path" ] || { [[ "$path" == *.sh ]] && [ -r "$path" ]; }; then
            [ "$verbose" = yes ] && printf '  OK      %s\n' "$path" || printf '  OK      %s\n' "${path##*/}"
        else
            printf '  ERROR   missing/not usable: %s\n' "$path"; bad=$((bad+1))
        fi
    done
    if [ "${#S2_CONFIG_ERRORS[@]}" -gt 0 ]; then
        printf '  ERROR   invalid configuration: %s\n' "$S2_CONFIG_FILE"; bad=$((bad+1))
    else
        printf '  OK      configuration valid\n'
    fi

    if [ -e "$pid_file" ]; then
        [ -s "$pid_file" ] && pid="$(<"$pid_file")"
        if s2_aether_pid_is_core "$pid"; then printf '  OK      tracked Aether PID %s\n' "$pid"
        else printf '  WARNING stale PID file (run: saman repair)\n'; warn=$((warn+1)); fi
    else
        printf '  OK      no stale PID file\n'
    fi

    echo
    echo "Termux / Android integration:"
    for c in termux-battery-status termux-wifi-connectioninfo termux-clipboard-get termux-notification termux-open; do
        s2_check_cmd "$c" no "$verbose" || true
    done
    if [ -d "$HOME/storage/shared" ]; then printf '  OK      shared storage available\n'
    else printf '  WARNING shared storage unavailable (run termux-setup-storage)\n'; warn=$((warn+1)); fi

    echo
    echo "Network visibility:"
    vpn_state="$(s2_vpn_state)"; iface_visibility="$(s2_net_iface_visibility)"
    printf '  INFO    network: %s\n' "$(s2_network_label '' "$(s2_default_iface)")"
    printf '  INFO    VPN: %s | interfaces: %s\n' "$vpn_state" "$iface_visibility"
    [ "$vpn_state" = '?' ] && { printf '  WARNING Android hides VPN state from Termux\n'; warn=$((warn+1)); }

    echo
    echo "Aether integration:"
    if [ -x "$PREFIX/bin/saman-aether-core" ]; then
        printf '  OK      patched core: %s\n' "$(timeout 3 "$PREFIX/bin/saman-aether-core" --version 2>/dev/null | head -n1)"
    else
        printf '  ERROR   patched core missing\n'; bad=$((bad+1))
    fi
    [ -x "$HOME/.aether-shortcut-runner" ] && printf '  OK      runner executable\n' || { printf '  ERROR   runner missing/not executable\n'; bad=$((bad+1)); }

    echo
    if [ "$bad" -gt 0 ]; then
        printf 'ERROR: %s required check(s) failed; %s warning(s).\n' "$bad" "$warn"
        printf 'ACTION REQUIRED: run saman repair, then saman doctor --verbose.\n'
        return 1
    fi
    if [ "$warn" -gt 0 ]; then
        printf 'WARNING: required checks passed with %s warning(s).\n' "$warn"
    else
        printf 'OK: all required checks passed.\n'
    fi
    return 0
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
