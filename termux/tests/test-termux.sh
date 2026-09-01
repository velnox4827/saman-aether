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

# Installer version and release URLs are explicit and trusted.
grep -q 'SAMAN_TERMUX_VERSION="1.5.0"' "$ROOT/install.sh" || fail "Termux integration version"
grep -q 'https://github.com/\$REPO/releases/download/' "$ROOT/install.sh" || fail "trusted release URL check"
grep -q 'SHA-256 mismatch; refusing to install' "$ROOT/install.sh" || fail "checksum fail-closed behavior"
grep -q 'rollback' "$ROOT/install.sh" || fail "rollback support"
pass "installer update safety policy"

printf 'All Termux maintenance tests passed.\n'
