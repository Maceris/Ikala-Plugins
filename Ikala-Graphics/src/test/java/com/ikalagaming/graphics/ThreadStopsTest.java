package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tests for stopping threads and executors and waiting for them. */
class ThreadStopsTest {

    /** Long enough for an interrupted task to end on a busy machine. */
    private static final Duration WAIT = Duration.ofSeconds(5);

    /** Short, for waits that are meant to run out. */
    private static final Duration SHORT = Duration.ofMillis(50);

    /** What the tests call what they stop. */
    private static final String WHAT = "test worker";

    /**
     * A task that waits until interrupted, and says when it started.
     *
     * @param started Counted down once it runs.
     * @param finished Set once it ends.
     * @return The task.
     */
    private static Runnable sleeper(CountDownLatch started, AtomicBoolean finished) {
        return () -> {
            started.countDown();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finished.set(true);
            }
        };
    }

    @Test
    void stopWaitsForTheRunningTask() throws InterruptedException {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicBoolean finished = new AtomicBoolean();
        executor.execute(sleeper(started, finished));
        assertTrue(started.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

        assertTrue(ThreadStops.stop(executor, WAIT, WHAT));
        assertTrue(executor.isTerminated());
        // The task ended before stop returned, so whatever it wrote can be freed now
        assertTrue(finished.get());
    }

    @Test
    void stopSaysWhenTheWaitRanOut() throws InterruptedException {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        executor.execute(
                () -> {
                    started.countDown();
                    // Ignores interrupts until released
                    while (true) {
                        try {
                            release.await();
                            return;
                        } catch (InterruptedException e) {
                            // Keep waiting
                        }
                    }
                });
        assertTrue(started.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

        assertFalse(ThreadStops.stop(executor, SHORT, WHAT));
        release.countDown();
        assertTrue(executor.awaitTermination(WAIT.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Test
    void joinInterruptsAndWaits() throws InterruptedException {
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicBoolean finished = new AtomicBoolean();
        final Thread thread = new Thread(sleeper(started, finished), WHAT);
        thread.setDaemon(true);
        thread.start();
        assertTrue(started.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));

        assertTrue(ThreadStops.join(thread, WAIT, WHAT));
        assertFalse(thread.isAlive());
        assertTrue(finished.get());
    }

    @Test
    void joinSkipsNoThreadAndItself() {
        assertTrue(ThreadStops.join(null, SHORT, WHAT));
        assertTrue(ThreadStops.join(Thread.currentThread(), SHORT, WHAT));
        // Joining itself must not interrupt it
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void threadsAreNamedForThePlugin() {
        final Thread thread = ThreadStops.thread(WHAT, () -> {});
        assertTrue(thread.getName().startsWith(GraphicsPlugin.PLUGIN_NAME + "/"));
        assertTrue(thread.isDaemon());
    }
}
