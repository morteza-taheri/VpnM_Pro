@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "JAVA_TOOL_OPTIONS="
set "_JAVA_OPTIONS="
cd /d d:\PMC\Documents\Android\VpnM_Pro
call "C:\Users\PMC\.gradle\wrapper\dists\gradle-9.4.1-all\eckclv9s5vi9exbmnyvpg2a3h\gradle-9.4.1\bin\gradle.bat" assembleDebug --console=plain --no-daemon --stacktrace