#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d "${TMPDIR:-${HOME}/.cache}/saman-xray-check.XXXXXX")"
trap 'rm -rf "$OUT"' EXIT
MAIN="$ROOT/android-app/app/src/main/java/com/saman/tunnel"
TEST="$ROOT/android-app/app/src/test/java/com/saman/tunnel"
"${CC:-clang}" -O2 "$ROOT/android-app/app/src/main/cpp/xray_guard.c" -o "$OUT/guard"
kotlinc "$MAIN/XrayRunner.kt" "$TEST/XrayRunnerCheck.kt" -include-runtime -d "$OUT/runner.jar"
XRAY_GUARD="$OUT/guard" java -jar "$OUT/runner.jar"
kotlinc "$MAIN/ModeCatalog.kt" "$MAIN/AetherArguments.kt" "$TEST/StoreModeCheck.kt" -include-runtime -d "$OUT/modes.jar"
java -jar "$OUT/modes.jar"
python3 - "$OUT/guard" <<'PY'
import os, pathlib, subprocess, sys, time
shell = '/system/bin/sleep' if pathlib.Path('/system/bin/sleep').exists() else '/bin/sleep'
code = 'import subprocess,time,sys; p=subprocess.Popen([sys.argv[1],sys.argv[2],"30"]); print(p.pid,flush=True); time.sleep(30)'
owner = subprocess.Popen([sys.executable, '-c', code, sys.argv[1], shell], stdout=subprocess.PIPE, text=True)
try:
    pid = int(owner.stdout.readline().strip())
    time.sleep(.2)
    assert pathlib.Path(f'/proc/{pid}').exists()
finally:
    owner.kill()
    owner.wait()
for _ in range(30):
    status = pathlib.Path(f'/proc/{pid}/stat')
    if not status.exists() or status.read_text().split()[2] == 'Z':
        break
    time.sleep(.1)
else:
    os.kill(pid, 9)
    raise AssertionError('Xray guard left a running orphan')
print('PASS: native guard kills core when Android service owner dies')
PY
