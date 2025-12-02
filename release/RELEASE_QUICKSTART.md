# Quick Release Guide

## TL;DR - Release in 4 Steps

```bash
# 1. Update version in package.json
# Edit package.json and change "version": "1.0.0" to "1.0.1" (or desired version)

# 2. Commit the version change
git add package.json
git commit -m "Bump version to 1.0.1"

# 3. Push to main
git push origin main

# 4. Create and push version tag
git tag v1.0.1
git push origin v1.0.1
```

✅ Done! GitHub Actions will automatically build and release.

## ⚠️ IMPORTANT: Version Must Match

The version in `package.json` **MUST match** the git tag:
- `package.json`: `"version": "1.0.1"`
- Git tag: `v1.0.1`

If they don't match, electron-builder will use the package.json version and may overwrite an existing release!

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

### Stable Release (1.0.0)
```bash
# 1. Update package.json: "version": "1.0.0"
# 2. Commit and push
git add package.json
git commit -m "Bump version to 1.0.0"
git push origin main

# 3. Tag and push
git tag v1.0.0
git push origin v1.0.0
```

### Beta Release (1.0.0-beta.1)
```bash
# 1. Update package.json: "version": "1.0.0-beta.1"
# 2. Commit and push
git add package.json
git commit -m "Bump version to 1.0.0-beta.1"
git push origin main

# 3. Tag and push
git tag v1.0.0-beta.1
git push origin v1.0.0-beta.1
```

### Patch Release (1.0.1)
```bash
# 1. Update package.json: "version": "1.0.1"
# 2. Commit and push
git add package.json
git commit -m "Bump version to 1.0.1"
git push origin main

# 3. Tag and push
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

**Q: Wrong version number in installer?**
- **Check package.json version matches the git tag!**
- Example: If tag is `v1.0.1`, package.json must have `"version": "1.0.1"`
- electron-builder reads the version from package.json, not the git tag

**Q: Release is overwriting existing release?**
- This happens when package.json version doesn't match the tag
- Delete the tag, update package.json, and recreate the tag:
  ```bash
  git tag -d v1.0.1
  git push origin :refs/tags/v1.0.1
  # Update package.json version
  git add package.json
  git commit -m "Bump version to 1.0.1"
  git push origin main
  git tag v1.0.1
  git push origin v1.0.1
  ```

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
