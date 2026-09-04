#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SAMAN_TERMUX_VERSION="1.5.0"
SAMAN_TUNNEL_VERSION="1.5.0"
AETHER_BASE_VERSION="1.8.0"
REPO="velnox4827/saman-aether"
PINNED_TERMUX_TAG="termux-v$SAMAN_TERMUX_VERSION"
SOURCE_REF="${SAMAN_SOURCE_REF:-main}"
API_BASE="https://api.github.com/repos/$REPO"
TERMUX_RELEASE_FALLBACK="termux-v1.5.0"
ACTION="${1:-install}"

CORE_BIN="$PREFIX/bin/saman-aether-core"
DIAGNOSTICS_BIN="$PREFIX/bin/saman-aether-diagnostics"
CONTROL_BIN="$PREFIX/bin/aether-control"
VERSION_FILE="$PREFIX/etc/saman-aether-termux.version"
CENTER_ROOT="$HOME/.local/share/saman-center-v2"
RUNNER="$HOME/.aether-shortcut-runner"
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
NETWORK_CONNECT_TIMEOUT=10
NETWORK_MAX_TIME=120

log() { printf '%s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

usage() {
    cat <<'EOF'
Usage: install.sh [install|update|check|uninstall]

  install     Clean install or idempotent repair (default)
  update      Check releases, back up, and update
  check       Report installed and latest stable versions
  uninstall   Back up and remove canonical Saman files

Environment:
  SAMAN_SOURCE_REF=<git-ref>  Optional trusted maintenance source ref (resolved to a commit)
EOF
}

detect_arch() {
    case "$(uname -m)" in
        aarch64|arm64) printf 'arm64\n' ;;
        armv7l|armv8l|arm) printf 'armv7\n' ;;
        x86_64|amd64) printf 'x86_64\n' ;;
        *) return 1 ;;
    esac
}

require_termux() {
    [ -n "${PREFIX:-}" ] || die "PREFIX is not set; run this installer inside Termux."
    case "$PREFIX" in
        */com.termux/files/usr) ;;
        *) die "unsupported PREFIX: $PREFIX" ;;
    esac
}

install_requirements() {
    have pkg || die "Termux pkg command not found. Install Termux from F-Droid or GitHub."
    log "[1/6] Checking Termux dependencies..."
    pkg install -y bash curl coreutils procps grep sed tar jq iproute2 >/dev/null
    for command_name in bash curl sha256sum tar jq ss awk sed; do
        have "$command_name" || die "required command is unavailable after package install: $command_name"
    done
}

check_readonly_requirements() {
    local missing=0 command_name
    for command_name in curl jq; do
        if ! have "$command_name"; then
            printf 'ERROR: required command is missing: %s\n' "$command_name" >&2
            missing=1
        fi
    done
    [ "$missing" -eq 0 ] || die "install missing dependencies with: pkg install curl jq"
}

api_json() {
    local url="$1" endpoint
    if have gh && gh auth status >/dev/null 2>&1; then
        endpoint="${url#https://api.github.com/}"
        gh api -H 'Accept: application/vnd.github+json' \
            -H 'X-GitHub-Api-Version: 2022-11-28' "$endpoint"
    else
        curl --proto '=https' --tlsv1.2 -fsSL --connect-timeout "$NETWORK_CONNECT_TIMEOUT" --max-time "$NETWORK_MAX_TIME" --retry 3 --retry-delay 2 \
            -H 'Accept: application/vnd.github+json' \
            -H 'X-GitHub-Api-Version: 2022-11-28' "$url"
    fi
}

latest_android_tag() {
    api_json "$API_BASE/releases?per_page=30" |
        jq -r '[.[] | select((.draft|not) and (.prerelease|not) and (.tag_name|test("^v[0-9]+\\.[0-9]+\\.[0-9]+$")))] | first | .tag_name // empty'
}

latest_termux_release_json() {
    api_json "$API_BASE/releases?per_page=30" |
        jq -c '[.[] | select((.draft|not) and (.prerelease|not) and (.tag_name|test("^termux-v[0-9]+\\.[0-9]+\\.[0-9]+$")))] | first // empty'
}

pinned_termux_release_json() {
    api_json "$API_BASE/releases/tags/$PINNED_TERMUX_TAG"
}

installed_version() {
    if [ -s "$VERSION_FILE" ]; then
        tr -d '\r\n' < "$VERSION_FILE"
    else
        printf 'not installed\n'
    fi
}

check_versions() {
    local android_tag termux_json termux_tag
    android_tag="$(latest_android_tag)" || die "could not query the latest Android release."
    termux_json="$(latest_termux_release_json)" || die "could not query the latest Termux release."
    termux_tag="$(jq -r '.tag_name // empty' <<<"$termux_json")"
    [ -n "$termux_tag" ] || termux_tag="$TERMUX_RELEASE_FALLBACK"
    printf 'Installed Saman Termux : %s\n' "$(installed_version)"
    printf 'Installer integration  : %s\n' "$SAMAN_TERMUX_VERSION"
    printf 'Latest Termux core      : %s\n' "$termux_tag"
    printf 'Saman Tunnel stable     : %s\n' "${android_tag:-unavailable}"
    printf 'Aether Core base        : %s\n' "$AETHER_BASE_VERSION"
}

make_tmp() {
    TMP="$(mktemp -d)"
}

cleanup() {
    local rc=$?
    if [ "$rc" -ne 0 ] && [ "$INSTALL_STARTED" -eq 1 ] && [ "$ROLLBACK_DONE" -eq 0 ]; then
        if ! rollback; then
            printf 'CRITICAL: automatic rollback failed; use backup: %s\n' "$BACKUP_DIR" >&2
        fi
    fi
    [ -n "$TMP" ] && rm -rf "$TMP"
    exit "$rc"
}
trap cleanup EXIT INT TERM

targets() {
    printf '%s\n' \
        "$CORE_BIN" "$DIAGNOSTICS_BIN" "$CONTROL_BIN" "$VERSION_FILE" \
        "$CENTER_ROOT" "$RUNNER" "$SAMAN_BIN" "$SAMAN2_BIN" \
        "$SHORTCUT" "$SHORTCUT_V2"
}

create_backup() {
    local target relative stamp
    umask 077
    stamp="$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$BACKUP_ROOT"
    BACKUP_DIR="$(mktemp -d "$BACKUP_ROOT/$stamp.XXXXXX")" || return 1
    mkdir -p "$BACKUP_DIR/root" || return 1
    : > "$BACKUP_DIR/existing.list"
    : > "$BACKUP_DIR/absent.list"
    while IFS= read -r target; do
        relative="${target#/}"
        if [ -e "$target" ] || [ -L "$target" ]; then
            mkdir -p "$BACKUP_DIR/root/$(dirname "$relative")"
            cp -a "$target" "$BACKUP_DIR/root/$relative" || return 1
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
    if [ -f "$BACKUP_DIR/absent.list" ]; then
        while IFS= read -r target; do
            [ -n "$target" ] && rm -rf "$target" || return 1
        done < "$BACKUP_DIR/absent.list"
    fi
    if [ -f "$BACKUP_DIR/existing.list" ]; then
        while IFS= read -r target; do
            [ -n "$target" ] || continue
            relative="${target#/}"
            rm -rf "$target" || return 1
            mkdir -p "$(dirname "$target")" || return 1
            cp -a "$BACKUP_DIR/root/$relative" "$target" || return 1
        done < "$BACKUP_DIR/existing.list"
    fi
    ROLLBACK_DONE=1
    log "Rollback complete." >&2
}

core_pid_matches_installed_binary() {
    local pid="${1:-}" expected actual
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    kill -0 "$pid" 2>/dev/null || return 1
    expected="$(realpath -m "$CORE_BIN" 2>/dev/null || true)"
    actual="$(readlink "/proc/$pid/exe" 2>/dev/null || true)"
    actual="${actual% (deleted)}"
    [ -n "$expected" ] && [ "$actual" = "$expected" ]
}

installed_core_pids() {
    local proc
    for proc in /proc/[0-9]*; do
        core_pid_matches_installed_binary "${proc##*/}" && printf '%s\n' "${proc##*/}"
    done
}

stop_canonical_core() {
    local pids=() pid alive=0
    while IFS= read -r pid; do [ -n "$pid" ] && pids+=("$pid"); done < <(installed_core_pids)
    [ "${#pids[@]}" -gt 0 ] || { rm -f "$STATE_DIR/aether.pid"; return 0; }
    log "Stopping ${#pids[@]} installed canonical Aether Core process(es)..."
    for pid in "${pids[@]}"; do
        core_pid_matches_installed_binary "$pid" && kill -TERM "$pid" 2>/dev/null || true
    done
    for _ in $(seq 1 50); do
        alive=0
        for pid in "${pids[@]}"; do core_pid_matches_installed_binary "$pid" && alive=1; done
        [ "$alive" -eq 0 ] && break
        sleep 0.1
    done
    for pid in "${pids[@]}"; do
        if core_pid_matches_installed_binary "$pid"; then
            kill -KILL "$pid" 2>/dev/null || true
        fi
    done
    sleep 0.1
    if [ -n "$(installed_core_pids)" ]; then
        die "could not stop all installed canonical Aether Core processes; live files were not replaced."
    fi
    rm -f "$STATE_DIR/aether.pid"
}

installer_tcp_port_accepting() {
    local port="$1"
    timeout 1 bash -c 'exec 3<>"/dev/tcp/127.0.0.1/$1"' _ "$port" >/dev/null 2>&1
}

local_proxy_ports_free() {
    local listeners rc
    listeners="$(ss -ltnH 2>&1)"; rc=$?
    if [ "$rc" -eq 0 ] && [[ "$listeners" != *'Permission denied'* ]] &&
       [[ "$listeners" != *'Cannot open netlink socket'* ]]; then
        ! awk '$4 ~ /:(1819|1820)$/ {found=1} END{exit(found?0:1)}' <<<"$listeners"
        return $?
    fi
    ! installer_tcp_port_accepting 1819 && ! installer_tcp_port_accepting 1820
}

download_source() {
    local commit archive root encoded_ref
    log "[2/6] Resolving trusted source ref $SOURCE_REF..."
    encoded_ref="$(printf '%s' "$SOURCE_REF" | jq -sRr @uri)"
    commit="$(api_json "$API_BASE/commits/$encoded_ref" | jq -r '.sha // empty')" ||
        die "could not resolve GitHub source ref: $SOURCE_REF"
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || die "GitHub returned an invalid source commit."
    archive="$TMP/source.tar.gz"
    curl --proto '=https' --tlsv1.2 -fL --connect-timeout "$NETWORK_CONNECT_TIMEOUT" --max-time "$NETWORK_MAX_TIME" --retry 3 --retry-delay 2 \
        "https://github.com/$REPO/archive/$commit.tar.gz" -o "$archive"
    tar -tzf "$archive" >/dev/null || die "source archive integrity check failed."
    mkdir -p "$TMP/source"
    tar -xzf "$archive" -C "$TMP/source"
    root="$(printf '%s\n' "$TMP/source"/* | head -n1)"
    [ -f "$root/install.sh" ] || die "source archive does not contain install.sh."
    [ -x "$root/aether-shortcut-runner" ] || die "source archive does not contain the runner."
    [ -x "$root/termux/saman-center-v2/saman2" ] || die "source archive does not contain Saman Center."
    [ -x "$root/saman-aether-diagnostics" ] || die "source archive does not contain diagnostics."
    [ -x "$root/termux/aether-control" ] || die "source archive does not contain aether-control."
    compgen -G "$root/termux/saman-center-v2/lib/*.sh" >/dev/null || die "source archive has no Center libraries."
    compgen -G "$root/termux/saman-center-v2/modules/*.sh" >/dev/null || die "source archive has no Center modules."
    printf '%s\n' "$root" > "$TMP/source-root"
    printf '%s\n' "$commit" > "$TMP/source-commit"
}

resolve_termux_release() {
    local release_json tag
    case "$ACTION" in
        install)
            release_json="$(pinned_termux_release_json)" ||
                die "could not query pinned Termux release $PINNED_TERMUX_TAG."
            ;;
        update)
            release_json="$(latest_termux_release_json)" || die "could not query Termux releases."
            ;;
        *) die "unsupported core release action: $ACTION" ;;
    esac
    tag="$(jq -r '.tag_name // empty' <<<"$release_json")"
    [ -n "$tag" ] || die "no stable Termux core release was found."
    [ "$(jq -r '.draft or .prerelease' <<<"$release_json")" = false ] ||
        die "Termux core release must be stable."
    if [ "$ACTION" = install ]; then
        [ "$tag" = "$PINNED_TERMUX_TAG" ] || die "pinned Termux release tag mismatch."
    else
        [[ "$tag" =~ ^termux-v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "invalid Termux release tag."
        [ "$tag" = "$PINNED_TERMUX_TAG" ] || die "a newer Termux release exists ($tag); download its matching installer before updating."
    fi
    printf '%s\n' "$release_json" > "$TMP/termux-release.json"
    printf '%s\n' "$tag" > "$TMP/termux-tag"
}

download_core() {
    local arch release_json tag archive url checksum_url expected actual
    arch="$(detect_arch)" || die "unsupported architecture: $(uname -m)"
    [ -s "$TMP/termux-release.json" ] || resolve_termux_release
    release_json="$(<"$TMP/termux-release.json")"
    tag="$(<"$TMP/termux-tag")"
    archive="saman-aether-termux-${arch}.tar.gz"
    url="$(jq -r --arg name "$archive" '.assets[] | select(.name == $name) | .browser_download_url' <<<"$release_json")"
    checksum_url="$(jq -r --arg name "$archive.sha256" '.assets[] | select(.name == $name) | .browser_download_url' <<<"$release_json")"
    [[ "$url" == "https://github.com/$REPO/releases/download/$tag/$archive" ]] ||
        die "trusted core asset not found for $arch in $tag."
    [[ "$checksum_url" == "https://github.com/$REPO/releases/download/$tag/$archive.sha256" ]] ||
        die "trusted checksum asset not found for $arch in $tag."
    log "[3/6] Downloading $tag core for $arch..."
    curl --proto '=https' --tlsv1.2 -fL --connect-timeout "$NETWORK_CONNECT_TIMEOUT" --max-time "$NETWORK_MAX_TIME" --retry 3 --retry-delay 2 "$url" -o "$TMP/$archive"
    expected="$(curl --proto '=https' --tlsv1.2 -fsSL --connect-timeout "$NETWORK_CONNECT_TIMEOUT" --max-time "$NETWORK_MAX_TIME" --retry 3 "$checksum_url" | awk 'NR==1 {print $1}')"
    actual="$(sha256sum "$TMP/$archive" | awk '{print $1}')"
    [[ "$expected" =~ ^[0-9a-fA-F]{64}$ ]] || die "release checksum is invalid."
    [ "${expected,,}" = "$actual" ] || die "SHA-256 mismatch; refusing to install."
    tar -tzf "$TMP/$archive" | grep -qx 'saman-aether-core' || die "core archive layout is invalid."
    tar -xzf "$TMP/$archive" -C "$TMP" saman-aether-core
    chmod 0755 "$TMP/saman-aether-core"
    log "SHA-256: verified"
}

write_wrapper() {
    local destination target tmp
    destination="$1"; target="$2"
    tmp="${destination}.install.$$"
    printf '#!/data/data/com.termux/files/usr/bin/bash\nexec "%s" "$@"\n' "$target" > "$tmp"
    chmod 0755 "$tmp"
    mv -f -- "$tmp" "$destination"
}

install_file_atomic() {
    local source="$1" destination="$2" mode="$3" tmp
    tmp="${destination}.install.$$"
    rm -f -- "$tmp"
    install -m "$mode" "$source" "$tmp"
    mv -f -- "$tmp" "$destination"
}

install_tree_atomic() {
    local source="$1" destination="$2" stage old
    stage="${destination}.install.$$"
    old="${destination}.previous.$$"
    rm -rf -- "$stage" "$old"
    cp -a "$source" "$stage"
    chmod 0755 "$stage/saman2" "$stage/lib/"*.sh "$stage/modules/"*.sh
    bash -n "$stage/saman2" "$stage/lib/"*.sh "$stage/modules/"*.sh
    if [ -e "$destination" ] || [ -L "$destination" ]; then
        mv -- "$destination" "$old"
    fi
    if ! mv -- "$stage" "$destination"; then
        [ -e "$old" ] && mv -- "$old" "$destination"
        return 1
    fi
    rm -rf -- "$old"
}

install_files() {
    local source_root
    source_root="$(<"$TMP/source-root")"
    log "[4/6] Backing up the current installation..."
    create_backup
    INSTALL_STARTED=1
    stop_canonical_core
    mkdir -p "$PREFIX/bin" "$PREFIX/etc" "$HOME/bin" "$HOME/.shortcuts" "$STATE_DIR" "$(dirname "$CENTER_ROOT")"
    install_tree_atomic "$source_root/termux/saman-center-v2" "$CENTER_ROOT"
    install_file_atomic "$TMP/saman-aether-core" "$CORE_BIN" 0755
    install_file_atomic "$source_root/saman-aether-diagnostics" "$DIAGNOSTICS_BIN" 0755
    install_file_atomic "$source_root/termux/aether-control" "$CONTROL_BIN" 0755
    install_file_atomic "$source_root/aether-shortcut-runner" "$RUNNER" 0755
    write_wrapper "$SAMAN_BIN" "$CENTER_ROOT/saman2"
    write_wrapper "$SAMAN2_BIN" "$CENTER_ROOT/saman2"
    write_wrapper "$SHORTCUT" "$SAMAN_BIN"
    write_wrapper "$SHORTCUT_V2" "$SAMAN_BIN"
    printf '%s\n' "$SAMAN_TERMUX_VERSION" | install_file_atomic /dev/stdin "$VERSION_FILE" 0644
}

verify_install() {
    local command_path
    log "[5/6] Verifying canonical command routing..."
    for command_path in "$CORE_BIN" "$DIAGNOSTICS_BIN" "$CONTROL_BIN" "$RUNNER" "$SAMAN_BIN" "$SAMAN2_BIN" "$SHORTCUT"; do
        [ -x "$command_path" ] || die "installed command is not executable: $command_path"
    done
    bash -n "$RUNNER" "$DIAGNOSTICS_BIN" "$CONTROL_BIN" "$SAMAN_BIN" "$SAMAN2_BIN" "$CENTER_ROOT/saman2" "$CENTER_ROOT/lib/"*.sh "$CENTER_ROOT/modules/"*.sh
    [ "$("$SAMAN_BIN" version)" = "2.1.0" ] || die "Saman Center version check failed."
    "$CORE_BIN" --version 2>/dev/null | grep -q '1\.8\.0' || die "Aether Core version check failed."
    [ "$(<"$VERSION_FILE")" = "$SAMAN_TERMUX_VERSION" ] || die "version file verification failed."
    [ -z "$(installed_core_pids)" ] || die "an old canonical Aether Core process survived the update."
    local_proxy_ports_free || die "local proxy port 1819 or 1820 is already in use; rolling back."
    INSTALL_STARTED=0
    log "[6/6] Install verified."
}

uninstall() {
    require_termux
    create_backup
    INSTALL_STARTED=1
    stop_canonical_core
    while IFS= read -r target; do rm -rf "$target"; done < <(targets)
    INSTALL_STARTED=0
    log "Canonical Saman Termux files removed."
    log "User configuration, logs, backups, upstream aether, and legacy compatibility shortcuts were retained."
}

main() {
    case "$ACTION" in
        help|-h|--help) usage ;;
        check) require_termux; check_readonly_requirements; check_versions ;;
        uninstall) uninstall ;;
        install|update)
            require_termux
            install_requirements
            make_tmp
            resolve_termux_release
            download_source
            download_core
            install_files
            verify_install
            log
            log "Saman Termux integration: v$SAMAN_TERMUX_VERSION"
            log "Saman Tunnel stable: v$SAMAN_TUNNEL_VERSION"
            log "Aether Core: v$AETHER_BASE_VERSION ($(cat "$TMP/termux-tag"))"
            log "Canonical command: saman"
            log "Compatibility commands: saman2, aether-control"
            log "Local proxies: SOCKS5 127.0.0.1:1819; HTTP CONNECT 127.0.0.1:1820"
            log "Refresh Termux:Widget after installation."
            ;;
        *) usage >&2; exit 2 ;;
    esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
