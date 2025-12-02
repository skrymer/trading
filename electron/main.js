const { app, BrowserWindow, dialog } = require('electron')
const { spawn } = require('child_process')
const path = require('path')
const kill = require('tree-kill')

let backendProcess = null
let mainWindow = null
let backendLogs = []
const isDev = process.argv.includes('--dev')

// Configuration
const BACKEND_PORT = 8080
const BACKEND_STARTUP_TIMEOUT = 120000 // 120 seconds (2 minutes) - Spring Boot can be slow to start
const BACKEND_CHECK_INTERVAL = 1000 // Check every 1 second

/**
 * Find the Spring Boot JAR file
 */
function findBackendJar() {
  if (isDev) {
    // In dev mode, use the JAR from build directory
    return path.join(__dirname, '../udgaard/build/libs/udgaard-0.0.1-SNAPSHOT.jar')
  } else {
    // In production, JAR is in resources/backend
    return path.join(process.resourcesPath, 'backend/udgaard-0.0.1-SNAPSHOT.jar')
  }
}

/**
 * Find the Java executable
 */
function findJavaExecutable() {
  if (isDev) {
    // In dev mode, use system Java
    return 'java'
  } else {
    // In production, use bundled JRE
    const javaExecutable = process.platform === 'win32' ? 'java.exe' : 'java'
    return path.join(process.resourcesPath, 'jre/bin', javaExecutable)
  }
}

/**
 * Check if backend is ready by attempting to connect
 */
async function checkBackendReady() {
  return new Promise((resolve) => {
    const http = require('http')
    const req = http.get(`http://localhost:${BACKEND_PORT}/actuator/health`, (res) => {
      resolve(res.statusCode === 200)
    })
    req.on('error', () => resolve(false))
    req.setTimeout(1000, () => {
      req.destroy()
      resolve(false)
    })
  })
}

/**
 * Wait for backend to be ready
 */
async function waitForBackend() {
  const startTime = Date.now()
  let attempts = 0

  while (Date.now() - startTime < BACKEND_STARTUP_TIMEOUT) {
    attempts++
    const elapsed = Math.floor((Date.now() - startTime) / 1000)

    if (attempts % 5 === 0) {
      console.log(`Waiting for backend... (${elapsed}s elapsed, attempt ${attempts})`)
    }

    const isReady = await checkBackendReady()
    if (isReady) {
      console.log(`Backend is ready! (took ${elapsed} seconds)`)
      return true
    }
    await new Promise(resolve => setTimeout(resolve, BACKEND_CHECK_INTERVAL))
  }

  console.error('Backend startup timeout reached')
  return false
}

/**
 * Start the Spring Boot backend
 */
function startBackend() {
  return new Promise((resolve, reject) => {
    const jarPath = findBackendJar()
    const javaPath = findJavaExecutable()
    console.log('Starting backend:', jarPath)
    console.log('Using Java:', javaPath)

    // Check if JAR exists
    const fs = require('fs')
    if (!fs.existsSync(jarPath)) {
      reject(new Error(`Backend JAR not found: ${jarPath}\n\nPlease build the backend first:\ncd udgaard && ./gradlew bootJar`))
      return
    }

    // Check if Java exists (in production mode)
    if (!isDev && !fs.existsSync(javaPath)) {
      reject(new Error(`Java runtime not found: ${javaPath}\n\nPlease ensure JRE is bundled in the application.`))
      return
    }

    // Reset backend logs
    backendLogs = []

    // Start the Spring Boot application
    backendProcess = spawn(javaPath, [
      '-jar',
      jarPath,
      `--server.port=${BACKEND_PORT}`
    ], {
      stdio: ['ignore', 'pipe', 'pipe']
    })

    backendProcess.stdout.on('data', (data) => {
      const logLine = data.toString().trim()
      console.log(`[Backend] ${logLine}`)
      backendLogs.push(logLine)
      // Keep only last 100 lines
      if (backendLogs.length > 100) {
        backendLogs.shift()
      }
    })

    backendProcess.stderr.on('data', (data) => {
      const logLine = data.toString().trim()
      console.error(`[Backend Error] ${logLine}`)
      backendLogs.push(`ERROR: ${logLine}`)
      // Keep only last 100 lines
      if (backendLogs.length > 100) {
        backendLogs.shift()
      }
    })

    backendProcess.on('error', (error) => {
      console.error('Failed to start backend:', error)
      reject(error)
    })

    backendProcess.on('exit', (code, signal) => {
      console.log(`Backend exited with code ${code} and signal ${signal}`)
      if (code !== 0 && code !== null) {
        console.error('Backend logs:', backendLogs.join('\n'))
      }
      backendProcess = null
    })

    // Wait for backend to be ready
    console.log('Waiting for backend to start...')
    console.log('This may take up to 2 minutes for first startup...')
    waitForBackend()
      .then(ready => {
        if (ready) {
          resolve()
        } else {
          const errorMsg = `Backend failed to start within timeout period.\n\nRecent logs:\n${backendLogs.slice(-20).join('\n')}`
          reject(new Error(errorMsg))
        }
      })
      .catch(reject)
  })
}

/**
 * Stop the backend process
 */
function stopBackend() {
  return new Promise((resolve) => {
    if (backendProcess && backendProcess.pid) {
      console.log('Stopping backend...')
      kill(backendProcess.pid, 'SIGTERM', (err) => {
        if (err) {
          console.error('Error stopping backend:', err)
        }
        backendProcess = null
        resolve()
      })
    } else {
      resolve()
    }
  })
}

/**
 * Create a loading window
 */
function createLoadingWindow() {
  const loadingWindow = new BrowserWindow({
    width: 400,
    height: 200,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    webPreferences: {
      nodeIntegration: false
    }
  })

  loadingWindow.loadURL(`data:text/html;charset=utf-8,
    <html>
      <head>
        <style>
          body {
            margin: 0;
            padding: 0;
            background: rgba(0, 0, 0, 0.8);
            color: white;
            font-family: Arial, sans-serif;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
            height: 100vh;
          }
          h2 { margin: 10px; }
          p { margin: 5px; color: #aaa; }
          .spinner {
            border: 4px solid rgba(255, 255, 255, 0.3);
            border-top: 4px solid white;
            border-radius: 50%;
            width: 40px;
            height: 40px;
            animation: spin 1s linear infinite;
            margin: 20px;
          }
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        </style>
      </head>
      <body>
        <div class="spinner"></div>
        <h2>Trading Backtester</h2>
        <p>Starting backend server...</p>
        <p style="font-size: 12px;">This may take up to 2 minutes</p>
      </body>
    </html>
  `)

  return loadingWindow
}

/**
 * Create the main application window
 */
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1200,
    minHeight: 700,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      enableRemoteModule: false,
      preload: path.join(__dirname, 'preload.js')
    },
    show: false // Don't show until ready
  })

  // Show window when ready to avoid flickering
  mainWindow.once('ready-to-show', () => {
    mainWindow.show()
  })

  if (isDev) {
    // In development, load from Nuxt dev server
    mainWindow.loadURL('http://localhost:3000')
    mainWindow.webContents.openDevTools()
  } else {
    // In production, load from built Nuxt app
    const frontendPath = path.join(__dirname, '../asgaard_nuxt/.output/public/index.html')
    mainWindow.loadFile(frontendPath)
  }

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

/**
 * Show error dialog
 */
function showError(title, message) {
  dialog.showErrorBox(title, message)
}

/**
 * Application ready event
 */
app.whenReady().then(async () => {
  let loadingWindow = null

  try {
    console.log('Starting Trading Desktop App...')
    console.log('Mode:', isDev ? 'Development' : 'Production')

    // Show loading window
    loadingWindow = createLoadingWindow()

    // Start backend first
    await startBackend()

    // Close loading window
    if (loadingWindow) {
      loadingWindow.close()
      loadingWindow = null
    }

    // Then create main window
    createWindow()

  } catch (error) {
    console.error('Startup error:', error)

    // Close loading window if still open
    if (loadingWindow) {
      loadingWindow.close()
    }

    showError('Startup Error', error.message)
    app.quit()
  }
})

/**
 * Handle window activation (macOS)
 */
app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow()
  }
})

/**
 * Handle all windows closed
 */
app.on('window-all-closed', () => {
  // On macOS, apps typically stay open until Cmd+Q
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

/**
 * Clean up before quit
 */
app.on('before-quit', async (event) => {
  if (backendProcess) {
    event.preventDefault()
    await stopBackend()
    app.quit()
  }
})

/**
 * Handle uncaught errors
 */
process.on('uncaughtException', (error) => {
  console.error('Uncaught exception:', error)
  showError('Application Error', error.message)
})
