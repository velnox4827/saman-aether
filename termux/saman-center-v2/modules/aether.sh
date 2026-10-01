#!/data/data/com.termux/files/usr/bin/bash

s2_aether_core_pids() {
    # Diagnostic inventory only.  Lifecycle operations never act on this list.
    local proc exe core
    core="$(readlink -f "${SAMAN_AETHER_CORE:-$PREFIX/bin/saman-aether-core}" 2>/dev/null || true)"
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
    core="$(readlink -f "${SAMAN_AETHER_CORE:-$PREFIX/bin/saman-aether-core}" 2>/dev/null || true)"
    exe="$(readlink -f "/proc/$pid/exe" 2>/dev/null || true)"
    [ -n "$core" ] && [ "$exe" = "$core" ]
}

s2_process_start_identity() {
    local pid="$1" stat rest
    local -a fields=()
    [[ "$pid" =~ ^[0-9]+$ ]] && [ -r "/proc/$pid/stat" ] || return 1
    IFS= read -r stat < "/proc/$pid/stat" || return 1
    rest="${stat##*) }"; read -r -a fields <<< "$rest"
    [ "${#fields[@]}" -ge 20 ] && [[ "${fields[19]}" =~ ^[0-9]+$ ]] || return 1
    printf '%s\n' "${fields[19]}"
}

s2_aether_owned_runner() {
    local lock="$HOME/.saman-aether/runner.lock" owner pid start nonce current cmd runner base
    [ -L "$lock" ] || return 1
    owner="$(readlink "$lock" 2>/dev/null || true)"
    IFS=: read -r pid start nonce <<< "$owner"
    [[ "$pid" =~ ^[0-9]+$ && "$start" =~ ^[0-9]+$ && -n "$nonce" ]] || return 1
    kill -0 "$pid" 2>/dev/null || return 1
    current="$(s2_process_start_identity "$pid" 2>/dev/null || true)"
    [ "$current" = "$start" ] || return 1
    runner="$(readlink -f "${SAMAN_AETHER_RUNNER:-$HOME/.aether-shortcut-runner}" 2>/dev/null || true)"
    base="$(readlink -f "${SAMAN_AETHER_RUNNER_BASE:-$HOME/.aether-shortcut-runner-base}" 2>/dev/null || true)"
    [ -n "$runner" ] || return 1
    cmd="$(tr '\0' '\n' < "/proc/$pid/cmdline" 2>/dev/null || true)"
    grep -Fxq "$runner" <<< "$cmd" || { [ -n "$base" ] && grep -Fxq "$base" <<< "$cmd"; } || return 1
    printf '%s\n' "$pid"
}

s2_aether_owned_pid() {
    local pf="$HOME/.saman-aether/aether.pid" pid runner ppid
    runner="$(s2_aether_owned_runner)" || return 1
    [ -s "$pf" ] || return 1
    pid="$(<"$pf")"; s2_aether_pid_is_core "$pid" || return 1
    ppid="$(awk '/^PPid:/{print $2}' "/proc/$pid/status" 2>/dev/null || true)"
    [ "$ppid" = "$runner" ] || return 1
    printf '%s\n' "$pid"
}

s2_aether_pid() { s2_aether_owned_pid 2>/dev/null || true; }

s2_aether_pid_running() {
    local pid="${1:-$(s2_aether_pid)}"
    [ -n "$pid" ] && s2_aether_pid_is_core "$pid" && [ "$pid" = "$(s2_aether_owned_pid 2>/dev/null || true)" ]
}

s2_aether_log_for_mode() {
    case "${1:-}" in
        'WireGuard'|WG|wg) printf '%s\n' "$HOME/aether-wg.log" ;;
        GOOL|gool) printf '%s\n' "$HOME/aether-gool.log" ;;
        'MASQUE H2'|h2|masque-h2) printf '%s\n' "$HOME/aether-masque-h2.log" ;;
        'MASQUE H3'|h3|masque-h3) printf '%s\n' "$HOME/aether-masque-h3.log" ;;
        'MASQUE-in-MASQUE'|mim) printf '%s\n' "$HOME/aether-mim.log" ;;
        'Tor inside selected transport'|tor-inside) printf '%s\n' "$HOME/aether-tor-inside.log" ;;
        'Tor reverse over MASQUE H2'|tor-reverse) printf '%s\n' "$HOME/aether-tor-reverse.log" ;;
        'Tor only'|tor-only) printf '%s\n' "$HOME/aether-tor-only.log" ;;
        *) return 1 ;;
    esac
}

s2_aether_mode_from_logs() {
    local f newest='' mt=0 t label
    for label in 'WireGuard' GOOL 'MASQUE H3' 'MASQUE H2' 'MASQUE-in-MASQUE' \
        'Tor inside selected transport' 'Tor reverse over MASQUE H2' 'Tor only'; do
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
        *' --tor-only '*) echo 'Tor only' ;;
        *' --tor-reverse '*) echo 'Tor reverse over MASQUE H2' ;;
        *' --tor '*)
            case " $cmd " in
                *' --wg '*) echo 'Tor inside WireGuard' ;;
                *' --gool '*) echo 'Tor inside GOOL' ;;
                *' --mim '*) echo 'Tor inside MASQUE-in-MASQUE' ;;
                *' --h2 '*) echo 'Tor inside MASQUE H2' ;;
                *) echo 'Tor inside MASQUE H3' ;;
            esac ;;
        *' --gool '*) echo 'GOOL' ;;
        *' --wg '*) echo 'WireGuard' ;;
        *' --masque '*' --h2 '*) echo 'MASQUE H2' ;;
        *' --mim '*) echo 'MASQUE-in-MASQUE' ;;
        *' --masque '*) echo 'MASQUE H3' ;;
        *) s2_aether_mode_from_logs ;;
    esac
}

s2_tcp_port_accepting() {
    local port="$1"
    timeout 1 bash -c 'exec 3<>"/dev/tcp/127.0.0.1/$1"' _ "$port" >/dev/null 2>&1
}

s2_socket_listeners() {
    local snapshot='' rc=1 port ss_unusable=0

    if s2_have ss; then
        snapshot="$(ss -ltnH 2>&1)"; rc=$?

        if [ "$rc" -eq 0 ] &&
           [[ "$snapshot" != *'Permission denied'* ]] &&
           [[ "$snapshot" != *'Cannot open netlink socket'* ]]; then
            printf '%s\n' "$snapshot"
            return 0
        fi

        if [[ "$snapshot" == *'Permission denied'* ]] ||
           [[ "$snapshot" == *'Cannot open netlink socket'* ]]; then
            ss_unusable=1
        fi
    fi

    # When ss is unavailable for an ordinary reason, /proc is still useful.
    # But an Android/netlink permission failure must not be mistaken for a
    # trustworthy empty socket snapshot.
    if [ "$ss_unusable" -eq 0 ]; then
        snapshot="$(awk 'NR>1 && $4=="0A" {print $2}' /proc/net/tcp /proc/net/tcp6 2>/dev/null || true)"
        if [ -n "$snapshot" ]; then
            printf '%s\n' "$snapshot"
            return 0
        fi
    fi

    # Android may hide both netlink and socket tables. Probe only the two
    # configured loopback ports, with the existing one-second bound.
    for port in "${AETHER_SOCKS_PORT:-1819}" "${AETHER_HTTP_PORT:-1820}"; do
        if s2_tcp_port_accepting "$port"; then
            printf 'LISTEN 0 0 127.0.0.1:%s 0.0.0.0:*\n' "$port"
        fi
    done

    return 0
}

s2_snapshot_port_listening() {
    local port="$1" snapshot="$2" hex
    if ! grep -Eq '^[[:xdigit:]]{8,32}:[[:xdigit:]]{4}([[:space:]]|$)' <<< "$snapshot"; then
        awk -v p=":$port" '$4 ~ (p "$"){f=1} END{exit(f?0:1)}' <<< "$snapshot"
    else
        hex="$(printf '%04X' "$port")"
        awk -v p=":$hex" '$0 ~ p {f=1} END{exit(f?0:1)}' <<< "$snapshot"
    fi
}

s2_port_listening() {
    local port="$1" snapshot
    snapshot="$(s2_socket_listeners)" || return 1
    s2_snapshot_port_listening "$port" "$snapshot"
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
        printf '%s [official]\n' "$(s2_aether_core_version)"
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
    s2_port_listening "$AETHER_SOCKS_PORT" && socks=1
    s2_port_listening "$AETHER_HTTP_PORT" && http=1
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
    line="$(s2_terminal_field "$line")"
    [ "${#line}" -gt 88 ] && line="${line:0:85}..."
    printf '%s\n' "$line"
}

s2_aether_probe_file() { printf '%s/aether-probe.tsv\n' "$SAMAN2_CACHE"; }

s2_aether_probe_collect() {
    local trace ip cc curlout code total ms now f tmp
    f="$(s2_aether_probe_file)"
    mkdir -p "$SAMAN2_CACHE" 2>/dev/null || return 1
    tmp="$(mktemp "$f.tmp.XXXXXX")" || return 1
    s2_port_listening "$AETHER_SOCKS_PORT" || { rm -f -- "$tmp"; return 1; }
    trace="$(curl -4 --silent --max-time "$NETWORK_TIMEOUT" --socks5-hostname "127.0.0.1:$AETHER_SOCKS_PORT" https://www.cloudflare.com/cdn-cgi/trace 2>/dev/null || true)"
    ip="$(printf '%s\n' "$trace" | sed -n 's/^ip=//p' | head -n1)"
    cc="$(printf '%s\n' "$trace" | sed -n 's/^loc=//p' | head -n1)"
    curlout="$(curl -4 -o /dev/null -sS --max-time "$NETWORK_TIMEOUT" --socks5-hostname "127.0.0.1:$AETHER_SOCKS_PORT" -w '%{http_code} %{time_total}' https://www.cloudflare.com/cdn-cgi/trace 2>/dev/null || true)"
    code="${curlout%% *}"; total="${curlout#* }"; ms=''
    if [ -n "$total" ] && [ "$total" != "$curlout" ]; then ms="$(awk -v t="$total" 'BEGIN{printf "%.0f", t*1000}')"; fi
    now="$(date +%s)"
    printf '%s\t%s\t%s\t%s\t%s\n' "$now" "${ip:-}" "${cc:-}" "${ms:-}" "${code:-}" > "$tmp"
    chmod 0600 "$tmp" && mv -f "$tmp" "$f" || { rm -f -- "$tmp"; return 1; }
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
    local tracked='' pid='' mode='-' health uptime core candidate version_file="$PREFIX/etc/saman-aether-termux.version"
    local listeners='' socks=0 http=0
    local -a core_pids=()

    [ -s "$HOME/.saman-aether/aether.pid" ] && tracked="$(<"$HOME/.saman-aether/aether.pid")"
    while IFS= read -r candidate; do [ -n "$candidate" ] && core_pids+=("$candidate"); done < <(s2_aether_core_pids)
    if s2_aether_pid_is_core "$tracked"; then
        pid="$tracked"
    elif [ "${#core_pids[@]}" -eq 1 ]; then
        pid="${core_pids[0]}"
    fi

    listeners="$(s2_socket_listeners 2>/dev/null || true)"
    s2_snapshot_port_listening "$AETHER_SOCKS_PORT" "$listeners" && socks=1
    s2_snapshot_port_listening "$AETHER_HTTP_PORT" "$listeners" && http=1
    if s2_aether_pid_running "$pid"; then
        if [ "$socks" -eq 1 ] && [ "$http" -eq 1 ]; then health=READY
        elif [ "$socks" -eq 1 ] || [ "$http" -eq 1 ]; then health=PARTIAL
        else health=CONNECTING; fi
    elif [ "$socks" -eq 1 ] || [ "$http" -eq 1 ]; then
        health='PORT BUSY'
    else
        health=OFF
    fi

    core="$(s2_aether_core_label)"
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
        [ "${#core_pids[@]}" -gt 1 ] && echo "Processes    : WARNING (${#core_pids[@]} Aether Core processes)"
    else
        echo "Service      : STOPPED"
        echo "Mode         : -"
        [ "${#core_pids[@]}" -gt 0 ] && echo "Processes    : WARNING (${#core_pids[@]} untracked Aether Core processes)"
    fi
    echo "Health       : $health"
    if [ "$socks" -eq 1 ]; then echo "SOCKS5       : LISTENING 127.0.0.1:$AETHER_SOCKS_PORT"; else echo "SOCKS5       : DOWN 127.0.0.1:$AETHER_SOCKS_PORT"; fi
    if [ "$http" -eq 1 ]; then echo "HTTP CONNECT : LISTENING 127.0.0.1:$AETHER_HTTP_PORT"; else echo "HTTP CONNECT : DOWN 127.0.0.1:$AETHER_HTTP_PORT"; fi
    if ! s2_aether_pid_running "$pid" && { [ "$socks" -eq 1 ] || [ "$http" -eq 1 ]; }; then
        echo "WARNING      : local proxy port is owned by another process"
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
    if s2_port_listening "$AETHER_SOCKS_PORT"; then echo "SOCKS5       : READY :$AETHER_SOCKS_PORT"; else echo "SOCKS5       : DOWN  :$AETHER_SOCKS_PORT"; fi
    if s2_port_listening "$AETHER_HTTP_PORT"; then echo "HTTP CONNECT : READY :$AETHER_HTTP_PORT"; else echo "HTTP CONNECT : DOWN  :$AETHER_HTTP_PORT"; fi
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
    if ! s2_port_listening "$AETHER_SOCKS_PORT"; then
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

s2_aether_exit() { s2_aether_probe full; }

s2_aether_launch_runner() {
    local dispatch="$1" runner="${SAMAN_AETHER_RUNNER:-$HOME/.aether-shortcut-runner}" log="$2"
    nohup "$runner" "$dispatch" >> "$log" 2>&1 </dev/null &
    S2_AETHER_LAUNCH_PID=$!
}

s2_aether_wait_for_start() {
    local log="$1" deadline=$((SECONDS + ${SAMAN_AETHER_START_WAIT_SECS:-120})) pid
    while [ "$SECONDS" -lt "$deadline" ]; do
        pid="$(s2_aether_owned_pid 2>/dev/null || true)"
        if [ -n "$pid" ] && awk -v m="${S2_AETHER_START_MARKER:-}" 'index($0,m){seen=1;next} seen && /^OK: Aether connected/{ok=1} END{exit(ok?0:1)}' "$log" 2>/dev/null; then
            printf 'READY\n'; return 0
        fi
        if [ -n "${S2_AETHER_LAUNCH_PID:-}" ] && ! kill -0 "$S2_AETHER_LAUNCH_PID" 2>/dev/null; then
            printf 'FAILED\n'; return 1
        fi
        sleep 0.25
    done
    printf 'TIMEOUT\n'; return 1
}

s2_aether_start() {
    local mode="${1:-}" runner="${SAMAN_AETHER_RUNNER:-$HOME/.aether-shortcut-runner}" dispatch log result
    [ -x "$runner" ] || { s2_err "Aether runner not found: $runner"; return 1; }
    if s2_aether_owned_pid >/dev/null 2>&1 || s2_aether_owned_runner >/dev/null 2>&1; then
        s2_err "Aether is already managed by Saman; stop it before starting another."
        return 1
    fi
    rm -f "$(s2_aether_probe_file)" 2>/dev/null || true
    if [ -z "${AETHER_PANEL_TOR_MODE+x}" ]; then saman_aether_init; saman_aether_load; saman_aether_detect_capabilities >/dev/null 2>&1 || true; fi
    case "${AETHER_PANEL_TOR_MODE:-off}" in
        inside) dispatch=TOR ;;
        reverse) dispatch=TOR_REVERSE ;;
        only) dispatch=TOR_ONLY ;;
        off)
            case "${mode,,}" in
                wg|wireguard) dispatch=WG ;;
                gool) dispatch=GOOL ;;
                masque|h3|masque-h3|masque_h3) [ "${AETHER_PANEL_H2:-0}" = 1 ] && dispatch=MASQUE_H2 || dispatch=MASQUE ;;
                h2|masque-h2|masque_h2) dispatch=MASQUE_H2 ;;
                mim|masque-in-masque) dispatch=MIM ;;
                *) s2_err "Valid modes: wg, gool, h3, h2, mim"; return 2 ;;
            esac
            ;;
    esac
    log="$HOME/.saman-aether/start-${dispatch,,}.log"
    mkdir -p "${log%/*}"; chmod 0700 "${log%/*}" 2>/dev/null || true
    [ ! -f "$log" ] || mv -f "$log" "$log.previous"
    S2_AETHER_START_MARKER="[Saman launch $$.$RANDOM.$SECONDS]"
    printf '%s\n' "$S2_AETHER_START_MARKER" > "$log"; chmod 0600 "$log"
    printf 'Starting %s; detailed output: %s\n' "$dispatch" "$log"
    s2_aether_launch_runner "$dispatch" "$log" || { s2_err 'Could not launch Aether runner.'; return 1; }
    if result="$(s2_aether_wait_for_start "$log")"; then
        s2_ok "Aether is ready ($dispatch)."
        return 0
    fi
    s2_err "Aether startup ${result:-FAILED}; inspect $log"
    return 1
}

s2_aether_signal_pid() { kill -"$1" "$2" 2>/dev/null; }
s2_aether_wait_pid_exit() {
    local pid="$1"
    for _ in $(seq 1 50); do kill -0 "$pid" 2>/dev/null || return 0; sleep 0.1; done
    return 1
}

s2_aether_stop() {
    local runner core pf="$HOME/.saman-aether/aether.pid"
    runner="$(s2_aether_owned_runner 2>/dev/null || true)"
    core="$(s2_aether_owned_pid 2>/dev/null || true)"
    if [ -z "$runner" ] || [ -z "$core" ]; then
        if [ -e "$pf" ] || [ -e "$HOME/.saman-aether/runner.lock" ] || [ -L "$HOME/.saman-aether/runner.lock" ]; then
            s2_err 'Refusing to stop: Saman ownership proof is invalid; no process was signalled.'
            return 1
        fi
        s2_ok 'Aether is already stopped.'
        return 0
    fi
    s2_aether_signal_pid TERM "$runner" || { s2_err "Could not signal owning runner PID $runner."; return 1; }
    if ! s2_aether_wait_pid_exit "$runner"; then
        s2_warn "Owning runner PID $runner did not exit after TERM."
        return 1
    fi
    rm -f "$(s2_aether_probe_file)" 2>/dev/null || true
    s2_ok "Aether stopped (runner $runner, core $core)."
}

s2_aether_restart() {
    local mode="${1:-}"
    [ -n "$mode" ] || { s2_err "Restart requires a saved transport."; return 2; }
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
        echo "5) Tor inside transport log"
        echo "6) Tor reverse log"
        echo "7) Tor-only log"
        echo "8) Safe diagnostics"
        echo "9) Full diagnostics"
        echo "0) Back"; echo
        x="$(s2_read_choice)"
        case "$x" in
            1) tail -n 100 "$HOME/aether-wg.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            2) tail -n 100 "$HOME/aether-gool.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            3) tail -n 100 "$HOME/aether-masque-h3.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            4) tail -n 100 "$HOME/aether-masque-h2.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            5) tail -n 100 "$HOME/aether-tor-inside.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            6) tail -n 100 "$HOME/aether-tor-reverse.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            7) tail -n 100 "$HOME/aether-tor-only.log" 2>/dev/null || echo "Log not found"; s2_pause ;;
            8) if s2_have saman-aether-diagnostics; then saman-aether-diagnostics safe; else s2_err "Diagnostics tool not installed"; fi; s2_pause ;;
            9) echo "Full diagnostics may contain addresses/identifiers."; read -r -p "Type FULL to continue: " c; [ "$c" = FULL ] && saman-aether-diagnostics full; s2_pause ;;
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

# Optional panel layer: all arguments remain capability-gated official Aether flags.
# shellcheck source=modules/aether-panel.sh
source "$SAMAN2_ROOT/modules/aether-panel.sh"
# Optional Psiphon integration remains a separate official Console Client.
# shellcheck source=modules/psiphon.sh
source "$SAMAN2_ROOT/modules/psiphon.sh"
