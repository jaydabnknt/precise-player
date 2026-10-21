#!/bin/bash
# Runs inside the emulator job. Never fails the step; results go to smoke/.
set -x
mkdir -p smoke
PKG=app.preciseplayer
shot() { adb exec-out screencap -p > "smoke/$1.png"; }
alive() {
  adb shell pidof $PKG >/dev/null && st=running || st="NOT RUNNING (crash?)"
  top=$(adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -o "app.preciseplayer/[^ ]*\|[a-z]*\.[a-z.]*/[^ ]*" | head -1)
  echo "$1: $st, top=$top" >> smoke/summary.txt
}

adb install -r -g PrecisePlayer.apk > smoke/install.txt 2>&1
adb shell mkdir -p /sdcard/Movies
adb push test.mp4 /sdcard/Movies/test.mp4
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/test.mp4 >/dev/null
adb shell pm grant $PKG android.permission.READ_MEDIA_VIDEO || true
adb logcat -c

# 1. home screen
adb shell am start -W -n $PKG/.ui.home.HomeActivity
sleep 4; shot 01-home; alive home
size=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); w=${size%x*}; h=${size#*x}
echo "screen $w x $h" >> smoke/summary.txt
adb shell input tap $((w*3/4)) $((h*16/100))   # "Videos" tab (approx)
sleep 3; shot 02-videos

# 2. play the test video
adb shell am start -W -a android.intent.action.VIEW -d file:///sdcard/Movies/test.mp4 -t video/mp4 -n $PKG/.ui.player.PlayerActivity
sleep 6; shot 03-playing; alive playing
# pause, then seek with keys and frame-step
alive "before keys"
adb shell input keyevent KEYCODE_SPACE; sleep 1; alive "after space"
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 2; shot 04-after-seek-fwd; alive "after seek key"
for i in 1 2 3 4 5; do adb shell input keyevent KEYCODE_PERIOD; done; sleep 2; shot 05-after-5-frames-fwd
for i in 1 2 3; do adb shell input keyevent KEYCODE_COMMA; done; sleep 3; shot 06-after-3-frames-back
# tap to show controls, then a drag along the seek bar area (bottom of screen)
alive "after frame steps"
adb shell input tap $((w/2)) $((h/3)); sleep 1; shot 07-controls
adb shell input swipe $((w/2)) $((h/2)) $((w/2+300)) $((h/2)) 1500; sleep 2; shot 08-after-swipe-seek
alive "after gestures"
# mpv log lines + any crash
adb logcat -d -v time > smoke/logcat-full.txt
grep -E "mpv|PrecisePlayer|AndroidRuntime|FATAL|DEBUG|libc" smoke/logcat-full.txt | tail -400 > smoke/logcat.txt
grep -E "FATAL EXCEPTION|Fatal signal|AndroidRuntime: " -A25 smoke/logcat-full.txt | head -150 > smoke/crashes.txt
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" >> smoke/summary.txt
cat smoke/summary.txt
exit 0
