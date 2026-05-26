package com.bingwallpaper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Manages periodic wallpaper fetching via ScheduledExecutorService. */
public class SchedulerService {
    private static final Logger LOG = Logger.getLogger(SchedulerService.class.getName());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "wallpaper-scheduler");
        t.setDaemon(false);
        return t;
    });

    private ScheduledFuture<?> future;

    /** Reschedule with the given strategy. Cancel any existing schedule. */
    public synchronized void schedule(Runnable task, int intervalHours, boolean dailyMode, int dailyHour) {
        cancel();
        Runnable wrapped = () -> {
            try {
                task.run();
            } catch (Exception e) {
                LOG.warning("Scheduled task failed: " + e.getMessage());
            }
        };
        if (dailyMode) {
            long delay = computeDelayToHour(dailyHour);
            future = scheduler.scheduleAtFixedRate(wrapped, Math.max(delay, 1), 24 * 60, TimeUnit.MINUTES);
            LOG.info("Scheduled daily at " + dailyHour + ":00, first in " + delay + " min");
        } else {
            int period = Math.max(intervalHours, 1) * 60;
            future = scheduler.scheduleAtFixedRate(wrapped, 1, period, TimeUnit.MINUTES);
            LOG.info("Scheduled every " + intervalHours + " hours");
        }
    }

    /** Compute minutes until the next occurrence of a given hour today (or tomorrow). */
    private static long computeDelayToHour(int hour) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.with(LocalTime.of(hour, 0));
        if (now.isAfter(target)) {
            target = target.plusDays(1);
        }
        return Duration.between(now, target).toMinutes();
    }

    /** Cancel the current schedule without shutting down the executor. */
    public synchronized void cancel() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    /** Shut down the scheduler thread. */
    public void stop() {
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warning("Scheduler did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
