package com.ikalagaming.factory.client;

import com.ikalagaming.factory.world.WorldTime;

/**
 * The world's clock on the client: the last time the server sent, moved on by how long ago that
 * was, so the sky moves smoothly between updates. Main thread only.
 */
public final class ClientClock {

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** The time the server last sent, in ticks. */
    private long ticks = WorldTime.START_TICKS;

    /** How many ticks pass each second. */
    private int ticksPerSecond;

    /** When the server's time arrived, from {@link System#nanoTime()}. */
    private long receivedNanos = System.nanoTime();

    /** Whether the server has sent the time yet. */
    private boolean known;

    /**
     * Take the server's time.
     *
     * @param newTicks The time, in ticks.
     * @param newTicksPerSecond How many ticks pass each second.
     */
    void set(long newTicks, int newTicksPerSecond) {
        ticks = newTicks;
        ticksPerSecond = newTicksPerSecond;
        receivedNanos = System.nanoTime();
        known = true;
    }

    /**
     * Whether the server has sent the time yet.
     *
     * @return True once it has.
     */
    public boolean isKnown() {
        return known;
    }

    /**
     * The time now.
     *
     * @return The time, in ticks with a fraction; see {@link WorldTime}.
     */
    public double now() {
        final double seconds = (System.nanoTime() - receivedNanos) / NANOS_PER_SECOND;
        return ticks + seconds * ticksPerSecond;
    }
}
