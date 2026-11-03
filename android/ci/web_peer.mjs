// Cliente do SITE (Chrome) para conversar com o app Android no teste automático
import { chromium } from 'playwright';
const log = (...a) => console.log('[web]', ...a);
const b = await chromium.launch({ args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', '--autoplay-policy=no-user-gesture-required'] });
const ctx = await b.newContext({ permissions: ['microphone', 'camera'] });
const p = await ctx.newPage();
p.on('console', m => { if (m.type() === 'error') log('console', m.text().slice(0, 200)); });
p.on('pageerror', e => log('pageerror', e.message));
await p.goto('https://gabrielvazquez.github.io/concord/');
await p.waitForSelector('#fLogin', { timeout: 30000 });
await p.fill('#fLogin [name=email]', 'web.teste@concord.app');
await p.fill('#fLogin [name=pass]', 'TesteWeb2026');
await p.click('#fLogin button[type=submit]');
await p.waitForSelector('#chanlist .ch', { timeout: 40000 });
log('logado; servidor:', await p.evaluate(() => S.server?.name));
await p.waitForTimeout(3000);
// mensagem para o Android ver
await p.click('#chanlist .ch:has-text("geral")');
await p.fill('#msgInp', 'olá do site 👋 **teste**'); await p.press('#msgInp', 'Enter');
await p.click('#chanlist .ch:has-text("Sala de Jogo")');
let ok = false;
for (let i = 0; i < 150; i++) {
  const st = await p.evaluate(async () => {
    if (!V) return null;
    const out = [];
    for (const x of V.peers.values()) {
      let inA = 0, outA = 0; const s = await x.pc.getStats();
      s.forEach(r => { if (r.type === 'inbound-rtp' && r.kind === 'audio') inA += r.bytesReceived; if (r.type === 'outbound-rtp' && r.kind === 'audio') outA += r.bytesSent; });
      out.push({ id: x.id.slice(0, 8), state: x.pc.connectionState, ice: x.pc.iceConnectionState, inAudio: inA, outAudio: outA, meta: V.meta[x.id]?.name });
    }
    return out;
  });
  if (i % 5 === 0) log('peers', JSON.stringify(st));
  if (st && st.some(x => x.state === 'connected')) { ok = true; if (i % 5 === 0) log('CONECTADO COM O ANDROID'); }
  if (ok && st.some(x => x.inAudio > 0)) { log('ÁUDIO CHEGANDO DO ANDROID', JSON.stringify(st)); }
  await p.waitForTimeout(2000);
}
log(ok ? 'RESULTADO: VOZ OK' : 'RESULTADO: SEM CONEXÃO');
await b.close();
