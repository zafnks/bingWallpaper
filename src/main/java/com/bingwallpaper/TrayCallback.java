package com.bingwallpaper;

/**
 * Callback interface for WallpaperService to update tray UI state.
 */
public interface TrayCallback {
    /** Enable/disable navigation items based on history position. */
    void updateMenuState(boolean hasPrevious, boolean hasNext);
    /** Show a balloon error notification via the tray icon. */
    void showError(String title, String message);
}
