const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('picker', {
  sources: () => ipcRenderer.invoke('picker:sources'),
  choose: (val) => ipcRenderer.invoke('picker:choose', val),
});
