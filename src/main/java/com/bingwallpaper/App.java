package com.bingwallpaper;

import java.awt.*;

/** Application entry point. Initializes the wallpaper service and system tray. */
public class App {

    /** OS check, then wire up WallpaperService and TrayManager on the EDT. */
    public static void main(String[] args) {
        LogConfig.init();
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            System.err.println("This application only supports Windows.");
            System.exit(1);
        }
        EventQueue.invokeLater(() -> {
            WallpaperService wallpaper = new WallpaperService();
            TrayManager tray = new TrayManager(new TrayManager.MenuActions() {
                @Override public void onPrevious() { wallpaper.navigatePrevious(); }
                @Override public void onNext() { wallpaper.navigateNext(); }
                @Override public void onUpdateNow() { wallpaper.fetchNow(); }
                @Override public void onBrowseHistory() { wallpaper.openHistoryWindow(); }
                @Override public void onExit() { wallpaper.stop(); System.exit(0); }
                @Override
                public void onScheduleChanged(int intervalHours, boolean dailyMode, int dailyHour) {
                    wallpaper.changeSchedule(intervalHours, dailyMode, dailyHour);
                }
                @Override public void onAutoStartToggled(boolean enabled) {
                    wallpaper.toggleAutoStart(enabled);
                }
                @Override public void onRetentionChanged(int days) {
                    wallpaper.changeRetention(days);
                }
                @Override public void onResolutionChanged(String resolution) {
                    wallpaper.changeResolution(resolution);
                }
            });
            tray.applySettings(wallpaper.getSettings());
            wallpaper.setTrayCallback(tray);
            wallpaper.start();
        });
    }
}
