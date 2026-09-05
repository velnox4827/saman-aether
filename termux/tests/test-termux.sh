#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_TMP="$(mktemp -d)"
trap 'rm -rf "$TEST_TMP"' EXIT

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS: %s\n' "$*"; }

# Syntax and executable checks.
mapfile -t shell_files < <(printf '%s\n' \
    "$ROOT/install.sh" \
    "$ROOT/aether-shortcut-runner" \
    "$ROOT/saman-aether-diagnostics" \
    "$ROOT/termux/aether-control" \
    "$ROOT/termux/saman-center-v2/saman2" \
    "$ROOT/termux/saman-center-v2/lib/"*.sh \
    "$ROOT/termux/saman-center-v2/modules/"*.sh)
bash -n "${shell_files[@]}"
for file in "${shell_files[@]}"; do [ -x "$file" ] || fail "not executable: $file"; done
pass "shell syntax and executable permissions"

# The same warning-level ShellCheck gate used by CI must stay green locally.
if command -v shellcheck >/dev/null 2>&1; then
    shellcheck --severity=warning "${shell_files[@]}"
    pass "warning-level ShellCheck gate"
fi

# Installer architecture mapping is deterministic and rejects unsupported CPUs.
PREFIX="${PREFIX:-$TEST_TMP/prefix}"
# shellcheck source=../../install.sh
source "$ROOT/install.sh"
uname() { printf 'aarch64\n'; }
[ "$(detect_arch)" = arm64 ] || fail "aarch64 mapping"
uname() { printf 'armv7l\n'; }
[ "$(detect_arch)" = armv7 ] || fail "armv7 mapping"
uname() { printf 'x86_64\n'; }
[ "$(detect_arch)" = x86_64 ] || fail "x86_64 mapping"
uname() { printf 'riscv64\n'; }
if detect_arch >/dev/null; then fail "unsupported architecture accepted"; fi
pass "architecture detection"

# Safety-critical update stop logic finds the exact installed executable without
# trusting pre-existing wrappers, then leaves unrelated processes untouched.
REAL_CORE_BIN="$CORE_BIN"
REAL_STATE_DIR="$STATE_DIR"
CORE_BIN="$TEST_TMP/saman-aether-core"
STATE_DIR="$TEST_TMP/state"
mkdir -p "$STATE_DIR"
printf '%s\n' '#include <unistd.h>' 'int main(void) { sleep(30); return 0; }' > "$TEST_TMP/fake-core.c"
"${CC:-cc}" "$TEST_TMP/fake-core.c" -o "$CORE_BIN"
"$CORE_BIN" &
test_core_pid=$!
printf '%s\n' "$test_core_pid" > "$STATE_DIR/aether.pid"
core_pid_matches_installed_binary "$test_core_pid" || fail "exact core PID identity"
[ "$(installed_core_pids)" = "$test_core_pid" ] || fail "core process discovery"
stop_canonical_core
if kill -0 "$test_core_pid" 2>/dev/null; then fail "canonical core survived stop"; fi
wait "$test_core_pid" 2>/dev/null || true
CORE_BIN="$REAL_CORE_BIN"
STATE_DIR="$REAL_STATE_DIR"
pass "exact core process stop"

# The check prerequisite path is read-only and never invokes pkg.
pkg() { fail "check attempted package installation"; }
check_readonly_requirements
unset -f pkg
pass "read-only check prerequisites"

# Port verification fails closed when socket inspection itself fails.
ss() { return 1; }
if local_proxy_ports_free; then fail "ss failure was treated as free ports"; fi
ss() { printf 'LISTEN 0 128 127.0.0.1:1819 0.0.0.0:*\n'; }
if local_proxy_ports_free; then fail "busy canonical port was treated as free"; fi
ss() { return 0; }
local_proxy_ports_free || fail "empty listener set was treated as busy"
ss() { printf 'Cannot open netlink socket: Permission denied\n' >&2; return 0; }
installer_tcp_port_accepting() { [ "$1" = 1819 ]; }
if local_proxy_ports_free; then fail "permission-denied ss hid an occupied loopback port"; fi
installer_tcp_port_accepting() { return 1; }
local_proxy_ports_free || fail "TCP fallback treated refused loopback ports as busy"
unset -f ss
pass "fail-closed port verification"

# Canonical installer must not recreate obsolete independent Aether shortcuts.
if grep -Eq '(^|/)(0-STOP-Aether|1-Aether-MASQUE|2-Aether-WG|3-Aether-GOOL)(["[:space:]]|$)' "$ROOT/install.sh"; then
    fail "installer references obsolete independent Aether shortcuts"
fi
grep -q 'Saman-Center' "$ROOT/install.sh" || fail "canonical Saman Center shortcut missing"
pass "canonical shortcut policy"

# Compatibility wrapper validates input and delegates exactly to ~/bin/saman.
FAKE_HOME="$TEST_TMP/home"
mkdir -p "$FAKE_HOME/bin"
cat > "$FAKE_HOME/bin/saman" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$*" > "$HOME/delegated"
MOCK
chmod +x "$FAKE_HOME/bin/saman"
HOME="$FAKE_HOME" bash "$ROOT/termux/aether-control" start h3
[ "$(<"$FAKE_HOME/delegated")" = 'aether start h3' ] || fail "aether-control delegation"
if HOME="$FAKE_HOME" bash "$ROOT/termux/aether-control" start invalid >/dev/null 2>&1; then
    fail "invalid mode accepted"
fi
HOME="$FAKE_HOME" bash "$ROOT/termux/aether-control" diagnostics safe
[ "$(<"$FAKE_HOME/delegated")" = 'aether diagnostics safe' ] || fail "diagnostics delegation"
pass "aether-control validation and delegation"

# Installer version, source pin, and release URLs are explicit and trusted.
grep -q 'SAMAN_TERMUX_VERSION="1.6.0"' "$ROOT/install.sh" || fail "Termux integration version"
grep -q 'SOURCE_REF="${SAMAN_SOURCE_REF:-main}"' "$ROOT/install.sh" || fail "maintenance source ref is separated from the core release tag"
grep -q 'TERMUX_RELEASE_FALLBACK="termux-v1.6.0"' "$ROOT/install.sh" || fail "Termux release fallback"
if grep -q 'termux-v1.4.0' "$ROOT/install.sh"; then fail "active legacy Termux release reference"; fi
grep -q 'https://github.com/\$REPO/releases/download/' "$ROOT/install.sh" || fail "trusted release URL check"
grep -q 'SHA-256 mismatch; refusing to install' "$ROOT/install.sh" || fail "checksum fail-closed behavior"
grep -q 'rollback' "$ROOT/install.sh" || fail "rollback support"
grep -q '\[ "$("\$SAMAN_BIN" version)" = "2.1.0" \]' "$ROOT/install.sh" || fail "installer verifies current Center version"
if grep -q 'rm -rf "\$CENTER_ROOT"' "$ROOT/install.sh"; then fail "installer destructively removes live Center before publication"; fi
grep -q 'install_tree_atomic' "$ROOT/install.sh" || fail "installer lacks atomic Center publication"
pass "installer update safety policy"

# A v1.6.0 install must ignore a hypothetical newer stable Termux release and
# select the pinned v1.6.0 core asset plus checksum for every supported arch.
release_object() {
    local tag="$1" arch assets=''
    for arch in arm64 armv7 x86_64; do
        assets+="{\"name\":\"saman-aether-termux-$arch.tar.gz\",\"browser_download_url\":\"https://github.com/$REPO/releases/download/$tag/saman-aether-termux-$arch.tar.gz\"},"
        assets+="{\"name\":\"saman-aether-termux-$arch.tar.gz.sha256\",\"browser_download_url\":\"https://github.com/$REPO/releases/download/$tag/saman-aether-termux-$arch.tar.gz.sha256\"},"
    done
    printf '{\"tag_name\":\"%s\",\"draft\":false,\"prerelease\":false,\"assets\":[%s]}' "$tag" "${assets%,}"
}
api_json() {
    case "$1" in
        "$API_BASE/releases/tags/termux-v1.6.0") release_object termux-v1.6.0 ;;
        "$API_BASE/releases?per_page=30")
            printf '[%s,%s]
' "$(release_object termux-v1.7.0)" "$(release_object termux-v1.6.0)"
            ;;
        *) fail "unexpected release API URL: $1" ;;
    esac
}
detect_arch() { printf '%s\n' "$TEST_ARCH"; }
curl() {
    local arg url='' out=''
    while [ "$#" -gt 0 ]; do
        arg="$1"; shift
        case "$arg" in
            https://*) url="$arg" ;;
            -o) out="$1"; shift ;;
        esac
    done
    printf '%s\n' "$url" >> "$TEST_TMP/core-urls"
    if [ -n "$out" ]; then printf 'archive\n' > "$out"; else printf '%064d\n' 0; fi
}
sha256sum() { printf '%064d  %s\n' 0 "$1"; }
tar() {
    case " $* " in
        *' -tzf '*) printf 'saman-aether-core\n' ;;
        *' -xzf '*) : > "$TMP/saman-aether-core" ;;
    esac
}
ACTION=install
for TEST_ARCH in arm64 armv7 x86_64; do
    TMP="$TEST_TMP/core-$TEST_ARCH"
    mkdir -p "$TMP"
    download_core >/dev/null
    [ "$(<"$TMP/termux-tag")" = 'termux-v1.6.0' ] || fail "install selected an unpinned core for $TEST_ARCH"
done
if grep -q '/releases/download/termux-v1.7.0/' "$TEST_TMP/core-urls"; then
    fail "v1.6.0 install selected a hypothetical newer core"
fi
for TEST_ARCH in arm64 armv7 x86_64; do
    grep -qx "https://github.com/$REPO/releases/download/termux-v1.6.0/saman-aether-termux-$TEST_ARCH.tar.gz" "$TEST_TMP/core-urls" ||
        fail "pinned core archive URL missing for $TEST_ARCH"
    grep -qx "https://github.com/$REPO/releases/download/termux-v1.6.0/saman-aether-termux-$TEST_ARCH.tar.gz.sha256" "$TEST_TMP/core-urls" ||
        fail "pinned checksum URL missing for $TEST_ARCH"
done
mkdir -p "$TEST_TMP/separate-source"
ACTION=install
SOURCE_REF=main
TMP="$TEST_TMP/separate-source"
resolve_termux_release
[ "$SOURCE_REF" = main ] || fail "core resolution overwrote the independently pinned maintenance source"
ACTION=update
: "$ACTION" # Consumed by the functions sourced from install.sh.
TEST_ARCH=arm64
TMP="$TEST_TMP/update-core"
mkdir -p "$TMP"
if (download_core >/dev/null 2>&1); then
    fail "old installer accepted a newer incompatible Termux release"
fi
unset -f api_json detect_arch curl sha256sum tar release_object
pass "version-pinned Termux core selection"

# CI must execute every maintained Termux test suite, not only the legacy aggregate.
workflow="$ROOT/.github/workflows/termux-maintenance.yml"
for suite in test-termux.sh test-center.sh test-safety.sh test-backup.sh test-diagnostics.sh test-runner.sh; do
    grep -q "bash termux/tests/$suite" "$workflow" || fail "CI omits $suite"
done
grep -q "python.*test_server.py" "$workflow" || fail "CI omits Saman Share server tests"
pass "CI covers all maintained Termux suites"

printf 'All Termux maintenance tests passed.\n'
