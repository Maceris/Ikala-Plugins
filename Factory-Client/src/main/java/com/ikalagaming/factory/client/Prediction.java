package com.ikalagaming.factory.client;

import com.ikalagaming.ecs.Schedule;
import com.ikalagaming.ecs.Snapshot;
import com.ikalagaming.ecs.World;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.sim.BlockHistory;
import com.ikalagaming.factory.sim.BlockView;
import com.ikalagaming.factory.sim.GameMode;
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

    /** The block changes the frames in the ring may need to step against as they were. */
    private final BlockHistory blockHistory = new BlockHistory();

    /** The blocks as the frame being stepped saw them. */
    private final BlockHistory.AtFrame blocksAtFrame;

    /**
     * The last frame stepped.
     *
     * @return The frame.
     */
    @Getter private long frame;

    /**
     * The newest frame the server has answered for.
     *
     * @return The frame.
     */
    @Getter private long acknowledged;

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
     * @param gameMode The player's game mode.
     * @param blocks The blocks the player moves through as they are now: the chunks received, with
     *     the rest solid. Each frame steps against them as they were then, from the block changes
     *     recorded with {@link #blockChanged}.
     */
    public Prediction(
            long serverFrame,
            @NonNull Movement start,
            @NonNull GameMode gameMode,
            @NonNull BlockView blocks) {
        frame = serverFrame - 1;
        player = world.create();
        world.set(PlayerSimulation.MOVEMENT, player, start);
        world.set(PlayerSimulation.GAME_MODE, player, gameMode);
        world.set(PlayerSimulation.INPUT, player, PlayerInput.NONE);
        blocksAtFrame = new BlockHistory.AtFrame(blocks, blockHistory);
        world.set(PlayerSimulation.BLOCKS, player, blocksAtFrame);
        inputs[slot(frame)] = PlayerInput.NONE;
        results[slot(frame)] = world.snapshot();
        // The server runs the frames before this client's first input with no input, which isn't
        // standing still once there is gravity, so predict them the same way
        for (int i = 0; i < LEAD; ++i) {
            step(PlayerInput.NONE);
        }
        acknowledged = frame;
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
     * Keep a block change from the server, before it is applied, so replaying the frames before it
     * steps against the block as it was.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param changedFrame The command frame it happened in.
     * @param wasSolid Whether the block was solid before.
     */
    public void blockChanged(int x, int y, int z, long changedFrame, boolean wasSolid) {
        blockHistory.record(x, y, z, changedFrame, wasSolid);
    }

    /**
     * How many block changes are kept for replaying.
     *
     * @return The count.
     */
    public int keptBlockChanges() {
        return blockHistory.size();
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
     * @param gameMode The player's game mode after it, on the server.
     * @param buffered How many inputs for later frames the server held.
     * @param misses How many frames so far ran with no input from this client.
     * @return True if the prediction changed.
     */
    public boolean onServerState(
            long serverFrame,
            @NonNull Movement state,
            @NonNull GameMode gameMode,
            int buffered,
            long misses) {
        if (serverFrame <= acknowledged) {
            // Older than an answer already handled, or from before this client started
            return false;
        }
        acknowledged = serverFrame;
        serverBuffered = buffered;
        serverMisses = misses;
        // Replaying starts after the newest frame answered, so older changes aren't needed
        blockHistory.forgetBefore(serverFrame + 1);
        if (serverFrame > frame) {
            // The server is ahead of the client, so every input has been late; take its state
            ++mispredicts;
            frame = serverFrame;
            inputs[slot(frame)] = PlayerInput.NONE;
            world.set(PlayerSimulation.MOVEMENT, player, state);
            world.set(PlayerSimulation.GAME_MODE, player, gameMode);
            results[slot(frame)] = world.snapshot();
            return true;
        }
        if (serverFrame <= frame - RING) {
            // Too old to replay from; take its state as now
            ++mispredicts;
            world.set(PlayerSimulation.MOVEMENT, player, state);
            world.set(PlayerSimulation.GAME_MODE, player, gameMode);
            results[slot(frame)] = world.snapshot();
            return true;
        }
        final Snapshot predicted = results[slot(serverFrame)];
        if (state.equals(predicted.get(PlayerSimulation.MOVEMENT, player))
                && gameMode == predicted.get(PlayerSimulation.GAME_MODE, player)) {
            return false;
        }
        ++mispredicts;
        world.restore(predicted);
        world.set(PlayerSimulation.MOVEMENT, player, state);
        world.set(PlayerSimulation.GAME_MODE, player, gameMode);
        results[slot(serverFrame)] = world.snapshot();
        for (long f = serverFrame + 1; f <= frame; ++f) {
            run(f, inputs[slot(f)]);
        }
        return true;
    }

    /**
     * Move to another frame number without stepping, as when the frame clock resyncs. The state
     * stays as it is. Frames skipped going forward get no input, so the server leaves the player
     * where this state has them; going back drops the frames past the new one.
     *
     * @param newFrame The frame to be on.
     */
    public void resync(long newFrame) {
        final Snapshot current = world.snapshot();
        if (newFrame > frame) {
            for (long f = Math.max(frame + 1, newFrame - RING + 1); f <= newFrame; ++f) {
                inputs[slot(f)] = PlayerInput.NONE;
                results[slot(f)] = current;
            }
        } else {
            results[slot(newFrame)] = current;
            if (inputs[slot(newFrame)] == null) {
                inputs[slot(newFrame)] = PlayerInput.NONE;
            }
        }
        frame = newFrame;
        acknowledged = Math.min(acknowledged, newFrame);
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
     * The player's game mode as of the latest frame.
     *
     * @return The game mode.
     */
    public GameMode gameMode() {
        return world.get(PlayerSimulation.GAME_MODE, player);
    }

    /**
     * Run a frame from the current world and keep its input and result.
     *
     * @param f The frame.
     * @param input Its input.
     */
    private void run(long f, PlayerInput input) {
        inputs[slot(f)] = input;
        blocksAtFrame.setFrame(f);
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
