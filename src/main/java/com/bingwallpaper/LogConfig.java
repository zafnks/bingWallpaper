package com.bingwallpaper;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Configures file-based logging with 7-day retention. Called once at startup. */
public class LogConfig {

    private static final Path LOG_DIR = Paths.get(
            System.getProperty("user.home"), ".bing-wallpaper", "logs"
    );
    private static final int RETENTION_DAYS = 7;
    private static final int MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB per file
    private static final int MAX_FILE_COUNT = 5;

    private static volatile boolean initialized = false;

    /** Initialize logging. Safe to call multiple times — only the first call takes effect. */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            Files.createDirectories(LOG_DIR);
            cleanOldLogs();
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }

        Logger rootLogger = Logger.getLogger("");

        // Remove default handlers to avoid duplicate console output
        for (Handler h : rootLogger.getHandlers()) {
            rootLogger.removeHandler(h);
        }

        // Console handler — keep stderr output for interactive debugging
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new CompactFormatter());
        rootLogger.addHandler(consoleHandler);

        // File handler — persistent log on disk
        try {
            Path logFile = LOG_DIR.resolve("bingwallpaper.log");
            FileHandler fileHandler = new FileHandler(
                    logFile.toString(),
                    MAX_FILE_SIZE,
                    MAX_FILE_COUNT,
                    true // append
            );
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(new CompactFormatter());
            rootLogger.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("Failed to create file log handler: " + e.getMessage());
        }

        // Also set our own logger level
        Logger.getLogger("com.bingwallpaper").setLevel(Level.ALL);

        // Quiet noisy third-party loggers
        Logger.getLogger("sun").setLevel(Level.WARNING);
        Logger.getLogger("com.sun").setLevel(Level.WARNING);
        Logger.getLogger("java.awt").setLevel(Level.WARNING);
    }

    /** Remove log files older than {@value #RETENTION_DAYS} days. */
    static void cleanOldLogs() {
        Instant cutoff = LocalDate.now()
                .minusDays(RETENTION_DAYS)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        try (Stream<Path> files = Files.list(LOG_DIR)) {
            files.filter(p -> p.getFileName().toString().endsWith(".log")
                           || p.getFileName().toString().endsWith(".lck"))
                 .forEach(p -> {
                     try {
                         BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                         Instant fileTime = attrs.lastModifiedTime().toInstant();
                         if (fileTime.isBefore(cutoff)) {
                             Files.deleteIfExists(p);
                         }
                     } catch (IOException ignored) {
                         // skip files we can't read
                     }
                 });
        } catch (IOException ignored) {
            // directory listing failed — skip cleanup this run
        }
    }

    /**
     * Compact single-line log formatter: {@code [2026-06-08 20:30:45] [INFO] [WallpaperService] message}.
     * Exceptions get their stack trace appended on following lines.
     */
    private static class CompactFormatter extends Formatter {

        private static final DateTimeFormatter TS =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder(256);
            sb.append('[');
            sb.append(TS.format(Instant.ofEpochMilli(record.getMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()));
            sb.append("] [");
            sb.append(record.getLevel().getName());
            sb.append("] [");
            String name = record.getLoggerName();
            // Shorten "com.bingwallpaper.Xxx" → "Xxx"
            int dot = name.lastIndexOf('.');
            sb.append(dot >= 0 ? name.substring(dot + 1) : name);
            sb.append("] ");
            sb.append(formatMessage(record));
            sb.append('\n');

            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                sb.append(sw.toString());
            }
            return sb.toString();
        }
    }
}
