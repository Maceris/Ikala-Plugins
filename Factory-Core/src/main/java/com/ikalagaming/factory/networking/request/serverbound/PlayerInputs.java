package com.ikalagaming.factory.networking.request.serverbound;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.sim.PlayerInput;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * A player's inputs for a run of command frames: every frame since the last one the server
 * acknowledged, so an input lost on the way arrives again with the next message. Runs of the same
 * input are sent once with a count, so a held key costs nothing extra.
 *
 * <p>Fields have setters and there is a no-argument constructor, so remote connections can
 * serialize it. A local connection passes the object itself, so nothing may change it after it is
 * sent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInputs implements Request {

    /** The most frames one message carries. */
    public static final int MAX_FRAMES = 64;

    /** The frame the first input is for. */
    private long firstFrame;

    /** How many frames each run lasts. */
    private int[] counts;

    /** Each run's buttons. */
    private int[] buttons;

    /** Each run's yaw. */
    private int[] yaws;

    /** Each run's pitch. */
    private int[] pitches;

    /**
     * When the client sent this, from its {@link System#nanoTime()}, which the server echoes so the
     * client can measure the round trip. 0 if not set.
     */
    private long sentNanos;

    /**
     * Inputs for consecutive frames, with runs of the same input joined.
     *
     * @param firstFrame The frame the first input is for.
     * @param inputs The inputs, one per frame, at most {@value #MAX_FRAMES}.
     * @return The message.
     * @throws IllegalArgumentException If there are no inputs, or too many.
     */
    public static PlayerInputs of(long firstFrame, @NonNull List<PlayerInput> inputs) {
        if (inputs.isEmpty() || inputs.size() > MAX_FRAMES) {
            throw new IllegalArgumentException(
                    "Inputs for " + inputs.size() + " frames; 1 to " + MAX_FRAMES + " fit");
        }
        final int[] counts = new int[inputs.size()];
        final int[] buttons = new int[inputs.size()];
        final int[] yaws = new int[inputs.size()];
        final int[] pitches = new int[inputs.size()];
        int runs = 0;
        PlayerInput previous = null;
        for (PlayerInput input : inputs) {
            if (input.equals(previous)) {
                ++counts[runs - 1];
                continue;
            }
            counts[runs] = 1;
            buttons[runs] = input.buttons();
            yaws[runs] = input.yaw();
            pitches[runs] = input.pitch();
            ++runs;
            previous = input;
        }
        return new PlayerInputs(
                firstFrame,
                Arrays.copyOf(counts, runs),
                Arrays.copyOf(buttons, runs),
                Arrays.copyOf(yaws, runs),
                Arrays.copyOf(pitches, runs),
                0);
    }

    /**
     * The inputs, one per frame from {@link #getFirstFrame()}. A message whose arrays don't line up
     * or whose counts are out of range gives none, since it can only come from a broken or hostile
     * client.
     *
     * @return The inputs, at most {@value #MAX_FRAMES}.
     */
    public List<PlayerInput> inputs() {
        if (counts == null
                || buttons == null
                || yaws == null
                || pitches == null
                || buttons.length != counts.length
                || yaws.length != counts.length
                || pitches.length != counts.length) {
            return List.of();
        }
        long total = 0;
        for (int count : counts) {
            if (count < 1) {
                return List.of();
            }
            total += count;
        }
        if (total > MAX_FRAMES) {
            return List.of();
        }
        final PlayerInput[] out = new PlayerInput[(int) total];
        int at = 0;
        for (int run = 0; run < counts.length; ++run) {
            final PlayerInput input = new PlayerInput(buttons[run], yaws[run], pitches[run]);
            Arrays.fill(out, at, at + counts[run], input);
            at += counts[run];
        }
        return List.of(out);
    }

    @Override
    public boolean isReliable() {
        return false;
    }

    @Override
    public void handleUsing(@NonNull ServerBoundRequestHandler handler) {
        handler.handle(this);
    }
}
