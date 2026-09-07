#!/usr/bin/env bash
set -euo pipefail

# Run in Termux. Build native Android libraries on GitHub's Linux runner.
# --push pushes an already-reviewed, clean commit; --build requests a new CI run.
REPO='velnox4827/saman-aether'
BRANCH='vpn-debug-hev'
WORKFLOW='android-apk.yml'
ROOT_DIR="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
cd "$ROOT_DIR"
command -v gh >/dev/null || { echo 'Install first: pkg install git gh'; exit 1; }
gh auth status >/dev/null 2>&1 || { echo 'Sign in first: gh auth login'; exit 1; }
[[ "$(git branch --show-current)" == "$BRANCH" ]] || { echo "Open branch $BRANCH first."; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo 'Local changes found. Review and commit or save them before building.'; exit 1; }
ORIGIN="$(git remote get-url origin)"
case "$ORIGIN" in
  https://github.com/velnox4827/saman-aether|https://github.com/velnox4827/saman-aether.git|git@github.com:velnox4827/saman-aether.git) ;;
  *) echo 'Unexpected origin repository; stopped.'; exit 1 ;;
esac

case "${1:-}" in
  --push) git push origin "HEAD:refs/heads/$BRANCH" ;;
  --build) ;;
  '') ;;
  *) echo 'Usage: bash termux/build-debug.sh [--push|--build]'; exit 1 ;;
esac
COMMIT="$(git rev-parse HEAD)"
REMOTE_COMMIT="$(gh api "repos/$REPO/git/ref/heads/$BRANCH" --jq '.object.sha')"
[[ "$COMMIT" == "$REMOTE_COMMIT" ]] || { echo 'Local and remote commits differ. Pull with --ff-only or use --push after review.'; exit 1; }
BASE_RUN_ID=0
if [[ "${1:-}" == '--build' ]]; then
  BASE_RUN_ID="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --branch "$BRANCH" --commit "$COMMIT" --limit 1 --json databaseId --jq '.[0].databaseId // 0')"
  [[ "$BASE_RUN_ID" =~ ^[0-9]+$ ]] || { echo 'Invalid previous run id'; exit 1; }
  # This workflow builds debug only unless build_release is explicitly true.
  gh workflow run "$WORKFLOW" --repo "$REPO" --ref "$BRANCH" -f build_release=false
fi

RUN_ID=''
for ((attempt=0; attempt<30; attempt++)); do
  RUN_ID="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --branch "$BRANCH" --commit "$COMMIT" --limit 10 --json databaseId --jq "map(select(.databaseId > $BASE_RUN_ID)) | .[0].databaseId // empty")"
  [[ -n "$RUN_ID" ]] && break
  sleep 2
done
[[ -n "$RUN_ID" ]] || { echo 'No matching run yet. Retry, or use --build.'; exit 1; }
RUN_COMMIT="$(gh run view "$RUN_ID" --repo "$REPO" --json headSha --jq '.headSha')"
[[ "$RUN_COMMIT" == "$COMMIT" ]] || { echo 'Run/commit mismatch; stopped.'; exit 1; }
echo "Building commit $COMMIT: https://github.com/$REPO/actions/runs/$RUN_ID"
if ! gh run watch "$RUN_ID" --repo "$REPO" --exit-status; then
  gh run view "$RUN_ID" --repo "$REPO" --log-failed > "$ROOT_DIR/../saman-v175-ci-failed.txt"
  echo 'Build failed. Log saved beside the repository: saman-v175-ci-failed.txt'
  exit 1
fi

VERSION="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' android-app/app/build.gradle.kts | head -n 1)"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid version'; exit 1; }
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/saman-debug.XXXXXX")"
trap 'rm -rf "$WORK_DIR"' EXIT
gh run download "$RUN_ID" --repo "$REPO" --name "saman-tunnel-v$VERSION-debug" --dir "$WORK_DIR"
(cd "$WORK_DIR" && sha256sum --check SHA256SUMS)
ABI_LIST="$(getprop ro.product.cpu.abilist 2>/dev/null || true)"
case "$ABI_LIST" in
  *arm64-v8a*) APK="Saman-Tunnel-v$VERSION-arm64-v8a-debug.apk" ;;
  *armeabi-v7a*) APK="Saman-Tunnel-v$VERSION-armeabi-v7a-debug.apk" ;;
  *) echo "Unsupported device ABI: $ABI_LIST"; exit 1 ;;
esac
DOWNLOAD_DIR="$HOME/storage/downloads"
if [[ ! -d "$DOWNLOAD_DIR" || ! -w "$DOWNLOAD_DIR" ]]; then
  DOWNLOAD_DIR="$HOME/Saman-Tunnel-downloads"
  mkdir -p "$DOWNLOAD_DIR"
fi
DEST_DIR="$(mktemp -d "$DOWNLOAD_DIR/Saman-Tunnel-v$VERSION.XXXXXX")"
cp "$WORK_DIR/$APK" "$DEST_DIR/$APK"
echo "APK ready: $DEST_DIR/$APK"
echo 'Install Saman Tunnel Debug. Stop the older Saman instance before connecting.'
if command -v termux-open >/dev/null; then
  termux-open --view --content-type application/vnd.android.package-archive "$DEST_DIR/$APK" || true
fi
