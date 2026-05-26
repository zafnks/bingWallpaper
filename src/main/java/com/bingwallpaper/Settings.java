package com.bingwallpaper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/** Persisted application settings backed by a JSON file. */
public class Settings {
    private static final Logger LOG = Logger.getLogger(Settings.class.getName());
    private static final Path FILE = WallpaperHistory.getDataDir().resolve("settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int intervalHours = 24;   // valid when dailyMode=false
    private int dailyHour = 8;        // valid when dailyMode=true
    private boolean dailyMode = true; // true=daily at dailyHour, false=every intervalHours
    private boolean scheduledEnabled = true;
    private String resolution = "4k";  // 1080p, 2k, 4k
    private int retentionDays = 30;
    private boolean autoStart;

    public int getIntervalHours() { return intervalHours; }
    public int getDailyHour() { return dailyHour; }
    public boolean isDailyMode() { return dailyMode; }
    public boolean isScheduledEnabled() { return scheduledEnabled; }
    public String getResolution() { return resolution; }
    public int getRetentionDays() { return retentionDays; }
    public boolean isAutoStart() { return autoStart; }

    public void setIntervalHours(int h) { intervalHours = h; }
    public void setDailyHour(int h) { dailyHour = h; }
    public void setDailyMode(boolean m) { dailyMode = m; }
    public void setScheduledEnabled(boolean e) { scheduledEnabled = e; }
    public void setResolution(String r) { resolution = r; }
    public void setRetentionDays(int d) { retentionDays = d; }
    public void setAutoStart(boolean b) { autoStart = b; }

    /** Write current settings to disk. */
    public synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warning("Failed to save settings: " + e.getMessage());
        }
    }

    /** Load settings from disk, falling back to defaults. */
    public static Settings load() {
        if (Files.exists(FILE)) {
            try {
                byte[] bytes = Files.readAllBytes(FILE);
                Settings s = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Settings.class);
                if (s != null) return s;
            } catch (Exception e) {
                LOG.warning("Failed to load settings: " + e.getMessage());
            }
        }
        return new Settings();
    }

    /** Human-readable schedule description. */
    public String describeSchedule() {
        if (!scheduledEnabled) return "不自动切换";
        if (dailyMode) {
            return "每天 " + dailyHour + ":00";
        } else {
            return "每 " + intervalHours + " 小时";
        }
    }
}
