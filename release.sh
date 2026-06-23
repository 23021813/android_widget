#!/usr/bin/env bash
#
# release.sh — End-to-end release pipeline for CarFloat.
#
# Steps:
#   1. Validate env (java 17, git, gh CLI, clean working tree)
#   2. Bump version.json (manually or auto patch/minor/major)
#   3. Build signed release APK (R8 minify, v2 scheme)
#   4. Verify APK signature
#   5. git commit + push main
#   6. Create + push annotated tag
#   7. Create GitHub release with APK + version.json
#   8. Commit corrected version.json (downloadUrl) + push
#
# Usage:
#   ./release.sh                          # interactive: ask for new version
#   ./release.sh 23 1.5.1 "fix stuff"     # explicit versionCode versionName changelog
#   ./release.sh patch "fix stuff"        # auto-bump 1.5.0 -> 1.5.1
#   ./release.sh minor "add feature"      # auto-bump 1.5.0 -> 1.6.0
#   ./release.sh major "breaking"         # auto-bump 1.5.0 -> 2.0.0
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

# ── 1. Prereq checks ───────────────────────────────────────────────────────
step "1/8  Validating environment"

[ -x "./gradlew" ] || die "gradlew not executable. Run: chmod +x gradlew"
[ -f "$VERSION_JSON" ] || die "$VERSION_JSON not found"

if [ ! -d "$JAVA_HOME_OPENJDK17" ]; then
    die "Java 17 not found at $JAVA_HOME_OPENJDK17
Edit JAVA_HOME_OPENJDK17 in this script to your local path."
fi
export JAVA_HOME="$JAVA_HOME_OPENJDK17"
export PATH="$JAVA_HOME/bin:$PATH"
ok "Java 17: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"

command -v gh >/dev/null 2>&1 || die "gh CLI not installed. Run: brew install gh"
gh auth status >/dev/null 2>&1 || die "gh CLI not authenticated. Run: gh auth login"
ok "gh CLI authenticated"

command -v git >/dev/null 2>&1 || die "git not installed"
ok "git available"

# Working tree must be clean (uncommitted changes need explicit handling)
if ! git diff-index --quiet HEAD -- 2>/dev/null; then
    warn "Working tree has uncommitted changes. They will be staged automatically."
    git add -A
fi

# ── 2. Resolve version bump ────────────────────────────────────────────────
step "2/8  Resolving version"

CURRENT_VERSION_CODE=$(python3 -c "import json; print(json.load(open('$VERSION_JSON'))['versionCode'])")
CURRENT_VERSION_NAME=$(python3 -c "import json; print(json.load(open('$VERSION_JSON'))['versionName'])")
ok "Current: $CURRENT_VERSION_NAME (code $CURRENT_VERSION_CODE)"

NEW_VERSION_CODE=""
NEW_VERSION_NAME=""
CHANGELOG=""

# Parse args
case "${1:-}" in
    "")
        # Interactive
        read -rp "  New version name (e.g. 1.5.1): " NEW_VERSION_NAME
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
        # Explicit: code name "changelog"
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

# ── 3. Bump version.json (placeholder downloadUrl; will fix after release) ─
step "3/8  Updating version.json"

python3 - "$VERSION_JSON" "$NEW_VERSION_CODE" "$NEW_VERSION_NAME" "$CHANGELOG" <<'PY'
import json, sys
path, code, name, cl = sys.argv[1:]
with open(path) as f:
    data = json.load(f)
data['versionCode'] = int(code)
data['versionName'] = name
data['changelog'] = cl
# downloadUrl is corrected in step 7 once we know the real asset URL
data['downloadUrl'] = f"https://github.com/{REPO_OWNER}/{REPO_NAME}/releases/download/{data['versionName']}/app-release.apk" if False else data.get('downloadUrl', '')
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

# ── 4. Build release APK ──────────────────────────────────────────────────
step "4/8  Building signed release APK (this may take 1-3 min)"

./gradlew :app:assembleRelease --no-daemon --console=plain 2>&1 | tail -3
[ -f "$APK_PATH" ] || die "APK not found at $APK_PATH"
APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
ok "APK built: $APK_SIZE"

# ── 5. Verify signature ───────────────────────────────────────────────────
step "5/8  Verifying APK signature (v2 scheme)"

if VERIFY_OUT=$("$APKSIGNER" verify --print-certs "$APK_PATH" 2>&1); then
    ok "Signature OK"
else
    die "Signature verification failed:\n$VERIFY_OUT"
fi

# ── 6. Commit + push main ─────────────────────────────────────────────────
step "6/8  Committing + pushing to $GIT_BRANCH"

git add -A
if git diff --cached --quiet; then
    warn "No changes to commit"
else
    git commit -m "chore: bump version to $NEW_VERSION_NAME"
    ok "Committed"
fi
git push "$GIT_REMOTE" "$GIT_BRANCH"
ok "Pushed to $GIT_REMOTE/$GIT_BRANCH"

# ── 7. Create tag + GitHub release ────────────────────────────────────────
step "7/8  Creating tag $TAG + GitHub release"

# Delete tag locally+remotely if it already exists (idempotent re-run)
if git rev-parse "$TAG" >/dev/null 2>&1; then
    warn "Tag $TAG already exists locally. Deleting and recreating."
    git tag -d "$TAG"
    git push "$GIT_REMOTE" ":refs/tags/$TAG" 2>/dev/null || true
fi

git tag -a "$TAG" -m "Release $NEW_VERSION_NAME"
git push "$GIT_REMOTE" "$TAG"
ok "Tag $TAG pushed"

# Create release (overwrite if exists)
NOTES=$(cat <<EOF
## CarFloat $NEW_VERSION_NAME

$CHANGELOG

### Build
- Min SDK 28, Target SDK 34
- Signed with v2 APK signature scheme
- R8 minified
EOF
)

gh release create "$TAG" \
    "$APK_PATH" \
    "$VERSION_JSON" \
    --title "CarFloat $NEW_VERSION_NAME" \
    --notes "$NOTES" \
    --clobber
ok "GitHub release created: https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/$TAG"

# ── 8. Fix downloadUrl in version.json (real URL) + commit + upload ───────
step "8/8  Fixing version.json downloadUrl + re-uploading"

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
git commit -m "fix: correct downloadUrl in version.json to match release asset name"
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
