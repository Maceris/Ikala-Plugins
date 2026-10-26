package com.ikalagaming.graphics.scene;

import java.util.concurrent.locks.ReentrantLock;

/**
 * The scene's particle time: whether particles move, how fast, single steps while paused, and
 * clearing every particle, for looking at effects closely in an editor. Also what the GPU last
 * reported, for showing. Changed from any thread; the render thread reads it once a frame.
 */
public class ParticleClock {

    /** How long a single step is, in seconds: a frame at 60 frames a second. */
    public static final double STEP_SECONDS = 1.0 / 60;

    /** The longest frame particles move through at once, so a stall doesn't fling them. */
    public static final double MAX_FRAME_SECONDS = 0.1;

    /** Guards everything here. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Whether particles stand still. */
    private boolean paused;

    /** How fast particle time runs against real time. */
    private double timeScale = 1;

    /** Steps asked for while paused, not yet taken. */
    private int pendingSteps;

    /** Whether every particle is to be removed on the next frame. */
    private boolean clearPending;

    /**
     * Whether time moves {@link #STEP_SECONDS} a frame however long frames take, so a run repeats
     * exactly, for measuring.
     */
    private boolean fixedStep;

    /** How many particles were alive in the last frame the GPU reported. */
    private volatile int liveParticles;

    /** How many spawns in that frame found the pool full. */
    private volatile int droppedSpawns;

    /**
     * Stop or start particle time. Paused particles still draw.
     *
     * @param paused True to stop it.
     */
    public void setPaused(boolean paused) {
        lock.lock();
        try {
            this.paused = paused;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether particle time is stopped.
     *
     * @return True if it is.
     */
    public boolean isPaused() {
        lock.lock();
        try {
            return paused;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set how fast particle time runs.
     *
     * @param scale 1 for real time, 0.5 for half speed, and so on; negative counts as 0.
     */
    public void setTimeScale(double scale) {
        lock.lock();
        try {
            timeScale = Math.max(0, scale);
        } finally {
            lock.unlock();
        }
    }

    /**
     * How fast particle time runs against real time.
     *
     * @return The scale.
     */
    public double getTimeScale() {
        lock.lock();
        try {
            return timeScale;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Move time {@link #STEP_SECONDS} a frame however long frames take, so the same emitters spawn
     * the same particles each run, for measuring; or with the frames' own time again.
     *
     * @param fixed True for fixed steps.
     */
    public void setFixedStep(boolean fixed) {
        lock.lock();
        try {
            fixedStep = fixed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether time moves fixed steps.
     *
     * @return True if it does.
     */
    public boolean isFixedStep() {
        lock.lock();
        try {
            return fixedStep;
        } finally {
            lock.unlock();
        }
    }

    /** Move particles one {@link #STEP_SECONDS} step on the next frame, even while paused. */
    public void step() {
        lock.lock();
        try {
            ++pendingSteps;
        } finally {
            lock.unlock();
        }
    }

    /** Remove every particle on the next frame. Emitters stay and spawn again. */
    public void clear() {
        lock.lock();
        try {
            clearPending = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How far particles move this frame. Render thread only.
     *
     * @param realSeconds How long the frame took.
     * @return How long particle time moves, in seconds.
     */
    public double advance(double realSeconds) {
        lock.lock();
        try {
            if (!paused) {
                final double frame =
                        fixedStep
                                ? STEP_SECONDS
                                : Math.min(Math.max(0, realSeconds), MAX_FRAME_SECONDS);
                return frame * timeScale;
            }
            if (pendingSteps > 0) {
                --pendingSteps;
                return STEP_SECONDS;
            }
            return 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a clear was asked for, forgetting it. Render thread only.
     *
     * @return True if every particle is to go this frame.
     */
    public boolean takeClear() {
        lock.lock();
        try {
            final boolean clear = clearPending;
            clearPending = false;
            return clear;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Note what the GPU reported for a frame. Render thread only.
     *
     * @param live How many particles were alive.
     * @param dropped How many spawns found the pool full.
     */
    public void report(int live, int dropped) {
        liveParticles = live;
        droppedSpawns = dropped;
    }

    /**
     * How many particles were alive in the last frame the GPU reported, a frame or two ago.
     *
     * @return The count.
     */
    public int getLiveParticles() {
        return liveParticles;
    }

    /**
     * How many spawns found the pool full in that frame.
     *
     * @return The count.
     */
    public int getDroppedSpawns() {
        return droppedSpawns;
    }
}
