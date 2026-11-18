package com.ikalagaming.graphics;

import com.ikalagaming.plugins.PluginThreads;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Stopping the graphics plugin's own threads and executors, and waiting a bounded time for them to
 * finish, so whatever their tasks write can be freed afterward. Executors and threads made here are
 * also handed to {@link PluginThreads} under {@link GraphicsPlugin#PLUGIN_NAME}, the backstop that
 * stops them when the plugin unloads.
 */
@Slf4j
public final class ThreadStops {
    /**
     * How long a close waits for workers to finish their task by default: building a cell or baking
     * a section takes well under a second, and an interrupted one stops at its next check.
     */
    public static final Duration STOP_WAIT = Duration.ofSeconds(5);

    /** Logged when something didn't stop in time. */
    private static final String LATE = "The {} did not stop within {} ms";

    /** Logged when waiting for something to stop was interrupted. */
    private static final String INTERRUPTED = "Interrupted while waiting for the {} to stop";

    /**
     * Have an executor stopped when the graphics plugin unloads, for one made with a factory of its
     * own, such as one giving its threads a low priority.
     *
     * @param <E> The kind of executor.
     * @param executor The executor.
     * @return The executor.
     */
    public static <E extends ExecutorService> E track(@NonNull E executor) {
        return PluginThreads.track(GraphicsPlugin.PLUGIN_NAME, executor);
    }

    /**
     * Make a daemon thread owned by the graphics plugin, not yet started.
     *
     * @param name The thread name, after the plugin's.
     * @param task What it runs.
     * @return The thread.
     */
    public static Thread thread(@NonNull String name, @NonNull Runnable task) {
        return PluginThreads.thread(GraphicsPlugin.PLUGIN_NAME, name, task);
    }

    /**
     * Interrupt an executor's tasks, drop the ones queued, and wait for the running ones to end. If
     * this thread is interrupted while waiting, it stops waiting and keeps its interrupt.
     *
     * @param executor The executor.
     * @param wait How long to wait at most.
     * @param what What the executor is, for the log.
     * @return True if it ended in time.
     */
    public static boolean stop(
            @NonNull ExecutorService executor, @NonNull Duration wait, @NonNull String what) {
        executor.shutdownNow();
        try {
            if (executor.awaitTermination(wait.toNanos(), TimeUnit.NANOSECONDS)) {
                return true;
            }
            log.warn(LATE, what, wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(INTERRUPTED, what);
        }
        return false;
    }

    /**
     * Interrupt a thread and wait for it to end. Does nothing for the calling thread itself, which
     * can't wait for its own end. If this thread is interrupted while waiting, it stops waiting and
     * keeps its interrupt.
     *
     * @param thread The thread, or null for none.
     * @param wait How long to wait at most.
     * @param what What the thread is, for the log.
     * @return True if it ended in time, or there was none to wait for.
     */
    public static boolean join(Thread thread, @NonNull Duration wait, @NonNull String what) {
        if (thread == null || thread == Thread.currentThread()) {
            return true;
        }
        thread.interrupt();
        try {
            thread.join(Math.max(1, wait.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(INTERRUPTED, what);
            return !thread.isAlive();
        }
        if (thread.isAlive()) {
            log.warn(LATE, what, wait.toMillis());
            return false;
        }
        return true;
    }

    /** Static helpers only. */
    private ThreadStops() {}
}
