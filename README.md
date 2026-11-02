# CONCORD

Chat, voz e transmissão de tela entre amigos, no estilo Discord.

**Acesse:** https://gabrielvazquez.github.io/concord/

## Recursos
- **Contas por email** (com troca e verificação de email), com perfil completo: foto (GIF animado também), banner, tema do cartão, bio, pronomes, status, apelido por servidor
- **Personalização liberada**: 23 decorações de avatar, 16 efeitos de perfil, 17 fontes e 7 efeitos de nome, formato do avatar, placa de nome animada, insígnias, banner animado, estilo do cartão e som de entrada na call
- **Aparência**: cor de destaque, tamanho da interface, modo compacto, esconder efeitos e reduzir animações
- **Servidores**: nome, ícone, banner, descrição, cor; canais (criar, renomear, tópico, reordenar, excluir); membros (remover); convite por link ou código
- **Voz**: escolha de microfone e saída, volume, sensibilidade automática/manual (noise gate em AudioWorklet), supressão de ruído, eco e ganho
- **Modo cinema**: filme em 1080p ou 4K com AV1/VP9, áudio estéreo a 320 kbps e buffer anti-engasgo; quem assiste entra em tela de cinema (widescreen, preencher/encaixar, tela cheia)
- **Vídeo e transmissão**: até 1440p a 60 fps com som; H.264 (placa de vídeo) para jogos e VP9 para texto; divisão de upload entre os espectadores; modo baixa latência; estatísticas ao vivo

Funciona melhor no Chrome ou no Edge do computador. Conexão direta entre os participantes (WebRTC), ideal para até 5–6 pessoas. Backend: Supabase (Auth, Postgres com RLS, Realtime, Storage).

## App para PC (Windows)

Baixe em **[Releases](https://github.com/gabrielvazquez/concord/releases/latest)** (`CONCORD-Setup.exe`).
O app abre o mesmo CONCORD do site (mesma conta, mesmos servidores, conversa com quem está no navegador) e adiciona:
atalhos globais e **push-to-talk** dentro do jogo (tecla ou botão lateral do mouse), seletor de tela com **som do PC**,
ícone na bandeja, iniciar com o Windows, notificações e janela que nunca "adormece".

O código fica em `desktop/` e o instalador é gerado pelo GitHub Actions ao criar uma tag `desktop-vX.Y.Z`.
