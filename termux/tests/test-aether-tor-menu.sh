#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS: %s\n' "$*"; }
assert_has() { [[ "$1" == *"$2"* ]] || fail "missing '$2'"; }
assert_not_has() { [[ "$1" != *"$2"* ]] || fail "unexpected '$2'"; }

export HOME="$TMP/home" PREFIX="$TMP/prefix" SAMAN2_ROOT="$ROOT/termux/saman-center-v2"
export SAMAN2_CONFIG="$TMP/center-config" SAMAN2_STATE="$TMP/state" SAMAN2_CACHE="$TMP/cache" SAMAN2_LOG="$TMP/log"
export SAMAN_AETHER_CONFIG_DIR="$TMP/panel" SAMAN_AETHER_BIN="$TMP/bin/aether"
mkdir -p "$HOME" "$PREFIX/bin" "$TMP/bin"

make_aether() {
    local compiled="$1"
    cat > "$SAMAN_AETHER_BIN" <<'FAKE'
#!/data/data/com.termux/files/usr/bin/bash
case "${1:-}" in
 --version) printf 'aether 2.0.0\n' ;;
 --help) printf '%s\n' 'Usage: aether --masque --wg --gool --mim --h2 --h3 --bind --http-proxy --tor --tor-reverse --tor-only --tor-bind --tor-dir --tor-bridges --no-tor-bridges --tor-bridge --tor-pt --tor-pt-dir' ;;
 *) : ;;
esac
FAKE
    [ "$compiled" = 1 ] && printf '%s\n' '# deterministic capability fixture: arti-client tor-proto tor-rtcompat' >> "$SAMAN_AETHER_BIN"
    chmod 700 "$SAMAN_AETHER_BIN"
}

source "$SAMAN2_ROOT/lib/common.sh"
source "$SAMAN2_ROOT/lib/config.sh"
source "$SAMAN2_ROOT/lib/ui.sh"
source "$SAMAN2_ROOT/modules/aether.sh"

# Help text is not proof that Tor was compiled into the executable.
make_aether 0
saman_aether_init
saman_aether_detect_capabilities
[ "$SAMAN_AETHER_CAP_HAS_TOR" = 0 ] || fail 'help-only fake was reported Tor-capable'
out="$(saman_aether_capabilities)"
assert_has "$out" 'Tor: unavailable (not compiled)'
make_aether 1
saman_aether_detect_capabilities
[ "$SAMAN_AETHER_CAP_HAS_TOR" = 1 ] || fail 'compiled Tor fixture was not detected'
pass 'compiled Tor capability is detected independently of help'

# Tor selections persist, Back does not mutate, and current values are marked.
saman_aether_defaults; saman_aether_save
out="$(printf '2\n' | saman_aether_tor_mode_menu)"
assert_has "$out" 'Saved: Tor Routing = inside'
saman_aether_load; [ "$AETHER_PANEL_TOR_MODE" = inside ] || fail 'Tor mode did not persist'
before="$(sha256sum "$SAMAN_AETHER_SETTINGS")"
out="$(printf '0\n' | saman_aether_tor_mode_menu)"
after="$(sha256sum "$SAMAN_AETHER_SETTINGS")"
[ "$before" = "$after" ] || fail 'Back changed Tor settings'
out="$(saman_aether_render_tor_menu)"
assert_has "$out" '[x] Inside selected transport'
assert_has "$out" '[ ] Off'
pass 'Tor selection, persistence, Back, and current markers'

# Official argument arrays are mode-correct and never expose private bridges in summaries.
SAMAN_AETHER_HELP_FILE="$SAMAN_AETHER_CONFIG_DIR/help.txt"
saman_aether_load
AETHER_PANEL_MODE=wg AETHER_PANEL_TOR_MODE=inside AETHER_PANEL_TOR_BIND=127.0.0.1:1821
AETHER_PANEL_TOR_BRIDGES=force AETHER_PANEL_TOR_BRIDGE='obfs4 203.0.113.1:443 TOP-SECRET'
mkdir -p "$TMP/private/pt"; printf '#!/bin/sh\n' > "$TMP/private/lyrebird"; chmod 700 "$TMP/private/lyrebird"
AETHER_PANEL_TOR_PT="obfs4=$TMP/private/lyrebird" AETHER_PANEL_TOR_PT_DIR="$TMP/private/pt"
saman_aether_build_args_array wg
args="$(printf '<%s> ' "${SAMAN_AETHER_ARGS[@]}")"
assert_has "$args" '<--wg>'
assert_has "$args" '<--tor>'
assert_has "$args" '<--tor-bind> <127.0.0.1:1821>'
assert_has "$args" '<--tor-bridges>'
assert_has "$args" '<--tor-bridge> <obfs4 203.0.113.1:443 TOP-SECRET>'
saman_aether_save
summary="$(saman_aether_show_config)"
assert_has "$summary" 'Custom bridges    : configured (private)'
assert_not_has "$summary" 'TOP-SECRET'
assert_not_has "$summary" "$TMP/private/lyrebird"

AETHER_PANEL_TOR_MODE=reverse AETHER_PANEL_MODE=wg
if saman_aether_build_args_array wg >/dev/null 2>&1; then fail 'Tor reverse accepted WireGuard'; fi
AETHER_PANEL_MODE=masque AETHER_PANEL_H2=0
saman_aether_build_args_array masque
args="$(printf '<%s> ' "${SAMAN_AETHER_ARGS[@]}")"
assert_has "$args" '<--tor-reverse>'
assert_has "$args" '<--h2>'
assert_not_has "$args" '<--h3>'
AETHER_PANEL_TOR_MODE=only AETHER_PANEL_TOR_BIND=127.0.0.1:29091
saman_aether_build_args_array masque
args="$(printf '<%s> ' "${SAMAN_AETHER_ARGS[@]}")"
assert_has "$args" '<--tor-only>'
assert_has "$args" '<--bind> <127.0.0.1:29091>'
assert_not_has "$args" '<--masque>'
assert_not_has "$args" '<--http-proxy>'
pass 'Tor args, reverse constraints, tor-only bind, and private redaction'

# The official Tor :1820 default suppresses HTTP CONNECT rather than colliding.
AETHER_PANEL_TOR_MODE=inside AETHER_PANEL_TOR_BIND=127.0.0.1:1820 AETHER_PANEL_HTTP=127.0.0.1:1820 AETHER_PANEL_BIND=127.0.0.1:1819
saman_aether_build_args_array masque
args="$(printf '<%s> ' "${SAMAN_AETHER_ARGS[@]}")"
assert_not_has "$args" '<--http-proxy>'
# A custom Tor listener may not collide with the active main SOCKS listener.
AETHER_PANEL_TOR_BIND=127.0.0.1:1819
if saman_aether_validate_launch >/dev/null 2>&1; then fail 'Tor/main SOCKS collision accepted'; fi
AETHER_PANEL_TOR_BIND=127.0.0.1:1821
SAMAN_AETHER_CAP_HAS_TOR=0
if saman_aether_validate_launch >/dev/null 2>&1; then fail 'uncompiled Tor launch accepted'; fi
SAMAN_AETHER_CAP_HAS_TOR=1 AETHER_PANEL_TOR_PT='obfs4=/missing/private-pt'
if saman_aether_validate_launch >/dev/null 2>&1; then fail 'missing custom PT accepted'; fi
pass 'port collisions and unavailable Tor dependencies are rejected'

# Menus reject empty/invalid input, acknowledge, redraw, and terminate on EOF/Ctrl-C.
saman_aether_defaults; saman_aether_save
export SAMAN_AETHER_TEST_TTY=1 TERM=xterm COLUMNS=34
out="$(printf '\n\n99\n\n0\n' | saman_aether_tor_mode_menu)"
assert_has "$out" 'Please enter a number.'
assert_has "$out" 'Invalid choice: 99'
[ "$(printf '%s' "$out" | grep -ao $'\033\[2J\033\[H' | wc -l)" -ge 3 ] || fail 'screens were not redrawn'
last="${out##*$'\033[2J\033[H'}"
[ "$(printf '%s\n' "$last" | grep -c 'TOR ROUTING')" -eq 1 ] || fail 'rendered screen stacks prior screens'
timeout 2 bash -c 'source "$1/lib/common.sh"; source "$1/lib/config.sh"; source "$1/lib/ui.sh"; source "$1/modules/aether.sh"; saman_aether_tor_mode_menu </dev/null' _ "$SAMAN2_ROOT" >/dev/null || fail 'EOF looped or failed'
timeout -s INT 1 bash -c 'source "$1/lib/common.sh"; source "$1/lib/config.sh"; source "$1/lib/ui.sh"; source "$1/modules/aether.sh"; saman_aether_tor_mode_menu < <(sleep 5)' _ "$SAMAN2_ROOT" >/dev/null 2>&1 || [ "$?" -eq 124 ] || fail 'Ctrl-C was not clean'
out="$(TERM= SAMAN_AETHER_TEST_TTY=0 saman_aether_tor_mode_menu <<< '0')"
assert_not_has "$out" $'\033[2J'
max="$(COLUMNS=34 saman_aether_render_main_menu | awk '{ if(length>m)m=length } END{print m+0}')"
[ "$max" -le 34 ] || fail "narrow menu emitted $max columns"
pass 'safe input, redraw, EOF/Ctrl-C, non-TTY, and narrow rendering'

# A stale success line cannot satisfy a new startup generation.
printf 'OK: Aether connected (stale).\n' > "$TMP/stale-start.log"
S2_AETHER_START_MARKER='[Saman launch fresh]'; S2_AETHER_LAUNCH_PID=999999
result="$(SAMAN_AETHER_START_WAIT_SECS=1 s2_aether_wait_for_start "$TMP/stale-start.log" 2>/dev/null || true)"
[ "$result" = FAILED ] || fail 'stale launch success was accepted'
pass 'startup readiness is generation-scoped'

# Installed adapters exec the managed base runner; either exact path is valid.
cat > "$TMP/runner-base" <<'RUN'
#!/data/data/com.termux/files/usr/bin/bash
sleep 5
RUN
cat > "$TMP/runner-adapter" <<RUN
#!/data/data/com.termux/files/usr/bin/bash
exec "$TMP/runner-base" "\$@"
RUN
chmod 700 "$TMP/runner-base" "$TMP/runner-adapter"
SAMAN_AETHER_RUNNER="$TMP/runner-adapter" SAMAN_AETHER_RUNNER_BASE="$TMP/runner-base" "$TMP/runner-adapter" TEST & adapter_pid=$!
for _ in 1 2 3 4 5 6 7 8 9 10; do [ -r "/proc/$adapter_pid/cmdline" ] && grep -q "$TMP/runner-base" "/proc/$adapter_pid/cmdline" && break; sleep 0.05; done
mkdir -p "$HOME/.saman-aether"
start_id="$(s2_process_start_identity "$adapter_pid")"
ln -sfn "$adapter_pid:$start_id:fixture" "$HOME/.saman-aether/runner.lock"
SAMAN_AETHER_RUNNER="$TMP/runner-adapter" SAMAN_AETHER_RUNNER_BASE="$TMP/runner-base"
[ "$(s2_aether_owned_runner)" = "$adapter_pid" ] || fail 'execed managed base runner was not accepted'
kill "$adapter_pid" 2>/dev/null || true; wait "$adapter_pid" 2>/dev/null || true
rm -f "$HOME/.saman-aether/runner.lock"
pass 'adapter-to-base runner ownership proof'

# Start dispatches the mature runner without exec, waits for honest result, and prevents duplicates.
launches="$TMP/launches"
s2_aether_owned_pid() { [ -s "$TMP/owned" ] && cat "$TMP/owned"; }
s2_aether_launch_runner() { printf '%s\n' "$1" >> "$launches"; return "${FAKE_LAUNCH_RC:-0}"; }
s2_aether_wait_for_start() { printf '%s\n' "${FAKE_START_RESULT:-READY}"; [ "${FAKE_START_RESULT:-READY}" = READY ]; }
SAMAN_AETHER_RUNNER="$TMP/runner"
: > "$SAMAN_AETHER_RUNNER"; chmod 700 "$SAMAN_AETHER_RUNNER"
AETHER_PANEL_TOR_MODE=reverse AETHER_PANEL_MODE=masque AETHER_PANEL_H2=1
s2_aether_start masque >/dev/null
printf 'after-start\n' > "$TMP/returned"
[ -s "$TMP/returned" ] || fail 'start exec-replaced caller'
[ "$(cat "$launches")" = TOR_REVERSE ] || fail 'wrong lifecycle dispatch for Tor reverse'
printf '123\n' > "$TMP/owned"
if s2_aether_start masque >/dev/null 2>&1; then fail 'duplicate start accepted'; fi
[ "$(wc -l < "$launches")" -eq 1 ] || fail 'duplicate start dispatched runner'
rm -f "$TMP/owned"
FAKE_START_RESULT=FAILED
if s2_aether_start masque >/dev/null 2>&1; then fail 'bootstrap failure reported success'; fi
pass 'detached lifecycle dispatch, return, duplicate prevention, and startup failure'

# Stop validates the lock owner/start identity and parent relationship, then
# signals only the owning runner so its trap can stop the exact official child.
killed="$TMP/killed"
s2_aether_owned_runner() { printf '777\n'; }
s2_aether_owned_pid() { printf '321\n'; }
s2_aether_signal_pid() { printf '%s:%s\n' "$1" "$2" >> "$killed"; }
s2_aether_wait_pid_exit() { return 0; }
s2_aether_stop >/dev/null
[ "$(cat "$killed")" = 'TERM:777' ] || fail 'stop did not target exactly the owning runner'
: > "$killed"
s2_aether_owned_runner() { return 1; }
s2_aether_owned_pid() { return 1; }
mkdir -p "$HOME/.saman-aether"; printf '999\n' > "$HOME/.saman-aether/aether.pid"
if s2_aether_stop >/dev/null 2>&1; then fail 'unowned PID reported stopped'; fi
[ ! -s "$killed" ] || fail 'unowned process was signalled'
pass 'stop is restricted to the validated Saman runner and official child'

# Tor lifecycle labels remain visible through the compatibility log/status menu.
[ "$(s2_aether_log_for_mode tor-inside)" = "$HOME/aether-tor-inside.log" ] || fail 'Tor-inside log path missing'
[ "$(s2_aether_log_for_mode tor-reverse)" = "$HOME/aether-tor-reverse.log" ] || fail 'Tor-reverse log path missing'
[ "$(s2_aether_log_for_mode tor-only)" = "$HOME/aether-tor-only.log" ] || fail 'Tor-only log path missing'
pass 'Tor lifecycle log paths are addressable'

printf 'All Aether Tor/menu/lifecycle regression tests passed.\n'
