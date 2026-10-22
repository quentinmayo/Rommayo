'use strict';
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('rommayo', {
  send: (message) => ipcRenderer.invoke('rommayo:command', message),
  onState: (callback) => ipcRenderer.on('rommayo:state', (_event, state) => callback(state))
});
