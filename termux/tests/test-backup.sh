#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
export HOME="$TMP/home" PREFIX="$TMP/prefix" SAMAN2_ROOT="$TMP/home/.local/share/saman-center-v2"
mkdir -p "$HOME/bin" "$SAMAN2_ROOT/lib" "$PREFIX/bin" "$PREFIX/etc"
printf '#!/bin/sh\n' > "$HOME/bin/saman"; chmod +x "$HOME/bin/saman"
printf 'center\n' > "$SAMAN2_ROOT/saman2"
printf 'lib\n' > "$SAMAN2_ROOT/lib/common.sh"
printf 'diag\n' > "$PREFIX/bin/saman-aether-diagnostics"
printf 'control\n' > "$PREFIX/bin/aether-control"
printf '1.5.0\n' > "$PREFIX/etc/saman-aether-termux.version"
source "$ROOT/termux/saman-center-v2/lib/common.sh"
mkdir -p "$SAMAN2_CONFIG"
printf '%s\n' 'NETWORK_TIMEOUT=6' 'API_TOKEN=secret-value' > "$SAMAN2_CONFIG/settings.conf"
source "$ROOT/termux/saman-center-v2/lib/config.sh"; s2_config_load
source "$ROOT/termux/saman-center-v2/lib/ui.sh"
source "$ROOT/termux/saman-center-v2/modules/backup.sh"
out="$(s2_backup)"
archive="$(printf '%s\n' "$out" | sed -n 's/^Archive: //p')"
[ -s "$archive" ] || { echo 'FAIL: archive missing' >&2; exit 1; }
[ -s "$archive.sha256" ] || { echo 'FAIL: checksum missing' >&2; exit 1; }
sha256sum -c "$archive.sha256" >/dev/null
tar -tzf "$archive" | grep -q '^home/.local/share/saman-center-v2/saman2$'
tar -tzf "$archive" | grep -q '^prefix/bin/aether-control$'
settings="$(tar -xOzf "$archive" home/.config/saman-center-v2/settings.conf)"
printf '%s\n' "$settings" | grep -q '^NETWORK_TIMEOUT=6$' || { echo 'FAIL: safe setting missing' >&2; exit 1; }
if printf '%s\n' "$settings" | grep -q 'API_TOKEN\|secret-value'; then echo 'FAIL: unknown credential copied' >&2; exit 1; fi
case "$archive" in "$HOME/.local/backups/saman-center/"*) ;; *) echo 'FAIL: backup not private' >&2; exit 1;; esac

rm -f "$HOME/.local/backups/saman-center/"*
mkdir -p "$TMP/fakebin"
printf '#!/bin/sh\nexit 9\n' > "$TMP/fakebin/tar"; chmod +x "$TMP/fakebin/tar"
if PATH="$TMP/fakebin:$PATH" s2_backup >/dev/null 2>&1; then echo 'FAIL: tar failure reported success' >&2; exit 1; fi
if compgen -G "$HOME/.local/backups/saman-center/*.tar.gz" >/dev/null; then echo 'FAIL: partial final archive retained' >&2; exit 1; fi

rm -rf "$TMP/fakebin"; mkdir -p "$TMP/fakebin"
REAL_MKTEMP="$(command -v mktemp)"; export REAL_MKTEMP MKTEST_COUNT="$TMP/mktemp.count"
cat > "$TMP/fakebin/mktemp" <<'EOF'
#!/bin/sh
count=0; [ ! -f "$MKTEST_COUNT" ] || count="$(cat "$MKTEST_COUNT")"
count=$((count + 1)); printf '%s\n' "$count" > "$MKTEST_COUNT"
[ "$count" -ne 2 ] || exit 9
exec "$REAL_MKTEMP" "$@"
EOF
chmod +x "$TMP/fakebin/mktemp"
if PATH="$TMP/fakebin:$PATH" s2_backup >/dev/null 2>&1; then echo 'FAIL: verification temp failure reported success' >&2; exit 1; fi
if compgen -G "$HOME/.local/backups/saman-center/.stage.*" >/dev/null; then echo 'FAIL: failed setup leaked staging directory' >&2; exit 1; fi
printf 'PASS: atomic verified private backup\n'
