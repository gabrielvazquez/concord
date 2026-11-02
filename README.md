# CONCORD

Chat, voz e transmissão de tela entre amigos, no estilo Discord.

**Acesse:** https://gabrielvazquez.github.io/concord/

## Recursos
- **Contas por email** (com troca e verificação de email), com perfil completo: foto (GIF animado também), banner, tema do cartão, bio, pronomes, status, apelido por servidor
- **Personalização liberada**: 11 decorações de avatar, 8 efeitos de perfil, 9 fontes e cores/gradientes/brilho no nome
- **Servidores**: nome, ícone, banner, descrição, cor; canais (criar, renomear, reordenar, excluir); membros (remover); convite por link ou código
- **Voz**: escolha de microfone e saída, volume, sensibilidade automática/manual (noise gate em AudioWorklet), supressão de ruído, eco e ganho
- **Modo cinema**: filme em 1080p ou 4K com AV1/VP9, áudio estéreo a 320 kbps e buffer anti-engasgo; quem assiste entra em tela de cinema (widescreen, preencher/encaixar, tela cheia)
- **Vídeo e transmissão**: até 1440p a 60 fps com som; H.264 (placa de vídeo) para jogos e VP9 para texto; divisão de upload entre os espectadores; modo baixa latência; estatísticas ao vivo

Funciona melhor no Chrome ou no Edge do computador. Conexão direta entre os participantes (WebRTC), ideal para até 5–6 pessoas. Backend: Supabase (Auth, Postgres com RLS, Realtime, Storage).
