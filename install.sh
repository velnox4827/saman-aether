#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SAMAN_TERMUX_VERSION="1.7.0"
SAMAN_TUNNEL_VERSION="1.6.0"
REPO="velnox4827/saman-aether"
SOURCE_REF="${SAMAN_SOURCE_REF:-main}"
API_BASE="https://api.github.com/repos/$REPO"
ACTION="${1:-install}"

CORE_ALIAS="$PREFIX/bin/saman-aether-core"
DIAGNOSTICS_BIN="$PREFIX/bin/saman-aether-diagnostics"
CONTROL_BIN="$PREFIX/bin/aether-control"
VERSION_FILE="$PREFIX/etc/saman-aether-termux.version"
CENTER_ROOT="$HOME/.local/share/saman-center-v2"
RUNNER="$HOME/.aether-shortcut-runner"
BASE_RUNNER="$HOME/.aether-shortcut-runner-base"
SAMAN_BIN="$HOME/bin/saman"
SAMAN2_BIN="$HOME/bin/saman2"
SHORTCUT="$HOME/.shortcuts/Saman-Center"
SHORTCUT_V2="$HOME/.shortcuts/Saman-Center-v2"
STATE_DIR="$HOME/.saman-aether"
BACKUP_ROOT="$HOME/.saman-aether-backups"

TMP=""
BACKUP_DIR=""
INSTALL_STARTED=0
ROLLBACK_DONE=0
AETHER_COMMAND=""
AETHER_REAL=""
NETWORK_CONNECT_TIMEOUT=10
NETWORK_MAX_TIME=120

log() { printf '%s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

usage() {
    cat <<'USAGE'
Usage: install.sh [install|update|check|uninstall]

  install     Install/repair Saman around the existing official Aether command
  update      Update Saman only; official Aether is left untouched
  check       Show Saman and official Aether versions/paths
  uninstall   Remove Saman integration only; keep official Aether installed

Environment:
  SAMAN_SOURCE_REF=<git-ref>   Trusted Saman source ref (default: main)
  SAMAN_AETHER_BIN=<path>      Optional explicit official Aether command path

Saman Termux does not ship, patch, replace, or overwrite Aether. It only adds
presets, lifecycle management, status/diagnostics, and Termux/Widget launchers.
USAGE
}

require_termux() {
    [ -n "${PREFIX:-}" ] || die "PREFIX is not set; run this installer inside Termux."
    case "$PREFIX" in
        */com.termux/files/usr) ;;
        *) die "unsupported PREFIX: $PREFIX" ;;
    esac
}

install_requirements() {
    local command_name
    have pkg || die "Termux pkg command not found. Install Termux from F-Droid or GitHub."
    log "[1/5] Checking Termux dependencies..."
    pkg install -y bash curl coreutils procps grep sed tar jq iproute2 >/dev/null
    for command_name in bash curl tar jq awk sed readlink; do
        have "$command_name" || die "required command is unavailable: $command_name"
    done
}

resolve_upstream_aether() {
    local candidate="${SAMAN_AETHER_BIN:-}"
    [ -n "$candidate" ] || candidate="$(command -v aether 2>/dev/null || true)"
    [ -n "$candidate" ] || die "official Aether is not installed or is not on PATH. Install/update Aether itself first, then rerun Saman."
    [ "$candidate" != "$CORE_ALIAS" ] || die "refusing recursive Saman compatibility alias as upstream Aether"
    [ -x "$candidate" ] || die "Aether command is not executable: $candidate"
    AETHER_REAL="$(readlink -f "$candidate" 2>/dev/null || true)"
    [ -n "$AETHER_REAL" ] && [ -x "$AETHER_REAL" ] || die "could not resolve official Aether executable: $candidate"
    AETHER_COMMAND="$AETHER_REAL"
}

upstream_aether_version() {
    [ -n "$AETHER_COMMAND" ] || resolve_upstream_aether
    "$AETHER_COMMAND" --version 2>/dev/null | head -n1 || true
}

installed_version() {
    [ -s "$VERSION_FILE" ] && tr -d '\r\n' < "$VERSION_FILE" || printf 'not installed\n'
}

check_versions() {
    resolve_upstream_aether
    printf 'Installed Saman Termux : %s\n' "$(installed_version)"
    printf 'Installer integration  : %s\n' "$SAMAN_TERMUX_VERSION"
    printf 'Saman Tunnel stable     : %s\n' "$SAMAN_TUNNEL_VERSION"
    printf 'Aether source           : official upstream (unmodified)\n'
    printf 'Aether command          : %s\n' "$AETHER_COMMAND"
    printf 'Aether real path        : %s\n' "$AETHER_REAL"
    printf 'Aether version          : %s\n' "$(upstream_aether_version)"
}

api_json() {
    local url="$1" endpoint
    if have gh && gh auth status >/dev/null 2>&1; then
        endpoint="${url#https://api.github.com/}"
        gh api -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' "$endpoint"
    else
        curl --proto '=https' --tlsv1.2 -fsSL --connect-timeout "$NETWORK_CONNECT_TIMEOUT" \
            --max-time "$NETWORK_MAX_TIME" --retry 3 --retry-delay 2 \
            -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' "$url"
    fi
}

make_tmp() { TMP="$(mktemp -d)"; }

cleanup() {
    local rc=$?
    if [ "$rc" -ne 0 ] && [ "$INSTALL_STARTED" -eq 1 ] && [ "$ROLLBACK_DONE" -eq 0 ]; then
        rollback || printf 'CRITICAL: rollback failed; backup: %s\n' "$BACKUP_DIR" >&2
    fi
    [ -n "$TMP" ] && rm -rf "$TMP"
    exit "$rc"
}
trap cleanup EXIT INT TERM

targets() {
    printf '%s\n' "$CORE_ALIAS" "$DIAGNOSTICS_BIN" "$CONTROL_BIN" "$VERSION_FILE" \
        "$CENTER_ROOT" "$RUNNER" "$BASE_RUNNER" "$SAMAN_BIN" "$SAMAN2_BIN" \
        "$SHORTCUT" "$SHORTCUT_V2"
}

create_backup() {
    local target relative stamp
    umask 077
    stamp="$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$BACKUP_ROOT"
    BACKUP_DIR="$(mktemp -d "$BACKUP_ROOT/$stamp.XXXXXX")" || return 1
    mkdir -p "$BACKUP_DIR/root"
    : > "$BACKUP_DIR/existing.list"; : > "$BACKUP_DIR/absent.list"
    while IFS= read -r target; do
        relative="${target#/}"
        if [ -e "$target" ] || [ -L "$target" ]; then
            mkdir -p "$BACKUP_DIR/root/$(dirname "$relative")"
            cp -a "$target" "$BACKUP_DIR/root/$relative"
            printf '%s\n' "$target" >> "$BACKUP_DIR/existing.list"
        else
            printf '%s\n' "$target" >> "$BACKUP_DIR/absent.list"
        fi
    done < <(targets)
    printf '%s\n' "$SAMAN_TERMUX_VERSION" > "$BACKUP_DIR/installer.version"
    log "Backup: $BACKUP_DIR"
}

rollback() {
    local target relative
    [ -n "$BACKUP_DIR" ] && [ -d "$BACKUP_DIR" ] || return 0
    log "Installation failed; restoring backup..." >&2
    while IFS= read -r target; do [ -n "$target" ] && rm -rf "$target"; done < "$BACKUP_DIR/absent.list"
    while IFS= read -r target; do
        [ -n "$target" ] || continue
        relative="${target#/}"
        rm -rf "$target"; mkdir -p "$(dirname "$target")"
        cp -a "$BACKUP_DIR/root/$relative" "$target" || return 1
    done < "$BACKUP_DIR/existing.list"
    ROLLBACK_DONE=1
    log "Rollback complete." >&2
}

managed_pid_matches_upstream() {
    local pid="${1:-}" actual
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    kill -0 "$pid" 2>/dev/null || return 1
    [ -n "$AETHER_REAL" ] || resolve_upstream_aether
    actual="$(readlink -f "/proc/$pid/exe" 2>/dev/null || true)"
    [ -n "$actual" ] && [ "$actual" = "$AETHER_REAL" ]
}

stop_managed_aether() {
    local pid count=0
    [ -s "$STATE_DIR/aether.pid" ] || return 0
    pid="$(<"$STATE_DIR/aether.pid")"
    if ! managed_pid_matches_upstream "$pid"; then rm -f "$STATE_DIR/aether.pid"; return 0; fi
    log "Stopping Saman-managed upstream Aether PID $pid..."
    kill -TERM "$pid" 2>/dev/null || true
    while managed_pid_matches_upstream "$pid" && [ "$count" -lt 50 ]; do sleep 0.1; count=$((count + 1)); done
    managed_pid_matches_upstream "$pid" && kill -KILL "$pid" 2>/dev/null || true
    rm -f "$STATE_DIR/aether.pid"
}

download_source() {
    local commit archive root encoded_ref
    log "[2/5] Resolving trusted Saman source ref $SOURCE_REF..."
    encoded_ref="$(printf '%s' "$SOURCE_REF" | jq -sRr @uri)"
    commit="$(api_json "$API_BASE/commits/$encoded_ref" | jq -r '.sha // empty')" || die "could not resolve source ref: $SOURCE_REF"
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || die "GitHub returned an invalid source commit."
    archive="$TMP/source.tar.gz"
    curl --proto '=https' --tlsv1.2 -fL --connect-timeout "$NETWORK_CONNECT_TIMEOUT" \
        --max-time "$NETWORK_MAX_TIME" --retry 3 --retry-delay 2 \
        "https://github.com/$REPO/archive/$commit.tar.gz" -o "$archive"
    tar -tzf "$archive" >/dev/null || die "source archive integrity check failed."
    mkdir -p "$TMP/source"; tar -xzf "$archive" -C "$TMP/source"
    root="$(printf '%s\n' "$TMP/source"/* | head -n1)"
    [ -f "$root/install.sh" ] || die "source archive does not contain install.sh."
    [ -r "$root/aether-shortcut-runner" ] || die "source archive does not contain the Saman Aether runner."
    [ -x "$root/termux/saman-center-v2/saman2" ] || die "source archive does not contain Saman Center."
    [ -x "$root/saman-aether-diagnostics" ] || die "source archive does not contain diagnostics."
    [ -x "$root/termux/aether-control" ] || die "source archive does not contain aether-control."
    printf '%s\n' "$root" > "$TMP/source-root"
}

write_wrapper() {
    local destination="$1" target="$2" tmp="${1}.install.$$"
    printf '#!/data/data/com.termux/files/usr/bin/bash\nexec "%s" "$@"\n' "$target" > "$tmp"
    chmod 0755 "$tmp"; mv -f -- "$tmp" "$destination"
}

install_file_atomic() {
    local source="$1" destination="$2" mode="$3" tmp="${2}.install.$$"
    rm -f -- "$tmp"; install -m "$mode" "$source" "$tmp"; mv -f -- "$tmp" "$destination"
}

install_tree_atomic() {
    local source="$1" destination="$2" stage="${2}.install.$$" old="${2}.previous.$$"
    rm -rf -- "$stage" "$old"; cp -a "$source" "$stage"
    chmod 0755 "$stage/saman2" "$stage/lib/"*.sh "$stage/modules/"*.sh
    bash -n "$stage/saman2" "$stage/lib/"*.sh "$stage/modules/"*.sh
    [ ! -e "$destination" ] && [ ! -L "$destination" ] || mv -- "$destination" "$old"
    if ! mv -- "$stage" "$destination"; then [ -e "$old" ] && mv -- "$old" "$destination"; return 1; fi
    rm -rf -- "$old"
}

install_upstream_center_adapter() {
    local entry="$CENTER_ROOT/modules/aether.sh" base="$CENTER_ROOT/modules/aether-base.sh"
    mv -- "$entry" "$base"
    cat > "$entry" <<'ADAPTER'
#!/data/data/com.termux/files/usr/bin/bash
# Keep Saman's UI/lifecycle logic but identify the core as official upstream.
# shellcheck source=modules/aether-base.sh
source "$SAMAN2_ROOT/modules/aether-base.sh"
s2_aether_core_label() {
    if [ -x "$PREFIX/bin/saman-aether-core" ]; then
        printf '%s [official upstream]\n' "$(s2_aether_core_version)"
    else
        printf 'not installed\n'
    fi
}
ADAPTER
    chmod 0755 "$entry"; bash -n "$entry" "$base"
}

install_upstream_runner_adapter() {
    cat > "$RUNNER" <<'RUNNER'
#!/data/data/com.termux/files/usr/bin/bash
set -u
AETHER_BIN="${SAMAN_AETHER_BIN:-$(command -v aether 2>/dev/null || true)}"
if [ -z "$AETHER_BIN" ] || [ ! -x "$AETHER_BIN" ]; then
    printf 'ERROR: official Aether is not installed or executable.\n' >&2
    printf 'ACTION: install/update Aether itself in Termux, then retry Saman.\n' >&2
    exit 1
fi
AETHER_REAL="$(readlink -f "$AETHER_BIN" 2>/dev/null || true)"
[ -n "$AETHER_REAL" ] || { printf 'ERROR: cannot resolve official Aether: %s\n' "$AETHER_BIN" >&2; exit 1; }
case "$AETHER_REAL" in
    "$(readlink -f "$PREFIX/bin/saman-aether-core" 2>/dev/null || true)") : ;;
esac
export SAMAN_AETHER_CORE="$AETHER_REAL"
version_line="$("$AETHER_REAL" --version 2>/dev/null | head -n1 || true)"
version_number="$(printf '%s\n' "$version_line" | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+' | head -n1 || true)"
export SAMAN_AETHER_VERSION="${version_number:-upstream}"
exec "$HOME/.aether-shortcut-runner-base" "$@"
RUNNER
    chmod 0755 "$RUNNER"; bash -n "$RUNNER"
}

install_core_alias() {
    local tmp="${CORE_ALIAS}.install.$$"
    rm -f -- "$tmp"; ln -s "$AETHER_REAL" "$tmp"; mv -f -- "$tmp" "$CORE_ALIAS"
}

install_files() {
    local source_root
    source_root="$(<"$TMP/source-root")"
    log "[3/5] Backing up current Saman integration..."
    create_backup; INSTALL_STARTED=1; stop_managed_aether
    mkdir -p "$PREFIX/bin" "$PREFIX/etc" "$HOME/bin" "$HOME/.shortcuts" "$STATE_DIR" "$(dirname "$CENTER_ROOT")"
    install_tree_atomic "$source_root/termux/saman-center-v2" "$CENTER_ROOT"
    install_upstream_center_adapter
    install_file_atomic "$source_root/aether-shortcut-runner" "$BASE_RUNNER" 0755
    # The base runner's version banner now follows the current official Aether.
    sed -i 's/^AETHER_BASE_VERSION=.*/AETHER_BASE_VERSION="${SAMAN_AETHER_VERSION:-upstream}"/' "$BASE_RUNNER"
    bash -n "$BASE_RUNNER"
    install_upstream_runner_adapter
    install_file_atomic "$source_root/saman-aether-diagnostics" "$DIAGNOSTICS_BIN" 0755
    install_file_atomic "$source_root/termux/aether-control" "$CONTROL_BIN" 0755
    install_core_alias
    write_wrapper "$SAMAN_BIN" "$CENTER_ROOT/saman2"; write_wrapper "$SAMAN2_BIN" "$CENTER_ROOT/saman2"
    write_wrapper "$SHORTCUT" "$SAMAN_BIN"; write_wrapper "$SHORTCUT_V2" "$SAMAN_BIN"
    printf '%s\n' "$SAMAN_TERMUX_VERSION" | install_file_atomic /dev/stdin "$VERSION_FILE" 0644
}

verify_install() {
    local path alias_real
    log "[4/5] Verifying official Aether routing..."
    for path in "$DIAGNOSTICS_BIN" "$CONTROL_BIN" "$RUNNER" "$BASE_RUNNER" "$SAMAN_BIN" "$SAMAN2_BIN" "$SHORTCUT"; do
        [ -x "$path" ] || die "installed command is not executable: $path"
    done
    [ -L "$CORE_ALIAS" ] || die "Aether compatibility path is not a symlink."
    alias_real="$(readlink -f "$CORE_ALIAS" 2>/dev/null || true)"
    [ "$alias_real" = "$AETHER_REAL" ] || die "compatibility alias does not resolve to official Aether."
    bash -n "$RUNNER" "$BASE_RUNNER" "$CENTER_ROOT/saman2" "$CENTER_ROOT/lib/"*.sh "$CENTER_ROOT/modules/"*.sh
    [ "$("$SAMAN_BIN" version)" = "2.1.0" ] || die "Saman Center version check failed."
    [ "$(<"$VERSION_FILE")" = "$SAMAN_TERMUX_VERSION" ] || die "Saman Termux version verification failed."
    [ -n "$(upstream_aether_version)" ] || die "official Aether version check failed."
    INSTALL_STARTED=0; log "[5/5] Install verified."
}

uninstall() {
    require_termux
    command -v aether >/dev/null 2>&1 && resolve_upstream_aether || true
    create_backup; INSTALL_STARTED=1
    [ -z "$AETHER_REAL" ] || stop_managed_aether
    while IFS= read -r target; do rm -rf "$target"; done < <(targets)
    INSTALL_STARTED=0
    log "Saman Termux integration removed. Official upstream Aether was not modified or removed."
}

main() {
    case "$ACTION" in
        help|-h|--help) usage ;;
        check) require_termux; resolve_upstream_aether; check_versions ;;
        uninstall) uninstall ;;
        install|update)
            require_termux; install_requirements; resolve_upstream_aether; make_tmp; download_source
            install_files; verify_install
            log; log "Saman Termux integration: v$SAMAN_TERMUX_VERSION"
            log "Aether: $(upstream_aether_version)"
            log "Aether source: $AETHER_COMMAND (official upstream, unmodified)"
            log "Updating Aether itself automatically updates what Saman launches."
            log "Canonical command: saman"
            ;;
        *) usage >&2; exit 2 ;;
    esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then main "$@"; fi
