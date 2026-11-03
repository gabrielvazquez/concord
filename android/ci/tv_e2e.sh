#!/bin/bash
set -x
adb install -r -g CONCORD.apk
adb shell am start -n com.gabrielvazquez.concord/.TvActivity
sleep 12
adb exec-out screencap -p > t1-codigo.png
for try in 1 2 3 4 5 6; do
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > ui.xml
python3 - <<'P'
import re,subprocess
s=open('ui.xml',encoding='utf-8',errors='ignore').read()
if "isn't responding" in s or "não está respondendo" in s:
    for m in re.finditer(r'<node [^>]*>', s):
        n=m.group(0)
        if re.search(r' text="(Wait|Aguardar)"', n):
            b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n); x1,y1,x2,y2=map(int,b.groups())
            subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)]); print('fechei aviso do emulador')
for t in re.findall(r' text="([^"]*)"', s):
    d=t.replace(' ','')
    if re.fullmatch(r'\d{6}', d): open('/tmp/tvcode.txt','w').write(d); print('CODIGO', d); break
P
[ -f /tmp/tvcode.txt ] && break; sleep 4
done
sleep 75; adb exec-out screencap -p > t2-assistindo.png
adb shell input keyevent 23; sleep 3; adb exec-out screencap -p > t3-som.png
sleep 20; adb exec-out screencap -p > t4.png
adb logcat -d | grep -iE "AndroidRuntime|FATAL" | tail -30
adb shell pidof com.gabrielvazquez.concord && echo "APP VIVO" || echo "APP MORREU"
