#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CENTER="$ROOT/termux/saman-center-v2"
TEST_TMP="$(mktemp -d)"
trap 'rm -rf "$TEST_TMP"' EXIT
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS: %s\n' "$*"; }

export HOME="$TEST_TMP/home" PREFIX="${PREFIX:?}" SAMAN2_ROOT="$CENTER"
mkdir -p "$HOME" "$TEST_TMP/bin"
source "$CENTER/lib/common.sh"
source "$CENTER/lib/config.sh"; s2_config_load
source "$CENTER/modules/download.sh"
source "$CENTER/modules/media.sh"
source "$CENTER/modules/phone.sh"
s2_err() { printf 'ERROR: %s\n' "$*" >&2; }

# Downloads accept only HTTP(S) and place the option terminator before external URLs.
aria2c() { printf '%s\n' "$@" > "$TEST_TMP/aria2.args"; }
s2_legacy() { return 1; }
export SAMAN2_DOWNLOADS="$TEST_TMP/downloads"
if s2_download_url '--out=/tmp/pwn' >/dev/null 2>&1; then fail "download accepted option injection"; fi
if s2_download_url 'file:///etc/passwd' >/dev/null 2>&1; then fail "download accepted local-file scheme"; fi
s2_download_url 'https://example.invalid/-payload' >/dev/null
awk 'prev=="--" && $0=="https://example.invalid/-payload" {ok=1} {prev=$0} END{exit(ok?0:1)}' "$TEST_TMP/aria2.args" || fail "aria2 URL lacks option terminator"
pass "download scheme and option-injection protection"

# Failed media conversion cannot overwrite an existing destination or publish a partial.
input="$TEST_TMP/input.mp4"; output="$TEST_TMP/output.mp4"
printf input > "$input"; printf sentinel > "$output"
ffmpeg() { printf partial > "${@: -1}"; return 1; }
if s2_media_transcode_atomic "$output" ffmpeg -i "$input"; then fail "failing media command succeeded"; fi
[ "$(cat "$output")" = sentinel ] || fail "failed media conversion overwrote destination"
if compgen -G "$TEST_TMP/.output.part.*.mp4" >/dev/null; then fail "failed media conversion left partial"; fi
fresh="$TEST_TMP/fresh.mp4"
ffmpeg() { printf partial > "${@: -1}"; return 1; }
if s2_media_transcode_atomic "$fresh" ffmpeg -i "$input"; then fail "failing fresh conversion succeeded"; fi
[ ! -e "$fresh" ] || fail "failed conversion published a destination"
if command -v /data/data/com.termux/files/usr/bin/ffmpeg >/dev/null 2>&1; then
    real_output="$TEST_TMP/real.mp4"
    s2_media_transcode_atomic "$real_output" /data/data/com.termux/files/usr/bin/ffmpeg -hide_banner -loglevel error -f lavfi -i color=c=black:s=16x16:d=0.05 -an -c:v mpeg4 -y
    [ -s "$real_output" ] || fail "real ffmpeg output was not atomically published"
fi
race_output="$TEST_TMP/race.mp4"
racing_writer() { printf partial > "${@: -1}"; printf winner > "$race_output"; }
if s2_media_transcode_atomic "$race_output" racing_writer; then fail "media race overwrote concurrent destination"; fi
[ "$(cat "$race_output")" = winner ] || fail "concurrent media destination was replaced"
pass "atomic non-destructive media publication"

# Terminal fields strip control sequences and collapse external newlines.
clean="$(s2_terminal_field $'safe\033[31mBAD\r\nnext\177')"
[[ "$clean" != *$'\033'* && "$clean" != *$'\177'* && "$clean" != *$'\n'* ]] || fail "terminal controls survived sanitization"
[[ "$clean" == *safe* && "$clean" == *next* ]] || fail "terminal sanitizer removed safe text"
pass "terminal-control sanitization"

# SSH target and port validation rejects option-like or malformed input.
s2_phone_validate_ssh example.com alice 22 || fail "valid SSH target rejected"
if s2_phone_validate_ssh '-oProxyCommand=bad' alice 22; then fail "SSH option injection accepted"; fi
if s2_phone_validate_ssh example.com '-Fbad' 22; then fail "SSH username option injection accepted"; fi
if s2_phone_validate_ssh example.com alice '22;id'; then fail "SSH port injection accepted"; fi
pass "SSH input validation"

# Independent web probes run concurrently under configured deadlines and sanitize output.
source "$CENTER/modules/aether.sh"
source "$CENTER/modules/network.sh"
curl() {
    sleep 0.4
    case " $* " in
        *' ipify.org '*) printf $'198.51.100.7\033[2J' ;;
        *' api64.ipify.org '*) printf '2001:db8::7' ;;
        *' ipwho.is/ '*) printf '{"success":false}' ;;
        *' -w '*) printf '200 0.100' ;;
        *) : ;;
    esac
}
s2_have() { [ "$1" != jq ]; }
s2_default_iface() { printf 'wlan0\n'; }
s2_network_label() { printf 'Wi-Fi\n'; }
s2_vpn_state() { printf '?\n'; }
s2_vpn_iface() { return 1; }
s2_network_route() { printf 'default dev wlan0\n'; }
s2_network_dns() { printf '1.1.1.1\n'; }
s2_port_listening() { return 1; }
start_ms="$(date +%s%3N)"
network_out="$(s2_network_quick)"
elapsed_ms=$(( $(date +%s%3N) - start_ms ))
[ "$elapsed_ms" -lt 1400 ] || fail "network probes were serialized (${elapsed_ms}ms)"
[[ "$network_out" != *$'\033'* ]] || fail "network output retained terminal escape"
pass "bounded parallel terminal-safe network probes"

# All Aether status paths honor configured proxy ports.
export AETHER_SOCKS_PORT=1919 AETHER_HTTP_PORT=1920
s2_aether_core_pids() { return 0; }
s2_socket_listeners() { printf 'LISTEN 0 128 127.0.0.1:1919 0.0.0.0:*\n'; }
aether_status="$(s2_aether_status)"
printf '%s\n' "$aether_status" | grep -q '127.0.0.1:1919' || fail "configured SOCKS port absent"
if printf '%s\n' "$aether_status" | grep -q '127.0.0.1:1819'; then fail "hardcoded SOCKS port remained"; fi
pass "configured Aether ports"
s2_snapshot_port_listening 1819 '0100007F:071B' || fail "proc tcp fallback missed listening port"
pass "proc socket fallback"

# Android can make ss return success with only a netlink permission error.
# In that case loopback connect probes must replace the unusable snapshot.
ss() { printf 'Cannot open netlink socket: Permission denied\n' >&2; return 0; }
source "$CENTER/modules/aether.sh"
s2_tcp_port_accepting() { [ "$1" = 1919 ]; }
listeners="$(s2_socket_listeners)" || fail "permission-denied socket fallback failed"
s2_snapshot_port_listening 1919 "$listeners" || fail "TCP fallback missed accepting SOCKS port"
if s2_snapshot_port_listening 1920 "$listeners"; then fail "TCP fallback invented an HTTP listener"; fi
pass "permission-denied ss fallback"

printf 'All Saman safety tests passed.\n'
