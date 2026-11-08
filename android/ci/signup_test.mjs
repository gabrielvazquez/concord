// Cadastro sem e-mail (com convite) e criação/exclusão de servidor por um usuário comum
import { chromium } from 'playwright';
const b = await chromium.launch();
const p = await (await b.newContext()).newPage();
p.on('pageerror', e => console.log('pageerror', e.message));
p.on('dialog', d => d.accept(d.message().includes('Digite o nome') ? 'Servidor CI' : undefined));
const email = `ci${Date.now()}@concord.test`;
await p.goto('https://gabrielvazquez.github.io/concord/?t=' + Date.now() + '#TESTE-CI');
await p.waitForSelector('#fLogin', { timeout: 30000 });
await p.click('[data-go="signup"]'); await p.waitForSelector('#fSignup');
console.log('convite preenchido:', await p.inputValue('#fSignup [name=invite]'));
// convite errado
await p.fill('#fSignup [name=name]', 'CI'); await p.fill('#fSignup [name=email]', email); await p.fill('#fSignup [name=pass]', 'SenhaCI123');
await p.fill('#fSignup [name=invite]', 'NAOEXISTE'); await p.click('#fSignup button[type=submit]'); await p.waitForTimeout(4000);
console.log('convite errado:', (await p.textContent('#authCard, .auth, body')).match(/Código de convite[^.]*\./)?.[0] || 'sem mensagem');
await p.fill('#fSignup [name=name]', 'CI'); await p.fill('#fSignup [name=email]', email); await p.fill('#fSignup [name=pass]', 'SenhaCI123');
await p.fill('#fSignup [name=invite]', 'TESTE-CI'); await p.click('#fSignup button[type=submit]');
await p.waitForSelector('#chanlist .ch', { timeout: 40000 });
console.log('entrou; servidores:', await p.evaluate(() => S.servers.map(s => s.name)));
await p.evaluate(() => openServerModal('create')); await p.waitForTimeout(500);
await p.fill('#nsName', 'Servidor CI'); await p.click('#nsGo'); await p.waitForTimeout(3000);
console.log('criou:', await p.evaluate(() => [S.server?.name, isOwner(), canEditServer()]));
await p.evaluate(() => deleteServer()); await p.waitForTimeout(3000);
console.log('depois de excluir:', await p.evaluate(() => S.servers.map(s => s.name)));
await b.close();
console.log('email de teste:', email);
