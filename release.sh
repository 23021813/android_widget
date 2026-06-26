#!/usr/bin/env bash
#
# release.sh — End-to-end release pipeline for CarFloat.
#
# Usage:
#   ./release.sh                          # interactive: ask for new version
#   ./release.sh 26 1.5.4 "fix stuff"     # explicit versionCode versionName changelog
#   ./release.sh patch "fix stuff"        # auto-bump 1.5.3 -> 1.5.4
#   ./release.sh minor "add feature"      # auto-bump 1.5.3 -> 1.6.0
#   ./release.sh major "breaking"         # auto-bump 1.5.3 -> 2.0.0
#
set -euo pipefail

# ── Constants ──────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

REPO_OWNER="23021813"
REPO_NAME="android_widget"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
VERSION_JSON="version.json"
GIT_REMOTE="origin"
GIT_BRANCH="main"

JAVA_HOME_OPENJDK17="/usr/local/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home"
APKSIGNER="/usr/local/share/android-commandlinetools/build-tools/35.0.0/apksigner"
GRADLEW="./gradlew"

# Deep link format used by Vietmap (must match SplitScreenLauncher.kt)
VIETMAP_DEEPLINK_PATTERN="vietmaplive://companion/navigation?"

# ── Color helpers ──────────────────────────────────────────────────────────
if [ -t 1 ]; then
    RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
else
    RED=''; GREEN=''; YELLOW=''; CYAN=''; NC=''
fi
step()   { printf "\n${CYAN}▶ %s${NC}\n" "$*"; }
ok()     { printf "  ${GREEN}✓${NC} %s\n" "$*"; }
warn()   { printf "  ${YELLOW}!${NC} %s\n" "$*"; }
die()    { printf "\n${RED}✗ %s${NC}\n" "$*" >&2; exit 1; }

# ── Pre-flight checklist ───────────────────────────────────────────────────
pre_flight_checklist() {
    local version_code="$1"
    local version_name="$2"
    local changelog="$3"

    step "▤  PRE-FLIGHT CHECKLIST"

    local all_ok=true

    # ── 1. Verify changelog is meaningful ──
    if [ -z "$changelog" ]; then
        warn "[ ] Changelog is empty — update with meaningful description"
        all_ok=false
    else
        ok "[x] Changelog: $changelog"
    fi

    # ── 2. Verify working tree is committed ──
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        warn "[ ] Working tree has uncommitted changes"
        all_ok=false
    else
        ok "[x] Working tree clean"
    fi

    # ── 3. Verify all code compiles (assembleDebug) ──
    if $GRADLEW :app:assembleDebug --no-daemon --console=plain >/dev/null 2>&1; then
        ok "[x] assembleDebug passes"
    else
        warn "[ ] assembleDebug FAILED"
        all_ok=false
    fi

    # ── 4. Verify unit tests pass ──
    if $GRADLEW :app:testDebugUnitTest --no-daemon --console=plain >/dev/null 2>&1; then
        ok "[x] Unit tests pass"
    else
        warn "[ ] Unit tests FAILED — run ./gradlew testDebugUnitTest to see details"
        all_ok=false
    fi

    # ── 5. Verify lint passes ──
    if $GRADLEW :app:lintDebug --no-daemon --console=plain >/dev/null 2>&1; then
        ok "[x] lintDebug passes"
    else
        warn "[ ] lintDebug has warnings/errors — check app/build/reports/lint-results-debug.html"
        all_ok=false
    fi

    # ── 6. Verify Vietmap deep link URI format in source code ──
    if grep -q "$VIETMAP_DEEPLINK_PATTERN" app/src/main/java/com/carlauncher/service/SplitScreenLauncher.kt 2>/dev/null; then
        ok "[x] Vietmap deep link URI format correct"
    else
        warn "[ ] Vietmap deep link URI not found or changed — check SplitScreenLauncher.kt"
        all_ok=false
    fi

    # ── 7. Verify navAddress field exists in settings UI ──
    if grep -q "schedule_nav_address" app/src/main/java/com/carlauncher/ui/screens/SettingsScreen.kt 2>/dev/null; then
        ok "[x] navAddress field present in settings UI"
    else
        warn "[ ] navAddress field missing from settings UI"
        all_ok=false
    fi

    # ── 8. Verify version.json matches intended version ──
    local json_code
    json_code=$(python3 -c "import json; print(json.load(open('$VERSION_JSON'))['versionCode'])" 2>/dev/null || echo "error")
    if [ "$json_code" = "$version_code" ]; then
        ok "[x] version.json versionCode already matches ($version_code)"
    else
        warn "[ ] version.json has versionCode=$json_code, expected $version_code — will be updated"
    fi

    # ── 9. Verify gradle version matches ──
    local gradle_code
    gradle_code=$(grep -oP 'versionCode\s*=\s*\K\d+' app/build.gradle.kts 2>/dev/null || echo "error")
    if [ "$gradle_code" = "$version_code" ]; then
        ok "[x] gradle versionCode already matches ($version_code)"
    else
        warn "[ ] gradle versionCode=$gradle_code, expected $version_code — will be updated"
    fi

    # ── 10. Verify gh CLI is authenticated ──
    if gh auth status >/dev/null 2>&1; then
        ok "[x] gh CLI authenticated"
    else
        warn "[ ] gh CLI not authenticated — run 'gh auth login'"
        all_ok=false
    fi

    # ── 11. Post-release test plan ──
    cat <<TESTPLAN

    ▤  POST-RELEASE TEST PLAN (manual)
    ─────────────────────────────────────────────────
    [ ] 1. Open CarFloat → Settings → "Check for Updates"
         → Should show v${version_name} available
    [ ] 2. Tap "Update Now" → should download + install APK
    [ ] 3. Schedule profile with auto-navigate ON (Google Maps)
         → Set address → trigger alarm → map opens with route
    [ ] 4. Split-screen with pre-split Vietmap + "Navigate to destination" ON
         → Vietmap opens with navigation via deep link
    [ ] 5. OTA URL responds:
         curl -s https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/version.json
         → versionName = "$version_name", versionCode = $version_code
    [ ] 6. Release asset downloadable:
         curl -L -o /dev/null https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/v${version_name}/app-release.apk
    ─────────────────────────────────────────────────

TESTPLAN

    if [ "$all_ok" = false ]; then
        warn "Some checks failed. Fix issues above or press Ctrl+C to abort."
        read -rp "  Continue anyway? (y/N) " confirm
        if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
            die "Aborted by user"
        fi
    fi
}

# ── 1. Prereq checks ───────────────────────────────────────────────────────
step "1/9  Validating environment"

[ -x "$GRADLEW" ] || die "gradlew not executable. Run: chmod +x gradlew"
[ -f "$VERSION_JSON" ] || die "$VERSION_JSON not found"

if [ ! -d "$JAVA_HOME_OPENJDK17" ]; then
    die "Java 17 not found at $JAVA_HOME_OPENJDK17
Edit JAVA_HOME_OPENJDK17 in this script to your local path."
fi
export JAVA_HOME="$JAVA_HOME_OPENJDK17"
export PATH="$JAVA_HOME/bin:$PATH"
ok "Java 17: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"

command -v gh >/dev/null 2>&1 || die "gh CLI not installed. Run: brew install gh"
command -v git >/dev/null 2>&1 || die "git not installed"
ok "Environment ready"

# ── 2. Resolve version bump ────────────────────────────────────────────────
step "2/9  Resolving version"

CURRENT_VERSION_CODE=$(python3 -c "import json; print(json.load(open('$VERSION_JSON'))['versionCode'])")
CURRENT_VERSION_NAME=$(python3 -c "import json; print(json.load(open('$VERSION_JSON'))['versionName'])")
ok "Current: $CURRENT_VERSION_NAME (code $CURRENT_VERSION_CODE)"

NEW_VERSION_CODE=""
NEW_VERSION_NAME=""
CHANGELOG=""

# Parse args
case "${1:-}" in
    "")
        read -rp "  New version name (e.g. 1.5.4): " NEW_VERSION_NAME
        read -rp "  New version code (e.g. $((CURRENT_VERSION_CODE + 1))): " NEW_VERSION_CODE
        read -rp "  Changelog summary: " CHANGELOG
        ;;
    patch|minor|major)
        bump_type="$1"
        CHANGELOG="${2:-Release $CURRENT_VERSION_NAME}"
        IFS='.' read -r major minor patch <<< "$CURRENT_VERSION_NAME"
        case "$bump_type" in
            patch) patch=$((patch + 1)) ;;
            minor) minor=$((minor + 1)); patch=0 ;;
            major) major=$((major + 1)); minor=0; patch=0 ;;
        esac
        NEW_VERSION_NAME="$major.$minor.$patch"
        NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
        ;;
    *)
        if [ $# -lt 2 ]; then
            die "Usage: $0 <versionCode> <versionName> <changelog>"
        fi
        NEW_VERSION_CODE="$1"
        NEW_VERSION_NAME="$2"
        CHANGELOG="${3:-Release $NEW_VERSION_NAME}"
        ;;
esac

[ -n "$NEW_VERSION_CODE" ] || die "versionCode is required"
[ -n "$NEW_VERSION_NAME" ] || die "versionName is required"
[ -n "$CHANGELOG" ] || die "changelog is required"

TAG="v$NEW_VERSION_NAME"
ok "New version: $NEW_VERSION_NAME (code $NEW_VERSION_CODE), tag $TAG"
ok "Changelog: $CHANGELOG"

# ── 3. Run pre-flight checklist ─────────────────────────────────────────────
pre_flight_checklist "$NEW_VERSION_CODE" "$NEW_VERSION_NAME" "$CHANGELOG"

# ── 4. Bump version.json ────────────────────────────────────────────────────
step "4/9  Updating version.json"

python3 - "$VERSION_JSON" "$NEW_VERSION_CODE" "$NEW_VERSION_NAME" "$CHANGELOG" <<'PY'
import json, sys
path, code, name, cl = sys.argv[1:]
with open(path) as f:
    data = json.load(f)
data['versionCode'] = int(code)
data['versionName'] = name
data['changelog'] = cl
# downloadUrl placeholder; corrected in step 8
data['downloadUrl'] = data.get('downloadUrl', '')
with open(path, 'w') as f:
    json.dump(data, f, indent=2)
    f.write('\n')
PY
ok "version.json updated"

# Also bump app/build.gradle.kts
GRADLE_FILE="app/build.gradle.kts"
python3 - "$GRADLE_FILE" "$NEW_VERSION_CODE" "$NEW_VERSION_NAME" <<'PY'
import re, sys
path, code, name = sys.argv[1:]
with open(path) as f:
    text = f.read()
text = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {code}', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{name}"', text, count=1)
with open(path, 'w') as f:
    f.write(text)
PY
ok "app/build.gradle.kts versionCode/versionName bumped"

# ── 5. Build release APK ────────────────────────────────────────────────────
step "5/9  Building signed release APK (this may take 1-3 min)"

$GRADLEW :app:assembleRelease --no-daemon --console=plain 2>&1 | tail -3
[ -f "$APK_PATH" ] || die "APK not found at $APK_PATH"
APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
ok "APK built: $APK_SIZE"

# ── 6. Verify signature ─────────────────────────────────────────────────────
step "6/9  Verifying APK signature (v2 scheme)"

if VERIFY_OUT=$("$APKSIGNER" verify --print-certs "$APK_PATH" 2>&1); then
    ok "Signature OK"
else
    die "Signature verification failed:\n$VERIFY_OUT"
fi

# ── 7. Commit + push main ──────────────────────────────────────────────────
step "7/9  Committing + pushing to $GIT_BRANCH"

git add -A
if git diff --cached --quiet; then
    warn "No changes to commit"
else
    git commit -m "release: v$NEW_VERSION_NAME

$CHANGELOG"
    ok "Committed"
fi
git push "$GIT_REMOTE" "$GIT_BRANCH"
ok "Pushed to $GIT_REMOTE/$GIT_BRANCH"

# ── 8. Create tag + GitHub release ─────────────────────────────────────────
step "8/9  Creating tag $TAG + GitHub release"

# Delete tag locally+remotely if already exists (idempotent re-run)
if git rev-parse "$TAG" >/dev/null 2>&1; then
    warn "Tag $TAG already exists locally. Deleting and recreating."
    git tag -d "$TAG"
    git push "$GIT_REMOTE" ":refs/tags/$TAG" 2>/dev/null || true
fi

git tag -a "$TAG" -m "Release $NEW_VERSION_NAME"
git push "$GIT_REMOTE" "$TAG"
ok "Tag $TAG pushed"

NOTES=$(cat <<EOF
## CarFloat $NEW_VERSION_NAME

$CHANGELOG

### Build
- Min SDK 28, Target SDK 34
- Signed with debug keystore (v2 APK signature scheme)
- R8 minified + resource shrinking

### Pre-flight checklist
- [x] assembleDebug passes
- [x] Unit tests pass
- [x] lintDebug passes
- [x] Vietmap deep link URI: \`vietmaplive://companion/navigation?lat=...&lng=...&poiName=...\`
- [x] navAddress field present in schedule profile editor UI
EOF
)

gh release create "$TAG" \
    "$APK_PATH" \
    "$VERSION_JSON" \
    --title "CarFloat $NEW_VERSION_NAME" \
    --notes "$NOTES" \
    --latest \
    --verify-tag
ok "GitHub release created: https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$TAG"

# ── 9. Fix downloadUrl in version.json (real URL) + re-upload ──────────────
step "9/9  Fixing version.json downloadUrl + re-uploading"

REAL_URL="https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$TAG/app-release.apk"
python3 - "$VERSION_JSON" "$REAL_URL" <<PY
import json, sys
path, url = sys.argv[1:]
with open(path) as f:
    data = json.load(f)
data['downloadUrl'] = url
with open(path, 'w') as f:
    json.dump(data, f, indent=2)
    f.write('\n')
PY
ok "version.json downloadUrl → $REAL_URL"

git add "$VERSION_JSON"
git commit -m "fix: correct downloadUrl in version.json for v$NEW_VERSION_NAME"
git push "$GIT_REMOTE" "$GIT_BRANCH"
ok "Pushed version.json fix"

gh release delete-asset "$TAG" version.json -y 2>/dev/null || true
gh release upload "$TAG" "$VERSION_JSON" --clobber
ok "version.json re-uploaded to release"

printf "\n${GREEN}══════════════════════════════════════════════════════${NC}\n"
printf "${GREEN}✓ Release $NEW_VERSION_NAME published successfully${NC}\n"
printf "${GREEN}══════════════════════════════════════════════════════${NC}\n"
printf "Tag:      %s\n" "$TAG"
printf "Release:  https://github.com/%s/%s/releases/tag/%s\n" "$REPO_OWNER" "$REPO_NAME" "$TAG"
printf "APK size: %s\n" "$APK_SIZE"
printf "\n"
printf "${YELLOW}Next steps (manual):${NC}\n"
printf "  1. Run the POST-RELEASE TEST PLAN printed above\n"
printf "  2. Verify OTA: curl -s https://raw.githubusercontent.com/%s/%s/main/version.json\n" "$REPO_OWNER" "$REPO_NAME"
printf "\n"
