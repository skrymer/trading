# JRE Bundling Implementation

## Date
2025-12-02

## Problem

When running the Windows .exe file generated from GitHub releases, users encountered the error:
```
spawn java ENOENT
```

**Root Cause:** The Electron app was spawning `java` directly, assuming Java was installed on the user's system. Most end users don't have Java installed, causing the app to fail on startup.

## Solution

Bundle a Java Runtime Environment (JRE) with the Electron app so users don't need to install Java separately.

## Changes Made

### 1. Updated `package.json`

Added JRE directory to `extraResources` so it gets bundled with the app:

```json
"extraResources": [
  {
    "from": "udgaard/build/libs/",
    "to": "backend",
    "filter": ["*.jar"]
  },
  {
    "from": "jre/",
    "to": "jre",
    "filter": ["**/*"]
  }
]
```

### 2. Updated `electron/main.js`

**Added `findJavaExecutable()` function:**
- In development mode: Uses system `java` command (requires Java installed)
- In production mode: Uses bundled JRE from `resources/jre/bin/java.exe`

**Updated `startBackend()` function:**
- Uses `findJavaExecutable()` instead of hardcoded `'java'`
- Added validation to check if bundled JRE exists
- Logs both JAR path and Java path for debugging

**Key changes:**
```javascript
// Before
backendProcess = spawn('java', [...])

// After
const javaPath = findJavaExecutable()
backendProcess = spawn(javaPath, [...])
```

### 3. Updated `.gitignore`

Added `jre/` to gitignore since JRE binaries shouldn't be committed to the repository:

```
# Electron Desktop App
node_modules/
dist-electron/
jre/
```

### 4. Created `JRE_SETUP.md`

Comprehensive guide for developers on:
- Where to download JRE (Adoptium OpenJDK 21)
- How to extract and place JRE in project
- Multi-platform build considerations
- Size implications (~180-200 MB extracted)
- Troubleshooting common issues

### 5. Updated `.github/workflows/release.yml`

Added automated JRE download step in CI/CD workflow:

```yaml
- name: Download and setup JRE
  run: |
    # Download OpenJDK 21 JRE for Windows
    $jreUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk"
    # ... extract and setup
  shell: pwsh
```

This ensures GitHub Actions automatically bundles JRE when creating releases.

## How It Works

### Development Mode (`npm run dev`)
1. Uses system Java (requires Java 21+ installed)
2. Loads frontend from Nuxt dev server (localhost:3000)
3. Faster iteration, no JRE bundling needed

### Production Mode (`npm start` or distributed .exe)
1. Uses bundled JRE from `resources/jre/bin/java.exe`
2. Loads frontend from built assets
3. No Java installation required by end users

## Setup for Local Distribution Builds

To build a distributable locally:

1. **Download JRE** (one-time setup):
   - Visit https://adoptium.net/temurin/releases/
   - Download JRE 21 ZIP for Windows (x64)
   - Extract to project root as `jre/`
   - Structure: `jre/bin/java.exe`, `jre/lib/`, etc.

2. **Build application**:
   ```bash
   npm run build:all     # Build backend + frontend
   npm run dist:win      # Create Windows installer
   ```

3. **Distribute**:
   - Installer: `dist-electron/Trading Backtester Setup X.X.X.exe`
   - Portable: `dist-electron/Trading Backtester X.X.X.exe` (if unpacked)

## GitHub Releases (Automated)

When you push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions automatically:
1. Downloads OpenJDK 21 JRE
2. Builds backend JAR
3. Builds frontend assets
4. Bundles everything with Electron
5. Creates Windows installer
6. Uploads to GitHub Release

## File Size Impact

**Before (without JRE):**
- Installer: ~50-80 MB

**After (with JRE):**
- Installer: ~120-150 MB (compressed)
- Installed size: ~400-500 MB

The size increase is necessary to ensure the app works out-of-box without requiring Java installation.

## Benefits

✅ **User-friendly**: No Java installation required
✅ **Consistent**: Same Java version across all installations
✅ **Reliable**: No Java version conflicts
✅ **Professional**: Works immediately after installation

## Alternatives Considered

### Alternative 1: Require System Java
**Pros:** Smaller installer size
**Cons:** Poor user experience, installation friction, version conflicts
**Verdict:** ❌ Not recommended for end-user distribution

### Alternative 2: jpackage (Native Java Tool)
**Pros:** Official Java packaging tool, creates native installers
**Cons:** More complex setup, Gradle integration needed, larger learning curve
**Verdict:** 🤔 Could explore for future versions

### Alternative 3: GraalVM Native Image
**Pros:** Single executable, fast startup, smaller size
**Cons:** Spring Boot limitations, reflection configuration, build complexity
**Verdict:** 🤔 Interesting but significant refactoring required

## Testing

To test the bundled JRE locally:

1. Build the app: `npm run build:all && npm run dist:win`
2. Install from `dist-electron/Trading Backtester Setup.exe`
3. Verify app starts without Java installed on system
4. Check backend logs in Electron DevTools

## Troubleshooting

### Error: "Java runtime not found"
- Verify `jre/bin/java.exe` exists in project
- Check `package.json` extraResources configuration
- Ensure JRE was downloaded correctly

### Error: "spawn java ENOENT" (still occurs)
- This should only happen in dev mode now
- Install Java 21+ for development
- Or modify `findJavaExecutable()` to always use bundled JRE

### Large installer size
- This is expected (~120-150 MB)
- JRE is ~50% of the total size
- Consider compression options in electron-builder if needed

## Future Improvements

1. **Multi-platform JRE bundling**: Automate for macOS and Linux
2. **JRE version updates**: Script to update bundled JRE version
3. **Size optimization**: Remove unnecessary JRE modules (jlink)
4. **Delta updates**: Electron auto-updater for smaller update sizes

## References

- [Adoptium OpenJDK](https://adoptium.net/temurin/releases/)
- [Electron Builder Documentation](https://www.electron.build/)
- [JRE vs JDK](https://www.baeldung.com/jvm-vs-jre-vs-jdk)
- See `JRE_SETUP.md` for detailed setup instructions
