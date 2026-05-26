@echo off
cd /d "%~dp0"
start /MIN javaw -jar bing-wallpaper.jar 2>nul
exit /b 0
