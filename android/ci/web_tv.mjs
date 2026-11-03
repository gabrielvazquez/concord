// Teste do Modo TV: o site entra na sala, "transmite", e pareia a TV do Android e a página de TV do site
import { chromium } from 'playwright';
import fs from 'fs';
const log = (...a) => console.log('[web]', ...a);
const b = await chromium.launch({ args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', '--autoplay-policy=no-user-gesture-required'] });
const ctx = await b.newContext({ permissions: ['microphone', 'camera'], viewport: { width: 1280, height: 720 } });
const p = await ctx.newPage();
p.on('pageerror', e => log('pageerror', e.message));
await p.goto('https://gabrielvazquez.github.io/concord/?t=' + Date.now());
await p.waitForSelector('#fLogin', { timeout: 30000 });
await p.fill('#fLogin [name=email]', 'web.teste@concord.app'); await p.fill('#fLogin [name=pass]', 'TesteWeb2026'); await p.click('#fLogin button[type=submit]');
await p.waitForSelector('#chanlist .ch', { timeout: 40000 });
await p.click('#chanlist .ch:has-text("Sala de Jogo")');
await p.waitForTimeout(4000);
// "transmissão" simulada com a câmera falsa
await p.evaluate(async () => { const s = await navigator.mediaDevices.getUserMedia({ video: { width: 1280, height: 720 } }); V.screen = s; V.screenQ = 'cinema'; addStream(s, 'screen'); afterToggle(); V.rt.track(myVoiceMeta()); });
log('transmitindo (simulado)');
// página de TV do site
const tv = await ctx.newPage();
tv.on('pageerror', e => log('tv pageerror', e.message));
await tv.goto('https://gabrielvazquez.github.io/concord/tv.html?t=' + Date.now());
await tv.waitForTimeout(4000);
const webCode = (await tv.textContent('#code')).trim();
await tv.screenshot({ path: 'tv-site-codigo.png' });
log('QR na TV do site:', await tv.evaluate(() => !!document.querySelector('#qr svg')));
log('código da TV (site):', webCode);
const pair = async code => p.evaluate(async c => tvSend(c, 'pair', { room: V.ch.id, roomName: V.ch.name, uid: S.me.id, name: dname(S.me.id) }), code);
log('parear TV do site (como pelo QR):', await p.evaluate(c => tvFromQr(c), webCode));
// TV do Android: espera o código aparecer no arquivo
let andCode = null;
for (let i = 0; i < 90 && !andCode; i++) { try { andCode = fs.readFileSync('/tmp/tvcode.txt', 'utf8').trim(); } catch {} if (!andCode) await p.waitForTimeout(2000); }
log('código da TV (Android):', andCode);
if (andCode) log('parear TV do Android:', await pair(andCode));
for (let i = 0; i < 25; i++) {
  await p.waitForTimeout(3000);
  const a = await p.evaluate(() => ({ tvs: myTvs().length, peers: [...V.peers.values()].map(x => (V.meta[x.id]?.name || '?') + ':' + x.pc.connectionState), tiles: V.tiles.size }));
  const t = await tv.evaluate(() => ({ room: !!TV.room, peers: [...TV.peers.values()].map(x => x.pc.connectionState), w: document.querySelector('#main').videoWidth }));
  log('site', JSON.stringify(a), '| tv-site', JSON.stringify(t));
}
await tv.screenshot({ path: 'tv-site.png' });
// TV vinculada à conta: digita o código da conta e deve abrir a sala sozinha
const linkCode = await p.evaluate(() => tvLinkCode(true));
log('código da conta:', linkCode);
const tv2 = await ctx.newPage();
tv2.on('pageerror', e => log('tv2 pageerror', e.message));
await tv2.goto('https://gabrielvazquez.github.io/concord/tv.html?t=' + Date.now());
await tv2.waitForTimeout(3000);
await tv2.fill('#link input', linkCode);
for (let i = 0; i < 10; i++) { await tv2.waitForTimeout(2000); const r = await tv2.evaluate(() => ({ room: TV.room, link: LINK, peers: [...TV.peers.values()].map(x => x.pc.connectionState), w: document.querySelector('#main').videoWidth })); log('tv vinculada', JSON.stringify(r)); if (r.w) break; }
await tv2.screenshot({ path: 'tv-vinculada.png' });
// sair e voltar para a sala: a TV vinculada deve sair e voltar junto
await p.evaluate(() => leaveVoice()); await p.waitForTimeout(3000);
log('depois de sair, tv vinculada na sala?', await tv2.evaluate(() => !!TV.room));
await p.click('#chanlist .ch:has-text("Sala de Jogo")'); await p.waitForTimeout(6000);
log('depois de voltar, tv vinculada na sala?', await tv2.evaluate(() => !!TV.room));
await p.screenshot({ path: 'web-sala.png' });
await b.close();
