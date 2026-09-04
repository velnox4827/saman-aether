#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/home/bin" "$TMP/home/storage/downloads" "$TMP/prefix/bin"
printf '#!/usr/bin/env bash\necho "aether 1.8.0"\n' > "$TMP/prefix/bin/saman-aether-core"
printf '#!/usr/bin/env bash\necho "2.1.0"\n' > "$TMP/home/bin/saman"
chmod +x "$TMP/prefix/bin/saman-aether-core" "$TMP/home/bin/saman"
printf '%s\n' \
  'Authorization: Bearer SUPERSECRET' \
  'url=https://user:pass@example.test/path?token=QUERYSECRET' \
  'api_key=KEYSECRET' \
  '-----BEGIN PRIVATE KEY-----' \
  'PRIVATESECRET' \
  '-----END PRIVATE KEY-----' > "$TMP/home/aether-wg.log"
out="$(HOME="$TMP/home" PREFIX="$TMP/prefix" bash "$ROOT/saman-aether-diagnostics" safe)"
report="$(printf '%s\n' "$out" | sed -n 's/^  //p' | head -n1)"
[ -s "$report" ] || { printf 'FAIL: diagnostics report missing\n' >&2; exit 1; }
[ "$(stat -c %a "$report")" = 600 ] || { printf 'FAIL: diagnostics report permissions\n' >&2; exit 1; }
if grep -Eq 'SUPERSECRET|QUERYSECRET|KEYSECRET|PRIVATESECRET|user:pass' "$report"; then
  printf 'FAIL: secret leaked in diagnostics\n' >&2; exit 1
fi
grep -q '<redacted>' "$report" || { printf 'FAIL: redaction marker missing\n' >&2; exit 1; }
printf 'PASS: diagnostics executes and redacts secrets\n'
