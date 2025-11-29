# Deployment Guide - Trading Backtester Desktop App

This guide explains how to deploy the Electron desktop app using automated GitHub Releases.

## Overview

The deployment is fully automated using GitHub Actions:
- **Trigger**: Push a version tag (e.g., `v1.0.0`)
- **Builds**: Automatically builds for Windows, macOS, and Linux
- **Releases**: Creates a GitHub Release with installers attached
- **Users**: Download installers directly from GitHub Releases page

## Prerequisites

1. **GitHub Repository**: Your code is pushed to GitHub
2. **GitHub Actions**: Enabled in repository settings
3. **Permissions**: Repository has "Read and write permissions" for workflows

### Enable GitHub Actions Permissions

1. Go to your repository on GitHub
2. Navigate to `Settings` → `Actions` → `General`
3. Under "Workflow permissions", select:
   - ✅ **Read and write permissions**
4. Click **Save**

## Release Process

### Step 1: Update Version

Update the version in `package.json`:

```json
{
  "version": "1.0.0"  // Change to your new version
}
```

### Step 2: Commit Changes

```bash
cd /home/sonni/development/git/trading

# Commit your changes
git add .
git commit -m "Release v1.0.0"
git push origin main
```

### Step 3: Create and Push Version Tag

```bash
# Create a version tag
git tag v1.0.0

# Push the tag to GitHub
git push origin v1.0.0
```

**That's it!** The CI/CD pipeline will automatically:
1. ✅ Build the backend JAR
2. ✅ Build the frontend
3. ✅ Create installers for Windows, macOS, and Linux
4. ✅ Create a GitHub Release
5. ✅ Upload installers to the release

### Step 4: Monitor the Build

1. Go to your repository on GitHub
2. Click the **Actions** tab
3. You'll see "Release Desktop App" workflow running
4. Click to see progress for all three platforms (Windows, macOS, Linux)

Build takes approximately **10-15 minutes** per platform.

### Step 5: Release is Ready

Once complete, the release will appear at:
```
https://github.com/YOUR_USERNAME/trading/releases
```

Users can download:
- **Windows**: `Trading-Backtester-Setup-1.0.0.exe`
- **macOS**: `Trading-Backtester-1.0.0.dmg`
- **Linux**: `Trading-Backtester-1.0.0.AppImage` or `.deb`

## Release Types

### Stable Release (Recommended)

```bash
git tag v1.0.0
git push origin v1.0.0
```

### Pre-release (Beta/Alpha)

```bash
git tag v1.0.0-beta.1
git push origin v1.0.0-beta.1
```

Electron-builder automatically marks tags with `-alpha`, `-beta`, `-rc` as pre-releases.

## Update Existing Release

If you need to update a release:

```bash
# Delete local tag
git tag -d v1.0.0

# Delete remote tag
git push origin :refs/tags/v1.0.0

# Create new tag
git tag v1.0.0

# Push new tag
git push origin v1.0.0
```

The workflow will re-run and update the release.

## Common Release Scenarios

### Patch Release (Bug Fix)

```bash
# v1.0.0 → v1.0.1
git tag v1.0.1
git push origin v1.0.1
```

### Minor Release (New Features)

```bash
# v1.0.0 → v1.1.0
git tag v1.1.0
git push origin v1.1.0
```

### Major Release (Breaking Changes)

```bash
# v1.0.0 → v2.0.0
git tag v2.0.0
git push origin v2.0.0
```

## Troubleshooting

### Build Fails on One Platform

Check the Actions tab to see which platform failed:
- Windows: Usually dependency issues
- macOS: May need code signing
- Linux: Rarely fails

You can re-run failed jobs from the Actions page.

### Permission Denied Errors

Ensure your repository has write permissions:
1. Settings → Actions → General
2. Workflow permissions: "Read and write permissions"

### Release Not Created

Check:
1. Tag must start with `v` (e.g., `v1.0.0`)
2. `GH_TOKEN` is automatically provided by GitHub Actions
3. Check workflow logs for errors

### Installers Not Uploaded

If the release is created but installers are missing:
1. Check if builds succeeded on all platforms
2. Look for errors in "Build Electron app" step
3. Verify `dist-electron` directory contains files

## Advanced: Code Signing

### macOS Code Signing (Optional)

For distributing on macOS, you need:
1. Apple Developer account ($99/year)
2. Developer ID Application certificate
3. Add secrets to GitHub:
   - `MAC_CERTIFICATE` (base64-encoded .p12 file)
   - `MAC_CERTIFICATE_PASSWORD`

Uncomment these lines in `.github/workflows/release.yml`:
```yaml
env:
  CSC_LINK: ${{ secrets.MAC_CERTIFICATE }}
  CSC_KEY_PASSWORD: ${{ secrets.MAC_CERTIFICATE_PASSWORD }}
```

### Windows Code Signing (Optional)

For Windows code signing:
1. Purchase code signing certificate
2. Add secrets:
   - `WIN_CERTIFICATE`
   - `WIN_CERTIFICATE_PASSWORD`

Uncomment in workflow file.

## Auto-Updates (Future Enhancement)

To enable auto-updates:

1. Users will be notified of new versions
2. Can download and install updates from within the app
3. Requires code signing for Windows/macOS

Add to `electron/main.js`:
```javascript
const { autoUpdater } = require('electron-updater')

app.on('ready', () => {
  autoUpdater.checkForUpdatesAndNotify()
})
```

## Release Checklist

Before creating a release:

- [ ] Update version in `package.json`
- [ ] Test the app locally (`npm run dist`)
- [ ] Update `CHANGELOG.md` (if you have one)
- [ ] Commit all changes
- [ ] Create and push version tag
- [ ] Monitor GitHub Actions workflow
- [ ] Verify release page has all installers
- [ ] Test download and installation
- [ ] Announce release to users

## Distribution

Share the release with users:

```
Download the latest version:
https://github.com/YOUR_USERNAME/trading/releases/latest

Or specific version:
https://github.com/YOUR_USERNAME/trading/releases/tag/v1.0.0
```

Users click on the appropriate installer for their platform.

## Rollback

To rollback to a previous version:

1. Users can download older releases from the releases page
2. Or create a new release with the previous version code
3. GitHub keeps all previous releases available

## Support

If users have issues:
1. Check the releases page for compatible versions
2. Verify system requirements (Java 21+)
3. Check port 8080 is available
4. Review logs in the app

## Next Steps

Consider setting up:
- [ ] App icons (currently using placeholder)
- [ ] Code signing certificates
- [ ] Auto-update functionality
- [ ] Crash reporting (Sentry, etc.)
- [ ] Analytics (optional)
