# JRE Setup for Electron Distribution

## Overview

To distribute the Trading Desktop App, you need to bundle a Java Runtime Environment (JRE) so users don't need to install Java separately.

## Setup Instructions

### 1. Download JRE

Download OpenJDK 21 for your target platform:

**For Windows (64-bit):**
- Visit: https://adoptium.net/temurin/releases/
- Select:
  - **Version**: 21 (LTS)
  - **Operating System**: Windows
  - **Architecture**: x64
  - **Package Type**: JRE
  - **Archive**: .zip (NOT .msi installer)
- Download the ZIP file (e.g., `OpenJDK21U-jre_x64_windows_hotspot_21.0.X_XX.zip`)

**For macOS:**
- Select:
  - **Operating System**: macOS
  - **Architecture**: x64 or aarch64 (for M1/M2 Macs)
  - **Package Type**: JRE
  - **Archive**: .tar.gz

**For Linux:**
- Select:
  - **Operating System**: Linux
  - **Architecture**: x64
  - **Package Type**: JRE
  - **Archive**: .tar.gz

### 2. Extract JRE

Extract the downloaded archive:

**Windows:**
```powershell
# Extract the ZIP file
# You should get a folder like: jdk-21.0.x-jre
```

**macOS/Linux:**
```bash
tar -xzf OpenJDK21U-jre_*.tar.gz
```

### 3. Place JRE in Project

1. In your project root, create a `jre/` directory
2. Copy the **contents** of the extracted JRE folder into `jre/`

**Important:** Copy the contents (bin, lib, etc.), not the parent folder itself.

Your structure should look like:
```
trading/
├── jre/
│   ├── bin/
│   │   ├── java.exe (Windows)
│   │   └── java (macOS/Linux)
│   ├── lib/
│   └── ...
├── electron/
├── udgaard/
└── ...
```

### 4. Verify Setup

Check that the java executable exists:

**Windows:**
```powershell
dir jre\bin\java.exe
```

**macOS/Linux:**
```bash
ls -la jre/bin/java
```

### 5. Build Distributable

Now you can build the distributable with the bundled JRE:

```bash
# Build everything
npm run build:all

# Create distributable for your platform
npm run dist          # Current platform
npm run dist:win      # Windows
npm run dist:mac      # macOS
npm run dist:linux    # Linux
```

## Multi-Platform Builds

To build for multiple platforms, you need to download and extract JRE for each platform:

**Option 1: Manual (Recommended)**
- Build on each platform with its respective JRE
- Windows builds on Windows with Windows JRE
- macOS builds on macOS with macOS JRE
- Linux builds on Linux with Linux JRE

**Option 2: Cross-platform (Advanced)**
- Use electron-builder's multi-platform build
- Requires all JREs to be available
- More complex setup

## CI/CD Integration

For GitHub Actions release workflow, the JRE needs to be downloaded during the build process:

See `.github/workflows/release.yml` for automated JRE download and setup.

## Size Considerations

- **Windows JRE**: ~40-50 MB compressed, ~180-200 MB extracted
- **macOS JRE**: ~40-50 MB compressed, ~160-180 MB extracted
- **Linux JRE**: ~40-50 MB compressed, ~170-190 MB extracted

The final installer will be significantly larger due to the bundled JRE.

## Troubleshooting

### "Java runtime not found" error
- Verify `jre/bin/java.exe` (or `java` on macOS/Linux) exists
- Check that you copied the JRE contents, not the parent folder

### "Permission denied" (macOS/Linux)
- Make the java executable runnable:
  ```bash
  chmod +x jre/bin/java
  ```

### Wrong architecture
- Ensure you downloaded the correct architecture (x64, arm64, etc.)
- Windows: x64 for most PCs
- macOS: aarch64 for M1/M2, x64 for Intel
- Linux: Usually x64

## Alternative: System Java (Not Recommended)

If you prefer users to install Java themselves:
1. Remove the `jre/` directory
2. Update `electron/main.js` `findJavaExecutable()` to always return `'java'`
3. Include Java installation instructions in your README

This is **not recommended** for end-user distribution as it adds friction to the installation process.
