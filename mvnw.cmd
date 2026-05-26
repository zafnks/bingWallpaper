@echo off
setlocal enabledelayedexpansion

set APP_BASE_NAME=%~n0
set APP_HOME=%CD%

set MW_REPOURL=
set DOWNLOAD_URL=

if "%MW_REPOURL%"=="" (
    set MW_REPOURL=https://repo.maven.apache.org/maven2
)

set WRAPPER_JAR=%APP_HOME%\.mvn\wrapper\maven-wrapper.jar
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

if exist "%WRAPPER_JAR%" (
    "%JAVA_HOME%/bin/java.exe" -jar "%WRAPPER_JAR%" %*
) else (
    echo.
    echo Error: maven-wrapper.jar not found.
    echo Downloading Maven...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "Invoke-WebRequest -Uri '%MW_REPOURL%/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '%WRAPPER_JAR%'"
    if errorlevel 1 (
        echo Failed to download maven-wrapper.jar
        exit /b 1
    )
    "%JAVA_HOME%/bin/java.exe" -jar "%WRAPPER_JAR%" %*
)
endlocal
