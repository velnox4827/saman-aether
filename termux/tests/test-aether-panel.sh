#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export HOME="$TMP/home"
export PREFIX="$TMP/prefix"
export SAMAN2_ROOT="$ROOT/saman-center-v2"
export SAMAN2_CONFIG="$TMP/center-config"
export SAMAN2_STATE="$TMP/state"
export SAMAN2_CACHE="$TMP/cache"
export SAMAN2_LOG="$TMP/log"
mkdir -p "$HOME" "$PREFIX/bin" "$TMP/fake-bin"

cat > "$TMP/fake-bin/aether" <<'FAKE'
#!/bin/sh
case "${1:-}" in
  --version) printf '%s\n' 'aether 2.0.0' ;;
  --help) cat <<'HELP'
Usage: aether [OPTIONS]
  --masque --wg --gool --mim --protocol <name>
  --scan <mode> turbo | balanced | thorough | stealth | ironclad
  --noize <profile> off | light | firewall | balanced | gfw | aggressive
  --h2 --h3 --ech <auto|base64> --fragment --fragment-size <n|a-b> --fragment-delay <n|a-b>
  --bind <addr> --http-proxy <addr> --upstream <url> --quick-reconnect --no-quick-reconnect
  --ip <v4|v6|both> --peer <ip:port> --wg-peer <ip:port>
  --wiw-outer <ip:port> --wiw-inner <ip:port> --wiw-peers <out[,in]> --wiw-scan
  --mim-outer <ip:port> --mim-inner <ip:port> --mim-peers <out[,in]> --mim-scan
  --dns <list> --reconnect-secs <n> --startup-secs <n> --validate-secs <n>
  --keepalive <n> --perf <low|medium|high> --tls-groups <list> --log-level <level>
  --route-block <list> --route-direct <list> --routes <path> --h2-peer <ip:port>
HELP
  ;;
  *) exit 0 ;;
esac
FAKE
chmod 700 "$TMP/fake-bin/aether"
export PATH="$TMP/fake-bin:$PATH"

# RED: this intentionally fails until the panel module exists.
source "$ROOT/saman-center-v2/lib/common.sh"
source "$ROOT/saman-center-v2/lib/config.sh"
source "$ROOT/saman-center-v2/modules/aether-panel.sh"

saman_aether_init
saman_aether_detect_capabilities
[ "$SAMAN_AETHER_CAP_VERSION" = "2.0.0" ]
[ "$SAMAN_AETHER_CAP_HAS_MIM" = 1 ]
[ "$SAMAN_AETHER_CAP_HAS_IRONCLAD" = 1 ]

saman_aether_defaults
saman_aether_set MODE mim
saman_aether_set SCAN ironclad
saman_aether_set NOIZE aggressive
saman_aether_save
saman_aether_load
[ "$AETHER_PANEL_MODE" = mim ]
[ "$AETHER_PANEL_SCAN" = ironclad ]
[ "$AETHER_PANEL_NOIZE" = aggressive ]

args="$(saman_aether_build_args)"
case " $args " in *' --mim '*) ;; *) exit 1 ;; esac
case " $args " in *' --scan ironclad '*) ;; *) exit 1 ;; esac
case " $args " in *' --noize aggressive '*) ;; *) exit 1 ;; esac

saman_aether_apply_preset balanced
[ "$AETHER_PANEL_PRESET" = balanced ]
[ "$AETHER_PANEL_SCAN" = balanced ]
[ "$AETHER_PANEL_MODE" = masque ]

saman_aether_mark_custom SCAN turbo
[ "$AETHER_PANEL_PRESET" = custom ]

saman_aether_apply_preset balanced
saman_aether_reset --yes
saman_aether_load
[ "$AETHER_PANEL_PRESET" = upstream-default ]
[ -z "$AETHER_PANEL_SCAN" ]
[ "$AETHER_PANEL_QUICK" = 0 ]

printf '%s\n' 'PASS: capability detection, persistence, args, presets, custom marker'
