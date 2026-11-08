// prévia dos selos de título com a fonte do app
import { chromium } from 'playwright';
import fs from 'fs';
const s = fs.readFileSync('../../index.html', 'utf8');
const css = s.slice(s.indexOf('/* ===== títulos: metais'), s.indexOf('/* ===== amigos ===== */'));
const blk = s.slice(s.indexOf("const GEM = '"), s.indexOf('};', s.indexOf('const TICON')) + 2);
const TICON = new Function(blk + '; return TICON;')();
const T = [['ceo', 'CEO', 'Diamond'], ['presidente', 'PRESIDENT', 'Platinum'], ['diretor', 'DIRECTOR', 'Gold'], ['adm', 'ADMIN', 'Silver'], ['moderador', 'MOD', 'Bronze'], ['fundador', 'FOUNDER', 'Emerald']];
const rows = T.map(([k, l, t]) => `<div class="r"><span class="n">Gabriel</span><span class="tbadge t-${k}">${TICON[k]}${l}</span><span class="sp"></span><span class="tbadge t-${k} lg">${TICON[k]}${l}<small>${t}</small></span></div>`).join('');
const html = `<!doctype html><html><head><meta charset="utf-8"><link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700;800&display=swap" rel="stylesheet"><style>body{margin:0;background:#0a0a0b;font-family:'Plus Jakarta Sans',sans-serif;color:#fafafa}
.w{padding:40px 48px;width:720px}h2{margin:0 0 24px;font-size:22px;font-weight:800}.r{display:flex;align-items:center;height:56px;border-bottom:1px solid #1f1f23}.n{font-weight:700;font-size:15px}.sp{flex:1}${css}</style></head><body><div class="w"><h2>CONCORD titles</h2>${rows}</div></body></html>`;
const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 816, height: 480 }, deviceScaleFactor: 2 });
await p.setContent(html); await p.waitForTimeout(2200); await p.locator('.w').screenshot({ path: 'ui-titulos.png' }); await b.close();
