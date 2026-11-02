// CONCORD para PC — janela que carrega o site + recursos que só um app nativo tem:
// atalhos globais, push-to-talk dentro do jogo, seletor de tela com som do PC, bandeja e inicialização com o Windows.
const { app, BrowserWindow, session, desktopCapturer, ipcMain, Tray, Menu, nativeImage, shell, nativeTheme, globalShortcut, screen } = require('electron');
let autoUpdater = null; try { autoUpdater = require('electron-updater').autoUpdater; } catch (e) { console.warn('updater indisponível', e.message); }
const path = require('path');
const fs = require('fs');

const APP_URL = process.env.CONCORD_URL || 'https://gabrielvazquez.github.io/concord/';
const ORIGIN = new URL(APP_URL).origin;
const SETTINGS_FILE = () => path.join(app.getPath('userData'), 'desktop-settings.json');

// Desempenho: nunca "adormecer" a janela, mesmo minimizada ou atrás do jogo
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.commandLine.appendSwitch('disable-background-timer-throttling');
app.commandLine.appendSwitch('disable-backgrounding-occluded-windows');
app.commandLine.appendSwitch('force_high_performance_gpu');
// Economia de memória: um processo de página só (o app carrega apenas o CONCORD), V8 compacto e sem recursos que não usamos
app.commandLine.appendSwitch('disable-site-isolation-trials');
app.commandLine.appendSwitch('renderer-process-limit', '2');
app.commandLine.appendSwitch('js-flags', '--optimize-for-size --max-old-space-size=384 --expose-gc');
app.commandLine.appendSwitch('disable-features', 'SpareRendererForSitePerProcess,CalculateNativeWinOcclusion,MediaRouter,DialMediaRouteProvider,GlobalMediaControls,HardwareMediaKeyHandling,Translate,AutofillServerCommunication,BackForwardCache,OptimizationHints,OptimizationGuideModelDownloading,InterestFeedContentSuggestions,WebOTP,PrivacySandboxSettings4');
app.disableDomainBlockingFor3DAPIs?.();

if (!app.requestSingleInstanceLock()) { app.quit(); process.exit(0); }
app.setAppUserModelId('com.gabrielvazquez.concord');
nativeTheme.themeSource = 'dark';

let win = null, tray = null, quitting = false, pickerWin = null;
let settings = { closeToTray: true, trayTipShown: false };
try { settings = { ...settings, ...JSON.parse(fs.readFileSync(SETTINGS_FILE(), 'utf8')) }; } catch {}
const saveSettings = () => { try { fs.writeFileSync(SETTINGS_FILE(), JSON.stringify(settings)); } catch {} };

/* ---------------- Teclado/mouse global (push-to-talk) ---------------- */
let hook = null, keyNames = {};
try {
  const u = require('uiohook-napi');
  hook = u.uIOhook;
  keyNames = Object.fromEntries(Object.entries(u.UiohookKey).map(([k, v]) => [v, k]));
} catch (e) { console.warn('uiohook indisponível:', e.message); }
let ptt = null;            // { type:'key'|'mouse', code:number, label:string }
let pttDown = false, capture = null, hookStarted = false;
const MOUSE_NAMES = { 3: 'Botão do meio', 4: 'Mouse 4 (lateral)', 5: 'Mouse 5 (lateral)' };
function send(ev, data) { if (win && !win.isDestroyed()) win.webContents.send('concord:event', { ev, data }); }
function startHook() {
  if (!hook || hookStarted) return;
  hook.on('keydown', e => {
    if (capture) { const c = capture; capture = null; c({ type: 'key', code: e.keycode, label: keyNames[e.keycode] || ('Tecla ' + e.keycode) }); return; }
    if (ptt && ptt.type === 'key' && e.keycode === ptt.code && !pttDown) { pttDown = true; send('ptt-down'); }
  });
  hook.on('keyup', e => { if (ptt && ptt.type === 'key' && e.keycode === ptt.code && pttDown) { pttDown = false; send('ptt-up'); } });
  hook.on('mousedown', e => {
    if (capture && e.button >= 3) { const c = capture; capture = null; c({ type: 'mouse', code: e.button, label: MOUSE_NAMES[e.button] || ('Mouse ' + e.button) }); return; }
    if (ptt && ptt.type === 'mouse' && e.button === ptt.code && !pttDown) { pttDown = true; send('ptt-down'); }
  });
  hook.on('mouseup', e => { if (ptt && ptt.type === 'mouse' && e.button === ptt.code && pttDown) { pttDown = false; send('ptt-up'); } });
  try { hook.start(); hookStarted = true; } catch (e) { console.warn('hook start', e); }
}

/* ---------------- Atalhos globais (mutar / ensurdecer) ---------------- */
let shortcuts = { mute: 'Ctrl+Shift+M', deafen: 'Ctrl+Shift+D' };
function registerShortcuts() {
  globalShortcut.unregisterAll();
  const result = {};
  for (const [action, accel] of Object.entries(shortcuts)) {
    if (!accel) continue;
    try { result[action] = globalShortcut.register(accel, () => send(action)); } catch { result[action] = false; }
  }
  return result;
}

/* ---------------- Janela principal ---------------- */
function createWindow() {
  const { width, height } = screen.getPrimaryDisplay().workAreaSize;
  win = new BrowserWindow({
    width: Math.min(1480, width), height: Math.min(940, height), minWidth: 940, minHeight: 600,
    backgroundColor: '#000000', title: 'CONCORD', show: false, autoHideMenuBar: true, frame: false, thickFrame: true, roundedCorners: true,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true, backgroundThrottling: false, spellcheck: false, enableWebSQL: false, v8CacheOptions: 'bypassHeatCheck' }
  });
  win.removeMenu();
  win.once('ready-to-show', () => win.show());
  win.loadURL(APP_URL);
  win.webContents.on('did-fail-load', (_e, code, _desc, url, isMain) => {
    if (isMain && code !== -3) win.loadFile(path.join(__dirname, 'offline.html'), { query: { url: APP_URL } });
  });
  // Links externos abrem no navegador
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith(ORIGIN) && !url.startsWith('file:')) { e.preventDefault(); shell.openExternal(url); } });
  win.on('close', e => {
    if (!quitting && settings.closeToTray) {
      e.preventDefault(); win.hide();
      if (!settings.trayTipShown && tray) { settings.trayTipShown = true; saveSettings(); tray.displayBalloon?.({ title: 'CONCORD continua aberto', content: 'Ele fica aqui perto do relógio. Clique com o botão direito para sair.', iconType: 'info' }); }
    }
  });
  win.on('focus', () => win.flashFrame(false));
  const sendState = () => send('window-state', { maximized: win.isMaximized(), fullscreen: win.isFullScreen() });
  ['maximize','unmaximize','enter-full-screen','leave-full-screen'].forEach(ev => win.on(ev, sendState));
  win.webContents.on('did-finish-load', () => { sendState(); if (pendingUpdate) send('update', pendingUpdate); });
  // Ao esconder na bandeja, libera caches da página (imagens decodificadas, código não usado)
  win.on('hide', () => { setTimeout(() => { if (win && !win.isVisible()) { send('trim-memory'); } }, 15000); });
  win.on('minimize', () => { setTimeout(() => { if (win && win.isMinimized()) send('trim-memory'); }, 15000); });
}

/* ---------------- Bandeja ---------------- */
function buildTray() {
  const img = nativeImage.createFromPath(path.join(__dirname, 'build', 'tray@2x.png')).resize({ width: 16, height: 16 });
  tray = new Tray(img);
  tray.setToolTip('CONCORD');
  const refresh = () => tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Abrir CONCORD', click: () => { win.show(); win.focus(); } },
    { type: 'separator' },
    { label: 'Mutar / desmutar microfone', click: () => send('mute') },
    { label: 'Ensurdecer / ouvir', click: () => send('deafen') },
    { type: 'separator' },
    { label: 'Iniciar com o Windows', type: 'checkbox', checked: app.getLoginItemSettings().openAtLogin, click: (i) => { app.setLoginItemSettings({ openAtLogin: i.checked, args: ['--hidden'] }); refresh(); } },
    { label: 'Fechar vai para a bandeja', type: 'checkbox', checked: settings.closeToTray, click: (i) => { settings.closeToTray = i.checked; saveSettings(); refresh(); } },
    { type: 'separator' },
    { label: 'Sair do CONCORD', click: () => { quitting = true; app.quit(); } },
  ]));
  refresh();
  tray.on('click', () => { if (win.isVisible() && win.isFocused()) win.hide(); else { win.show(); win.focus(); } });
}

/* ---------------- Seletor de tela próprio (com som do PC) ---------------- */
function openPicker() {
  return new Promise(resolve => {
    if (pickerWin) { pickerWin.focus(); return resolve(null); }
    let done = false;
    pickerWin = new BrowserWindow({
      parent: win, modal: true, width: 920, height: 640, minWidth: 700, minHeight: 480, resizable: true,
      backgroundColor: '#0a0a0b', title: 'Escolher o que transmitir', autoHideMenuBar: true, show: false,
      webPreferences: { preload: path.join(__dirname, 'picker-preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true }
    });
    pickerWin.removeMenu();
    pickerWin.loadFile(path.join(__dirname, 'picker.html'));
    pickerWin.once('ready-to-show', () => pickerWin.show());
    const finish = (val) => { if (done) return; done = true; resolve(val); if (pickerWin && !pickerWin.isDestroyed()) pickerWin.close(); };
    ipcMain.handleOnce('picker:choose', (_e, val) => { finish(val); return true; });
    pickerWin.on('closed', () => { pickerWin = null; ipcMain.removeHandler('picker:choose'); finish(null); });
  });
}
let lastSources = new Map();
ipcMain.handle('picker:sources', async () => {
  const list = await desktopCapturer.getSources({ types: ['screen', 'window'], thumbnailSize: { width: 400, height: 225 }, fetchWindowIcons: true });
  lastSources = new Map(list.map(s => [s.id, s]));
  return list.filter(s => s.name !== 'Escolher o que transmitir').map(s => ({
    id: s.id, name: s.name, screen: s.id.startsWith('screen:'),
    thumb: s.thumbnail.isEmpty() ? null : s.thumbnail.toDataURL(),
    icon: s.appIcon && !s.appIcon.isEmpty() ? s.appIcon.toDataURL() : null
  }));
});

/* ---------------- Ponte com o site ---------------- */
ipcMain.handle('concord:info', () => ({ version: app.getVersion(), platform: process.platform, ptt: !!hook, loopback: process.platform === 'win32' }));
ipcMain.handle('concord:setShortcuts', (_e, s) => {
  shortcuts = { mute: s?.mute || '', deafen: s?.deafen || '' };
  ptt = s?.ptt && typeof s.ptt.code === 'number' ? s.ptt : null; pttDown = false;
  if (ptt) startHook();
  return registerShortcuts();
});
ipcMain.handle('concord:capturePtt', () => new Promise(resolve => {
  if (!hook) return resolve(null);
  startHook(); capture = resolve;
  setTimeout(() => { if (capture === resolve) { capture = null; resolve(null); } }, 10000);
}));
ipcMain.on('concord:attention', () => { if (win && !win.isFocused()) win.flashFrame(true); });
ipcMain.on('concord:focus', () => { if (win) { win.show(); win.focus(); } });
ipcMain.handle('concord:openExternal', (_e, url) => { if (/^https?:/.test(url)) shell.openExternal(url); });
ipcMain.on('win:minimize', () => win?.minimize());
ipcMain.handle('concord:trim', async () => { try { await win?.webContents.session.clearHostResolverCache(); } catch {} return true; });
ipcMain.on('win:maximize', () => { if (!win) return; win.isMaximized() ? win.unmaximize() : win.maximize(); });
ipcMain.on('win:close', () => win?.close());
ipcMain.handle('concord:metrics', () => {
  // Memória privada (a mesma coluna do Gerenciador de Tarefas); o "working set" soma memória compartilhada e assusta
  const list = app.getAppMetrics();
  const kb = m => (m.memory?.privateBytes || m.memory?.workingSetSize || 0);
  const by = { page: 0, gpu: 0, other: 0 };
  for (const m of list) { const k = m.type === 'Tab' || m.type === 'Renderer' ? 'page' : m.type === 'GPU' ? 'gpu' : 'other'; by[k] += kb(m); }
  const mb = v => Math.round(v / 1024);
  return { mb: mb(by.page + by.gpu + by.other), page: mb(by.page), gpu: mb(by.gpu), other: mb(by.other), processes: list.length, version: app.getVersion() };
});

/* ---------------- Atualizações do app ---------------- */
let pendingUpdate = null;
function setUpdate(state) { pendingUpdate = state; send('update', state); if (tray) tray.setToolTip(state.status === 'ready' ? 'CONCORD — atualização pronta' : 'CONCORD'); }
if (autoUpdater) {
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.on('checking-for-update', () => setUpdate({ status: 'checking' }));
  autoUpdater.on('update-available', i => setUpdate({ status: 'downloading', version: i.version, percent: 0 }));
  autoUpdater.on('update-not-available', () => setUpdate({ status: 'latest', version: app.getVersion() }));
  autoUpdater.on('download-progress', p => setUpdate({ status: 'downloading', version: pendingUpdate?.version, percent: Math.round(p.percent) }));
  autoUpdater.on('update-downloaded', i => setUpdate({ status: 'ready', version: i.version }));
  autoUpdater.on('error', e => setUpdate({ status: 'error', message: String(e?.message || e).slice(0, 200) }));
}
const portable = !!process.env.PORTABLE_EXECUTABLE_FILE;
ipcMain.handle('update:check', async () => {
  if (!autoUpdater || !app.isPackaged) return { status: 'unsupported', reason: app.isPackaged ? 'updater' : 'dev' };
  if (portable) return { status: 'unsupported', reason: 'portable' };
  try { await autoUpdater.checkForUpdates(); } catch (e) { setUpdate({ status: 'error', message: String(e?.message || e).slice(0, 200) }); }
  return pendingUpdate;
});
ipcMain.handle('update:install', () => { if (pendingUpdate?.status === 'ready') { quitting = true; autoUpdater.quitAndInstall(false, true); } });

app.whenReady().then(() => {
  const ses = session.defaultSession;
  const okOrigin = u => { try { return new URL(u).origin === ORIGIN; } catch { return false; } };
  const ALLOWED = new Set(['media', 'display-capture', 'notifications', 'fullscreen', 'clipboard-sanitized-write', 'speaker-selection', 'mediaKeySystem']);
  ses.setPermissionRequestHandler((wc, perm, cb, details) => cb(ALLOWED.has(perm) && okOrigin(details.requestingUrl || wc.getURL())));
  ses.setPermissionCheckHandler((wc, perm, origin) => ALLOWED.has(perm) && (origin ? origin.startsWith(ORIGIN) : true));
  ses.setDisplayMediaRequestHandler(async (request, callback) => {
    const choice = await openPicker();
    const src = choice && lastSources.get(choice.id);
    if (!src) return callback({});
    const cfg = { video: src };
    if (choice.audio && process.platform === 'win32') cfg.audio = 'loopback';
    callback(cfg);
  }, { useSystemPicker: false });

  createWindow();
  buildTray();
  registerShortcuts();
  if (autoUpdater && app.isPackaged && !portable) { setTimeout(() => autoUpdater.checkForUpdates().catch(() => {}), 8000); setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 6 * 3600 * 1000); }
  if (process.argv.includes('--hidden')) win.once('ready-to-show', () => win.hide());
});

app.on('second-instance', () => { if (win) { win.show(); win.focus(); } });
app.on('before-quit', () => { quitting = true; });
app.on('will-quit', () => { globalShortcut.unregisterAll(); try { hook && hookStarted && hook.stop(); } catch {} });
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
