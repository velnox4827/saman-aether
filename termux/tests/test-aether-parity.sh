#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export HOME="$TMP/home" PREFIX="$TMP/prefix" SAMAN2_ROOT="$ROOT/saman-center-v2"
mkdir -p "$HOME" "$PREFIX/bin" "$TMP/bin"
cat > "$TMP/bin/aether" <<'FAKE'
#!/bin/sh
case "${1:-}" in
--version) printf 'aether 2.0.0\n';;
--help) cat <<'HELP'
Usage: aether [OPTIONS]
--bind --http-proxy --upstream --mark --quick-reconnect --no-quick-reconnect -4 -6 --dual --ip --peer --wg-peer
--masque --wg --wireguard --warp --gool --wiw --mim --masque-in-masque --protocol
--wiw-outer --wiw-inner --wiw-peers --wiw-scan --mim-outer --mim-inner --mim-peers --mim-scan
--scan --turbo --balanced --thorough --stealth --ironclad --noize
--h2 --http2 --h3 --quic --no-quic-v2 --h2-peer --ech --no-data-check --validate-secs --startup-secs --reconnect-secs --dns --fragment --fragment-size --fragment-delay
--keepalive --no-profile-retry --tor --tor-reverse --tor-only --tor-bind --tor-dir --tor-bridges --no-tor-bridges --tor-bridge --tor-pt --tor-pt-dir
--team --access-id --access-secret --access-email --access-token --gateway
--route-block --route-direct --routes --config --wg-config --masque-config --tls-groups --perf --log-level --verbose
Environment variables:
AETHER_SOCKS AETHER_HTTP_PROXY AETHER_UPSTREAM AETHER_MARK AETHER_TOR AETHER_TOR_BRIDGES AETHER_TOR_PT AETHER_TOR_PT_DIR AETHER_TOR_BIND AETHER_TOR_DIR AETHER_TOR_DIRECT_SECS AETHER_TOR_STALL_SECS AETHER_TOR_BRIDGE_SECS AETHER_TOR_COUNTRY AETHER_TOR_CHECK AETHER_TOR_LOG AETHER_QUICK_RECONNECT AETHER_IP AETHER_PEER AETHER_WG_PEER AETHER_PROTOCOL AETHER_WIW_OUTER_PEER AETHER_WIW_INNER_PEER AETHER_WIW_PEERS AETHER_MIM_OUTER_PEER AETHER_MIM_INNER_PEER AETHER_MIM_PEERS AETHER_SCAN AETHER_NOIZE AETHER_MASQUE_HTTP2 AETHER_QUIC_V2 AETHER_MASQUE_H2_PEER AETHER_ECH AETHER_MASQUE_NO_DATA_CHECK AETHER_WG_NO_DATA_CHECK AETHER_MASQUE_VALIDATE_SECS AETHER_WG_VALIDATE_SECS AETHER_MASQUE_STARTUP_SECS AETHER_MASQUE_RECONNECT_SECS AETHER_WG_RECONNECT_SECS AETHER_DNS AETHER_MASQUE_H2_FRAGMENT AETHER_MASQUE_H2_FRAGMENT_SIZE AETHER_MASQUE_H2_FRAGMENT_DELAY AETHER_WG_KEEPALIVE AETHER_WG_NO_PROFILE_RETRY AETHER_TEAM AETHER_ACCESS_CLIENT_ID AETHER_ACCESS_CLIENT_SECRET AETHER_ACCESS_TOKEN AETHER_ACCESS_EMAIL AETHER_GATEWAY AETHER_ROUTE_BLOCK AETHER_ROUTE_DIRECT AETHER_ROUTES_FILE AETHER_CONFIG AETHER_WG_CONFIG AETHER_MASQUE_CONFIG AETHER_TLS_GROUPS AETHER_PERF_PROFILE AETHER_LOG_LEVEL AETHER_ROUTE_SNIFF AETHER_ROUTE_SNIFF_MS AETHER_WG_ENDPOINT_COOLDOWN_SECS AETHER_WG_STALE_SECS AETHER_MASQUE_H2_KEEPALIVE_SECS AETHER_MASQUE_H2_KEEPALIVE_TIMEOUT_SECS AETHER_IRONCLAD_PORT AETHER_MAX_CLIENTS AETHER_HALF_CLOSE_SECS AETHER_TCP_KEEPALIVE_SECS AETHER_TCP_CONNECT_SECS AETHER_REPROVISION RUST_LOG
HELP
;; esac
FAKE
chmod 700 "$TMP/bin/aether"
export PATH="$TMP/bin:$PATH"
source "$ROOT/saman-center-v2/lib/common.sh"
source "$ROOT/saman-center-v2/lib/config.sh"
source "$ROOT/saman-center-v2/lib/ui.sh"
source "$ROOT/saman-center-v2/modules/aether-panel.sh"
saman_aether_init
saman_aether_detect_capabilities >/dev/null
report="$(saman_aether_parity_report)"
printf '%s\n' "$report"
printf '%s\n' "$report" | grep -q '^MISSING_FLAGS=0$'
printf '%s\n' "$report" | grep -q '^MISSING_ENVS=0$'
printf '%s\n' 'PASS: complete upstream parity registry'
