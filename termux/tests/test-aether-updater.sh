#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS: %s\n' "$*"; }

export HOME="$TMP/home" PREFIX="$TMP/prefix" SAMAN2_ROOT="$ROOT/termux/saman-center-v2"
export SAMAN_AETHER_CONFIG_DIR="$TMP/panel" SAMAN_AETHER_BIN="$TMP/install/aether"
mkdir -p "$HOME" "$PREFIX/bin" "$TMP/install/pt" "$TMP/mockbin" "$TMP/assets"
printf 'old-binary\n' > "$SAMAN_AETHER_BIN"; chmod 700 "$SAMAN_AETHER_BIN"
printf 'old-pt\n' > "$TMP/install/pt/lyrebird"; chmod 700 "$TMP/install/pt/lyrebird"

cat > "$TMP/mockbin/curl" <<'SH'
#!/data/data/com.termux/files/usr/bin/bash
set -u
url=''; out=''
for ((i=1;i<=$#;i++)); do
  case "${!i}" in http://*|https://*|mock://*) url="${!i}" ;; esac
  [ "${!i}" = -o ] && { j=$((i+1)); out="${!j}"; }
done
[ "${MOCK_DOWNLOAD_FAIL:-0}" = 0 ] || exit 22
case "$url" in
 *api.github.com*) src="$MOCK_RELEASE_JSON" ;;
 mock://archive) src="$MOCK_ARCHIVE" ;;
 mock://checksum) src="$MOCK_CHECKSUM" ;;
 *) exit 22 ;;
esac
if [ -n "$out" ]; then cp "$src" "$out"; else command cat "$src"; fi
SH
chmod 700 "$TMP/mockbin/curl"
export PATH="$TMP/mockbin:$PATH"

source "$SAMAN2_ROOT/lib/common.sh"
source "$SAMAN2_ROOT/lib/config.sh"
source "$SAMAN2_ROOT/lib/ui.sh"
source "$SAMAN2_ROOT/modules/aether.sh"
saman_aether_init
printf 'MODE=masque\nPRIVATE_SENTINEL=kept\n' > "$SAMAN_AETHER_SETTINGS"

write_release_json() {
  cat > "$TMP/release.json" <<'JSON'
{"draft":false,"prerelease":false,"tag_name":"v2.0.0","assets":[
 {"name":"aether-android-arm64.tar.gz","browser_download_url":"mock://archive"},
 {"name":"aether-android-arm64.tar.gz.sha256","browser_download_url":"mock://checksum"}]}
JSON
  export MOCK_RELEASE_JSON="$TMP/release.json"
}
make_good_archive() {
  local behavior="${1:-good}" stage="$TMP/stage"
  rm -rf "$stage"; mkdir -p "$stage/pt"
  cat > "$stage/aether" <<SH
#!/data/data/com.termux/files/usr/bin/bash
if [ "$behavior" = rollback ] && [ "\$(readlink -f "\$0")" = "$(readlink -f "$SAMAN_AETHER_BIN")" ]; then exit 9; fi
case "\${1:-}" in --version) printf 'aether 2.0.0\\n';; --help) printf '%s\\n' 'Usage --masque --tor';; *) :;; esac
SH
  chmod 700 "$stage/aether"
  printf 'new-pt\n' > "$stage/pt/lyrebird"; chmod 700 "$stage/pt/lyrebird"
  tar -czf "$TMP/assets/archive.tgz" -C "$stage" aether pt
  (cd "$TMP/assets" && sha256sum archive.tgz | sed 's/ archive.tgz$/ aether-android-arm64.tar.gz/' > archive.tgz.sha256)
  export MOCK_ARCHIVE="$TMP/assets/archive.tgz" MOCK_CHECKSUM="$TMP/assets/archive.tgz.sha256"
}
write_release_json

# Success installs the checksummed official binary and PT directory together,
# while backing up the previous binary, PT tree, and Saman settings.
make_good_archive good
out="$(SAMAN_AETHER_ARCH=arm64 saman_aether_update)" || fail 'mocked updater success failed'
[ "$($SAMAN_AETHER_BIN --version)" = 'aether 2.0.0' ] || fail 'new binary was not installed'
[ "$(cat "$TMP/install/pt/lyrebird")" = new-pt ] || fail 'official PT directory was not installed'
backup="$(find "$SAMAN_AETHER_BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d | sort | tail -n1)"
[ -f "$backup/aether" ] && [ -f "$backup/pt/lyrebird" ] && [ -f "$backup/settings.conf" ] || fail 'backup lacks binary/PT/settings'
[[ "$out" == *'installed aether 2.0.0'* ]] || fail 'success did not report installed version accurately'
pass 'checksummed binary/PT install and complete backup'

# Download and checksum failures cannot alter the installed binary or PT.
printf 'stable-binary\n' > "$SAMAN_AETHER_BIN"; chmod 700 "$SAMAN_AETHER_BIN"
printf 'stable-pt\n' > "$TMP/install/pt/lyrebird"
if MOCK_DOWNLOAD_FAIL=1 SAMAN_AETHER_ARCH=arm64 saman_aether_update >/dev/null 2>&1; then fail 'download failure succeeded'; fi
[ "$(cat "$SAMAN_AETHER_BIN")" = stable-binary ] && [ "$(cat "$TMP/install/pt/lyrebird")" = stable-pt ] || fail 'download failure changed installation'
printf 'wrong checksum\n' > "$MOCK_CHECKSUM"
if SAMAN_AETHER_ARCH=arm64 saman_aether_update >/dev/null 2>&1; then fail 'checksum failure succeeded'; fi
[ "$(cat "$SAMAN_AETHER_BIN")" = stable-binary ] && [ "$(cat "$TMP/install/pt/lyrebird")" = stable-pt ] || fail 'checksum failure changed installation'
pass 'download and checksum failures preserve installation'

# Unsafe paths and non-regular archive entries are rejected before extraction.
python - "$TMP/assets/unsafe.tgz" <<'PY'
import io, tarfile, sys
with tarfile.open(sys.argv[1], 'w:gz') as t:
    d=b'escape'
    x=tarfile.TarInfo('../escape'); x.size=len(d); t.addfile(x, io.BytesIO(d))
    y=tarfile.TarInfo('aether'); y.type=tarfile.SYMTYPE; y.linkname='/bin/sh'; t.addfile(y)
PY
MOCK_ARCHIVE="$TMP/assets/unsafe.tgz"
(cd "$TMP/assets" && sha256sum unsafe.tgz | sed 's/ unsafe.tgz$/ aether-android-arm64.tar.gz/' > unsafe.tgz.sha256); MOCK_CHECKSUM="$TMP/assets/unsafe.tgz.sha256"
export MOCK_ARCHIVE MOCK_CHECKSUM
if SAMAN_AETHER_ARCH=arm64 saman_aether_update >/dev/null 2>&1; then fail 'unsafe archive succeeded'; fi
[ ! -e "$TMP/escape" ] || fail 'unsafe archive escaped staging'
[ "$(cat "$SAMAN_AETHER_BIN")" = stable-binary ] || fail 'unsafe archive changed binary'
pass 'unsafe archive paths/types are rejected'

# If post-install validation fails, both binary and PT directory roll back.
make_good_archive rollback
if SAMAN_AETHER_ARCH=arm64 saman_aether_update >/dev/null 2>&1; then fail 'post-install validation failure succeeded'; fi
[ "$(cat "$SAMAN_AETHER_BIN")" = stable-binary ] || fail 'binary rollback failed'
[ "$(cat "$TMP/install/pt/lyrebird")" = stable-pt ] || fail 'PT rollback failed'
find "$SAMAN_AETHER_CONFIG_DIR" -maxdepth 1 -name 'update.*' -print -quit | grep -q . && fail 'temporary updater directory leaked'
pass 'atomic pair rollback and temporary cleanup'

printf 'All Aether updater regression tests passed.\n'
