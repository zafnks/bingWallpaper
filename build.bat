@echo off
setlocal enabledelayedexpansion
title Bing Wallpaper Build

cd /d "%~dp0"
set "ROOT_DIR=%CD%"
set "MAVEN_VERSION=3.9.6"
set "MAVEN_HOME=%ROOT_DIR%\.mvn\apache-maven-%MAVEN_VERSION%"
set "MAVEN_ZIP=%ROOT_DIR%\.mvn\apache-maven-%MAVEN_VERSION%-bin.zip"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"

:: ---- Step 1: Check/Download Maven ----
if not exist "%MAVEN_CMD%" (
    echo [INFO] Maven %MAVEN_VERSION% not found locally, downloading...
    if not exist "%MAVEN_ZIP%" (
        powershell -NoProfile -Command ^
            "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%MAVEN_ZIP%'" 2>nul
        if !errorlevel! neq 0 (
            echo [ERROR] Failed to download Maven. Please check your internet connection.
            pause
            exit /b 1
        )
    )
    echo [INFO] Extracting Maven...
    powershell -NoProfile -Command ^
        "Expand-Archive -Path '%MAVEN_ZIP%' -DestinationPath '%ROOT_DIR%\.mvn\' -Force" 2>nul
    if !errorlevel! neq 0 (
        echo [ERROR] Failed to extract Maven.
        pause
        exit /b 1
    )
    echo [INFO] Maven extracted successfully.
) else (
    echo [INFO] Maven found locally.
)

:: ---- Step 2: Build ----
echo.
echo ==========================================
echo  Building Bing Wallpaper Auto-Changer
echo ==========================================
echo.

"%MAVEN_CMD%" clean package -DskipTests

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

:: ---- Step 3: Done ----
echo [SUCCESS] JAR written to bing-wallpaper.jar

echo.
echo ==========================================
echo  Build successful!
echo  JAR: bing-wallpaper.jar
echo ==========================================
echo.
echo Quick start:
echo   java -jar bing-wallpaper.jar
echo.
echo Register startup:
echo   double-click startup_register.bat
echo.
pause
