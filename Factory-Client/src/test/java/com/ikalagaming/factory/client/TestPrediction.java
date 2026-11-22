package com.ikalagaming.factory.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.sim.Angle;
import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;
import com.ikalagaming.factory.sim.PlayerSimulation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for the client's prediction, against a stand-in server that steps the same simulation with
 * the inputs it was sent, and answers a few frames late.
 */
class TestPrediction {

    /** Where the player starts. */
    private static final Movement START =
            Movement.at(MoveMode.FLYING, Fixed.ofMeters(5), Fixed.ofMeters(70), 0);

    /** The frame the server runs next when the player joins. */
    private static final long JOIN_FRAME = 1000;

    /** How many frames late the server's answers arrive. */
    private static final int DELAY = 5;

    /** A stand-in server: one player, stepped with whatever inputs arrived for each frame. */
    private static final class FakeServer {
        /** The inputs that arrived, by frame. */
        final Map<Long, PlayerInput> inputs = new HashMap<>();

        /** The frame to run next. */
        long next = JOIN_FRAME;

        /** The player's movement now. */
        Movement movement = START;

        /** The last input used. */
        PlayerInput last = PlayerInput.NONE;

        /** The movement after each frame. */
        final Map<Long, Movement> results = new HashMap<>();

        /**
         * Take a message from the client.
         *
         * @param message The message.
         */
        void receive(PlayerInputs message) {
            final List<PlayerInput> list = message.inputs();
            for (int i = 0; i < list.size(); ++i) {
                final long frame = message.getFirstFrame() + i;
                if (frame >= next) {
                    inputs.put(frame, list.get(i));
                }
            }
        }

        /** Run the next frame. */
        void step() {
            final PlayerInput input = inputs.get(next);
            if (input != null) {
                last = input;
            }
            movement = PlayerSimulation.step(movement, last);
            results.put(next, movement);
            ++next;
        }
    }

    /**
     * The player's inputs: flying forward, turning, rising.
     *
     * @param count How many frames.
     * @return The inputs.
     */
    private static List<PlayerInput> inputs(int count) {
        final List<PlayerInput> out = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            final int buttons =
                    PlayerInput.FORWARD
                            | (i % 40 < 10 ? PlayerInput.UP : 0)
                            | (i > 60 ? PlayerInput.SPRINT : 0);
            out.add(new PlayerInput(buttons, Angle.wrap(i * 300L), 0));
        }
        return out;
    }

    /**
     * Play: each frame the client steps and sends its window, the server steps, and the server's
     * answer arrives {@value #DELAY} frames later. A dropped message is simply never delivered.
     *
     * @param prediction The client.
     * @param server The server.
     * @param inputs The client's inputs.
     * @param dropFrom The first client frame whose message is lost, or -1.
     * @param dropCount How many messages in a row are lost.
     * @param drift Extra frames the server runs at the start, as if the client started late.
     */
    private static void play(
            Prediction prediction,
            FakeServer server,
            List<PlayerInput> inputs,
            long dropFrom,
            int dropCount,
            int drift) {
        for (int i = 0; i < drift; ++i) {
            server.step();
        }
        final List<Long> pending = new ArrayList<>();
        for (PlayerInput input : inputs) {
            final long frame = prediction.step(input);
            final PlayerInputs message = prediction.unacknowledged();
            if (frame < dropFrom || frame >= dropFrom + dropCount) {
                server.receive(message);
            }
            server.step();
            pending.add(server.next - 1);
            if (pending.size() > DELAY) {
                final long answered = pending.remove(0);
                prediction.onServerState(answered, server.results.get(answered), 0, 0);
            }
        }
        for (long answered : pending) {
            prediction.onServerState(answered, server.results.get(answered), 0, 0);
        }
    }

    /** With every input arriving in time, the prediction always matches the server. */
    @Test
    void testInStep() {
        final Prediction prediction = new Prediction(JOIN_FRAME, START);
        final FakeServer server = new FakeServer();
        play(prediction, server, inputs(200), -1, 0, 0);
        assertEquals(0, prediction.getMispredicts());
        assertEquals(server.results.get(server.next - 1), prediction.movement(server.next - 1));
        assertEquals(server.next, prediction.unacknowledged().getFirstFrame());
    }

    /**
     * Lost messages don't cause mispredicts: the next message's window carries their inputs, as
     * long as it arrives before the frames run.
     */
    @Test
    void testLossCovered() {
        final Prediction prediction = new Prediction(JOIN_FRAME, START);
        final FakeServer server = new FakeServer();
        // The client leads by a few frames, so one lost message is made up by the next
        play(prediction, server, inputs(200), JOIN_FRAME + 50, 1, 0);
        assertEquals(0, prediction.getMispredicts());
    }

    /**
     * When the server runs frames before their inputs arrive, it repeats old inputs; the client
     * mispredicts, rewinds, replays, and ends exactly where the server does.
     */
    @Test
    void testMispredictConverges() {
        final Prediction prediction = new Prediction(JOIN_FRAME, START);
        final FakeServer server = new FakeServer();
        // A long gap of lost messages, longer than the client's lead
        play(prediction, server, inputs(300), JOIN_FRAME + 80, 20, 0);
        assertTrue(prediction.getMispredicts() > 0, "nothing mispredicted");
        final long last = server.next - 1;
        assertEquals(server.results.get(last), prediction.movement(last));
    }

    /** States older than one already handled change nothing. */
    @Test
    void testOldStatesIgnored() {
        final Prediction prediction = new Prediction(JOIN_FRAME, START);
        for (PlayerInput input : inputs(20)) {
            prediction.step(input);
        }
        final long frame = prediction.getFrame();
        final Movement wrong = Movement.at(MoveMode.FLYING, 0, 0, 0);
        assertTrue(prediction.onServerState(frame - 5, wrong, 0, 0));
        final Movement corrected = prediction.movement(frame);
        assertFalse(prediction.onServerState(frame - 7, START, 0, 0));
        assertEquals(corrected, prediction.movement(frame));
        assertNotEquals(START, corrected);
        // The window now starts after the newest answer
        final PlayerInputs window = prediction.unacknowledged();
        assertEquals(frame - 4, window.getFirstFrame());
    }

    /** A client that falls behind the server takes the server's state and frame. */
    @Test
    void testBehindServer() {
        final Prediction prediction = new Prediction(JOIN_FRAME, START);
        final Movement far = Movement.at(MoveMode.FLYING, Fixed.ofMeters(100), 0, 0);
        assertTrue(prediction.onServerState(prediction.getFrame() + 10, far, 0, 0));
        assertEquals(far, prediction.movement(prediction.getFrame()));
        assertEquals(1, prediction.getMispredicts());
    }
}
