#!/data/data/com.termux/files/usr/bin/bash

s2_aether_core_pids() {
    local proc exe core
    core="$(readlink -f "$PREFIX/bin/saman-aether-core" 2>/dev/null || true)"
    [ -n "$core" ] || return 0
    for proc in /proc/[0-9]*; do
        [ -r "$proc/exe" ] || continue
        exe="$(readlink -f "$proc/exe" 2>/dev/null || true)"
        [ "$exe" = "$core" ] && printf '%s\n' "${proc##*/}"
    done
}

s2_aether_pid_is_core() {
    local pid="${1:-}" exe core
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    kill -0 "$pid" 2>/dev/null || return 1
    core="$(readlink -f "$PREFIX/bin/saman-aether-core" 2>/dev/null || true)"
    exe="$(readlink -f "/proc/$pid/exe" 2>/dev/null || true)"
    [ -n "$core" ] && [ "$exe" = "$core" ]
}

s2_aether_pid() {
    local pf="$HOME/.saman-aether/aether.pid" pid='' found='' count=0 candidate
    [ -s "$pf" ] && pid="$(<"$pf")"
    if s2_aether_pid_is_core "$pid"; then
        printf '%s\n' "$pid"
        return 0
    fi
    [ -e "$pf" ] && rm -f "$pf"
    while IFS= read -r candidate; do
        [ -n "$candidate" ] || continue
        found="$candidate"; count=$((count + 1))
    done < <(s2_aether_core_pids)
    if [ "$count" -eq 1 ]; then
        mkdir -p "${pf%/*}"
        printf '%s\n' "$found" > "$pf"
        printf '%s\n' "$found"
    fi
}

s2_aether_pid_running() {
    local pid="${1:-$(s2_aether_pid)}"
    s2_aether_pid_is_core "$pid"
}

s2_aether_log_for_mode() {
    case "${1:-}" in
        'WireGuard'|WG|wg) printf '%s\n' "$HOME/aether-wg.log" ;;
        GOOL|gool) printf '%s\n' "$HOME/aether-gool.log" ;;
        'MASQUE H2'|h2|masque-h2) printf '%s\n' "$HOME/aether-masque-h2.log" ;;
        'MASQUE H3'|h3|masque-h3) printf '%s\n' "$HOME/aether-masque-h3.log" ;;
        *) return 1 ;;
    esac
}

s2_aether_mode_from_logs() {
    local f newest='' mt=0 t label
    for label in 'WireGuard' GOOL 'MASQUE H3' 'MASQUE H2'; do
        f="$(s2_aether_log_for_mode "$label" 2>/dev/null || true)"
        [ -f "$f" ] || continue
        t="$(stat -c %Y "$f" 2>/dev/null || echo 0)"
        if [ "$t" -gt "$mt" ] 2>/dev/null; then mt="$t"; newest="$label"; fi
    done
    [ -n "$newest" ] && printf '%s\n' "$newest" || printf 'Unknown\n'
}

s2_aether_mode() {
    local pid="${1:-$(s2_aether_pid)}" cmd=''
    [ -n "$pid" ] && [ -r "/proc/$pid/cmdline" ] && cmd="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
    case " $cmd " in
        *' --gool '*) echo 'GOOL' ;;
        *' --wg '*) echo 'WireGuard' ;;
        *' --masque '*' --h2 '*) echo 'MASQUE H2' ;;
        *' --masque '*) echo 'MASQUE H3' ;;
        *) s2_aether_mode_from_logs ;;
    esac
}

s2_port_listening() {
    local port="$1"
    if s2_have ss && ss -ltnH >/dev/null 2>&1; then
        ss -ltnH 2>/dev/null | awk -v p=":$port" '$4 ~ (p "$"){f=1} END{exit(f?0:1)}'
        return $?
    fi
    local hex
    hex="$(printf '%04X' "$port")"
    awk -v p=":$hex" 'NR>1 && $2 ~ (p "$") && $4=="0A"{f=1} END{exit(f?0:1)}' /proc/net/tcp /proc/net/tcp6 2>/dev/null
}

s2_aether_core_version() {
    if [ -x "$PREFIX/bin/saman-aether-core" ]; then
        "$PREFIX/bin/saman-aether-core" --version 2>/dev/null | head -n1
    elif s2_have aether; then
        aether --version 2>/dev/null | head -n1
    else
        echo 'not installed'
    fi
}

s2_aether_core_label() {
    if [ -x "$PREFIX/bin/saman-aether-core" ]; then
        printf '%s [patched]\n' "$(s2_aether_core_version)"
    elif s2_have aether; then
        printf '%s [official]\n' "$(s2_aether_core_version)"
    else
        printf 'not installed\n'
    fi
}

s2_duration_human() {
    local sec="${1:-0}" d h m s out=''
    [[ "$sec" =~ ^[0-9]+$ ]] || { printf '?\n'; return; }
    d=$((sec/86400)); h=$(((sec%86400)/3600)); m=$(((sec%3600)/60)); s=$((sec%60))
    [ "$d" -gt 0 ] && out+="${d}d "
    [ "$h" -gt 0 ] && out+="${h}h "
    [ "$m" -gt 0 ] && out+="${m}m "
    [ "$d" -eq 0 ] && [ "$h" -eq 0 ] && out+="${s}s"
    printf '%s\n' "${out% }"
}

s2_aether_uptime() {
    local pid="${1:-$(s2_aether_pid)}" sec=''
    s2_aether_pid_running "$pid" || { printf -- '-\n'; return; }
    sec="$(ps -o etimes= -p "$pid" 2>/dev/null | tr -d ' ' | head -n1 || true)"
    if [[ "$sec" =~ ^[0-9]+$ ]]; then
        s2_duration_human "$sec"
    else
        printf 'running\n'
    fi
}

s2_aether_health() {
    local pid socks=0 http=0
    pid="$(s2_aether_pid)"
    s2_port_listening 1819 && socks=1
    s2_port_listening 1820 && http=1
    if s2_aether_pid_running "$pid"; then
        if [ "$socks" -eq 1 ] && [ "$http" -eq 1 ]; then printf 'READY\n'
        elif [ "$socks" -eq 1 ] || [ "$http" -eq 1 ]; then printf 'PARTIAL\n'
        else printf 'CONNECTING\n'; fi
    else
        if [ "$socks" -eq 1 ] || [ "$http" -eq 1 ]; then printf 'PORT BUSY\n'; else printf 'OFF\n'; fi
    fi
}

s2_aether_last_event() {
    local pid mode log line
    pid="$(s2_aether_pid)"
    s2_aether_pid_running "$pid" || return 1
    mode="$(s2_aether_mode "$pid")"
    log="$(s2_aether_log_for_mode "$mode" 2>/dev/null || true)"
    [ -f "$log" ] || return 1
    line="$(tail -n 160 "$log" 2>/dev/null | grep -Ei 'connected|reconnect|scanning|scan mode|cached|gateway|endpoint|tunnel|listening|error|failed|timeout' | tail -n1 || true)"
    [ -n "$line" ] || return 1
    line="$(printf '%s' "$line" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+/ /g')"
    [ "${#line}" -gt 88 ] && line="${line:0:85}..."
    printf '%s\n' "$line"
}

s2_aether_probe_file() { printf '%s/aether-probe.tsv\n' "$SAMAN2_CACHE"; }

s2_aether_probe_collect() {
    local trace ip cc curlout code total ms now f tmp
    f="$(s2_aether_probe_file)"; tmp="$f.tmp"
    s2_port_listening 1819 || return 1
    trace="$(curl -4 --silent --max-time 10 --socks5-hostname 127.0.0.1:1819 https://www.cloudflare.com/cdn-cgi/trace 2>/dev/null || true)"
    ip="$(printf '%s\n' "$trace" | sed -n 's/^ip=//p' | head -n1)"
    cc="$(printf '%s\n' "$trace" | sed -n 's/^loc=//p' | head -n1)"
    curlout="$(curl -4 -o /dev/null -sS --max-time 10 --socks5-hostname 127.0.0.1:1819 -w '%{http_code} %{time_total}' https://www.cloudflare.com/cdn-cgi/trace 2>/dev/null || true)"
    code="${curlout%% *}"; total="${curlout#* }"; ms=''
    if [ -n "$total" ] && [ "$total" != "$curlout" ]; then ms="$(awk -v t="$total" 'BEGIN{printf "%.0f", t*1000}')"; fi
    now="$(date +%s)"
    mkdir -p "$SAMAN2_CACHE" 2>/dev/null || true
    printf '%s\t%s\t%s\t%s\t%s\n' "$now" "${ip:-}" "${cc:-}" "${ms:-}" "${code:-}" > "$tmp"
    mv -f "$tmp" "$f"
    [ -n "$ip" ]
}

s2_aether_probe_cache_age() {
    local f ts now
    f="$(s2_aether_probe_file)"; [ -s "$f" ] || { echo 999999; return; }
    IFS=$'\t' read -r ts _ < "$f" || true
    [[ "$ts" =~ ^[0-9]+$ ]] || { echo 999999; return; }
    now="$(date +%s)"; echo $((now-ts))
}

s2_aether_probe_background() {
    local age
    [ "$(s2_aether_health)" = READY ] || return 0
    age="$(s2_aether_probe_cache_age)"
    [ "$age" -le 30 ] 2>/dev/null && return 0
    (s2_aether_probe_collect >/dev/null 2>&1 || true) &
}

s2_aether_probe_cached_line() {
    local f ts ip cc ms code age
    f="$(s2_aether_probe_file)"; [ -s "$f" ] || return 1
    IFS=$'\t' read -r ts ip cc ms code < "$f" || return 1
    age="$(s2_aether_probe_cache_age)"
    [ -n "$ip" ] || return 1
    printf '%s | %s%s%s' "$ip" "${cc:-?}" "${ms:+ | ${ms} ms}" "${age:+ | ${age}s ago}"
}

s2_aether_status() {
    local pid mode='-' health uptime core core_count=0 candidate version_file="$PREFIX/etc/saman-aether-termux.version"
    pid="$(s2_aether_pid)"; core="$(s2_aether_core_label)"; health="$(s2_aether_health)"
    while IFS= read -r candidate; do [ -n "$candidate" ] && core_count=$((core_count + 1)); done < <(s2_aether_core_pids)
    echo "Saman Center : $SAMAN2_VERSION"
    echo "Saman Termux : ${SAMAN_TERMUX_VERSION:-unknown} (installed: $([ -s "$version_file" ] && tr -d '\r\n' < "$version_file" || echo unknown))"
    echo "Saman Tunnel : ${SAMAN_TUNNEL_VERSION:-unknown}"
    echo "Aether Core  : $core"
    echo "Core path    : $PREFIX/bin/saman-aether-core"
    echo "Runner path  : $HOME/.aether-shortcut-runner"
    if s2_aether_pid_running "$pid"; then
        mode="$(s2_aether_mode "$pid")"; uptime="$(s2_aether_uptime "$pid")"
        echo "Service      : RUNNING (PID $pid)"
        echo "Mode         : $mode"
        echo "Uptime       : $uptime"
        [ "$core_count" -gt 1 ] && echo "Processes    : WARNING ($core_count Aether Core processes)"
    else
        echo "Service      : STOPPED"
        echo "Mode         : -"
        [ "$core_count" -gt 0 ] && echo "Processes    : WARNING ($core_count untracked Aether Core processes)"
    fi
    echo "Health       : $health"
    if s2_port_listening 1819; then echo "SOCKS5       : LISTENING 127.0.0.1:1819"; else echo "SOCKS5       : DOWN 127.0.0.1:1819"; fi
    if s2_port_listening 1820; then echo "HTTP CONNECT : LISTENING 127.0.0.1:1820"; else echo "HTTP CONNECT : DOWN 127.0.0.1:1820"; fi
    if ! s2_aether_pid_running "$pid" && { s2_port_listening 1819 || s2_port_listening 1820; }; then
        echo "Port conflict: local proxy port is owned by another process"
    fi
}

s2_aether_overview() {
    local pid mode='-' health uptime event phone_ip phone_cc cached
    pid="$(s2_aether_pid)"; health="$(s2_aether_health)"
    phone_ip="$(s2_cache_read ip '...')"; phone_cc="$(s2_cache_read country '?')"
    echo "Core         : $(s2_aether_core_label)"
    echo "Health       : $health"
    if s2_aether_pid_running "$pid"; then
        mode="$(s2_aether_mode "$pid")"; uptime="$(s2_aether_uptime "$pid")"
        echo "Service      : RUNNING | $mode | $uptime"
    else
        echo "Service      : STOPPED"
    fi
    if s2_port_listening 1819; then echo "SOCKS5       : READY :1819"; else echo "SOCKS5       : DOWN  :1819"; fi
    if s2_port_listening 1820; then echo "HTTP CONNECT : READY :1820"; else echo "HTTP CONNECT : DOWN  :1820"; fi
    echo "Phone IP     : $phone_ip | $phone_cc"
    if cached="$(s2_aether_probe_cached_line 2>/dev/null)"; then
        echo "Aether exit  : $cached"
    elif [ "$health" = READY ]; then
        echo "Aether exit  : checking..."
    else
        echo "Aether exit  : -"
    fi
    event="$(s2_aether_last_event 2>/dev/null || true)"
    [ -n "$event" ] && echo "Last event   : $event"
    s2_aether_probe_background
}

s2_aether_probe() {
    local style="${1:-full}" f ts ip cc ms code event
    if ! s2_port_listening 1819; then
        [ "$style" = compact ] && echo "SOCKS5 : DOWN" || s2_warn "Aether SOCKS5 is not listening."
        return 1
    fi

    s2_aether_probe_collect || true
    f="$(s2_aether_probe_file)"
    if [ -s "$f" ]; then
        IFS=$'\t' read -r ts ip cc ms code < "$f" || true
    fi
    event="$(s2_aether_last_event 2>/dev/null || true)"

    if [ "$style" = compact ]; then
        echo "Exit IP: ${ip:-Unknown} | ${cc:-?}${ms:+ | ${ms} ms}"
        [ -n "$ip" ]
        return $?
    fi

    echo "Aether connection test"
    echo "----------------------"
    echo "Health     : $(s2_aether_health)"
    echo "Mode       : $(s2_aether_mode)"
    echo "Exit IP    : ${ip:-Unknown}"
    echo "Country    : ${cc:-Unknown}"
    echo "Proxy time : ${ms:-Unavailable}${ms:+ ms}"
    echo "HTTP       : ${code:-Unavailable}"
    [ -n "$event" ] && echo "Last event : $event"
    if [ -n "${ip:-}" ]; then s2_ok "Aether proxy is working"; else s2_err "Proxy is listening but internet test failed"; fi
}

s2_aether_exit() { s2_aether_probe; }

s2_aether_start() {
    local mode="${1:-}" runner="$HOME/.aether-shortcut-runner"
    [ -x "$runner" ] || { s2_err "Aether runner not found: $runner"; return 1; }
    rm -f "$(s2_aether_probe_file)" 2>/dev/null || true
    case "${mode,,}" in
        wg|wireguard) exec "$runner" WG ;;
        gool) exec "$runner" GOOL ;;
        masque|h3|masque-h3|masque_h3) exec "$runner" MASQUE ;;
        h2|masque-h2|masque_h2) exec "$runner" MASQUE_H2 ;;
        *) s2_err "Valid modes: wg, gool, h3, h2"; return 2 ;;
    esac
}

s2_aether_stop() {
    local pid pf="$HOME/.saman-aether/aether.pid" pids=() candidate alive=0
    while IFS= read -r candidate; do [ -n "$candidate" ] && pids+=("$candidate"); done < <(s2_aether_core_pids)
    if [ "${#pids[@]}" -eq 0 ]; then
        rm -f "$pf" "$(s2_aether_probe_file)" 2>/dev/null || true
        s2_ok "Aether is already stopped."; return 0
    fi
    echo "Stopping ${#pids[@]} canonical Aether Core process(es): ${pids[*]}"
    for pid in "${pids[@]}"; do s2_aether_pid_is_core "$pid" && kill -TERM "$pid" 2>/dev/null || true; done
    for _ in $(seq 1 50); do
        alive=0
        for pid in "${pids[@]}"; do s2_aether_pid_is_core "$pid" && alive=1; done
        [ "$alive" -eq 0 ] && break
        sleep 0.1
    done
    for pid in "${pids[@]}"; do
        if s2_aether_pid_is_core "$pid"; then
            s2_warn "Graceful stop timed out for PID $pid; forcing stop."
            kill -KILL "$pid" 2>/dev/null || true
        fi
    done
    sleep 0.1
    for pid in "${pids[@]}"; do
        if s2_aether_pid_is_core "$pid"; then
            s2_err "Aether Core PID $pid survived TERM and KILL; state was retained."
            return 1
        fi
    done
    rm -f "$pf" "$(s2_aether_probe_file)"
    s2_have termux-wake-unlock && termux-wake-unlock >/dev/null 2>&1 || true
    s2_ok "Aether stopped."
}

s2_aether_restart() {
    local mode="${1:-}"
    [ -n "$mode" ] || { s2_err "Restart requires a mode: wg, gool, h3, h2"; return 2; }
    s2_aether_stop || return
    s2_aether_start "$mode"
}

s2_aether_diagnostics() {
    local mode="${1:-safe}"
    case "$mode" in safe|full) ;; *) s2_err "Diagnostics mode must be safe or full"; return 2 ;; esac
    s2_have saman-aether-diagnostics || { s2_err "Diagnostics tool not installed"; return 127; }
    saman-aether-diagnostics "$mode"
}

s2_aether_logs_menu() {
    while true; do
        s2_clear; s2_title "AETHER LOGS / DIAGNOSTICS"
        echo "1) WireGuard log"
        echo "2) GOOL log"
        echo "3) MASQUE H3 log"
        echo "4) MASQUE H2 log"
        echo "5) Safe diagnostics"
        echo "6) Full diagnostics"
        echo "0) Back"; echo
        x="$(s2_read_choice)"
        case "$x" in
            1) tail -n 100 "$HOME/aether-wg.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            2) tail -n 100 "$HOME/aether-gool.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            3) tail -n 100 "$HOME/aether-masque-h3.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            4) tail -n 100 "$HOME/aether-masque-h2.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            5) if s2_have saman-aether-diagnostics; then saman-aether-diagnostics safe; else s2_err "Diagnostics tool not installed"; fi; s2_pause ;;
            6) echo "Full diagnostics may contain addresses/identifiers."; read -r -p "Type FULL to continue: " c; [ "$c" = FULL ] && saman-aether-diagnostics full; s2_pause ;;
            0) return ;;
        esac
    done
}

s2_aether_menu() {
    while true; do
        s2_clear; s2_title "NETWORK & AETHER CENTER"
        s2_aether_overview
        echo
        echo "1) Connect MASQUE H3"
        echo "2) Connect MASQUE H2"
        echo "3) Connect WireGuard"
        echo "4) Connect GOOL"
        echo "5) Stop Aether"
        echo "6) Refresh / connection test"
        echo "7) Logs / Diagnostics"
        echo "8) Detailed status"
        echo "0) Back"; echo
        x="$(s2_read_choice)"
        case "$x" in
            1) s2_aether_start h3 ;;
            2) s2_aether_start h2 ;;
            3) s2_aether_start wg ;;
            4) s2_aether_start gool ;;
            5) s2_aether_stop; s2_pause ;;
            6) echo; s2_aether_probe; s2_pause ;;
            7) s2_aether_logs_menu ;;
            8) echo; s2_aether_status; s2_pause ;;
            0) return ;;
        esac
    done
}
