#!/data/data/com.termux/files/usr/bin/bash

s2_backup_copy() {
    local source="$1" destination_root="$2" relative="$3"
    [ -e "$source" ] || [ -L "$source" ] || return 0
    mkdir -p "$destination_root/${relative%/*}" || return 1
    cp -a -- "$source" "$destination_root/$relative"
}

s2_backup() (
    set -u
    set -o pipefail
    local outdir="${SAMAN2_BACKUP_DIR:-$HOME/.local/backups/saman-center}" stamp final partial stage='' verify='' checksum
    umask 077
    mkdir -p "$outdir" || { printf 'ERROR: cannot create backup directory: %s\n' "$outdir" >&2; exit 1; }
    chmod 0700 "$outdir" 2>/dev/null || true
    stamp="$(date +%Y%m%d-%H%M%S)-$RANDOM"
    final="$outdir/saman-scripts-$stamp.tar.gz"
    partial="$final.partial"
    checksum="$final.sha256"
    cleanup_backup() { rm -rf -- "$stage" "$verify" "$partial"; }
    trap cleanup_backup EXIT INT TERM
    stage="$(mktemp -d "$outdir/.stage.XXXXXX")" || exit 1
    verify="$(mktemp -d "$outdir/.verify.XXXXXX")" || exit 1

    mkdir -p "$stage/home" "$stage/prefix"
    s2_backup_copy "$SAMAN2_ROOT" "$stage/home" ".local/share/saman-center-v2"
    local rel
    for rel in bin/saman bin/saman2 .aether-shortcut-runner .shortcuts/Saman-Center .shortcuts/Saman-Center-v2; do
        s2_backup_copy "$HOME/$rel" "$stage/home" "$rel"
    done
    if [ -f "$SAMAN2_CONFIG/settings.conf" ]; then
        mkdir -p "$stage/home/.config/saman-center-v2" || exit 1
        s2_config_show > "$stage/home/.config/saman-center-v2/settings.conf" || exit 1
        chmod 0600 "$stage/home/.config/saman-center-v2/settings.conf"
    fi
    for rel in bin/aether-control bin/saman-aether-diagnostics etc/saman-aether-termux.version; do
        s2_backup_copy "$PREFIX/$rel" "$stage/prefix" "$rel"
    done

    (
        cd "$stage" || exit 1
        find home prefix -type f -print0 | sort -z | xargs -0 sha256sum > MANIFEST.sha256
    ) || { printf 'ERROR: could not create backup manifest\n' >&2; exit 1; }
    if ! tar -C "$stage" -czf "$partial" MANIFEST.sha256 home prefix; then
        printf 'ERROR: archive creation failed; no final backup was published.\n' >&2
        exit 1
    fi
    tar -xzf "$partial" -C "$verify" || { printf 'ERROR: archive extraction verification failed.\n' >&2; exit 1; }
    (cd "$verify" && sha256sum -c MANIFEST.sha256 >/dev/null) || {
        printf 'ERROR: archive content verification failed.\n' >&2
        exit 1
    }
    chmod 0600 "$partial"
    mv -f "$partial" "$final" || exit 1
    sha256sum "$final" > "$checksum" || { rm -f -- "$final"; exit 1; }
    chmod 0600 "$final" "$checksum"
    printf 'OK: backup created and verified\nArchive: %s\nChecksum: %s\n' "$final" "$checksum"
)

s2_backup_menu() {
    s2_clear; s2_title "BACKUP"
    echo "Creates a verified private snapshot of Saman scripts and safe settings."
    echo "Logs, downloads, cookies, SSH keys, and credentials are excluded."
    echo
    read -r -p "Create backup now? [Y/n]: " x
    case "$x" in n|N) return;; esac
    s2_backup
    s2_pause
}
