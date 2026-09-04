#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNNER="$ROOT/aether-shortcut-runner"
TEST_TMP="$(mktemp -d)"
RUNNER_PID=''
CORE_PID=''
cleanup() {
    [ -n "$RUNNER_PID" ] && kill -TERM "$RUNNER_PID" 2>/dev/null || true
    [ -n "$RUNNER_PID" ] && wait "$RUNNER_PID" 2>/dev/null || true
    [ -n "$CORE_PID" ] && kill -TERM "$CORE_PID" 2>/dev/null || true
    [ -n "$CORE_PID" ] && wait "$CORE_PID" 2>/dev/null || true
    rm -rf "$TEST_TMP"
}
trap cleanup EXIT

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS: %s\n' "$*"; }
wait_for() {
    local description="$1" command="$2" attempts=0
    while [ "$attempts" -lt 100 ]; do
        eval "$command" && return 0
        sleep 0.05
        attempts=$((attempts + 1))
    done
    fail "timed out waiting for $description"
}

mkdir -p "$TEST_TMP/bin" "$TEST_TMP/prefix/bin"
REAL_TAIL="$(command -v tail)"
cat > "$TEST_TMP/fake-core.c" <<'C'
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
static volatile sig_atomic_t running = 1;
static void stop(int sig) { (void)sig; running = 0; }
int main(int argc, char **argv) {
    FILE *f;
    const char *args = getenv("FAKE_ARGS_FILE");
    const char *fill = getenv("FAKE_LOG_FILL");
    const char *exit_once = getenv("FAKE_EXIT_101_ONCE_FILE");
    signal(SIGTERM, stop); signal(SIGINT, stop);
    if (args && (f = fopen(args, "a"))) {
        for (int i = 1; i < argc; i++) fprintf(f, "%s%s", i == 1 ? "" : " ", argv[i]);
        fputc('\n', f); fclose(f);
    }
    if (fill) { long n = strtol(fill, 0, 10); while (n-- > 0) fputc('x', stdout); fputc('\n', stdout); }
    puts("scan mode=balanced"); fflush(stdout);
    usleep(750000);
    puts("socks5 server listening"); fflush(stdout);
    if (exit_once && access(exit_once, F_OK) != 0) {
        if ((f = fopen(exit_once, "w"))) { fputs("first\n", f); fclose(f); }
        usleep(200000);
        return 101;
    }
    while (running) usleep(100000);
    return 0;
}
C
"${CC:-cc}" "$TEST_TMP/fake-core.c" -o "$TEST_TMP/prefix/bin/saman-aether-core"

cat > "$TEST_TMP/bin/clear" <<'SH'
#!/usr/bin/env bash
:
SH
cat > "$TEST_TMP/bin/am" <<'SH'
#!/usr/bin/env bash
:
SH
cat > "$TEST_TMP/bin/ss" <<'SH'
#!/usr/bin/env bash
[ "${FAKE_SS_DENIED:-0}" -eq 0 ] || printf 'Cannot open netlink socket: Permission denied\n' >&2
exit 0
SH
cat > "$TEST_TMP/bin/curl" <<'SH'
#!/usr/bin/env bash
printf 'ip=203.0.113.10\nloc=DE\n'
SH
cat > "$TEST_TMP/bin/tail" <<SH
#!/usr/bin/env bash
printf '1\n' >> "\${TAIL_COUNT_FILE:?}"
exec "$REAL_TAIL" "\$@"
SH
chmod +x "$TEST_TMP/bin/"*

base_env=(
    "PATH=$TEST_TMP/bin:$PATH"
    "PREFIX=$TEST_TMP/prefix"
    "SAMAN_AETHER_CORE=$TEST_TMP/prefix/bin/saman-aether-core"
    "TAIL_COUNT_FILE=$TEST_TMP/tail-count"
    "FAKE_ARGS_FILE=$TEST_TMP/core-args"
)

# A successful ss exit with a netlink error must not be treated as proof that
# configured loopback ports are free. The TCP fallback detects the listener.
denied_home="$TEST_TMP/denied-home"; mkdir -p "$denied_home/.config/saman-center-v2"
cat > "$denied_home/.config/saman-center-v2/settings.conf" <<'CONF'
AETHER_SOCKS_PORT=39191
AETHER_HTTP_PORT=39192
AETHER_PORT_HANDOFF_TIMEOUT_SECS=1
CONF
python -m http.server 39191 --bind 127.0.0.1 >"$TEST_TMP/listener.out" 2>&1 &
listener_pid=$!
sleep 0.2
if timeout 3 env "${base_env[@]}" HOME="$denied_home" FAKE_SS_DENIED=1 bash "$RUNNER" WG >"$TEST_TMP/denied.out" 2>&1; then
    kill "$listener_pid" 2>/dev/null || true; wait "$listener_pid" 2>/dev/null || true
    fail "runner started despite an occupied port hidden by denied netlink"
fi
kill "$listener_pid" 2>/dev/null || true; wait "$listener_pid" 2>/dev/null || true
grep -q 'local proxy port remains busy' "$TEST_TMP/denied.out" || fail "runner lacked TCP listener fallback after denied netlink"
pass "permission-denied ss uses loopback listener fallback"

# Mode validation must happen before creating state, checking the core, taking a
# wake lock, rotating logs, or touching a running process.
invalid_home="$TEST_TMP/invalid-home"
mkdir -p "$invalid_home"
if env "${base_env[@]}" HOME="$invalid_home" bash "$RUNNER" NOT_A_MODE >"$TEST_TMP/invalid.out" 2>&1; then
    fail "invalid mode succeeded"
fi
[ ! -e "$invalid_home/.saman-aether" ] || fail "invalid mode created runner state"
grep -q '^ERROR: unknown mode NOT_A_MODE' "$TEST_TMP/invalid.out" || fail "invalid mode lacks actionable ERROR"
pass "mode validation precedes side effects"

# Center configuration is data, never shell code. Valid whitelisted values are
# applied and unknown/malicious lines cannot execute.
home="$TEST_TMP/home"
mkdir -p "$home/.config/saman-center-v2"
cat > "$home/.config/saman-center-v2/settings.conf" <<CONF
AETHER_SOCKS_PORT=2919
AETHER_HTTP_PORT=2920
NETWORK_TIMEOUT=3
LOG_MAX_CURRENT_BYTES=65536
LOG_MAX_PREVIOUS_BYTES=65536
AETHER_SMART_WG_MAX_RTT_MS=700
MALICIOUS=\$(touch "$TEST_TMP/injected")
CONF
: > "$TEST_TMP/tail-count"
env "${base_env[@]}" HOME="$home" FAKE_LOG_FILL=90000 bash "$RUNNER" WG >"$TEST_TMP/runner.out" 2>&1 &
RUNNER_PID=$!
wait_for "runner PID" "[ -s '$home/.saman-aether/aether.pid' ]"
CORE_PID="$(<"$home/.saman-aether/aether.pid")"
lock_identity="$(readlink "$home/.saman-aether/runner.lock")"
[[ "$lock_identity" =~ ^[0-9]+:[0-9]+: ]] || fail "runner lock lacks PID and process-start identity"
wait_for "ready status" "grep -q '^OK: Aether connected' '$TEST_TMP/runner.out'"
args="$(<"$TEST_TMP/core-args")"
[[ "$args" == *'--bind 127.0.0.1:2919'* ]] || fail "configured SOCKS port not applied"
[[ "$args" == *'--http-proxy 127.0.0.1:2920'* ]] || fail "configured HTTP port not applied"
[ ! -e "$TEST_TMP/injected" ] || fail "Center config was evaluated as shell"
[ "$(wc -c < "$home/aether-wg.log")" -le 65536 ] || fail "current log was not bounded"
[ "$(wc -c < "$home/aether-wg.previous.log")" -le 65536 ] || fail "previous log was not bounded"
[ "$(wc -l < "$TEST_TMP/tail-count")" -le 3 ] || fail "readiness repeatedly spawned tail/whole-log scans"
pass "whitelist config, incremental readiness, and bounded logs"

# A concurrent launch must fail without terminating the managed core. The PID
# file must always be a complete, private, numeric atomic publication.
first_core="$CORE_PID"
if env "${base_env[@]}" HOME="$home" bash "$RUNNER" MASQUE >"$TEST_TMP/concurrent.out" 2>&1; then
    fail "concurrent runner succeeded"
fi
kill -0 "$first_core" 2>/dev/null || fail "concurrent launch killed managed core"
[ "$(<"$home/.saman-aether/aether.pid")" = "$first_core" ] || fail "concurrent launch replaced managed PID"
[[ "$(stat -c %a "$home/.saman-aether/aether.pid")" == 600 ]] || fail "PID file permissions are not private"
find "$home/.saman-aether" -name 'aether.pid.tmp.*' -print -quit | grep -q . && fail "atomic PID temporary file leaked"
grep -Eq '^(WARNING|ERROR): .*already (running|managed)' "$TEST_TMP/concurrent.out" || fail "concurrency failure is not actionable"
pass "single-instance launch preserves managed core and publishes PID atomically"

# Cleanup must reap the monitor and core, remove owned state, and leave no
# descendants behind. It must not unlink a lock that was replaced after this
# runner acquired it.
owned_lock="$(readlink "$home/.saman-aether/runner.lock")"
foreign_lock='foreign-owner:1'
ln -sfn "$foreign_lock" "$home/.saman-aether/runner.lock"
kill -TERM "$RUNNER_PID"
wait "$RUNNER_PID" || true
RUNNER_PID=''
wait_for "core shutdown" "! kill -0 '$first_core' 2>/dev/null"
CORE_PID=''
[ ! -e "$home/.saman-aether/aether.pid" ] || fail "cleanup retained PID file"
[ "$(readlink "$home/.saman-aether/runner.lock")" = "$foreign_lock" ] || fail "cleanup removed a replacement runner lock (owned $owned_lock)"
rm -f "$home/.saman-aether/runner.lock"
if ps -eo ppid=,pid= | awk -v p="$first_core" '$1 == p { found=1 } END { exit(found ? 0 : 1) }'; then
    fail "cleanup left a core child process"
fi
pass "cleanup reaps processes and preserves replacement lock ownership"

# A restart must ignore the previous core's readiness line and wait for the new
# core to emit readiness after its own start marker.
restart_home="$TEST_TMP/restart-home"
mkdir -p "$restart_home"
: > "$TEST_TMP/tail-count"
args_before="$(wc -l < "$TEST_TMP/core-args")"
env "${base_env[@]}" HOME="$restart_home" FAKE_EXIT_101_ONCE_FILE="$TEST_TMP/exited-once" bash "$RUNNER" WG >"$TEST_TMP/restart.out" 2>&1 &
RUNNER_PID=$!
wait_for "first simulated network exit" "[ -s '$TEST_TMP/exited-once' ]"
wait_for "replacement core launch" "[ \"\$(wc -l < '$TEST_TMP/core-args')\" -ge $((args_before+2)) ]"
sleep 0.1
if grep -q '^OK: Aether reconnected' "$TEST_TMP/restart.out"; then fail "restart reused stale readiness line"; fi
wait_for "fresh restart readiness" "grep -q '^OK: Aether reconnected' '$TEST_TMP/restart.out'"
kill -TERM "$RUNNER_PID" 2>/dev/null || true
wait "$RUNNER_PID" 2>/dev/null || true
RUNNER_PID=''
pass "restart waits for fresh readiness"

printf 'All Aether runner tests passed.\n'
