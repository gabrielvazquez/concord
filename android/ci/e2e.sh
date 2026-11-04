#!/bin/bash
# Teste ponta a ponta no emulador: login, chat e sala de voz com o site
set -x
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > ui.xml; }
# o emulador às vezes mostra "Pixel Launcher isn't responding" por cima do app: toca em Wait
dismiss() {
  if grep -q "isn't responding\|não está respondendo" ui.xml; then
    w=$(python3 -c "
import re
s=open('ui.xml',encoding='utf-8',errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', s):
    n=m.group(0)
    if re.search(r' text=\"(Wait|Aguardar)\"', n):
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n); x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break")
    [ -n "$w" ] && adb shell input tap $w && echo "fechei aviso do emulador" && sleep 2 && ui
  fi
}
tap() { # tap "texto" [desc]
  for try in 1 2 3; do
  ui; dismiss
  xy=$(python3 - "$1" "${2:-}" <<'P'
import sys,re
t=sys.argv[1]; mode=sys.argv[2]
s=open('ui.xml',encoding='utf-8',errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', s):
    n=m.group(0)
    a=re.search(r'content-desc="([^"]*)"',n).group(1) if mode=='desc' else re.search(r' text="([^"]*)"',n).group(1)
    if a==t or (mode=='part' and t in a):
        b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n)
        x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
P
)
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "tap $1 -> $xy"; return; fi
  sleep 3; done
  echo "NAO ACHEI: $1"
}
shot() { adb exec-out screencap -p > "$1.png"; }
adb install -r -g CONCORD.apk
adb logcat -c
adb shell am start -n com.gabrielvazquez.concord/.MainActivity
sleep 12; shot e1-login
tap "Email"; adb shell input text "android.teste@concord.app"
tap "Senha"; adb shell input text "TesteAndroid2026"
adb shell input keyevent 111
tap "Entrar"
sleep 15; shot e2-gaveta
tap "geral"; sleep 6; shot e3-chat
tap "Conversar em #geral" part; adb shell input text "ola%sdo%sapp%sandroid"; sleep 1; tap "Enviar" desc
sleep 4; adb shell input keyevent 111; shot e4-enviado
tap "Menu" desc; sleep 3
tap "Sala de Jogo"; sleep 30; shot e5-voz
sleep 30; shot e6-voz2
tap "Teste Web" ; sleep 4; shot e7-perfil; adb shell input keyevent 4; sleep 2
tap "Mensagem"; sleep 4; shot e8-dm
adb shell input keyevent 4; sleep 2
tap "Menu" desc; sleep 3; tap "Mensagens diretas" desc; sleep 3; tap "Amigos"; sleep 4; shot e9-amigos
tap "@nome ou nome"; adb shell input text "teste"; sleep 5; adb shell input keyevent 111; sleep 1; shot e10-busca
adb logcat -d | grep -iE "AndroidRuntime|FATAL|webrtc|concord" | grep -v "SplashScreen" | tail -60
adb shell pidof com.gabrielvazquez.concord && echo "APP VIVO" || echo "APP MORREU"
