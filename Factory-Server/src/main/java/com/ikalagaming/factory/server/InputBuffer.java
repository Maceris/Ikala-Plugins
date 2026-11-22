package com.ikalagaming.factory.server;

import com.ikalagaming.factory.sim.PlayerInput;

import lombok.NonNull;

import java.util.Arrays;

/**
 * One player's inputs waiting for the frames they are for. Tick thread only.
 *
 * <ul>
 *   <li>Inputs for frames already run are dropped: the frame used something else.
 *   <li>Inputs more than {@value #MAX_AHEAD} frames ahead are dropped too, so a client running fast
 *       gains nothing: each frame still uses one input.
 *   <li>A frame with no input repeats the last one used, and counts as a miss once inputs have
 *       started arriving.
 * </ul>
 */
final class InputBuffer {

    /** How far ahead of the next frame an input may be. */
    static final int MAX_AHEAD = 32;

    /** Slots; a power of two above {@link #MAX_AHEAD}. */
    private static final int CAPACITY = 64;

    /** No frame. */
    private static final long EMPTY = Long.MIN_VALUE;

    /** The frame each slot holds an input for, or {@link #EMPTY}. */
    private final long[] frames = new long[CAPACITY];

    /** The inputs, by slot. */
    private final PlayerInput[] inputs = new PlayerInput[CAPACITY];

    /** The input the last frame used. */
    private PlayerInput last = PlayerInput.NONE;

    /** Whether any input has arrived yet. */
    private boolean started;

    /** Frames that had no input, since inputs started. */
    private long misses;

    /** An empty buffer. */
    InputBuffer() {
        Arrays.fill(frames, EMPTY);
    }

    /**
     * Take an input from the client.
     *
     * @param next The frame the server runs next.
     * @param frame The frame the input is for.
     * @param input The input, already sanitized.
     * @return True if it was kept, false if it was too late or too far ahead.
     */
    boolean offer(long next, long frame, @NonNull PlayerInput input) {
        if (frame < next || frame >= next + MAX_AHEAD) {
            return false;
        }
        final int slot = slot(frame);
        frames[slot] = frame;
        inputs[slot] = input;
        started = true;
        return true;
    }

    /**
     * The input for a frame that is about to run, which is then used up.
     *
     * @param frame The frame.
     * @return Its input, or the last input used if none arrived in time.
     */
    PlayerInput take(long frame) {
        final int slot = slot(frame);
        if (frames[slot] == frame) {
            last = inputs[slot];
            frames[slot] = EMPTY;
            inputs[slot] = null;
        } else if (started) {
            ++misses;
        }
        return last;
    }

    /**
     * How many inputs are waiting for frames after one.
     *
     * @param after The frame.
     * @return The count.
     */
    int buffered(long after) {
        int count = 0;
        for (long frame = after + 1; frame <= after + MAX_AHEAD; ++frame) {
            if (frames[slot(frame)] == frame) {
                ++count;
            }
        }
        return count;
    }

    /**
     * How many frames ran without an input, since inputs started arriving.
     *
     * @return The count.
     */
    long misses() {
        return misses;
    }

    /**
     * The slot for a frame.
     *
     * @param frame The frame.
     * @return The slot.
     */
    private static int slot(long frame) {
        return (int) (frame & (CAPACITY - 1));
    }
}
