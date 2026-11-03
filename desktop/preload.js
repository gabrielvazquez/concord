// Ponte segura entre o site e o app (só expõe estas funções)
const { contextBridge, ipcRenderer, webFrame } = require('electron');

contextBridge.exposeInMainWorld('concordDesktop', {
  info: () => ipcRenderer.invoke('concord:info'),
  setShortcuts: (s) => ipcRenderer.invoke('concord:setShortcuts', s),
  capturePtt: () => ipcRenderer.invoke('concord:capturePtt'),
  attention: () => ipcRenderer.send('concord:attention'),
  focus: () => ipcRenderer.send('concord:focus'),
  openExternal: (url) => ipcRenderer.invoke('concord:openExternal', url),
  win: { minimize: () => ipcRenderer.send('win:minimize'), maximize: () => ipcRenderer.send('win:maximize'), close: () => ipcRenderer.send('win:close') },
  metrics: () => ipcRenderer.invoke('concord:metrics'),
  activity: (on) => ipcRenderer.invoke('concord:activity', !!on),
  idle: () => ipcRenderer.invoke('concord:idle'),
  trim: () => { try { webFrame.clearCache(); } catch {} return ipcRenderer.invoke('concord:trim'); },
  reshare: () => ipcRenderer.invoke('concord:reshare'),
  checkUpdate: () => ipcRenderer.invoke('update:check'),
  installUpdate: () => ipcRenderer.invoke('update:install'),
  onEvent: (cb) => { const h = (_e, msg) => cb(msg.ev, msg.data); ipcRenderer.on('concord:event', h); return () => ipcRenderer.removeListener('concord:event', h); },
});
