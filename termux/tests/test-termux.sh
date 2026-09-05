#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_TMP="$(mktemp -d)"
trap 'rm -rf "$TEST_TMP"' EXIT
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS: %s\n' "$*"; }

# Canonical scripts remain valid shell.
mapfile -t shell_files < <(printf '%s\n' \
    "$ROOT/install.sh" \
    "$ROOT/aether-shortcut-runner" \
    "$ROOT/saman-aether-diagnostics" \
    "$ROOT/termux/aether-control" \
    "$ROOT/termux/saman-center-v2/saman2" \
    "$ROOT/termux/saman-center-v2/lib/"*.sh \
    "$ROOT/termux/saman-center-v2/modules/"*.sh)
bash -n "${shell_files[@]}"
for file in "${shell_files[@]}"; do [ -x "$file" ] || fail "not executable: $file"; done
pass "shell syntax and executable permissions"

if command -v shellcheck >/dev/null 2>&1; then
    shellcheck --severity=warning "${shell_files[@]}"
    pass "warning-level ShellCheck gate"
fi

# Source installer functions in a disposable fake Termux prefix.
export PREFIX="$TEST_TMP/prefix"
export HOME="$TEST_TMP/home"
mkdir -p "$PREFIX/bin" "$PREFIX/etc" "$HOME"
cat > "$PREFIX/bin/aether" <<'AETHER'
#!/usr/bin/env bash
if [ "${1:-}" = --version ]; then printf 'Aether 9.9.9\n'; exit 0; fi
sleep 30
AETHER
chmod +x "$PREFIX/bin/aether"
export PATH="$PREFIX/bin:$PATH"
# shellcheck source=../../install.sh
source "$ROOT/install.sh"

resolve_upstream_aether
[ "$AETHER_COMMAND" = "$PREFIX/bin/aether" ] || fail "official Aether command discovery"
[ "$(upstream_aether_version)" = 'Aether 9.9.9' ] || fail "dynamic Aether version"
pass "official upstream Aether discovery"

# v1.7.0 must not contain a patched-core download/build/release path.
grep -q 'SAMAN_TERMUX_VERSION="1.7.0"' "$ROOT/install.sh" || fail "Termux integration version"
if grep -q 'download_core' "$ROOT/install.sh"; then fail "installer still downloads a Saman Aether core"; fi
if grep -Eq 'saman-aether-termux-.*tar\.gz|PINNED_TERMUX_TAG|AETHER_BASE_VERSION=' "$ROOT/install.sh"; then
    fail "installer still pins or downloads a separate Aether core"
fi
grep -q 'official upstream (unmodified)' "$ROOT/install.sh" || fail "upstream-only architecture is not explicit"
grep -q 'aether-shortcut-runner-base' "$ROOT/install.sh" || fail "tested Saman runner is not retained as an adapter base"
pass "installer is upstream-only"

# Compatibility path must be a symlink to the same official Aether executable,
# never a copied or patched binary.
CORE_ALIAS="$PREFIX/bin/saman-aether-core"
AETHER_COMMAND="$PREFIX/bin/aether"
AETHER_REAL="$(readlink -f "$AETHER_COMMAND")"
install_core_alias
[ -L "$CORE_ALIAS" ] || fail "compatibility Aether path is not a symlink"
[ "$(readlink -f "$CORE_ALIAS")" = "$AETHER_REAL" ] || fail "compatibility alias does not follow official Aether"
[ "$CORE_ALIAS" -ef "$PREFIX/bin/aether" ] || fail "compatibility alias is not the same executable"
pass "compatibility alias follows official Aether"

# The runtime adapter exports the official Aether path into the mature runner.
BASE_RUNNER="$HOME/.aether-shortcut-runner-base"
RUNNER="$HOME/.aether-shortcut-runner"
cp "$ROOT/aether-shortcut-runner" "$BASE_RUNNER"; chmod +x "$BASE_RUNNER"
sed -i 's/^AETHER_BASE_VERSION=.*/AETHER_BASE_VERSION="${SAMAN_AETHER_VERSION:-upstream}"/' "$BASE_RUNNER"
install_upstream_runner_adapter
grep -q 'command -v aether' "$RUNNER" || fail "runner adapter does not discover official Aether"
grep -q 'export SAMAN_AETHER_CORE=' "$RUNNER" || fail "runner adapter does not delegate official Aether"
if grep -q 'saman-aether-core.*cp\|cp.*saman-aether-core' "$RUNNER"; then fail "runner adapter copies Aether"; fi
bash -n "$RUNNER" "$BASE_RUNNER"
pass "upstream runner adapter"

# The Center keeps its existing controls but clearly labels the core as upstream.
CENTER_ROOT="$TEST_TMP/center"
cp -a "$ROOT/termux/saman-center-v2" "$CENTER_ROOT"
install_upstream_center_adapter
grep -q '\[official upstream\]' "$CENTER_ROOT/modules/aether.sh" || fail "Center does not label upstream Aether"
grep -q 'aether-base.sh' "$CENTER_ROOT/modules/aether.sh" || fail "Center adapter does not preserve mature Aether controls"
bash -n "$CENTER_ROOT/modules/aether.sh" "$CENTER_ROOT/modules/aether-base.sh"
pass "Center upstream identity adapter"

# Explicit Aether paths are allowed, but recursive use of Saman's compatibility
# alias as the source command is rejected.
CUSTOM="$TEST_TMP/custom-aether"
cp "$PREFIX/bin/aether" "$CUSTOM"; chmod +x "$CUSTOM"
SAMAN_AETHER_BIN="$CUSTOM"; resolve_upstream_aether
[ "$AETHER_COMMAND" = "$CUSTOM" ] || fail "explicit SAMAN_AETHER_BIN ignored"
SAMAN_AETHER_BIN="$CORE_ALIAS"
if (resolve_upstream_aether >/dev/null 2>&1); then fail "recursive compatibility alias accepted as upstream"; fi
unset SAMAN_AETHER_BIN
pass "explicit upstream selection and recursion guard"

# Uninstall target list may include only Saman-managed files. It must never
# include the user's official `aether` command.
if targets | grep -Fxq "$PREFIX/bin/aether"; then fail "official Aether is an uninstall target"; fi
targets | grep -Fxq "$CORE_ALIAS" || fail "compatibility alias missing from uninstall targets"
pass "official Aether is never an uninstall target"

# The obsolete patched Termux build workflow must stay retired.
[ ! -e "$ROOT/.github/workflows/termux-core.yml" ] || fail "patched Termux core workflow still exists"
workflow="$ROOT/.github/workflows/termux-maintenance.yml"
for suite in test-termux.sh test-center.sh test-safety.sh test-backup.sh test-diagnostics.sh test-runner.sh; do
    grep -q "bash termux/tests/$suite" "$workflow" || fail "CI omits $suite"
done
pass "CI covers upstream-only Termux integration"

printf 'All Termux maintenance tests passed.\n'
