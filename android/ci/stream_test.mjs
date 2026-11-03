// Teste longo de transmissão: um navegador captura de verdade uma aba com "filme" (getDisplayMedia),
// transmite para outro navegador por alguns minutos, e o teste mede se a imagem continua chegando.
import { chromium } from 'playwright';
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);
const SITE = 'https://gabrielvazquez.github.io/concord/?t=' + Date.now();
const MODES = (process.env.MODES || 'cinema:180,jogo1080:90').split(',').map(s => { const [q, t] = s.split(':'); return { q, t: +t }; });

const FILME = `<!doctype html><title>FILME-TESTE</title><body style="margin:0;background:#000"><canvas id=c width=1920 height=1080 style="width:100vw;height:100vh"></canvas>
<script>const c=document.getElementById('c'),g=c.getContext('2d');let f=0;
(function draw(){f++;const t=f/30;g.fillStyle='hsl('+(f%360)+',60%,25%)';g.fillRect(0,0,1920,1080);
for(let i=0;i<60;i++){g.fillStyle='hsl('+((i*37+f*3)%360)+',80%,55%)';const x=(Math.sin(t*0.7+i)*0.5+0.5)*1800,y=(Math.cos(t*0.9+i*1.3)*0.5+0.5)*980;g.beginPath();g.arc(x,y,40+20*Math.sin(t+i),0,7);g.fill();}
const d=g.getImageData(0,0,480,270);for(let k=0;k<d.data.length;k+=4){const n=Math.random()*80;d.data[k]+=n;d.data[k+1]+=n;d.data[k+2]+=n;}g.putImageData(d,0,0);
g.fillStyle='#fff';g.font='bold 90px sans-serif';g.fillText('FRAME '+f,700,560);requestAnimationFrame(draw);})();</script>`;

const flags = ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', '--autoplay-policy=no-user-gesture-required',
  '--auto-select-tab-capture-source-by-title=FILME-TESTE', '--auto-select-desktop-capture-source=FILME-TESTE', '--enable-usermedia-screen-capturing',
  '--disable-renderer-backgrounding', '--disable-background-timer-throttling', '--disable-backgrounding-occluded-windows'];

async function login(page, email, pass) {
  page.on('pageerror', e => log('pageerror', email, e.message));
  page.on('console', m => { if (m.type() === 'warning' || m.type() === 'error') log('console', email.split('@')[0], m.type(), m.text().slice(0, 200)); });
  await page.goto(SITE);
  await page.waitForSelector('#fLogin', { timeout: 30000 });
  await page.fill('#fLogin [name=email]', email); await page.fill('#fLogin [name=pass]', pass); await page.click('#fLogin button[type=submit]');
  await page.waitForSelector('#chanlist .ch', { timeout: 40000 });
  await page.click('#chanlist .ch:has-text("Sala de Jogo")');
  await page.waitForTimeout(5000);
}

const A = await chromium.launch({ headless: false, args: flags });
const actx = await A.newContext({ permissions: ['microphone', 'camera'], viewport: { width: 1280, height: 720 } });
const filme = await actx.newPage(); await filme.setContent(FILME);
const a = await actx.newPage(); await login(a, 'web.teste@concord.app', 'TesteWeb2026');
const B = await chromium.launch({ args: flags });
const bctx = await B.newContext({ permissions: ['microphone', 'camera'], viewport: { width: 1280, height: 720 } });
const b = await bctx.newPage(); await login(b, 'android.teste@concord.app', 'TesteAndroid2026');
await a.waitForTimeout(6000);
log('pares', await a.evaluate(() => [...V.peers.values()].map(p => p.pc.connectionState)), await b.evaluate(() => [...V.peers.values()].map(p => p.pc.connectionState)));
log('codificadores por hardware (HW):', await a.evaluate(() => JSON.stringify(HW)));

const viewer = () => b.evaluate(async () => {
  const out = [];
  for (const p of V.peers.values()) {
    const rep = await p.pc.getStats(); const codecs = {}; rep.forEach(r => { if (r.type === 'codec') codecs[r.id] = r.mimeType; });
    rep.forEach(r => { if (r.type === 'inbound-rtp' && r.kind === 'video' && r.bytesReceived > 0) out.push({ st: p.pc.connectionState, codec: codecs[r.codecId], res: r.frameWidth + 'x' + r.frameHeight, fps: r.framesPerSecond, dec: r.framesDecoded, freezes: r.freezeCount, freezeS: +(r.totalFreezesDuration || 0).toFixed(1), lost: r.packetsLost, kbps: 0, bytes: r.bytesReceived }); });
  }
  return out;
});
const sharer = () => a.evaluate(() => ({ out: V.outStats, hw: V.outHw, bad: [...(V.badCodecs || [])], screen: !!V.screen, peers: [...V.peers.values()].map(p => p.pc.connectionState) }));

let problems = 0;
for (const { q, t } of MODES) {
  log(`=== transmitindo em ${q} por ${t}s ===`);
  await a.evaluate(q => {
    const btn = document.createElement('button'); btn.id = 'goShare'; btn.textContent = 'share'; btn.style.cssText = 'position:fixed;z-index:99999;left:0;top:0';
    btn.onclick = async () => {
      try {
        if (V.screen) stopScreen();
        V.screenQ = q; V.reshares = 0;
        V.screen = await navigator.mediaDevices.getDisplayMedia(screenConstraints(SHARE_Q[q]));
        const vt = V.screen.getVideoTracks()[0]; vt.contentHint = SHARE_Q[q].motion ? 'motion' : 'detail'; watchScreenEnd();
        addStream(V.screen, 'screen'); V.focus = 'screen:' + S.sid; afterToggle();
        V.peers.forEach(p => tuneSenders(p.pc)); setTimeout(() => V && V.peers.forEach(p => tuneSenders(p.pc)), 1500);
        window.__share = 'ok ' + vt.label + ' ' + JSON.stringify(vt.getSettings());
      } catch (e) { window.__share = 'erro ' + e.name + ' ' + e.message; }
      btn.remove();
    };
    document.body.append(btn);
  }, q);
  await a.click('#goShare');
  await a.waitForTimeout(4000);
  log('captura:', await a.evaluate(() => window.__share));
  let lastDec = -1, stalls = 0;
  for (let s = 0; s < t; s += 10) {
    await a.waitForTimeout(10000);
    const v = await viewer(), sh = await sharer();
    const scr = v.sort((x, y) => (y.bytes || 0) - (x.bytes || 0))[0];
    const dec = scr?.dec ?? 0; const moved = dec - (lastDec < 0 ? 0 : lastDec); lastDec = dec;
    if (moved < 30) { stalls++; problems++; }
    log(`t=${s + 10}s envia: ${sh.out?.codec || '?'} ${sh.out?.res || '?'} ${sh.out?.fps ? Math.round(sh.out.fps) : '?'}fps ${Math.round((sh.out?.br || 0) / 1000)}kbps limite=${sh.out?.limit || 'nenhum'} hw=${sh.hw} trocas=${sh.bad.join('>') || '-'} pares=${sh.peers.join(',')}`
      + ` | recebe: ${scr ? `${scr.codec} ${scr.res} ${Math.round(scr.fps || 0)}fps +${moved} quadros congelou=${scr.freezes}x/${scr.freezeS}s perdidos=${scr.lost} ${scr.st}` : 'NADA'}`);
  }
  log(`${q}: ${stalls} intervalo(s) de 10s com imagem parada`);
}
// captura encerrada pelo sistema: no site, para e avisa
await a.evaluate(() => V.screen.getVideoTracks()[0].dispatchEvent(new Event('ended')));
await a.waitForTimeout(4000);
log('depois do fim da captura: transmitindo?', await a.evaluate(() => !!V.screen), '| quem assiste ainda recebe vídeo?', (await viewer()).length);
await a.screenshot({ path: 'stream-envia.png' }); await b.screenshot({ path: 'stream-recebe.png' });
log(problems ? `RESULTADO: ${problems} intervalo(s) com problema` : 'RESULTADO: transmissão estável');
await A.close(); await B.close();
