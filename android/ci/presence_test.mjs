// Lista das salas de voz: quem entra aparece, quem sai some (visto por quem está fora da sala)
import { chromium } from 'playwright';
const b = await chromium.launch({ args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] });
async function user(email, pass) {
  const ctx = await b.newContext({ permissions: ['microphone', 'camera'] }); const p = await ctx.newPage();
  p.on('pageerror', e => console.log('pageerror', email, e.message));
  await p.goto('https://gabrielvazquez.github.io/concord/?t=' + Date.now());
  await p.waitForSelector('#fLogin', { timeout: 30000 });
  await p.fill('#fLogin [name=email]', email); await p.fill('#fLogin [name=pass]', pass); await p.click('#fLogin button[type=submit]');
  await p.waitForSelector('#chanlist .ch', { timeout: 40000 }); await p.click('#chanlist .ch:has-text("geral")'); await p.waitForTimeout(2500);
  return p;
}
const A = await user('web.teste@concord.app', 'TesteWeb2026');
const B = await user('android.teste@concord.app', 'TesteAndroid2026');
const list = p => p.evaluate(() => Object.fromEntries(S.channels.filter(c => c.kind === 'voice').map(c => [c.name, voiceUsers(c.id).map(u => dname(u.uid) + (u.muted ? '(mutado)' : '') + (u.deaf ? '(sem som)' : ''))])));
const step = async (t) => { await A.waitForTimeout(4000); console.log(t, '| A vê', JSON.stringify(await list(A)), '| B vê', JSON.stringify(await list(B))); };
await step('ninguém na sala');
await A.click('#chanlist .ch:has-text("Sala de Jogo")'); await step('A entrou');
await A.evaluate(() => setDeaf(true)); await step('A sem som');
await B.click('#chanlist .ch:has-text("Sala de Jogo")'); await B.waitForTimeout(2000); await B.evaluate(() => document.querySelector('#joinBtn')?.click()); await step('B entrou também');
await A.evaluate(() => leaveVoice()); await step('A saiu');
await B.evaluate(() => leaveVoice()); await step('B saiu');
await A.click('#chanlist .ch:has-text("Bate-papo")'); await step('A entrou no Bate-papo');
await A.evaluate(() => leaveVoice()); await step('A saiu de novo');
await b.close();
