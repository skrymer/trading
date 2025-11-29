# Trading Desktop App - Electron Wrapper

This directory contains the Electron wrapper that packages the Trading Backtesting application as a desktop app.

## Architecture

The desktop app consists of three parts:
1. **Electron Main Process** (`main.js`) - Manages the application lifecycle and native OS integration
2. **Spring Boot Backend** (`udgaard`) - Runs as a subprocess, provides REST API
3. **Nuxt Frontend** (`asgaard_nuxt`) - Rendered in Electron's browser window

## Prerequisites

- Node.js 18+ and npm
- Java 21+ (for running Spring Boot)
- All backend and frontend dependencies already installed

## Development

### First Time Setup

```bash
# From the root /trading directory
npm install
```

### Running in Development Mode

**Option 1: Full Dev Mode (recommended for frontend development)**
```bash
# Terminal 1: Start Nuxt dev server
cd asgaard_nuxt
npm run dev

# Terminal 2: Build backend JAR
cd udgaard
./gradlew bootJar

# Terminal 3: Start Electron in dev mode
cd ..
npm run dev
```

**Option 2: Quick Start (using built assets)**
```bash
# Build backend once
cd udgaard
./gradlew bootJar

# Build frontend once
cd ../asgaard_nuxt
npm run build

# Start Electron
cd ..
npm start
```

### Development Notes

- In dev mode (`npm run dev`), Electron loads the frontend from `http://localhost:3000` (Nuxt dev server)
- Backend always runs from the built JAR file
- Dev Tools are automatically opened in dev mode
- The app waits up to 30 seconds for the backend to start

## Building for Production

### Build Everything

```bash
# From /trading directory
npm run build:all    # Builds both backend and frontend
npm run dist         # Creates distributable for current platform
```

### Platform-Specific Builds

```bash
npm run dist:win     # Windows installer (.exe)
npm run dist:mac     # macOS DMG
npm run dist:linux   # Linux AppImage and .deb
```

Output files will be in `dist-electron/` directory.

### Build Artifacts

- **Windows**: `trading-desktop-setup-1.0.0.exe` (NSIS installer)
- **macOS**: `trading-desktop-1.0.0.dmg` (DMG disk image)
- **Linux**: `trading-desktop-1.0.0.AppImage` and `.deb` package

## Project Structure

```
trading/
├── electron/
│   ├── main.js           # Electron main process
│   ├── preload.js        # Security bridge
│   ├── icon.png          # App icon (to be added)
│   └── README.md         # This file
├── udgaard/              # Spring Boot backend
│   └── build/libs/       # Built JAR location
├── asgaard_nuxt/         # Nuxt frontend
│   └── .output/          # Built frontend
├── package.json          # Electron app config
└── dist-electron/        # Built desktop apps
```

## Configuration

### Backend Port

The backend runs on port `8080` by default. To change:
- Edit `BACKEND_PORT` in `electron/main.js`
- Update your Nuxt config to proxy to the new port

### App Icon

Add an app icon:
1. Create a 512x512 PNG: `electron/icon.png`
2. For macOS, also create `electron/icon.icns`
3. For Windows, also create `electron/icon.ico`

### Application ID

Change the app identifier in `package.json`:
```json
"build": {
  "appId": "com.yourcompany.trading"
}
```

## Troubleshooting

### Backend fails to start

**Error**: "Backend JAR not found"
- **Solution**: Build the backend first: `cd udgaard && ./gradlew bootJar`

**Error**: "Backend failed to start within timeout"
- **Solution**: Check if port 8080 is already in use
- **Solution**: Increase `BACKEND_STARTUP_TIMEOUT` in `main.js`

### Frontend not loading

**Dev mode**: Ensure Nuxt dev server is running on port 3000
**Production**: Ensure frontend was built: `cd asgaard_nuxt && npm run build`

### Port conflicts

If port 8080 is in use:
1. Change `BACKEND_PORT` in `electron/main.js`
2. Update `nuxt.config.ts` to proxy to the new port

### Memory issues

The app may use significant memory (JVM + Chromium). To reduce:
- Add JVM memory limits: `-Xmx512m` in the `spawn` args in `main.js`
- Close unused tabs/windows in the app

## Production Deployment

### Automated GitHub Releases (Recommended)

The project includes automated CI/CD via GitHub Actions:

```bash
# 1. Update version in package.json
# 2. Commit and tag
git tag v1.0.0
git push origin v1.0.0

# 3. GitHub Actions automatically:
#    - Builds for Windows, macOS, Linux
#    - Creates GitHub Release
#    - Uploads installers
```

**See detailed guides:**
- **Quick Start**: `../RELEASE_QUICKSTART.md`
- **Full Documentation**: `../DEPLOYMENT.md`

### Manual Build

If you prefer manual deployment:

```bash
npm run build:all
npm run dist
```

Installers will be in `dist-electron/`

## Production Checklist

Before distributing:
- [ ] Update version in `package.json`
- [ ] Add app icons (PNG, ICNS, ICO)
- [ ] Test on target platforms
- [ ] Configure code signing (for macOS/Windows)
- [ ] Set up auto-updates (optional)
- [ ] Push version tag to trigger CI/CD

## Security Notes

- `nodeIntegration` is disabled for security
- `contextIsolation` is enabled
- Only specific APIs are exposed via `preload.js`
- Backend runs as a subprocess, not in the renderer

## License

Same as parent project
