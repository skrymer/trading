# Quick Release Guide

## TL;DR - Release in 3 Steps

```bash
# 1. Update version in package.json
# 2. Commit and push
git add .
git commit -m "Release v1.0.0"
git push origin main

# 3. Create and push version tag
git tag v1.0.0
git push origin v1.0.0
```

✅ Done! GitHub Actions will automatically build and release.

## First Time Setup

### 1. Enable GitHub Actions Permissions

Go to: `https://github.com/YOUR_USERNAME/trading/settings/actions`

Enable: **Read and write permissions**

### 2. Verify Workflow File

The workflow file is already created at:
```
.github/workflows/release.yml
```

## What Happens Automatically

When you push a version tag:

1. **GitHub Actions triggers** (takes ~10-15 min)
2. **Builds on 3 platforms**:
   - Ubuntu (Linux AppImage + .deb)
   - macOS (DMG)
   - Windows (NSIS installer)
3. **Creates GitHub Release** with installers attached
4. **Users download** from releases page

## Release Examples

### Stable Release
```bash
git tag v1.0.0
git push origin v1.0.0
```

### Beta Release
```bash
git tag v1.0.0-beta.1
git push origin v1.0.0-beta.1
```

### Patch Release
```bash
git tag v1.0.1
git push origin v1.0.1
```

## Check Release Status

1. Go to: `https://github.com/YOUR_USERNAME/trading/actions`
2. Look for "Release Desktop App" workflow
3. Click to see progress

## Download Installers

After build completes:
```
https://github.com/YOUR_USERNAME/trading/releases
```

## Update a Release

```bash
# Delete existing tag
git tag -d v1.0.0
git push origin :refs/tags/v1.0.0

# Create new tag
git tag v1.0.0
git push origin v1.0.0
```

## Common Issues

**Q: Release not created?**
- Check tag starts with `v` (e.g., v1.0.0)
- Verify GitHub Actions has write permissions

**Q: Build failed?**
- Check Actions tab for error details
- Re-run failed jobs

**Q: Missing installers?**
- Wait for all platform builds to complete
- Check workflow logs

## Distribution

Share with users:
```
Download: https://github.com/YOUR_USERNAME/trading/releases/latest
```

For more details, see `DEPLOYMENT.md`
