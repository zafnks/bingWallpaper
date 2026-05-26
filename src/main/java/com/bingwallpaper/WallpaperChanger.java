package com.bingwallpaper;

import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;

import java.util.logging.Logger;

/** Sets the Windows desktop wallpaper via JNA, with PowerShell fallback. */
public class WallpaperChanger {
    private static final Logger LOG = Logger.getLogger(WallpaperChanger.class.getName());

    private static final int SPI_SETDESKWALLPAPER = 0x0014;
    private static final int SPIF_UPDATEINIFILE = 0x01;
    private static final int SPIF_SENDCHANGE = 0x02;

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean SystemParametersInfoW(int uiAction, int uiParam, WString pvParam, int fWinIni);
        boolean SystemParametersInfoA(int uiAction, int uiParam, String pvParam, int fWinIni);
    }

    /** Apply the given image as the desktop wallpaper. Tries JNA Wide-char, ANSI, then PowerShell. */
    public void setWallpaper(String imagePath) {
        LOG.info("Setting wallpaper: " + imagePath);

        // Strategy 1: SystemParametersInfoW with WString (correct for Unicode Windows)
        boolean success = User32.INSTANCE.SystemParametersInfoW(
                SPI_SETDESKWALLPAPER,
                0,
                new WString(imagePath),
                SPIF_UPDATEINIFILE | SPIF_SENDCHANGE
        );

        if (success) {
            LOG.info("Wallpaper set successfully via SystemParametersInfoW");
            return;
        }

        // Strategy 2: SystemParametersInfoA (ANSI variant, maps to char*)
        LOG.warning("SystemParametersInfoW failed, trying SystemParametersInfoA...");
        success = User32.INSTANCE.SystemParametersInfoA(
                SPI_SETDESKWALLPAPER,
                0,
                imagePath,
                SPIF_UPDATEINIFILE | SPIF_SENDCHANGE
        );

        if (success) {
            LOG.info("Wallpaper set successfully via SystemParametersInfoA");
            return;
        }

        // Strategy 3: PowerShell fallback
        LOG.warning("SystemParametersInfo failed, trying PowerShell...");
        try {
            setWallpaperViaPowerShell(imagePath);
        } catch (Exception e) {
            LOG.warning("PowerShell fallback failed: " + e.getMessage());
        }
    }

    private void setWallpaperViaPowerShell(String imagePath) throws Exception {
        String escapedPath = imagePath.replace("'", "''");
        String psScript = "Add-Type -TypeDefinition @\"\n" +
            "using System;\n" +
            "using System.Runtime.InteropServices;\n" +
            "public class Wallpaper {\n" +
            "    [DllImport(\"user32.dll\", CharSet = CharSet.Auto)]\n" +
            "    public static extern int SystemParametersInfo(int uAction, int uParam, string lpvParam, int fuWinIni);\n" +
            "}\n" +
            "\"@\n" +
            "[Wallpaper]::SystemParametersInfo(" + SPI_SETDESKWALLPAPER + ", 0, '" + escapedPath + "', " +
            (SPIF_UPDATEINIFILE | SPIF_SENDCHANGE) + ")";

        Process process = Runtime.getRuntime().exec(new String[]{
            "powershell.exe", "-NoProfile", "-Command", psScript
        });
        int exitCode = process.waitFor();
        LOG.info("PowerShell exit code: " + exitCode);
    }
}
