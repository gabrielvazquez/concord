// Ponte segura entre o site e o app (só expõe estas funções)
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('concordDesktop', {
  info: () => ipcRenderer.invoke('concord:info'),
  setShortcuts: (s) => ipcRenderer.invoke('concord:setShortcuts', s),
  capturePtt: () => ipcRenderer.invoke('concord:capturePtt'),
  attention: () => ipcRenderer.send('concord:attention'),
  focus: () => ipcRenderer.send('concord:focus'),
  openExternal: (url) => ipcRenderer.invoke('concord:openExternal', url),
  onEvent: (cb) => { const h = (_e, msg) => cb(msg.ev, msg.data); ipcRenderer.on('concord:event', h); return () => ipcRenderer.removeListener('concord:event', h); },
});
