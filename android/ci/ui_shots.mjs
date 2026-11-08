// Capturas da interface: modo escuro e claro, com a chamada aberta
import { chromium } from 'playwright';
const b = await chromium.launch({ args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] });
const ctx = await b.newContext({ permissions: ['microphone', 'camera'], viewport: { width: 1440, height: 860 } });
const p = await ctx.newPage();
p.on('pageerror', e => console.log('pageerror', e.message));
await p.goto('https://gabrielvazquez.github.io/concord/?t=' + Date.now());
await p.waitForSelector('#fLogin', { timeout: 30000 });
await p.fill('#fLogin [name=email]', 'web.teste@concord.app'); await p.fill('#fLogin [name=pass]', 'TesteWeb2026'); await p.click('#fLogin button[type=submit]');
await p.waitForSelector('#chanlist .ch', { timeout: 40000 });
await p.click('#chanlist .ch:has-text("geral")'); await p.waitForTimeout(2500);
// galeria dos 50 efeitos de título (desbloqueados só nesta tela, para a foto)
await p.evaluate(() => {
  S.titles.set(S.me.id, ['fundador']);
  const keys = cat => Object.keys(LOCK).filter(k => k.startsWith(cat + ':')).map(k => k.split(':')[1]);
  const tierTag = (cat, k) => { const t = LOCK[cat + ':' + k]; return t ? oneBadge(t) : '<span style="font-size:11px;color:#a1a1aa">todos</span>'; };
  const me = S.profiles.get(S.me.id);
  const g = document.createElement('div'); g.id = 'gal'; g.style.cssText = 'position:fixed;inset:0;z-index:9999;background:#09090b;overflow:auto;padding:24px;color:#fff;font-family:inherit';
  const sec = (t, html) => `<h2 style="margin:18px 0 12px;font-size:20px">${t}</h2><div style="display:flex;flex-wrap:wrap;gap:14px">${html}</div>`;
  g.innerHTML =
    sec('Cartões (15)', keys('card').map(k => `<div style="width:200px"><div class="pcard cs-${k}" style="width:200px;height:120px;--tp:#1e1b4b;--ta:#0b0b10"><div style="padding:14px;font-weight:800">${CARDS.find(c => c[0] === k)[1]}</div></div><div style="margin-top:6px">${tierTag('card', k)}</div></div>`).join('')) +
    sec('Banners (10)', keys('ban').map(k => `<div style="width:220px"><div class="pcard" style="width:220px"><div class="bn ban-${k}" style="height:90px"></div></div><div style="margin-top:6px;display:flex;gap:6px;align-items:center"><b style="font-size:13px">${BANNERS.find(c => c[0] === k)[1]}</b>${tierTag('ban', k)}</div></div>`).join('')) +
    sec('Molduras (10)', keys('deco').map(k => `<div style="width:120px;text-align:center;padding:16px 0">${avatarP({ ...me, decoration: k }, 64)}<div style="font-size:12px;margin:12px 0 4px">${DECOS.find(c => c[0] === k)[1]}</div>${tierTag('deco', k)}</div>`).join('')) +
    sec('Nomes (10)', keys('nfx').map(k => `<div style="width:190px;padding:8px 0"><div style="font-size:22px;font-weight:800">${nameP({ display_name: 'Gabriel', name_effect: k, name_color: '#c4b5fd', name_color2: '#67e8f9' })}</div><div style="margin-top:4px">${tierTag('nfx', k)}</div></div>`).join('')) +
    sec('Entradas (5)', ENTRANCES.filter(e => e[0]).map(([k, l]) => `<div style="width:160px"><div class="tile" style="position:relative;height:90px;border-radius:14px;background:#18181b;overflow:hidden"><div class="entfx ent-${k}" style="animation-play-state:paused;animation-delay:-.3s"></div></div><div style="margin-top:6px;display:flex;gap:6px"><b style="font-size:13px">${l}</b>${tierTag('ent', k)}</div></div>`).join(''));
  document.body.appendChild(g);
});
await p.waitForTimeout(2500);
await p.screenshot({ path: 'ui-efeitos-1.png', fullPage: false });
await p.evaluate(() => document.getElementById('gal').scrollTo(0, 700)); await p.waitForTimeout(800);
await p.screenshot({ path: 'ui-efeitos-2.png' });
await p.evaluate(() => document.getElementById('gal').scrollTo(0, 1400)); await p.waitForTimeout(800);
await p.screenshot({ path: 'ui-efeitos-3.png' });
await p.evaluate(() => { document.getElementById('gal').remove(); S.titles.delete(S.me.id); });
await p.screenshot({ path: 'ui-escuro-chat.png' });
await p.click('#chanlist .ch:has-text("Sala de Jogo")'); await p.waitForTimeout(5000);
await p.screenshot({ path: 'ui-escuro-chamada.png' });
await p.evaluate(() => { setPref('mode', 'light'); applyAppearance(); });
await p.waitForTimeout(800);
await p.screenshot({ path: 'ui-claro-chamada.png' });
await p.click('#chanlist .ch:has-text("geral")'); await p.waitForTimeout(2000);
await p.screenshot({ path: 'ui-claro-chat.png' });
await p.evaluate(() => { toast('Exemplo de aviso no modo claro'); }); await p.waitForTimeout(300);
await p.evaluate(() => { const b = document.querySelector('#ubSet'); b && b.click(); }); await p.waitForTimeout(1500);
await p.screenshot({ path: 'ui-claro-config.png' });
await p.evaluate(() => { setPref('mode', 'dark'); applyAppearance(); closeSettings?.(); });
await p.keyboard.press('Escape'); await p.waitForTimeout(800);
await p.evaluate(() => openFriends()); await p.waitForTimeout(1500);
await p.fill('#frSearch', 'teste'); await p.waitForTimeout(2500);
console.log('busca:', await p.evaluate(() => [...document.querySelectorAll('#frResults .frrow')].map(r => r.innerText.replace(/\n/g, ' '))));
await p.screenshot({ path: 'ui-amigos.png' });
// pedido de amizade de ponta a ponta com a conta de teste do Android
const andId = await p.evaluate(async () => (await sb.from('dc_profiles').select('id').eq('username', 'testeandroid').single()).data?.id);
console.log('pedido:', await p.evaluate(async id => { await frRespond(id, false); const r = await sb.rpc('dc_friend_request', { p_other: id }); await loadFriends(); return [r.error?.message || r.data, frState(id)]; }, andId));
const p2 = await ctx.browser().newContext().then(c => c.newPage());
await p2.goto('https://gabrielvazquez.github.io/concord/?t=' + Date.now());
await p2.waitForSelector('#fLogin', { timeout: 30000 });
await p2.fill('#fLogin [name=email]', 'android.teste@concord.app'); await p2.fill('#fLogin [name=pass]', 'TesteAndroid2026'); await p2.click('#fLogin button[type=submit]');
await p2.waitForSelector('#chanlist .ch', { timeout: 40000 }); await p2.waitForTimeout(2000);
console.log('do outro lado:', await p2.evaluate(async () => { await loadFriends(); const f = frPendingIn()[0]; return f ? frOther(f) : null; }));
await p2.evaluate(() => openFriends()); await p2.waitForTimeout(1200); await p2.screenshot({ path: 'ui-amigos-pedido.png' });
console.log('aceitar:', await p2.evaluate(async () => { const f = frPendingIn()[0]; if (!f) return 'sem pedido'; await frRespond(frOther(f), true); return frState(frOther(f)); }));
await p.waitForTimeout(2500);
console.log('quem pediu vê:', await p.evaluate(id => frState(id), andId));
await p.evaluate(async id => { await frRespond(id, false); }, andId);
// loja de temas (simula o app de PC)
const dctx = await b.newContext({ viewport: { width: 1440, height: 860 } });
await dctx.addInitScript(() => { const mk = () => new Proxy(function () { return Promise.resolve(null); }, { get: (t, k) => (k === 'then' ? undefined : mk()), apply: () => Promise.resolve(null) }); window.concordDesktop = mk(); });
const d = await dctx.newPage(); d.on('pageerror', e => console.log('pageerror pc', e.message));
await d.goto('https://gabrielvazquez.github.io/concord/?t=' + Date.now());
await d.waitForSelector('#fLogin', { timeout: 30000 });
await d.fill('#fLogin [name=email]', 'web.teste@concord.app'); await d.fill('#fLogin [name=pass]', 'TesteWeb2026'); await d.click('#fLogin button[type=submit]');
await d.waitForSelector('#chanlist .ch', { timeout: 40000 }); await d.waitForTimeout(1500);
console.log('publicar:', await d.evaluate(async () => { const r = await sb.from('dc_themes').insert({ name: 'Teste CI', data: { mode: 'dark', base: '#1e3a5f', accent: '#38bdf8' } }).select().single(); return r.error?.message || 'ok'; }));
await d.evaluate(() => { openSettings ? openSettings('aparencia') : null; }).catch(() => {});
await d.evaluate(() => { SET.sec = 'aparencia'; $('#settings').classList.remove('hidden'); renderSettings(); });
await d.waitForTimeout(2500);
console.log('loja:', await d.evaluate(() => [...document.querySelectorAll('#storeGrid .tcard span')].map(x => x.textContent)));
await d.screenshot({ path: 'ui-aparencia-temas.png' });
await d.click('.stabs [data-st="loja"]'); await d.waitForTimeout(800); await d.screenshot({ path: 'ui-loja.png' });
await d.click('.stabs [data-st="interface"]'); await d.waitForTimeout(500); await d.screenshot({ path: 'ui-aparencia-interface.png' });
await d.click('.stabs [data-st="loja"]'); await d.waitForTimeout(300);
await d.evaluate(() => { SET.sec = 'perfil'; SET.draft = null; renderSettings(); }); await d.waitForTimeout(1200);
await d.screenshot({ path: 'ui-perfil-basico.png' });
await d.click('.stabs [data-st="nome"]'); await d.waitForTimeout(600); await d.screenshot({ path: 'ui-perfil-nome.png' });
await d.click('.stabs [data-st="cartao"]'); await d.waitForTimeout(600); await d.screenshot({ path: 'ui-perfil-cartao.png' });
await d.evaluate(() => { SET.sec = 'aparencia'; renderSettings(); }); await d.waitForTimeout(600);
await d.click('[data-store="lavanda"]'); await d.waitForTimeout(800);
await d.evaluate(() => { $('#settings').classList.add('hidden'); }); await d.click('#chanlist .ch:has-text("geral")'); await d.waitForTimeout(1500);
await d.screenshot({ path: 'ui-tema-lavanda.png' });
console.log('apagar:', await d.evaluate(async () => { const { data } = await sb.from('dc_themes').select('id').eq('name', 'Teste CI'); for (const r of data || []) await sb.from('dc_themes').delete().eq('id', r.id); setPref('appTheme', 'oled'); setPref('mode', 'dark'); setPref('accent', '#ffffff'); return (data || []).length; }));
await b.close();
