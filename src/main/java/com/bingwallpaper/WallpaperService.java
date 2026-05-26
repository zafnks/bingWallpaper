package com.bingwallpaper;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Orchestrates fetching, history, wallpaper application, and scheduling. The core service layer. */
public class WallpaperService {
    private static final Logger LOG = Logger.getLogger(WallpaperService.class.getName());

    private final WallpaperHistory history;
    private final BingFetcher fetcher;
    private final WallpaperChanger changer;
    private final SchedulerService scheduler;
    private final Settings settings;
    private TrayCallback tray;
    private HistoryWindow historyWindow;

    public WallpaperService() {
        this.settings = Settings.load();
        this.history = new WallpaperHistory();
        this.fetcher = new BingFetcher();
        this.changer = new WallpaperChanger();
        this.scheduler = new SchedulerService();
    }

    public Settings getSettings() { return settings; }

    /** Register the tray callback for UI notifications. */
    public void setTrayCallback(TrayCallback tray) {
        this.tray = tray;
    }

    /** Start initial fetch and scheduler. */
    public void start() {
        Thread initThread = new Thread(this::initialFetchWithRetry, "initial-fetch");
        initThread.setDaemon(false);
        initThread.start();

        applySchedule();

        // Clean old wallpapers on startup
        if (settings.getRetentionDays() > 0) {
            cleanOldWallpapers(settings.getRetentionDays());
        }
    }

    // ── Fetch ──

    /** Trigger an immediate wallpaper fetch and apply. */
    public void fetchNow() {
        fetchAndSetWallpaper();
    }

    // ── Navigation ──

    /** Switch to the previous wallpaper in history. */
    public void navigatePrevious() {
        Path prev = history.previous();
        if (prev != null) {
            changer.setWallpaper(prev.toAbsolutePath().toString());
            notifyStateChanged();
        }
    }

    /** Switch to the next wallpaper in history. */
    public void navigateNext() {
        Path next = history.next();
        if (next != null) {
            changer.setWallpaper(next.toAbsolutePath().toString());
            notifyStateChanged();
        }
    }

    /** Apply the wallpaper at a specific history index. Called from HistoryWindow. */
    public void navigateTo(int index) {
        List<String> all = history.getAllPaths();
        if (index >= 0 && index < all.size()) {
            Path p = Paths.get(all.get(index));
            if (p.toFile().exists()) {
                history.goTo(index);
                changer.setWallpaper(p.toAbsolutePath().toString());
                notifyStateChanged();
                return;
            }
        }
        // Selected file deleted or index invalid — repair and sync tray state
        int removed = history.repair();
        if (removed > 0) {
            LOG.info("Repaired " + removed + " missing history entries");
        }
        notifyStateChanged();
    }

    // ── History window ──

    /** Open or bring to front the history browsing window. */
    public void openHistoryWindow() {
        // Repair before showing thumbnails so missing files don't appear
        history.repair();

        if (historyWindow != null && historyWindow.isDisplayable()) {
            historyWindow.refresh(history.getAllPaths(), history.getCurrentIndex());
            historyWindow.toFront();
            return;
        }
        EventQueue.invokeLater(() -> {
            historyWindow = new HistoryWindow(
                    history.getAllPaths(),
                    history.getCurrentIndex(),
                    this::navigateTo
            );
            historyWindow.setVisible(true);
        });
    }

    // ── Schedule ──

    /** Re-apply the schedule from current settings. */
    public void applySchedule() {
        if (!settings.isScheduledEnabled()) {
            scheduler.cancel();
            return;
        }
        scheduler.schedule(
                this::fetchAndSetWallpaper,
                settings.getIntervalHours(),
                settings.isDailyMode(),
                settings.getDailyHour()
        );
    }

    /** Update scheduling mode and persist. */
    public void changeSchedule(int intervalHours, boolean dailyMode, int dailyHour) {
        if (intervalHours == 0 && !dailyMode && dailyHour == 0) {
            settings.setScheduledEnabled(false);
        } else {
            settings.setScheduledEnabled(true);
            settings.setIntervalHours(intervalHours);
            settings.setDailyMode(dailyMode);
            settings.setDailyHour(dailyHour);
        }
        settings.save();
        applySchedule();
    }

    /** Change wallpaper resolution and re-fetch. */
    public void changeResolution(String resolution) {
        settings.setResolution(resolution);
        settings.save();
        fetchNow();
    }

    // ── Auto-start ──

    /** Toggle Windows registry auto-start for this application. */
    public void toggleAutoStart(boolean enable) {
        settings.setAutoStart(enable);
        settings.save();
        try {
            java.net.URI jarUri = App.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            String jarPath = Paths.get(jarUri).toAbsolutePath().toString();
            String javaExe = System.getProperty("java.home") + "\\bin\\javaw.exe";
            if (enable) {
                String cmd = "reg add HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run "
                        + "/v BingWallpaper /d \"" + javaExe + " -jar \\\"" + jarPath + "\\\"\" /f";
                Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", cmd});
                LOG.info("Auto-start enabled: " + jarPath);
            } else {
                String cmd = "reg delete HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run "
                        + "/v BingWallpaper /f";
                Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", cmd});
                LOG.info("Auto-start disabled");
            }
        } catch (Exception e) {
            LOG.warning("Failed to toggle auto-start: " + e.getMessage());
        }
    }

    // ── Cleanup ──

    /** Change the wallpaper retention policy and run cleanup. */
    public void changeRetention(int days) {
        settings.setRetentionDays(days);
        settings.save();
        if (days > 0) {
            cleanOldWallpapers(days);
        }
    }

    /** Delete wallpaper files older than the retention threshold. */
    private void cleanOldWallpapers(int retentionDays) {
        Path dir = history.getWallpaperDir();
        if (!Files.exists(dir)) return;
        long cutoff = System.currentTimeMillis() - retentionDays * 86400000L;
        int deleted = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (p.toString().endsWith(".jpg") && p.toFile().lastModified() < cutoff) {
                    try {
                        Files.delete(p);
                        deleted++;
                    } catch (IOException ex) {
                        LOG.warning("Failed to delete " + p.getFileName());
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("Cleanup scan failed: " + e.getMessage());
        }
        if (deleted > 0) {
            LOG.info("Cleaned " + deleted + " old wallpaper(s)");
        }
    }

    // ── Shutdown ──

    /** Stop the scheduler (graceful shutdown). */
    public void stop() {
        scheduler.stop();
    }

    // ── Private ──

    /** First-run fetch with exponential backoff: 0s, 30s, 2m, 5m, 10m. */
    private void initialFetchWithRetry() {
        int[] delaysSec = {0, 30, 120, 300, 600};
        for (int i = 0; i < delaysSec.length; i++) {
            if (i > 0) {
                try { Thread.sleep(delaysSec[i] * 1000L); } catch (InterruptedException e) { return; }
            }
            try {
                Path imagePath = fetcher.downloadLatestWallpaper(history.getWallpaperDir(), settings.getResolution());
                if (imagePath != null) {
                    history.add(imagePath);
                    changer.setWallpaper(imagePath.toAbsolutePath().toString());
                    notifyStateChanged();
                    return;
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Initial fetch attempt " + (i + 1) + " failed", e);
            }
        }
        if (tray != null) {
            tray.showError("壁纸失败", "多次获取壁纸失败，请检查网络");
        }
    }

    /** Fetch today's wallpaper. Auto-applies only if user is at the latest entry. */
    private void fetchAndSetWallpaper() {
        try {
            Path imagePath = fetcher.downloadLatestWallpaper(history.getWallpaperDir(), settings.getResolution());
            if (imagePath != null) {
                boolean wasAtEnd = !history.hasNext();
                history.add(imagePath);
                // Only auto-apply if user was already viewing the latest wallpaper
                if (wasAtEnd) {
                    changer.setWallpaper(imagePath.toAbsolutePath().toString());
                }
                notifyStateChanged();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to fetch wallpaper", e);
            if (tray != null) {
                tray.showError("壁纸失败", e.getMessage());
            }
        }
    }

    /** Push current nav state to the tray UI. */
    private void notifyStateChanged() {
        if (tray != null) {
            tray.updateMenuState(history.hasPrevious(), history.hasNext());
        }
    }
}
