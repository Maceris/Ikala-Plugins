package com.ikalagaming.factory.client;

import com.ikalagaming.factory.sim.Frames;

import lombok.Getter;

/**
 * When the client runs its command frames. The client runs ahead of the server so each input
 * arrives just before the server needs it: a round trip plus a small buffer ahead of the newest
 * frame the server has answered for, since that answer took half a round trip to come back and the
 * next input takes half a round trip to get there.
 *
 * <ul>
 *   <li><b>Round trip:</b> each input message carries when it was sent; the server echoes the
 *       newest with how long it held it, so the client measures the network alone.
 *   <li><b>Time dilation:</b> from the server's reports of how many inputs it holds, frames get a
 *       little shorter (to build the buffer up) or longer (to let it drain), never changing the
 *       step the simulation takes.
 *   <li><b>Resync:</b> a client far from where it should be, after a stall or at the start, jumps
 *       its frame number instead.
 * </ul>
 *
 * <p>See the Command Frames design doc. Main thread only; times are from {@link System#nanoTime()}.
 */
public final class FrameClock {

    /** The most a frame is stretched or shrunk, as a share of its length. */
    static final double MAX_DILATION = 0.05;

    /** How much a frame is stretched per frame of extra buffer, as a share of its length. */
    static final double GAIN = 0.01;

    /** How much each report moves the smoothed values toward the reported ones. */
    static final double SMOOTHING = 0.1;

    /** The smallest buffer to aim for, in frames. */
    static final int MIN_TARGET = 1;

    /** The largest buffer to aim for, in frames. */
    static final int MAX_TARGET = 4;

    /** How long without misses before the target buffer comes down a frame. */
    static final long CALM_NANOS = 2_000_000_000L;

    /**
     * How many frames short of its lead the client can be before resyncing. Jitter swings the lead
     * by a frame or two between reports; dilation closes small gaps.
     */
    static final int RESYNC_BEHIND = 6;

    /** How many frames past its lead the client can be before resyncing. */
    static final int RESYNC_AHEAD = 16;

    /** No resync needed. */
    public static final long NO_RESYNC = Long.MIN_VALUE;

    /** When the next frame is due. */
    private long nextFrameNanos;

    /**
     * How long a frame lasts on the wall clock now, in nanoseconds.
     *
     * @return The length.
     */
    @Getter private long frameNanos = Frames.FRAME_NANOS;

    /**
     * The buffer to aim for, in frames.
     *
     * @return The target.
     */
    @Getter private int target = MIN_TARGET;

    /**
     * The server's buffer, smoothed over its reports, in frames.
     *
     * @return The buffer.
     */
    @Getter private double buffered = MIN_TARGET;

    /** The server's miss count at its last report. */
    private long lastMisses;

    /** When the target last changed or a miss was last seen. */
    private long calmSince;

    /** The round trip, smoothed, in nanoseconds; negative until measured. */
    private double roundTripNanos = -1;

    /**
     * How many times the clock has resynced.
     *
     * @return The count.
     */
    @Getter private long resyncs;

    /**
     * Start the clock, with the first frame due now.
     *
     * @param now The time.
     */
    public void start(long now) {
        nextFrameNanos = now;
        calmSince = now;
    }

    /**
     * Count the frames due by now, and move the clock past them.
     *
     * @param now The time.
     * @param max The most to count; any more stay due.
     * @return How many frames to run.
     */
    public int due(long now, int max) {
        int count = 0;
        while (now - nextFrameNanos >= 0 && count < max) {
            nextFrameNanos += frameNanos;
            ++count;
        }
        return count;
    }

    /**
     * When the frame that ran last started, on the wall clock, for drawing between frames.
     *
     * @return The time.
     */
    public long frameStartNanos() {
        return nextFrameNanos - frameNanos;
    }

    /**
     * Take the server's report for a frame, and adjust.
     *
     * @param serverFrame The frame the report is for.
     * @param clientFrame The client's last stepped frame, before the report changed anything.
     * @param serverBuffered How many inputs for later frames the server held.
     * @param misses How many frames so far ran on the server with no input from this client.
     * @param echoNanos When the newest input message the server had was sent, or 0 for none.
     * @param heldNanos How long the server had held that message when it answered.
     * @param now The time.
     * @return The frame to jump to, or {@link #NO_RESYNC}.
     */
    public long onServer(
            long serverFrame,
            long clientFrame,
            int serverBuffered,
            long misses,
            long echoNanos,
            long heldNanos,
            long now) {
        if (misses > lastMisses) {
            target = Math.min(MAX_TARGET, target + 1);
            calmSince = now;
        } else if (now - calmSince > CALM_NANOS && target > MIN_TARGET) {
            --target;
            calmSince = now;
        }
        lastMisses = misses;

        if (echoNanos != 0) {
            final double sample = now - echoNanos - (double) heldNanos;
            if (sample >= 0) {
                roundTripNanos =
                        roundTripNanos < 0
                                ? sample
                                : roundTripNanos + SMOOTHING * (sample - roundTripNanos);
            }
        }
        buffered += SMOOTHING * (serverBuffered - buffered);

        final long ahead = clientFrame - serverFrame;
        final long lead = lead();
        if (ahead < lead - RESYNC_BEHIND || ahead > lead + RESYNC_AHEAD) {
            return resync(serverFrame + lead);
        }
        final double stretch = Math.clamp(GAIN * (buffered - target), -MAX_DILATION, MAX_DILATION);
        frameNanos = Math.round(Frames.FRAME_NANOS * (1 + stretch));
        return NO_RESYNC;
    }

    /**
     * How far ahead of the newest frame the server answered for the client should be: the round
     * trip, rounded up to frames, plus the target buffer.
     *
     * @return The lead, in frames.
     */
    public long lead() {
        final double trip = Math.max(0, roundTripNanos);
        return (long) Math.ceil(trip / Frames.FRAME_NANOS) + target;
    }

    /**
     * The round trip, smoothed.
     *
     * @return The round trip in milliseconds, or -1 before one is measured.
     */
    public double roundTripMillis() {
        return roundTripNanos < 0 ? -1 : roundTripNanos / 1_000_000;
    }

    /**
     * How fast frames run against the wall clock, where above 1 is faster.
     *
     * @return The rate.
     */
    public double rate() {
        return (double) Frames.FRAME_NANOS / frameNanos;
    }

    /**
     * Jump, and start measuring the buffer afresh.
     *
     * @param frame The frame to jump to.
     * @return The frame.
     */
    private long resync(long frame) {
        ++resyncs;
        buffered = target;
        frameNanos = Frames.FRAME_NANOS;
        return frame;
    }
}
