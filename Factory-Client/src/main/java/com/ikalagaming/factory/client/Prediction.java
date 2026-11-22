package com.ikalagaming.factory.client;

import com.ikalagaming.ecs.Schedule;
import com.ikalagaming.ecs.Snapshot;
import com.ikalagaming.ecs.World;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;
import com.ikalagaming.factory.sim.PlayerSimulation;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's prediction of its own player. It steps the same simulation as the server, ahead of
 * it, and keeps the last {@value #RING} frames' inputs and results. When the server's state for a
 * frame differs from the prediction, it rewinds to that frame, takes the server's state, and
 * replays its inputs to catch up. See the Command Frames design doc. Main thread only.
 */
@Slf4j
public final class Prediction {

    /** How many frames of inputs and results are kept; a power of two. */
    static final int RING = 128;

    /** How many frames ahead of the server the client starts. */
    static final int LEAD = 3;

    /** The predicted world: the player, and later anything else predicted. */
    private final World world = new World();

    /** The same systems the server runs. */
    private final Schedule schedule = PlayerSimulation.schedule();

    /** The player's entity in {@link #world}. */
    private final long player;

    /** The input for each frame, by slot. */
    private final PlayerInput[] inputs = new PlayerInput[RING];

    /** The world after each frame, by slot. */
    private final Snapshot[] results = new Snapshot[RING];

    /**
     * The last frame stepped.
     *
     * @return The frame.
     */
    @Getter private long frame;

    /** The newest frame the server has answered for. */
    private long acknowledged;

    /**
     * How many times the server disagreed with the prediction.
     *
     * @return The count.
     */
    @Getter private long mispredicts;

    /**
     * How many inputs for later frames the server held, as of its last answer.
     *
     * @return The count.
     */
    @Getter private int serverBuffered;

    /**
     * How many frames ran on the server with no input from this client, as of its last answer.
     *
     * @return The count.
     */
    @Getter private long serverMisses;

    /**
     * Start predicting.
     *
     * @param serverFrame The frame the server runs next, from its answer to joining.
     * @param start The player's movement as the server put it.
     */
    public Prediction(long serverFrame, @NonNull Movement start) {
        frame = serverFrame + LEAD - 1;
        acknowledged = frame;
        player = world.create();
        world.set(PlayerSimulation.MOVEMENT, player, start);
        world.set(PlayerSimulation.INPUT, player, PlayerInput.NONE);
        inputs[slot(frame)] = PlayerInput.NONE;
        results[slot(frame)] = world.snapshot();
    }

    /**
     * Step one frame.
     *
     * @param input The player's input for it.
     * @return The frame stepped.
     */
    public long step(@NonNull PlayerInput input) {
        ++frame;
        run(frame, input);
        return frame;
    }

    /**
     * The inputs the server hasn't answered for yet, at most {@value PlayerInputs#MAX_FRAMES} of
     * the latest, to send it.
     *
     * @return The message, or null if there are none.
     */
    public PlayerInputs unacknowledged() {
        final long first = Math.max(acknowledged + 1, frame - PlayerInputs.MAX_FRAMES + 1);
        if (first > frame) {
            return null;
        }
        final List<PlayerInput> window = new ArrayList<>((int) (frame - first + 1));
        for (long f = first; f <= frame; ++f) {
            window.add(inputs[slot(f)]);
        }
        return PlayerInputs.of(first, window);
    }

    /**
     * Check the server's state for a frame against the prediction, and correct the prediction if
     * they differ.
     *
     * @param serverFrame The frame the server's state is after.
     * @param state The player's movement after it, on the server.
     * @param buffered How many inputs for later frames the server held.
     * @param misses How many frames so far ran with no input from this client.
     * @return True if the prediction changed.
     */
    public boolean onServerState(
            long serverFrame, @NonNull Movement state, int buffered, long misses) {
        if (serverFrame <= acknowledged) {
            // Older than an answer already handled, or from before this client started
            return false;
        }
        acknowledged = serverFrame;
        serverBuffered = buffered;
        serverMisses = misses;
        if (serverFrame > frame) {
            // The server is ahead of the client, so every input has been late; take its state
            ++mispredicts;
            frame = serverFrame;
            inputs[slot(frame)] = PlayerInput.NONE;
            world.set(PlayerSimulation.MOVEMENT, player, state);
            results[slot(frame)] = world.snapshot();
            return true;
        }
        if (serverFrame <= frame - RING) {
            // Too old to replay from; take its state as now
            ++mispredicts;
            world.set(PlayerSimulation.MOVEMENT, player, state);
            results[slot(frame)] = world.snapshot();
            return true;
        }
        final Snapshot predicted = results[slot(serverFrame)];
        if (state.equals(predicted.get(PlayerSimulation.MOVEMENT, player))) {
            return false;
        }
        ++mispredicts;
        world.restore(predicted);
        world.set(PlayerSimulation.MOVEMENT, player, state);
        results[slot(serverFrame)] = world.snapshot();
        for (long f = serverFrame + 1; f <= frame; ++f) {
            run(f, inputs[slot(f)]);
        }
        return true;
    }

    /**
     * The player's predicted movement after a frame.
     *
     * @param f The frame, within the last {@value #RING}.
     * @return The movement, or the latest if that frame is no longer kept.
     */
    public Movement movement(long f) {
        final long kept = f > frame || f <= frame - RING ? frame : f;
        final Snapshot result = results[slot(kept)];
        return result == null
                ? world.get(PlayerSimulation.MOVEMENT, player)
                : result.get(PlayerSimulation.MOVEMENT, player);
    }

    /**
     * Run a frame from the current world and keep its input and result.
     *
     * @param f The frame.
     * @param input Its input.
     */
    private void run(long f, PlayerInput input) {
        inputs[slot(f)] = input;
        world.set(PlayerSimulation.INPUT, player, input);
        schedule.run(world);
        results[slot(f)] = world.snapshot();
    }

    /**
     * The slot for a frame.
     *
     * @param f The frame.
     * @return The slot.
     */
    private static int slot(long f) {
        return (int) (f & (RING - 1));
    }
}
