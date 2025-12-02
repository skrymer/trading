# Version Mismatch Issue - package.json vs Git Tag

## Date
2025-12-02

## Problem

When creating release v1.0.1, the GitHub Actions workflow built version **1.0.0** instead of **1.0.1**, and overwrote the existing v1.0.0 release.

**GitHub Actions Logs:**
```
• building target=nsis file=dist-electron\Trading Backtester Setup 1.0.0.exe
• publishing publisher=Github (owner: skrymer, project: trading, version: 1.0.0)
• overwrite published file file=Trading-Backtester-Setup-1.0.0.exe reason=already exists on GitHub
```

## Root Cause

**electron-builder reads the version from `package.json`, NOT from the git tag.**

When the v1.0.1 tag was pushed:
- Git tag: `v1.0.1` ✅
- package.json version: `"1.0.0"` ❌

electron-builder saw version 1.0.0 in package.json and:
1. Built installer with filename: `Trading Backtester Setup 1.0.0.exe`
2. Tried to publish to GitHub Release v1.0.1
3. Found existing v1.0.0 release
4. Overwrote the v1.0.0 release files

**Result:** No v1.0.1 release was created; v1.0.0 was updated instead.

## How electron-builder Determines Version

electron-builder uses the following priority:

1. **package.json** `"version"` field (PRIMARY SOURCE)
2. Git tag (only for release metadata, not for versioning)

From the electron-builder source code:
```javascript
// electron-builder reads:
const appVersion = appInfo.version // from package.json
const buildNumber = process.env.BUILD_NUMBER || appInfo.buildNumber
```

The git tag (`v1.0.1`) is used to:
- Determine which GitHub Release to publish to
- Generate release notes

But the **actual application version** comes from package.json.

## Solution

Always ensure package.json version matches the git tag:

### Correct Release Process:

```bash
# Step 1: Update package.json version
# Edit package.json: "version": "1.0.1"

# Step 2: Commit the version change
git add package.json
git commit -m "Bump version to 1.0.1"

# Step 3: Push to main
git push origin main

# Step 4: Create and push tag (matching package.json version)
git tag v1.0.1
git push origin v1.0.1
```

### Version Matching Examples:

| package.json | Git Tag | Result |
|--------------|---------|--------|
| `"1.0.0"` | `v1.0.0` | ✅ Correct |
| `"1.0.1"` | `v1.0.1` | ✅ Correct |
| `"1.0.0"` | `v1.0.1` | ❌ Wrong - builds 1.0.0 |
| `"1.0.1"` | `v1.0.0` | ❌ Wrong - builds 1.0.1 |

## What Happened in Our Case

### Attempt 1 (Incorrect):
```bash
# package.json had "version": "1.0.0"
git tag v1.0.1
git push origin v1.0.1
```

**Result:**
- electron-builder built version 1.0.0 (from package.json)
- Tried to publish to v1.0.1 release
- Found and overwrote existing v1.0.0 release
- No v1.0.1 release created

### Attempt 2 (Correct):
```bash
# Updated package.json to "version": "1.0.1"
git add package.json
git commit -m "Bump version to 1.0.1"
git push origin main

# Deleted and recreated tag
git tag -d v1.0.1
git push origin :refs/tags/v1.0.1
git tag v1.0.1
git push origin v1.0.1
```

**Result:**
- electron-builder built version 1.0.1 (from package.json)
- Published to v1.0.1 release
- Created new v1.0.1 release with correct version ✅

## Prevention

### Documentation Updates

Updated the following files to include version update step:

1. **`release/RELEASE_QUICKSTART.md`**
   - Added "4 Steps" instead of "3 Steps"
   - Step 1: Update package.json version
   - Added warning about version matching
   - Updated all release examples

2. **`release/DEPLOYMENT.md`**
   - Made version update step more prominent
   - Added critical warning about version matching
   - Added version matching examples

3. **Common Issues Section**
   - Added "Wrong version number in installer?" troubleshooting
   - Added "Release is overwriting existing release?" troubleshooting
   - Included fix instructions

### Checklist for Future Releases

Before creating a release:
- [ ] Update `package.json` version to match intended tag
- [ ] Commit package.json change: `git add package.json && git commit -m "Bump version to X.Y.Z"`
- [ ] Push to main: `git push origin main`
- [ ] Create tag matching package.json: `git tag vX.Y.Z`
- [ ] Push tag: `git push origin vX.Y.Z`
- [ ] Verify workflow builds correct version in Actions tab
- [ ] Verify release appears with correct version number

## Automated Version Management (Future Enhancement)

To prevent this issue in the future, we could:

### Option 1: npm version command
Use npm's built-in version management:

```bash
# Automatically updates package.json AND creates git tag
npm version patch  # 1.0.0 -> 1.0.1
npm version minor  # 1.0.1 -> 1.1.0
npm version major  # 1.1.0 -> 2.0.0

# Then push
git push origin main --follow-tags
```

### Option 2: Release Script
Create a `scripts/release.sh`:

```bash
#!/bin/bash
VERSION=$1

if [ -z "$VERSION" ]; then
  echo "Usage: ./scripts/release.sh 1.0.1"
  exit 1
fi

# Update package.json
npm version $VERSION --no-git-tag-version

# Commit
git add package.json
git commit -m "Bump version to $VERSION"
git push origin main

# Tag
git tag v$VERSION
git push origin v$VERSION

echo "✅ Release v$VERSION created!"
```

Usage:
```bash
./scripts/release.sh 1.0.1
```

### Option 3: GitHub Actions Workflow
Create a manual workflow that takes version as input:

```yaml
name: Create Release
on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Version number (e.g., 1.0.1)'
        required: true

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - name: Update version
        run: npm version ${{ github.event.inputs.version }} --no-git-tag-version

      - name: Commit and tag
        run: |
          git config user.name github-actions
          git config user.email github-actions@github.com
          git add package.json
          git commit -m "Bump version to ${{ github.event.inputs.version }}"
          git tag v${{ github.event.inputs.version }}
          git push origin main
          git push origin v${{ github.event.inputs.version }}
```

## Lessons Learned

1. **electron-builder uses package.json version, not git tag version**
2. **Always update package.json BEFORE creating the tag**
3. **Commit package.json changes separately** for clarity
4. **Document the process clearly** to prevent future mistakes
5. **Add warnings** in documentation about version matching

## References

- electron-builder versioning: https://www.electron.build/configuration/configuration#Metadata-version
- npm version command: https://docs.npmjs.com/cli/v8/commands/npm-version
- Semantic Versioning: https://semver.org/

## Related Files

- `release/RELEASE_QUICKSTART.md` - Quick release guide
- `release/DEPLOYMENT.md` - Detailed deployment guide
- `package.json` - Contains application version
- `.github/workflows/release.yml` - GitHub Actions release workflow
