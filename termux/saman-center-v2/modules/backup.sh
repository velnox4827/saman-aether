#!/data/data/com.termux/files/usr/bin/bash

s2_backup() {
    local outdir="$HOME/storage/downloads/SamanCenter/Backups" stamp out list
    mkdir -p "$outdir"; stamp="$(date +%Y%m%d-%H%M%S)"; out="$outdir/saman-suite-scripts-$stamp.tar.gz"
    list="$(mktemp)"
    for f in \
        "$HOME/bin/saman" "$HOME/bin/sdm" "$HOME/bin/media" "$HOME/bin/netcheck" \
        "$HOME/bin/saman-share" "$HOME/bin/saman-share-server.py" "$HOME/bin/saman-filebox" \
        "$HOME/bin/saman-imagebox" "$HOME/bin/saman-progress-lib.sh" "$HOME/bin/termux-url-opener" \
        "$HOME/.aether-shortcut-runner" "$PREFIX/bin/aether-control" \
        "$PREFIX/bin/saman-aether-diagnostics" "$PREFIX/bin/clear-screenshots"; do
        [ -f "$f" ] && printf '%s\n' "${f#/}" >> "$list"
    done
    if [ -d "$SAMAN2_ROOT" ]; then
        find "$SAMAN2_ROOT" -type f 2>/dev/null | sed 's#^/##' >> "$list" || true
    fi
    (cd / && tar -czf "$out" -T "$list")
    rm -f "$list"
    s2_ok "Backup created"
    echo "$out"
}

s2_backup_menu() {
    s2_clear; s2_title "BACKUP"
    echo "This creates a source/script snapshot only."
    echo "Cookies, SSH keys, downloads and personal files are excluded."
    echo
    read -r -p "Create backup now? [Y/n]: " x
    case "$x" in n|N) return;; esac
    s2_backup
    s2_pause
}
