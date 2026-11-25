package com.ikalagaming.factory.sim;

import com.ikalagaming.ecs.ComponentType;

/**
 * How fast the world runs, and how far it has run: which world frames and world ticks each command
 * frame runs. See the Command Frames design doc, "Clocks".
 *
 * <p>Players always step once a command frame. Everything else that moves steps in world frames,
 * the same 16 ms step, of which each command frame runs a whole number: one at the normal rate,
 * none while frozen. World ticks run on world frames, {@value #NORMAL_RATE} to a second of world
 * time: world tick k runs in world frame {@code ceil(k * 25 / 8)}, 8 ticks in every 25 world
 * frames.
 *
 * <p>The clock is simulated state: a pure value that changes only through these methods, so which
 * world frames and ticks ran in a command frame follows from the history alone.
 *
 * @param rate How many world ticks a second of real time, {@value #NORMAL_RATE} normally.
 * @param frozen Whether the world is frozen: no world frames run, except while stepping.
 * @param remainder The rate carried over from earlier command frames, below {@value #NORMAL_RATE}.
 * @param worldFrames How many world frames have run.
 * @param worldTicks How many world ticks have run.
 * @param stepTicks World ticks left to step while frozen, one world frame a command frame.
 * @param sprintTicks World ticks left to sprint through, as fast as the server can.
 */
public record WorldClock(
        int rate,
        boolean frozen,
        int remainder,
        long worldFrames,
        long worldTicks,
        long stepTicks,
        long sprintTicks) {

    /** The world's clock, on the one entity that has it. */
    public static final ComponentType<WorldClock> COMPONENT =
            ComponentType.simulated("lotomation:world_clock", WorldClock.class);

    /** The normal rate: world ticks a second. */
    public static final int NORMAL_RATE = 20;

    /** The fastest rate. */
    public static final int MAX_RATE = 1000;

    /** World frames in each run of {@value #TICKS_PER_RUN} world ticks. */
    static final int FRAMES_PER_RUN = 25;

    /** World ticks in each run of {@value #FRAMES_PER_RUN} world frames. */
    static final int TICKS_PER_RUN = 8;

    /** A world at the normal rate that hasn't run yet. */
    public static final WorldClock START = new WorldClock(NORMAL_RATE, false, 0, 0, 0, 0, 0);

    /**
     * A clock.
     *
     * @throws IllegalArgumentException If the rate is below 1 or above {@value #MAX_RATE}, or a
     *     count is negative.
     */
    public WorldClock {
        if (rate < 1 || rate > MAX_RATE) {
            throw new IllegalArgumentException("The rate must be 1 to " + MAX_RATE + ": " + rate);
        }
        if (remainder < 0
                || remainder >= NORMAL_RATE
                || worldFrames < 0
                || worldTicks < 0
                || stepTicks < 0
                || sprintTicks < 0) {
            throw new IllegalArgumentException("Clock counts can't be negative");
        }
    }

    /**
     * How many world frames the next command frame runs on its schedule. Sprinting runs more, as
     * time allows; see {@link #isSprinting()}.
     *
     * @return The count: 0 or more.
     */
    public int framesDue() {
        if (sprintTicks > 0) {
            return 0;
        }
        if (frozen) {
            return stepTicks > 0 ? 1 : 0;
        }
        return (remainder + rate) / NORMAL_RATE;
    }

    /**
     * The clock once a command frame has taken its world frames from the schedule.
     *
     * @return The clock.
     */
    public WorldClock afterCommandFrame() {
        if (frozen || sprintTicks > 0) {
            return this;
        }
        return new WorldClock(
                rate,
                false,
                (remainder + rate) % NORMAL_RATE,
                worldFrames,
                worldTicks,
                stepTicks,
                sprintTicks);
    }

    /**
     * Whether the next world frame runs a world tick.
     *
     * @return True if it does.
     */
    public boolean tickDue() {
        return ticksBy(worldFrames + 1) > ticksBy(worldFrames);
    }

    /**
     * The clock once a world frame, and its tick if one was due, has run.
     *
     * @return The clock.
     */
    public WorldClock afterWorldFrame() {
        final boolean ticked = tickDue();
        return new WorldClock(
                rate,
                frozen,
                remainder,
                worldFrames + 1,
                ticked ? worldTicks + 1 : worldTicks,
                ticked && stepTicks > 0 ? stepTicks - 1 : stepTicks,
                ticked && sprintTicks > 0 ? sprintTicks - 1 : sprintTicks);
    }

    /**
     * How many world ticks have run by the end of a world frame.
     *
     * @param worldFrame How many world frames have run.
     * @return The ticks: 8 for every 25 world frames.
     */
    public static long ticksBy(long worldFrame) {
        return Math.floorDiv(worldFrame * TICKS_PER_RUN, FRAMES_PER_RUN);
    }

    /**
     * Run at another rate.
     *
     * @param newRate World ticks a second, 1 to {@value #MAX_RATE}.
     * @return The clock.
     * @throws IllegalArgumentException If the rate is out of range.
     */
    public WorldClock withRate(int newRate) {
        return new WorldClock(
                newRate, frozen, remainder, worldFrames, worldTicks, stepTicks, sprintTicks);
    }

    /**
     * Freeze or unfreeze the world. Unfreezing stops any stepping.
     *
     * @param freeze True to freeze.
     * @return The clock.
     */
    public WorldClock withFrozen(boolean freeze) {
        return new WorldClock(
                rate, freeze, remainder, worldFrames, worldTicks, freeze ? stepTicks : 0, 0);
    }

    /**
     * Step a frozen world on by some ticks, one world frame a command frame.
     *
     * @param ticks How many world ticks, added to any still to step.
     * @return The clock, frozen.
     */
    public WorldClock stepping(long ticks) {
        return new WorldClock(rate, true, remainder, worldFrames, worldTicks, stepTicks + ticks, 0);
    }

    /**
     * Run some ticks as fast as the server can.
     *
     * @param ticks How many world ticks; 0 stops sprinting.
     * @return The clock.
     */
    public WorldClock sprinting(long ticks) {
        return new WorldClock(rate, frozen, remainder, worldFrames, worldTicks, 0, ticks);
    }

    /**
     * Whether the world is sprinting: running world frames as time allows, not on the schedule.
     *
     * @return True while sprinting.
     */
    public boolean isSprinting() {
        return sprintTicks > 0;
    }

    /**
     * How many world ticks a second the world runs now, for clients to move their clock by.
     *
     * @return The rate, or 0 while frozen or sprinting, when the clock moves in jumps.
     */
    public int effectiveRate() {
        return frozen || sprintTicks > 0 ? 0 : rate;
    }
}
