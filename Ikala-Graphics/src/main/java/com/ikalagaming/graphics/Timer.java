package com.ikalagaming.graphics;

import lombok.Getter;

/** A timer for determining time between events. */
public class Timer {

    /**
     * The last time we checked for time.
     *
     * @return The last time we checked for the time. Measured in seconds.
     */
    @Getter private double lastLoopTime;

    /**
     * Get the elapsed time since the last call to this method.
     *
     * @return The time that has passed since we last called this method, in seconds.
     */
    public float getElapsedTime() {
        double time = getTime();
        float elapsedTime = (float) (time - lastLoopTime);
        lastLoopTime = time;
        return elapsedTime;
    }

    /**
     * Get the current system time in seconds.
     *
     * @return The current time.
     */
    public double getTime() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    /** Start tracking the time. */
    public void init() {
        lastLoopTime = getTime();
    }
}
