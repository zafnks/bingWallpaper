@echo off
title Bing Wallpaper - Register Startup
cd /d "%~dp0"

echo ====================================
echo  Bing Wallpaper Auto-Changer
echo  Register Windows Startup
echo ====================================
echo.

:: Check if JAR exists
if not exist "bing-wallpaper.jar" (
    echo [ERROR] bing-wallpaper.jar not found!
    echo Please run 'mvn clean package' first.
    pause
    exit /b 1
)

:: Check Java
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH.
    echo Please install Java 8 or later.
    pause
    exit /b 1
)

set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "SCRIPT_DIR=%~dp0"
set "SHORTCUT_NAME=BingWallpaper.lnk"

echo Creating startup shortcut...
powershell -NoProfile -Command ^
    "$WshShell = New-Object -ComObject WScript.Shell;" ^
    "$Shortcut = $WshShell.CreateShortcut('%STARTUP_DIR%\%SHORTCUT_NAME%');" ^
    "$Shortcut.TargetPath = '%SCRIPT_DIR%startup.bat';" ^
    "$Shortcut.WorkingDirectory = '%SCRIPT_DIR%';" ^
    "$Shortcut.WindowStyle = 7;" ^
    "$Shortcut.Description = 'Bing Wallpaper Auto-Changer - Daily wallpaper updates';" ^
    "$Shortcut.Save()"

if %errorlevel% equ 0 (
    echo [SUCCESS] Startup shortcut created.
    echo The app will auto-start on next login.
) else (
    echo [ERROR] Failed to create startup shortcut.
    echo Try running as Administrator.
)

echo.
echo To manually add to startup:
echo  Copy startup.bat to: %STARTUP_DIR%
echo.
pause
