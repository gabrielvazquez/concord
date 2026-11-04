// Lista da sala: entra, muta várias vezes rápido, sai — quem está de fora tem que ver tudo certo
import { chromium } from 'playwright';
const log = (...a) => console.log('[presenca]', ...a);
const b = await chromium.launch({ args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] });
async function login(email, pass) {
  const ctx = await b.newContext({ permissions: ['microphone'] }); const p = await ctx.newPage();
  p.on('pageerror', e => log('pageerror', e.message));
  await p.goto('https://gabrielvazquez.github.io/concord/?t=' + Date.now());
  await p.waitForSelector('#fLogin', { timeout: 30000 });
  await p.fill('#fLogin [name=email]', email); await p.fill('#fLogin [name=pass]', pass); await p.click('#fLogin button[type=submit]');
  await p.waitForSelector('#chanlist .ch', { timeout: 40000 }); await p.waitForTimeout(2000); return p;
}
const a = await login('web.teste@concord.app', 'TesteWeb2026');
const o = await login('android.teste@concord.app', 'TesteAndroid2026');
const seen = () => o.evaluate(() => { const ch = S.channels.find(c => c.name === 'Sala de Jogo'); return voiceUsers(ch.id).map(u => dname(u.uid) + (u.deaf ? '[sem som]' : u.muted ? '[mutado]' : '')); });
for (let round = 1; round <= 3; round++) {
  await a.click('#chanlist .ch:has-text("Sala de Jogo")'); await a.waitForTimeout(5000);
  log(`rodada ${round} entrou →`, await seen());
  for (let i = 0; i < 12; i++) { await a.evaluate(() => setMuted(!S.muted)); await a.waitForTimeout(120); }
  await a.evaluate(() => { setMuted(true); setDeaf(true); }); await a.waitForTimeout(4000);
  log(`rodada ${round} mutou e tirou o som →`, await seen());
  await a.evaluate(() => { setDeaf(false); setMuted(false); }); await a.evaluate(() => leaveVoice()); await a.waitForTimeout(4000);
  log(`rodada ${round} saiu →`, await seen());
}
await b.close();
