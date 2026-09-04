#!/data/data/com.termux/files/usr/bin/bash

s2_screenshot_stats() {
    local count=0 bytes=0 dir n b
    for dir in "$HOME/storage/shared/DCIM/Screenshots" "$HOME/storage/shared/Pictures/Screenshots"; do
        [ -d "$dir" ] || continue
        n="$(find "$dir" -maxdepth 1 -type f \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.webp' \) -printf '.' 2>/dev/null | wc -c)"
        b="$(find "$dir" -maxdepth 1 -type f \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.webp' \) -printf '%s\n' 2>/dev/null | awk '{s+=$1}END{print s+0}')"
        count=$((count+n)); bytes=$((bytes+b))
    done
    printf '%s %s\n' "$count" "$bytes"
}

s2_screenshot_archive() {
    local stamp root dest dir label moved=0
    stamp="$(date +%Y%m%d-%H%M%S)"
    root="$HOME/storage/downloads/SamanCenter/Trash/Screenshots"
    mkdir -p "$root" || return 1
    dest="$(mktemp -d "$root/$stamp.XXXXXX")" || return 1
    for dir in "$HOME/storage/shared/DCIM/Screenshots" "$HOME/storage/shared/Pictures/Screenshots"; do
        [ -d "$dir" ] || continue
        case "$dir" in *DCIM*) label=DCIM;; *) label=Pictures;; esac
        mkdir -p "$dest/$label"
        while IFS= read -r -d '' f; do mv -n -- "$f" "$dest/$label/" && moved=$((moved+1)); done < <(find "$dir" -maxdepth 1 -type f \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.webp' \) -print0 2>/dev/null)
    done
    s2_ok "Archived $moved screenshots"
    echo "Archive: $dest"
}

s2_screenshot_delete() {
    local dir count=0 n
    read -r -p "Type DELETE to permanently delete all screenshots: " confirm
    [ "$confirm" = DELETE ] || { s2_warn "Cancelled"; return 0; }
    for dir in "$HOME/storage/shared/DCIM/Screenshots" "$HOME/storage/shared/Pictures/Screenshots"; do
        [ -d "$dir" ] || continue
        n="$(find "$dir" -maxdepth 1 -type f \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.webp' \) -printf '.' 2>/dev/null | wc -c)"
        if ! find "$dir" -maxdepth 1 -type f \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.webp' \) -delete; then
            s2_err "Could not delete every screenshot in $dir"
            return 1
        fi
        count=$((count+n))
    done
    s2_ok "Permanently deleted $count screenshots"
}

s2_clear_cache() {
    local canonical="$HOME/.cache/saman-center-v2"
    [ "$SAMAN2_CACHE" = "$canonical" ] || {
        s2_err "Refusing to clear unexpected cache path: $SAMAN2_CACHE"
        return 1
    }
    rm -rf -- "$SAMAN2_CACHE"
    s2_init_runtime
}

s2_maintenance_menu() {
    while true; do
        s2_clear; s2_title "MAINTENANCE"
        read -r count bytes < <(s2_screenshot_stats)
        echo "Screenshots : $count ($(s2_human_bytes "$bytes"))"
        echo
        echo "1) Archive screenshots (recommended)"
        echo "2) Delete screenshots permanently"
        echo "3) Clear Saman v2 cache"
        echo "0) Back"; echo
        x="$(s2_read_choice)"
        case "$x" in
            1) s2_screenshot_archive; s2_pause ;;
            2) s2_screenshot_delete; s2_pause ;;
            3) if s2_clear_cache; then s2_ok "Saman v2 cache cleared"; fi; s2_pause ;;
            0) return ;;
        esac
    done
}
