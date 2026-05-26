# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build fat JAR (uses bundled Maven; can also use mvnw)
build.bat                  # Windows — auto-downloads Maven 3.9.6 if missing
./mvnw clean package       # Unix / WSL
mvn clean package          # If Maven is on PATH

# Run
java -jar bing-wallpaper.jar
startup.bat                # Launch minimized via javaw
startup_register.bat       # Register Windows startup shortcut
```

- Output: `target/bing-wallpaper-1.0.0.jar`, copied to `bing-wallpaper.jar`
- Requires: Java 8+, Windows (OS check at startup)
- Dependencies: JNA 5.13.0, JNA Platform 5.13.0, Gson 2.10.1 (all shaded into fat JAR)
- No test suite exists; build skips tests via `-DskipTests`
- App is Chinese-localized: Bing API uses `mkt=zh-CN`, all UI labels are Chinese, copyright text is reversed from small→large to large→small place name convention

## Architecture

Windows-only Swing desktop app that auto-fetches and applies Bing daily wallpapers.

```
App.java (entry point)
 └─ WallpaperService.java (orchestrator)
     ├─ BingFetcher.java        — calls Bing API, downloads, overlays text
     ├─ WallpaperChanger.java   — sets wallpaper via JNA (SystemParametersInfo + fallbacks)
     ├─ WallpaperHistory.java   — in-memory + JSON file history, nav, dedup, repair
     ├─ SchedulerService.java   — ScheduledExecutorService, daily or interval mode
     ├─ Settings.java           — persisted JSON settings
     ├─ TrayCallback.java       — interface: service → tray UI
     ├─ TrayManager.java        — system tray icon + popup menu
     └─ HistoryWindow.java      — thumbnail grid jframe for browsing history
```

### Data files (stored in `~/.bing-wallpaper/`)
- `settings.json` — schedule, resolution, retention, auto-start
- `history.json` — ordered wallpaper path list + current index
- `wallpapers/` — downloaded JPEGs named `bing_wallpaper_YYYY-MM-DD.jpg`

### Key design points
- **Retry on boot**: initial fetch retries with backoff (0s, 30s, 2m, 5m, 10m) before showing error
- **History repair**: missing files are auto-removed from history during navigation; index adjusts accordingly
- **Text overlay**: Bing copyright description is rendered in a frosted-glass panel (fast blur via downscale/upscale)
- **Wallpaper fallback chain**: JNA SystemParametersInfoW → SystemParametersInfoA → PowerShell
- **Scheduling**: supports daily-at-hour mode or every-N-hours interval, using `ScheduledExecutorService`
- **Navigation**: tray menu "previous/next" navigates through history; only auto-applies when user is at the latest entry
- **Auto-start**: two mechanisms — `WallpaperService.toggleAutoStart()` writes to `HKCU\...\Run` registry, while `startup_register.bat` creates a Startup folder shortcut pointing to `startup.bat`
- **No external assets**: all tray/menu icons and the app icon are drawn programmatically in `TrayManager`
- **HistoryWindow**: thumbnails displayed newest-first (reverse iteration); current wallpaper highlighted with blue border
- **Logging**: uses `java.util.logging` (JUL) throughout — no external logging framework
