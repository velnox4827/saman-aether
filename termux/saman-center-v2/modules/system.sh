#!/data/data/com.termux/files/usr/bin/bash

s2_logs_path() {
    local area="${1:-center}" newest='' file mtime best=0
    case "$area" in
        center) printf '%s\n' "$SAMAN2_LOG/saman.log" ;;
        wg|wireguard) printf '%s\n' "$HOME/aether-wg.log" ;;
        gool) printf '%s\n' "$HOME/aether-gool.log" ;;
        h3|masque-h3) printf '%s\n' "$HOME/aether-masque-h3.log" ;;
        h2|masque-h2) printf '%s\n' "$HOME/aether-masque-h2.log" ;;
        aether)
            for file in "$HOME"/aether-{wg,gool,masque-h3,masque-h2}.log; do
                [ -f "$file" ] || continue
                mtime="$(stat -c %Y "$file" 2>/dev/null || printf 0)"
                if [[ "$mtime" =~ ^[0-9]+$ ]] && [ "$mtime" -gt "$best" ]; then newest="$file"; best="$mtime"; fi
            done
            [ -n "$newest" ] && printf '%s\n' "$newest" || return 1
            ;;
        *) return 2 ;;
    esac
}

s2_logs() {
    local area="${1:-center}" lines=80 file
    [ "$#" -gt 0 ] && shift
    while [ "$#" -gt 0 ]; do
        case "$1" in
            --lines) [ "$#" -ge 2 ] || { printf 'ERROR: --lines requires a value\n' >&2; return 2; }; lines="$2"; shift 2 ;;
            *) printf 'ERROR: unknown logs option: %s\n' "$1" >&2; return 2 ;;
        esac
    done
    [[ "$lines" =~ ^[0-9]+$ ]] && [ "$lines" -ge 1 ] && [ "$lines" -le 1000 ] || {
        printf 'ERROR: --lines must be an integer from 1 to 1000\n' >&2
        return 2
    }
    if ! file="$(s2_logs_path "$area")"; then
        printf 'WARNING: no log is available for %s\n' "$area"
        return 0
    fi
    [ -f "$file" ] || { printf 'WARNING: log not found: %s\n' "$file"; return 0; }
    tail -n "$lines" -- "$file"
}

s2_trim_file() {
    local file="$1" max="$2" dry="$3" size tmp
    [ -f "$file" ] || return 1
    size="$(wc -c < "$file" 2>/dev/null || printf 0)"
    [[ "$size" =~ ^[0-9]+$ ]] || return 1
    [ "$size" -gt "$max" ] || return 1
    printf 'ACTION REQUIRED: trim %s from %s to at most %s bytes\n' "$file" "$size" "$max"
    [ "$dry" = yes ] && return 0
    tmp="$(mktemp "${file}.tmp.XXXXXX")" || return 2
    if tail -c "$max" -- "$file" > "$tmp" && chmod 0600 "$tmp" && mv -f "$tmp" "$file"; then return 0; fi
    rm -f -- "$tmp"
    return 2
}

s2_repair() {
    local dry=no changed=0 failed=0 pid_file="$HOME/.saman-aether/aether.pid" pid='' path file
    case "${1:-}" in '' ) ;; --dry-run) dry=yes ;; *) printf 'ERROR: usage: saman repair [--dry-run]\n' >&2; return 2 ;; esac

    for path in "$SAMAN2_STATE" "$SAMAN2_CACHE" "$SAMAN2_CONFIG" "$SAMAN2_LOG" "$HOME/.saman-aether"; do
        if [ ! -d "$path" ]; then
            printf 'ACTION REQUIRED: create private directory %s\n' "$path"; changed=$((changed+1))
            if [ "$dry" = no ]; then mkdir -p "$path" && chmod 0700 "$path" || failed=$((failed+1)); fi
        elif [ "$(stat -c %a "$path" 2>/dev/null || true)" != 700 ]; then
            printf 'ACTION REQUIRED: set private permissions on %s\n' "$path"; changed=$((changed+1))
            if [ "$dry" = no ]; then chmod 0700 "$path" || failed=$((failed+1)); fi
        fi
    done

    if [ -e "$pid_file" ]; then
        [ -s "$pid_file" ] && pid="$(<"$pid_file")"
        if ! s2_aether_pid_is_core "$pid"; then
            printf 'ACTION REQUIRED: remove stale Aether PID file %s\n' "$pid_file"; changed=$((changed+1))
            if [ "$dry" = no ]; then rm -f -- "$pid_file" || failed=$((failed+1)); fi
        fi
    fi

    if [ -d "$SAMAN2_CACHE" ]; then
        while IFS= read -r -d '' file; do
            printf 'ACTION REQUIRED: remove stale cache temporary %s\n' "$file"; changed=$((changed+1))
            if [ "$dry" = no ]; then rm -f -- "$file" || failed=$((failed+1)); fi
        done < <(find "$SAMAN2_CACHE" -maxdepth 1 -type f -name '*.tmp' -print0 2>/dev/null)
    fi

    for file in "$HOME"/aether-{wg,gool,masque-h3,masque-h2}.log; do
        if s2_trim_file "$file" "$LOG_MAX_CURRENT_BYTES" "$dry"; then changed=$((changed+1)); fi
    done
    for file in "$HOME"/aether-{wg,gool,masque-h3,masque-h2}.previous.log; do
        if s2_trim_file "$file" "$LOG_MAX_PREVIOUS_BYTES" "$dry"; then changed=$((changed+1)); fi
    done

    if [ "$failed" -gt 0 ]; then
        printf 'ERROR: %s repair action(s) failed. Check storage and permissions.\n' "$failed"
        s2_log_event ERROR "repair failed: $failed action(s)"
        return 1
    fi
    if [ "$dry" = yes ]; then
        if [ "$changed" -eq 0 ]; then printf 'OK: no safe repair is needed.\n'; else printf 'ACTION REQUIRED: %s safe repair action(s) available.\n' "$changed"; fi
    else
        printf 'OK: conservative repair complete; %s action(s) applied.\n' "$changed"
        s2_log_event INFO "repair completed: $changed action(s)"
    fi
}
