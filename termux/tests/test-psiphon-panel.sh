#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export HOME="$TMP/home" PREFIX="$TMP/prefix" SAMAN2_ROOT="$ROOT/saman-center-v2"
mkdir -p "$HOME" "$PREFIX/bin" "$TMP/bin"
cat > "$TMP/bin/psiphon-tunnel-core" <<'FAKE'
#!/bin/sh
case "${1:-}" in
  --version) printf 'psiphon-tunnel-core test\n' ;;
  -config) test -r "${2:?}" ;;
esac
FAKE
chmod 700 "$TMP/bin/psiphon-tunnel-core"
export PATH="$TMP/bin:$PATH"
source "$ROOT/saman-center-v2/lib/common.sh"
source "$ROOT/saman-center-v2/lib/config.sh"
source "$ROOT/saman-center-v2/lib/ui.sh"
source "$ROOT/saman-center-v2/modules/aether.sh"
source "$ROOT/saman-center-v2/modules/psiphon.sh"

saman_psiphon_init
missing="$(saman_psiphon_status)"
printf '%s\n' "$missing" | grep -q 'CONFIGURATION REQUIRED'
cat > "$HOME/client.json" <<'JSON'
{
  "PropagationChannelId": "channel",
  "SponsorId": "sponsor",
  "LocalSocksProxyPort": 1081,
  "LocalHttpProxyPort": 8081
}
JSON
saman_psiphon_set_config "$HOME/client.json"
saman_psiphon_config_ready
saman_psiphon_prepare_aether_chain 'socks5://127.0.0.1:1819'
test "$(jq -r .UpstreamProxyURL "$SAMAN_PSIPHON_CHAIN_CONFIG")" = 'socks5://127.0.0.1:1819'
test "$(jq -r 'has("UpstreamProxyURL")' "$HOME/client.json")" = false
printf '%s\n' 'PASS: Psiphon adapter detects a valid official config and derives an Aether chain without mutating it'
