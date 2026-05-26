package com.bingwallpaper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;

/** Ordered history of downloaded wallpapers, persisted as JSON. Supports navigation, dedup, and repair. */
public class WallpaperHistory {
    private static final Logger LOG = Logger.getLogger(WallpaperHistory.class.getName());

    private static final Path DATA_DIR = Paths.get(
            System.getProperty("user.home"), ".bing-wallpaper"
    );
    private static final Path HISTORY_FILE = DATA_DIR.resolve("history.json");
    private static final Path WALLPAPER_DIR = DATA_DIR.resolve("wallpapers");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private List<String> history = new ArrayList<>();
    private int currentIndex = -1;

    /** @return the data directory (~/.bing-wallpaper). */
    public static Path getDataDir() {
        return DATA_DIR;
    }

    public WallpaperHistory() {
        load();
    }

    /** @return the wallpaper storage directory (~/.bing-wallpaper/wallpapers). */
    public Path getWallpaperDir() {
        return WALLPAPER_DIR;
    }

    /** @return a copy of all history paths. */
    public synchronized List<String> getAllPaths() {
        return new ArrayList<>(history);
    }

    public synchronized int getCurrentIndex() {
        return currentIndex;
    }

    /** Jump to a specific history index. */
    public synchronized void goTo(int index) {
        if (index >= 0 && index < history.size()) {
            currentIndex = index;
            save();
        }
    }

    /** Add a path to history, moving to it (no duplicates). */
    public synchronized void add(Path imagePath) {
        String path = imagePath.toAbsolutePath().normalize().toString();
        int existing = history.indexOf(path);
        if (existing >= 0) {
            // Path already in history → just move index to it (no duplicate)
            currentIndex = existing;
            save();
            return;
        }
        history.add(path);
        currentIndex = history.size() - 1;
        save();
    }

    /** Navigate to the previous existing entry, repairing as needed. */
    public synchronized Path previous() {
        repair();
        while (currentIndex > 0) {
            currentIndex--;
            Path p = Paths.get(history.get(currentIndex));
            if (p.toFile().exists()) return p;
            // File missing — remove entry and keep going
            history.remove(currentIndex);
        }
        // All preceding entries were missing; save the cleaned state
        save();
        return null;
    }

    /** Navigate to the next existing entry, repairing as needed. */
    public synchronized Path next() {
        repair();
        while (currentIndex < history.size() - 1) {
            currentIndex++;
            Path p = Paths.get(history.get(currentIndex));
            if (p.toFile().exists()) return p;
            // File missing — remove entry and keep going
            history.remove(currentIndex);
            currentIndex--; // size just shrank, rewind one
        }
        // All following entries were missing; save the cleaned state
        save();
        return null;
    }

    public synchronized boolean hasPrevious() {
        return currentIndex > 0;
    }

    public synchronized boolean hasNext() {
        return currentIndex < history.size() - 1;
    }

    /** Write history to disk. */
    private void save() {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }
            HistoryData data = new HistoryData(history, currentIndex);
            String json = gson.toJson(data);
            Files.write(HISTORY_FILE, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warning("Failed to save history: " + e.getMessage());
        }
    }

    /** Load history from disk, repairing on first load. */
    private void load() {
        if (Files.exists(HISTORY_FILE)) {
            try {
                byte[] bytes = Files.readAllBytes(HISTORY_FILE);
                String json = new String(bytes, StandardCharsets.UTF_8);
                HistoryData data = gson.fromJson(json, HistoryData.class);
                if (data != null && data.history != null) {
                    history = new ArrayList<>(data.history);
                    currentIndex = data.currentIndex;
                    // Dedup and remove missing files, tracking index shift
                    repair();
                }
            } catch (IOException e) {
                LOG.warning("Failed to load history: " + e.getMessage());
            }
        }
    }

    /**
     * Remove entries whose files no longer exist, adjusting currentIndex
     * accordingly. Safe to call any time.
     * @return number of entries removed
     */
    public synchronized int repair() {
        String currentPath = (currentIndex >= 0 && currentIndex < history.size())
                ? history.get(currentIndex) : null;

        int removedBefore = 0;
        int totalRemoved = 0;
        List<String> valid = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            String path = history.get(i);
            if (Files.exists(Paths.get(path))) {
                valid.add(path);
            } else {
                totalRemoved++;
                if (i < currentIndex) {
                    removedBefore++;
                }
            }
        }
        // Dedup while preserving order
        LinkedHashSet<String> deduped = new LinkedHashSet<>(valid);
        boolean hadDupes = deduped.size() != valid.size();

        if (totalRemoved == 0 && !hadDupes) {
            return 0;
        }

        if (hadDupes) {
            // Recalculate removedBefore from dedup perspective
            history = new ArrayList<>(deduped);
            valid = new ArrayList<>(deduped);

            // Track how many entries before currentIndex were removed by dedup
            int dedupRemovedBefore = 0;
            if (currentPath != null) {
                // Find the new index of the current wallpaper
                int newIdx = history.indexOf(currentPath);
                if (newIdx >= 0) {
                    removedBefore = currentIndex - newIdx;
                } else {
                    // Current wallpaper was itself removed (file deleted)
                    removedBefore = currentIndex;
                }
            }
            currentIndex -= removedBefore;
        } else {
            history = valid;
            currentIndex -= removedBefore;
        }

        if (currentIndex >= history.size()) {
            currentIndex = history.size() - 1;
        }
        if (history.isEmpty()) {
            currentIndex = -1;
        }
        save();
        return totalRemoved;
    }

    private static class HistoryData {
        List<String> history;
        int currentIndex;

        HistoryData() {}

        HistoryData(List<String> history, int currentIndex) {
            this.history = history;
            this.currentIndex = currentIndex;
        }
    }
}
