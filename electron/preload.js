/**
 * Preload script for security
 * This file runs in the renderer process before other scripts
 */

const { contextBridge } = require('electron')

// Expose protected methods that allow the renderer process to use
// specific Electron APIs without exposing the entire Electron module
contextBridge.exposeInMainWorld('electronAPI', {
  platform: process.platform,
  versions: {
    node: process.versions.node,
    chrome: process.versions.chrome,
    electron: process.versions.electron
  }
})

console.log('Preload script loaded')
