#!/bin/bash
set -x
adb install -r -g CONCORD.apk
adb shell am start -n com.gabrielvazquez.concord/.TvActivity
sleep 12
adb exec-out screencap -p > t1-codigo.png
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > ui.xml
python3 - <<'P'
import re
s=open('ui.xml',encoding='utf-8',errors='ignore').read()
for t in re.findall(r' text="([^"]*)"', s):
    d=t.replace(' ','')
    if re.fullmatch(r'\d{6}', d): open('/tmp/tvcode.txt','w').write(d); print('CODIGO', d); break
P
sleep 75; adb exec-out screencap -p > t2-assistindo.png
adb shell input keyevent 23; sleep 3; adb exec-out screencap -p > t3-som.png
sleep 20; adb exec-out screencap -p > t4.png
adb logcat -d | grep -iE "AndroidRuntime|FATAL" | tail -30
adb shell pidof com.gabrielvazquez.concord && echo "APP VIVO" || echo "APP MORREU"
