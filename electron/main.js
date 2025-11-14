const { app, BrowserWindow, dialog } = require('electron')
const { spawn } = require('child_process')
const path = require('path')
const kill = require('tree-kill')

let backendProcess = null
let mainWindow = null
const isDev = process.argv.includes('--dev')

// Configuration
const BACKEND_PORT = 8080
const BACKEND_STARTUP_TIMEOUT = 30000 // 30 seconds
const BACKEND_CHECK_INTERVAL = 500 // Check every 500ms

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

  while (Date.now() - startTime < BACKEND_STARTUP_TIMEOUT) {
    const isReady = await checkBackendReady()
    if (isReady) {
      console.log('Backend is ready!')
      return true
    }
    await new Promise(resolve => setTimeout(resolve, BACKEND_CHECK_INTERVAL))
  }

  return false
}

/**
 * Start the Spring Boot backend
 */
function startBackend() {
  return new Promise((resolve, reject) => {
    const jarPath = findBackendJar()
    console.log('Starting backend:', jarPath)

    // Check if JAR exists
    const fs = require('fs')
    if (!fs.existsSync(jarPath)) {
      reject(new Error(`Backend JAR not found: ${jarPath}\n\nPlease build the backend first:\ncd udgaard && ./gradlew bootJar`))
      return
    }

    // Start the Spring Boot application
    backendProcess = spawn('java', [
      '-jar',
      jarPath,
      `--server.port=${BACKEND_PORT}`
    ], {
      stdio: ['ignore', 'pipe', 'pipe']
    })

    backendProcess.stdout.on('data', (data) => {
      console.log(`[Backend] ${data.toString().trim()}`)
    })

    backendProcess.stderr.on('data', (data) => {
      console.error(`[Backend Error] ${data.toString().trim()}`)
    })

    backendProcess.on('error', (error) => {
      console.error('Failed to start backend:', error)
      reject(error)
    })

    backendProcess.on('exit', (code, signal) => {
      console.log(`Backend exited with code ${code} and signal ${signal}`)
      backendProcess = null
    })

    // Wait for backend to be ready
    console.log('Waiting for backend to start...')
    waitForBackend()
      .then(ready => {
        if (ready) {
          resolve()
        } else {
          reject(new Error('Backend failed to start within timeout period'))
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
  try {
    console.log('Starting Trading Desktop App...')
    console.log('Mode:', isDev ? 'Development' : 'Production')

    // Start backend first
    await startBackend()

    // Then create window
    createWindow()

  } catch (error) {
    console.error('Startup error:', error)
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
