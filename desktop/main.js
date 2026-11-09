// CONCORD para PC — janela que carrega o site + recursos que só um app nativo tem:
// atalhos globais, push-to-talk dentro do jogo, seletor de tela com som do PC, bandeja e inicialização com o Windows.
const { app, BrowserWindow, session, desktopCapturer, ipcMain, Tray, Menu, nativeImage, shell, nativeTheme, globalShortcut, screen, powerMonitor } = require('electron');
const { execFile } = require('child_process');
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
// última fonte escolhida: se a captura cair sozinha, o site pede para retomar a mesma, sem abrir o seletor
let lastChoice = null, reuseNext = null;
ipcMain.handle('concord:reshare', async () => {
  if (!lastChoice) return false;
  const list = await desktopCapturer.getSources({ types: ['screen', 'window'], thumbnailSize: { width: 0, height: 0 } });
  const s = list.find(x => x.id === lastChoice.id) || (!lastChoice.screen && list.find(x => x.name === lastChoice.name)) || null;
  if (!s) return false;
  lastSources.set(s.id, s);
  reuseNext = { id: s.id, audio: lastChoice.audio };
  return true;
});
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

/* ---------------- Atividade: jogo aberto e música no Spotify ---------------- */
// Lê só o nome dos programas com janela aberta (nada é enviado além do nome do jogo / música).
const GAMES = {
  'valorant-win64-shipping': 'VALORANT', 'cs2': 'Counter-Strike 2', 'csgo': 'Counter-Strike', 'league of legends': 'League of Legends',
  'fortniteclient-win64-shipping': 'Fortnite', 'rocketleague': 'Rocket League', 'gta5': 'GTA V', 'gta5_enhanced': 'GTA V', 'gtavlauncher': 'GTA V',
  'rdr2': 'Red Dead Redemption 2', 'eldenring': 'Elden Ring', 'nightreign': 'Elden Ring Nightreign', 'overwatch': 'Overwatch 2', 'r5apex': 'Apex Legends', 'r5apex_dx12': 'Apex Legends',
  'fc24': 'EA SPORTS FC 24', 'fc25': 'EA SPORTS FC 25', 'fc26': 'EA SPORTS FC 26', 'pes2021': 'eFootball PES 2021', 'efootball': 'eFootball',
  'robloxplayerbeta': 'Roblox', 'dota2': 'Dota 2', 'tslgame': 'PUBG', 'cod': 'Call of Duty', 'blackops6': 'Call of Duty: Black Ops 6', 'minecraft.windows': 'Minecraft',
  'rainbowsix': 'Rainbow Six Siege', 'rainbowsix_vulkan': 'Rainbow Six Siege', 'destiny2': 'Destiny 2', 'witcher3': 'The Witcher 3', 'cyberpunk2077': 'Cyberpunk 2077',
  'hollow_knight': 'Hollow Knight', 'terraria': 'Terraria', 'stardew valley': 'Stardew Valley', 'among us': 'Among Us', 'fallguys_client_game': 'Fall Guys',
  'deadbydaylight-win64-shipping': 'Dead by Daylight', 'marvel-win64-shipping': 'Marvel Rivals', 'helldivers2': 'Helldivers 2', 'palworld-win64-shipping': 'Palworld',
  'bg3': "Baldur's Gate 3", 'bg3_dx11': "Baldur's Gate 3", 'forzahorizon5': 'Forza Horizon 5', 'forzahorizon4': 'Forza Horizon 4', 'eurotrucks2': 'Euro Truck Simulator 2',
  'sons of the forest': 'Sons of the Forest', 'rust': 'Rust', 'phasmophobia': 'Phasmophobia', 'lethal company': 'Lethal Company', 'repo': 'R.E.P.O.', 'peak': 'PEAK',
  'ryujinx': 'Ryujinx (Switch)', 'yuzu': 'Yuzu (Switch)', 'pcsx2-qt': 'PCSX2', 'rpcs3': 'RPCS3', 'dolphin': 'Dolphin', 'retroarch': 'RetroArch', 'cemu': 'Cemu',
};
const NOT_GAMES = new Set(['steam', 'steamwebhelper', 'epicgameslauncher', 'riotclientservices', 'riotclientux', 'leagueclient', 'leagueclientux', 'eadesktop', 'eabackgroundservice', 'battle.net', 'ubisoftconnect', 'upc', 'gog galaxy', 'galaxyclient', 'unitycrashhandler64', 'crashreportclient']);
const GAME_DIRS = /\\steamapps\\common\\|\\epic games\\|\\riot games\\|\\xboxgames\\|\\gog galaxy\\games\\|\\ea games\\|\\ubisoft game launcher\\games\\/i;
let actTimer = null, actLast = '', actSince = {};
function readActivity() {
  return new Promise(res => {
    if (process.platform !== 'win32') return res(null);
    const cmd = "Get-Process | Where-Object { $_.MainWindowTitle } | Select-Object ProcessName,MainWindowTitle,Path | ConvertTo-Json -Compress";
    execFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-Command', cmd], { windowsHide: true, timeout: 9000, maxBuffer: 1 << 20 }, (err, out) => {
      if (err || !out) return res(null);
      let list; try { list = JSON.parse(out); } catch { return res(null); }
      if (!Array.isArray(list)) list = [list];
      let game = null, music = null;
      for (const p of list) {
        const n = String(p.ProcessName || '').toLowerCase(), t = String(p.MainWindowTitle || '').trim(), path = String(p.Path || '');
        if (n === 'spotify' && t && !/^spotify( premium| free)?$/i.test(t) && t.includes(' - ')) { const i = t.indexOf(' - '); music = { app: 'Spotify', artist: t.slice(0, i).trim(), title: t.slice(i + 3).trim() }; continue; }
        if (game || NOT_GAMES.has(n)) continue;
        if (GAMES[n]) game = GAMES[n];
        else if (n === 'javaw' && /^minecraft/i.test(t)) game = 'Minecraft';
        else if (GAME_DIRS.test(path)) { const m = path.match(/\\(?:common|epic games|riot games|xboxgames|games)\\([^\\]+)/i); game = (t && t.length < 48 ? t : (m ? m[1] : n)).replace(/\s{2,}/g, ' '); }
      }
      const now = Date.now();
      if (game) { actSince[game] = actSince[game] || now; } for (const k of Object.keys(actSince)) if (k !== game) delete actSince[k];
      res({ game: game ? { name: game, since: actSince[game] } : null, music });
    });
  });
}
async function pollActivity() {
  const a = await readActivity(); if (!a) return;
  const key = JSON.stringify([a.game?.name, a.music?.title, a.music?.artist]);
  if (key !== actLast) { actLast = key; send('activity', a); }
}
ipcMain.handle('concord:activity', (_e, on) => {
  clearInterval(actTimer); actTimer = null; actLast = '';
  if (on) { pollActivity(); actTimer = setInterval(pollActivity, 15000); } else send('activity', { game: null, music: null });
  return true;
});
ipcMain.handle('concord:idle', () => { try { return powerMonitor.getSystemIdleTime(); } catch { return 0; } });

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


/* ---------------- Jogar junto (estilo Parsec): teclado, mouse e controle remotos ----------------
   O site manda os comandos já liberados pelo dono da tela; aqui viram entrada de verdade no Windows
   por um ajudante (ConcordInput.exe: SendInput + controles de Xbox virtuais pelo ViGEmBus). */
const { spawn } = require('child_process');
let inp = null, inpReady = null, inpInfo = { ok: false, vigem: false };
const SC = { Escape:[1],Digit1:[2],Digit2:[3],Digit3:[4],Digit4:[5],Digit5:[6],Digit6:[7],Digit7:[8],Digit8:[9],Digit9:[10],Digit0:[11],Minus:[12],Equal:[13],Backspace:[14],Tab:[15],
  KeyQ:[16],KeyW:[17],KeyE:[18],KeyR:[19],KeyT:[20],KeyY:[21],KeyU:[22],KeyI:[23],KeyO:[24],KeyP:[25],BracketLeft:[26],BracketRight:[27],Enter:[28],ControlLeft:[29],
  KeyA:[30],KeyS:[31],KeyD:[32],KeyF:[33],KeyG:[34],KeyH:[35],KeyJ:[36],KeyK:[37],KeyL:[38],Semicolon:[39],Quote:[40],Backquote:[41],ShiftLeft:[42],Backslash:[43],
  KeyZ:[44],KeyX:[45],KeyC:[46],KeyV:[47],KeyB:[48],KeyN:[49],KeyM:[50],Comma:[51],Period:[52],Slash:[53],ShiftRight:[54],NumpadMultiply:[55],AltLeft:[56],Space:[57],CapsLock:[58],
  F1:[59],F2:[60],F3:[61],F4:[62],F5:[63],F6:[64],F7:[65],F8:[66],F9:[67],F10:[68],NumLock:[69],ScrollLock:[70],Numpad7:[71],Numpad8:[72],Numpad9:[73],NumpadSubtract:[74],
  Numpad4:[75],Numpad5:[76],Numpad6:[77],NumpadAdd:[78],Numpad1:[79],Numpad2:[80],Numpad3:[81],Numpad0:[82],NumpadDecimal:[83],IntlBackslash:[86],F11:[87],F12:[88],IntlRo:[115],
  NumpadEnter:[28,1],ControlRight:[29,1],NumpadDivide:[53,1],PrintScreen:[55,1],AltRight:[56,1],Home:[71,1],ArrowUp:[72,1],PageUp:[73,1],ArrowLeft:[75,1],ArrowRight:[77,1],
  End:[79,1],ArrowDown:[80,1],PageDown:[81,1],Insert:[82,1],Delete:[83,1],MetaLeft:[91,1],MetaRight:[92,1],ContextMenu:[93,1],NumpadComma:[126] };
function inputExe() {
  const dirs = [path.join(process.resourcesPath || '', 'input'), path.join(__dirname, 'build', 'input'), path.join(__dirname, "inputbin")];
  for (const d of dirs) { const f = path.join(d, 'ConcordInput.exe'); if (fs.existsSync(f)) return f; }
  return null;
}
function startInput() {
  if (inp) return inpReady;
  const exe = process.platform === 'win32' && inputExe();
  if (!exe) { inpInfo = { ok: false, vigem: false, reason: 'missing' }; return Promise.resolve(inpInfo); }
  inp = spawn(exe, [], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  inpReady = new Promise(res => {
    let buf = ''; const t = setTimeout(() => res(inpInfo = { ok: true, vigem: false }), 4000);
    inp.stdout.on('data', d => { buf += d; const m = buf.match(/ready vigem=(\d)/); if (m) { clearTimeout(t); res(inpInfo = { ok: true, vigem: m[1] === '1' }); } });
  });
  inp.on('exit', () => { inp = null; inpInfo = { ok: false, vigem: false }; });
  inp.on('error', () => { inp = null; });
  return inpReady;
}
const inW = line => { try { inp?.stdin.write(line + '\n'); } catch {} };
let lastSrc = null;
function targetRect() {
  if (!lastChoice) return null;
  if (lastChoice.screen) {
    const src = lastSources.get(lastChoice.id); const ds = screen.getAllDisplays();
    const d = ds.find(x => String(x.id) === String(src?.display_id)) || screen.getPrimaryDisplay();
    try { return screen.dipToScreenRect(null, d.bounds); } catch { return { x: d.bounds.x * d.scaleFactor, y: d.bounds.y * d.scaleFactor, width: d.bounds.width * d.scaleFactor, height: d.bounds.height * d.scaleFactor }; }
  }
  return null;
}
function hwnd() { const m = /^window:(\d+):/.exec(lastChoice?.id || ''); return m ? m[1] : null; }
const clampN = v => Math.max(0, Math.min(1, +v || 0));
ipcMain.handle('play:start', async () => { const r = await startInput(); const h = hwnd(); if (h) inW('fg ' + h); return { ...r, window: !!h }; });
ipcMain.handle('play:info', () => ({ ...inpInfo, available: process.platform === 'win32' && !!inputExe() }));
ipcMain.on('play:stop', () => inW('reset'));
ipcMain.on('play:in', (_e, m) => {
  if (!inp || !m) return;
  switch (m.t) {
    case 'm': { const h = hwnd(); if (h) { inW(`win ${h} ${Math.round(clampN(m.x) * 10000)} ${Math.round(clampN(m.y) * 10000)}`); break; }
      const r = targetRect(); if (r) inW(`pos ${Math.round(r.x + clampN(m.x) * (r.width - 1))} ${Math.round(r.y + clampN(m.y) * (r.height - 1))}`); break; }
    case 'r': inW(`rel ${Math.round(+m.dx || 0)} ${Math.round(+m.dy || 0)}`); break;
    case 'b': inW(`btn ${(+m.b || 0) | 0} ${m.d ? 1 : 0}`); break;
    case 'w': inW(`whl ${Math.round(-(+m.dy || 0))}`); if (m.dx) inW(`hwl ${Math.round(+m.dx)}`); break;
    case 'k': { const k = SC[m.c]; if (k) inW(`key ${k[0]} ${k[1] ? 1 : 0} ${m.d ? 1 : 0}`); break; }
    case 'p': { const a = (m.a || []).map(v => Math.max(-32767, Math.min(32767, v | 0))); inW(`pad ${(m.i | 0) & 3} ${(m.b | 0) & 0x1ffff} ${a[0] || 0} ${a[1] || 0} ${a[2] || 0} ${a[3] || 0} ${Math.max(0, Math.min(255, m.lt | 0))} ${Math.max(0, Math.min(255, m.rt | 0))}`); break; }
    case 'poff': inW(`padoff ${(m.i | 0) & 3}`); break;
    case 'reset': inW('reset'); break;
  }
});
ipcMain.handle('play:driver', () => { shell.openExternal('https://github.com/nefarius/ViGEmBus/releases/latest'); return true; });
app.on('will-quit', () => { try { inp?.stdin.end(); inp?.kill(); } catch {} });

app.whenReady().then(() => {
  const ses = session.defaultSession;
  const okOrigin = u => { try { return new URL(u).origin === ORIGIN; } catch { return false; } };
  const ALLOWED = new Set(['media', 'display-capture', 'notifications', 'fullscreen', 'clipboard-sanitized-write', 'speaker-selection', 'mediaKeySystem']);
  ses.setPermissionRequestHandler((wc, perm, cb, details) => cb(ALLOWED.has(perm) && okOrigin(details.requestingUrl || wc.getURL())));
  ses.setPermissionCheckHandler((wc, perm, origin) => ALLOWED.has(perm) && (origin ? origin.startsWith(ORIGIN) : true));
  ses.setDisplayMediaRequestHandler(async (request, callback) => {
    const choice = reuseNext || await openPicker();
    reuseNext = null;
    const src = choice && lastSources.get(choice.id);
    if (!src) return callback({});
    lastChoice = { id: src.id, name: src.name, audio: !!choice.audio, screen: src.id.startsWith('screen:') };
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
