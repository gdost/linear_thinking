#!/bin/bash
# Plays one game on a running emulator and saves screenshots to shots/.
set -x
mkdir -p shots
adb install -r build/water-glass-debug.apk
adb shell am start -n app.linearthinking.waterglass/.MainActivity
sleep 5
read W H < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr x ' ')
shot() { adb exec-out screencap -p > "shots/$1.png"; }
# Center of the first UI element whose attributes match $1.
center() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep "$1" | head -1 \
    | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | paste -sd' ' \
    | awk '{print int(($1+$3)/2), int(($2+$4)/2), $1, $3}'
}
shot 01_linear_ask
adb shell input tap $((W/2)) $((H/2))
sleep 1; shot 02_linear_anim
sleep 4; shot 03_linear_done
read X Y _ _ < <(center 'text="Next question"')
adb shell input tap $X $Y
sleep 2; shot 04_double_ask
read X Y L R < <(center 'SeekBar')
adb shell input swipe $((L+20)) $Y $((L+(R-L)*45/100)) $Y 800
sleep 1; shot 05_zoomed_45
adb shell input swipe $((L+(R-L)*45/100)) $Y $((L+(R-L)*80/100)) $Y 800
sleep 1; shot 06_zoomed_80
adb shell input tap $((W/2)) $((H*35/100))
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13; do sleep 1; shot "07_reveal_$(printf %02d $i)"; done
sleep 3; shot 08_done
