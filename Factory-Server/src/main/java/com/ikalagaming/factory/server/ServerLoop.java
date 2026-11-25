package com.ikalagaming.factory.server;

import com.ikalagaming.factory.sim.Frames;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * The server's thread: runs a command frame every {@value Frames#FRAME_MILLIS} ms, on one thread so
 * the world never changes under a frame. World frames and world ticks run inside command frames, on
 * the world's own clock (see {@code WorldClock}).
 *
 * <ul>
 *   <li><b>Catching up:</b> frames that fall behind run back to back, up to {@value #MAX_CATCH_UP};
 *       anything further behind is dropped and logged.
 *   <li><b>Waiting:</b> the loop parks until the next deadline. Parking on Windows wakes about 2 to
 *       3.5 ms late, so steps start that late, but deadlines stay on the clock, so the rates are
 *       exact. A late frame never starves a client: its input has had longer to arrive.
 *   <li><b>Precise waiting</b> ({@code -Dikala.server.preciseFrames=true}, for dedicated servers):
 *       park until a margin before the deadline and yield for the rest, so steps start within tens
 *       of microseconds. The margin follows the lateness the loop actually sees. It costs about a
 *       third of a core.
 * </ul>
 */
@Slf4j
final class ServerLoop implements Runnable {

    /** The most frames run back to back to catch up. */
    static final int MAX_CATCH_UP = 5;

    /** The margin before a deadline to stop parking at, before the loop has seen any lateness. */
    private static final long START_MARGIN = TimeUnit.MILLISECONDS.toNanos(4);

    /** The smallest margin. */
    private static final long MIN_MARGIN = TimeUnit.MICROSECONDS.toNanos(250);

    /** The largest margin. */
    private static final long MAX_MARGIN = TimeUnit.MILLISECONDS.toNanos(6);

    /** Added to the lateness seen to make the margin. */
    private static final long SLACK = TimeUnit.MICROSECONDS.toNanos(250);

    /** How much the margin shrinks each time parking wakes early enough. */
    private static final long SHRINK = TimeUnit.MICROSECONDS.toNanos(10);

    /** Runs a command frame. */
    private final Runnable frame;

    /** When the next frame is due, from {@link System#nanoTime()}. */
    private long nextFrame;

    /** The system property that turns on precise waiting. */
    static final String PRECISE_PROPERTY = "ikala.server.preciseFrames";

    /** Whether to yield until each deadline rather than only parking. */
    private final boolean precise;

    /** How long before a deadline parking stops, when precise. */
    private long margin = START_MARGIN;

    /** Frames dropped because the loop fell too far behind. */
    private volatile long droppedFrames;

    /** Set to end the loop. */
    private volatile boolean stopped;

    /** Run while paused, so posted work like a join still happens. */
    private volatile Runnable whilePaused = () -> {};

    /** Whether pausing is allowed right now; checked while paused, so it can end a pause. */
    private volatile BooleanSupplier pauseAllowed = () -> true;

    /** The thread running the loop, to wake from a pause. */
    private volatile Thread owner;

    /** Set to stop running frames until cleared, as while single player is paused. */
    private volatile boolean paused;

    /** How long a paused loop parks between checks. */
    private static final long PAUSED_PARK_NANOS = TimeUnit.MILLISECONDS.toNanos(5);

    /**
     * A loop.
     *
     * @param frame Runs a command frame; it should not throw.
     * @param start When the first frame is due, from {@link System#nanoTime()}.
     * @param precise Whether to yield until each deadline rather than only parking.
     */
    ServerLoop(@NonNull Runnable frame, long start, boolean precise) {
        this.frame = frame;
        this.precise = precise;
        nextFrame = start;
    }

    @Override
    public void run() {
        owner = Thread.currentThread();
        while (!stopped && !Thread.currentThread().isInterrupted()) {
            if (paused) {
                waitWhilePaused();
                continue;
            }
            waitUntil(runDue(System.nanoTime()));
        }
    }

    /**
     * Stop or restart running frames. Paused time is skipped, not caught up on, so the frame
     * numbers carry on from where they stopped.
     *
     * @param pause True to pause, false to carry on.
     */
    void setPaused(boolean pause) {
        paused = pause;
        if (!pause) {
            LockSupport.unpark(owner);
        }
    }

    /**
     * What a paused loop does.
     *
     * @param idle Run every few milliseconds while paused; it should not throw.
     * @param allowed Whether the loop may stay paused; once false, the pause ends.
     */
    void whilePaused(@NonNull Runnable idle, @NonNull BooleanSupplier allowed) {
        whilePaused = idle;
        pauseAllowed = allowed;
    }

    /**
     * Whether the loop is paused.
     *
     * @return True while paused.
     */
    boolean isPaused() {
        return paused;
    }

    /** Park until unpaused or stopped, then move the deadlines past the time spent. */
    private void waitWhilePaused() {
        final long start = System.nanoTime();
        while (paused && !stopped && !Thread.currentThread().isInterrupted()) {
            whilePaused.run();
            if (!pauseAllowed.getAsBoolean()) {
                paused = false;
                log.info("Unpaused, since another player joined");
                break;
            }
            LockSupport.parkNanos(PAUSED_PARK_NANOS);
        }
        shift(System.nanoTime() - start);
    }

    /**
     * Move the deadline later, as after a pause.
     *
     * @param nanos How far.
     */
    void shift(long nanos) {
        nextFrame += nanos;
    }

    /** End the loop after the step it is on. */
    void stop() {
        stopped = true;
    }

    /**
     * Run every frame that is due.
     *
     * @param now The time, from {@link System#nanoTime()}.
     * @return When the next frame is due.
     */
    long runDue(long now) {
        int run = 0;
        while (now - nextFrame >= 0 && run < MAX_CATCH_UP) {
            frame.run();
            nextFrame += Frames.FRAME_NANOS;
            ++run;
        }
        if (now - nextFrame >= 0) {
            final long behind = (now - nextFrame) / Frames.FRAME_NANOS + 1;
            nextFrame += behind * Frames.FRAME_NANOS;
            droppedFrames += behind;
            log.warn("The server fell behind and skipped {} command frames", behind);
        }
        return nextFrame;
    }

    /**
     * Wait until a deadline: park until it, or when precise until the margin before it, then yield.
     *
     * @param deadline The deadline, from {@link System#nanoTime()}.
     */
    private void waitUntil(long deadline) {
        if (!precise) {
            long remaining = deadline - System.nanoTime();
            while (remaining > 0 && !stopped) {
                LockSupport.parkNanos(remaining);
                remaining = deadline - System.nanoTime();
            }
            return;
        }
        final long before = System.nanoTime();
        final long remaining = deadline - before;
        if (remaining > margin) {
            final long ask = remaining - margin;
            LockSupport.parkNanos(ask);
            final long late = System.nanoTime() - before - ask;
            if (late + SLACK > margin) {
                margin = Math.min(MAX_MARGIN, late + SLACK);
            } else {
                margin = Math.max(MIN_MARGIN, margin - SHRINK);
            }
        }
        while (System.nanoTime() - deadline < 0 && !stopped) {
            Thread.yield();
        }
    }

    /**
     * How many frames were dropped because the loop fell too far behind.
     *
     * @return The count.
     */
    long getDroppedFrames() {
        return droppedFrames;
    }
}
