#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CENTER="$ROOT/termux/saman-center-v2"
TEST_TMP="$(mktemp -d)"
trap 'rm -rf "$TEST_TMP"' EXIT

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS: %s\n' "$*"; }
run_center() { HOME="$TEST_TMP/home" PREFIX="${PREFIX:?}" SAMAN2_ROOT="$CENTER" bash "$CENTER/saman2" "$@"; }
mkdir -p "$TEST_TMP/home"

# Read-only metadata commands must not create state, cache, or config directories.
[ "$(run_center version)" = "2.1.0" ] || fail "modernized version"
[ ! -e "$TEST_TMP/home/.local/state/saman-center-v2" ] || fail "version created state"
[ ! -e "$TEST_TMP/home/.cache/saman-center-v2" ] || fail "version created cache"
[ ! -e "$TEST_TMP/home/.config/saman-center-v2" ] || fail "version created config"
help="$(run_center help)"
printf '%s\n' "$help" | grep -q '^  saman ' || fail "help does not document canonical command"
printf '%s\n' "$help" | grep -q '^  saman repair' || fail "repair missing from help"
printf '%s\n' "$help" | grep -q '^  saman config' || fail "config missing from help"
printf '%s\n' "$help" | grep -q '^  saman logs' || fail "logs missing from help"
[ ! -e "$TEST_TMP/home/.local/state/saman-center-v2" ] || fail "help created state"
pass "fast read-only metadata commands"

# Configuration is whitelist-parsed rather than sourced as shell code.
mkdir -p "$TEST_TMP/home/.config/saman-center-v2"
printf '%s\n' \
    'AETHER_SOCKS_PORT=1919' \
    'NETWORK_TIMEOUT=4' \
    'UNKNOWN_KEY=ignored' \
    'MALICIOUS=$(touch /tmp/saman-config-injection)' \
    > "$TEST_TMP/home/.config/saman-center-v2/settings.conf"
config="$(run_center config show)"
printf '%s\n' "$config" | grep -q '^AETHER_SOCKS_PORT=1919$' || fail "valid config not loaded"
printf '%s\n' "$config" | grep -q '^NETWORK_TIMEOUT=4$' || fail "valid timeout not loaded"
! printf '%s\n' "$config" | grep -q 'UNKNOWN_KEY\|MALICIOUS' || fail "unknown config key exposed"
[ ! -e /tmp/saman-config-injection ] || fail "config was shell sourced"
pass "safe centralized configuration"
run_center config set NETWORK_TIMEOUT 6 >/dev/null
[ "$(run_center config show | sed -n 's/^NETWORK_TIMEOUT=//p')" = 6 ] || fail "config set did not persist"
grep -q '^UNKNOWN_KEY=ignored$' "$TEST_TMP/home/.config/saman-center-v2/settings.conf" || fail "config set overwrote custom settings"
pass "configuration update preserves custom lines"

# Invalid values fall back safely and make config validation fail clearly.
printf '%s\n' 'AETHER_SOCKS_PORT=99999' 'NETWORK_TIMEOUT=abc' > "$TEST_TMP/home/.config/saman-center-v2/settings.conf"
if run_center config validate >"$TEST_TMP/config-invalid.out" 2>&1; then fail "invalid config accepted"; fi
grep -q 'ERROR' "$TEST_TMP/config-invalid.out" || fail "invalid config lacks actionable error"
pass "configuration validation"
printf '%s\n' 'AETHER_SOCKS_PORT=1819' 'AETHER_HTTP_PORT=1819' 'SHARE_PORT=1819' > "$TEST_TMP/home/.config/saman-center-v2/settings.conf"
if run_center config validate >"$TEST_TMP/config-conflict.out" 2>&1; then fail "conflicting listener ports accepted"; fi
grep -q 'must be different' "$TEST_TMP/config-conflict.out" || fail "port conflict lacks actionable error"
pass "configuration port conflict detection"

# Logs are noninteractive and bounded by a validated line count.
mkdir -p "$TEST_TMP/home/.local/state/saman-center-v2/logs"
printf 'one\ntwo\nthree\n' > "$TEST_TMP/home/.local/state/saman-center-v2/logs/saman.log"
[ "$(run_center logs center --lines 2)" = $'two\nthree' ] || fail "bounded center logs"
if run_center logs center --lines '2;touch /tmp/saman-lines-injection' >/dev/null 2>&1; then fail "invalid line count accepted"; fi
[ ! -e /tmp/saman-lines-injection ] || fail "line-count injection executed"
pass "safe noninteractive logs"

# Repair dry-run reports issues without mutating them; repair removes only stale state.
mkdir -p "$TEST_TMP/home/.saman-aether" "$TEST_TMP/home/.cache/saman-center-v2"
printf '99999999\n' > "$TEST_TMP/home/.saman-aether/aether.pid"
: > "$TEST_TMP/home/.cache/saman-center-v2/orphan.tmp"
run_center repair --dry-run > "$TEST_TMP/repair-dry.out"
[ -e "$TEST_TMP/home/.saman-aether/aether.pid" ] || fail "dry-run removed PID"
[ -e "$TEST_TMP/home/.cache/saman-center-v2/orphan.tmp" ] || fail "dry-run removed temp"
grep -q 'ACTION REQUIRED' "$TEST_TMP/repair-dry.out" || fail "dry-run did not report action"
run_center repair > "$TEST_TMP/repair.out"
[ ! -e "$TEST_TMP/home/.saman-aether/aether.pid" ] || fail "repair retained stale PID"
[ ! -e "$TEST_TMP/home/.cache/saman-center-v2/orphan.tmp" ] || fail "repair retained stale temp"
grep -q 'OK' "$TEST_TMP/repair.out" || fail "repair lacks success status"
pass "conservative repair"

# Status takes one socket snapshot instead of repeatedly spawning ss.
mkdir -p "$TEST_TMP/fakebin"
cat > "$TEST_TMP/fakebin/ss" <<'MOCK'
#!/usr/bin/env bash
printf 'x\n' >> "$HOME/ss-calls"
exit 0
MOCK
chmod +x "$TEST_TMP/fakebin/ss"
PATH="$TEST_TMP/fakebin:$PATH" run_center status >/dev/null
ss_calls="$(wc -l < "$TEST_TMP/home/ss-calls")"
[ "$ss_calls" -le 1 ] || fail "status invoked ss $ss_calls times"
pass "single-snapshot status"

# Doctor must return non-zero when a required core dependency fails.
if (
    export HOME="$TEST_TMP/doctor-home" SAMAN2_ROOT="$CENTER"
    source "$CENTER/lib/common.sh"
    source "$CENTER/lib/config.sh"; s2_config_load
    source "$CENTER/lib/ui.sh"
    source "$CENTER/modules/aether.sh"
    source "$CENTER/modules/network.sh"
    source "$CENTER/modules/diagnostics.sh"
    s2_check_cmd() { [ "$1" != bash ]; }
    s2_default_iface() { return 1; }
    s2_network_label() { printf 'Network\n'; }
    s2_vpn_state() { printf '?\n'; }
    s2_net_iface_visibility() { printf 'HIDDEN\n'; }
    s2_vpn_iface() { return 1; }
    s2_have() { return 1; }
    s2_doctor >/dev/null
); then fail "doctor returned success with missing required dependency"; fi
pass "doctor exit status"

# Dashboard refreshes are TTL-gated to avoid repeated API/network work.
(
    export HOME="$TEST_TMP/cache-home" SAMAN2_ROOT="$CENTER"
    source "$CENTER/lib/common.sh"
    source "$CENTER/lib/config.sh"; s2_config_load
    source "$CENTER/lib/ui.sh"
    mkdir -p "$SAMAN2_CACHE"
    : > "$SAMAN2_CACHE/.last-refresh"
    if s2_dashboard_refresh_due; then exit 1; fi
    touch -d '2 minutes ago' "$SAMAN2_CACHE/.last-refresh"
    s2_dashboard_refresh_due
) || fail "dashboard cache TTL"
pass "dashboard refresh TTL"

# Forced refresh bypasses a fresh TTL marker and completes before returning.
(
    export HOME="$TEST_TMP/refresh-home" SAMAN2_ROOT="$CENTER"
    source "$CENTER/lib/common.sh"
    source "$CENTER/lib/config.sh"; s2_config_load
    source "$CENTER/lib/ui.sh"
    mkdir -p "$SAMAN2_CACHE"; : > "$SAMAN2_CACHE/.last-refresh"
    s2_have() { return 1; }
    s2_default_iface() { printf 'wlan0\n'; }
    s2_network_label() { printf 'Network\n'; }
    s2_vpn_state() { printf '?\n'; }
    s2_refresh_dashboard force
    [ -n "${S2_REFRESH_PID:-}" ] && wait "$S2_REFRESH_PID"
    [ -f "$SAMAN2_CACHE/.last-refresh" ]
) || fail "forced dashboard refresh"
pass "forced dashboard refresh"

# Cache deletion refuses environment-overridden victim paths.
(
    export HOME="$TEST_TMP/maintenance-home" SAMAN2_ROOT="$CENTER"
    source "$CENTER/lib/common.sh"
    source "$CENTER/lib/config.sh"; s2_config_load
    source "$CENTER/lib/ui.sh"
    source "$CENTER/modules/maintenance.sh"
    SAMAN2_CACHE="$TEST_TMP/victim"; mkdir -p "$SAMAN2_CACHE"; printf sentinel > "$SAMAN2_CACHE/ip"
    if s2_clear_cache >/dev/null 2>&1; then exit 1; fi
    [ "$(<"$SAMAN2_CACHE/ip")" = sentinel ]
) || fail "cache clear path guard"
pass "cache clear path guard"

printf 'All Saman Center tests passed.\n'
