package com.ikalagaming.factory.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.sim.Angle;
import com.ikalagaming.factory.sim.BlockView;
import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.Frames;
import com.ikalagaming.factory.sim.GameMode;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;
import com.ikalagaming.factory.sim.PlayerSimulation;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * The client's prediction and frame clock against a stand-in server over a bad network, in virtual
 * time, so seconds of play run in milliseconds and every run is the same. The server follows the
 * real one's rules: one input per frame, late inputs dropped, a missing input repeated and counted.
 */
@Slf4j
class TestNetcode {

    /** A millisecond in nanoseconds. */
    private static final long MS = 1_000_000;

    /** A second in nanoseconds. */
    private static final long SECOND = 1000 * MS;

    /** Where the player starts. */
    private static final Movement START = Movement.at(MoveMode.WALKING, 0, Fixed.ofMeters(64), 0);

    /** Solid ground below y = 64, so the player walks, falls and lands. */
    private static final BlockView FLOOR = (x, y, z) -> y < 64;

    /** How far ahead the server keeps inputs. */
    private static final int MAX_AHEAD = 32;

    /**
     * A bad network, the same both ways.
     *
     * @param delayMs The least one-way time.
     * @param jitterMs The most extra time.
     * @param loss The share of unreliable messages lost.
     */
    private record Network(long delayMs, long jitterMs, double loss) {}

    /**
     * What happened in a run.
     *
     * @param frames Server frames run after the warm-up.
     * @param misses Server misses after the warm-up.
     * @param mispredicts Client mispredicts after the warm-up.
     * @param resyncs Resyncs in the whole run.
     * @param roundTripMillis The client's round trip estimate at the end.
     * @param rate The client's frame rate against normal at the end.
     * @param agree Whether the client's state for the server's last frame matches the server's.
     */
    private record Result(
            long frames,
            long misses,
            long mispredicts,
            long resyncs,
            double roundTripMillis,
            double rate,
            boolean agree) {}

    /** Events in virtual time. */
    private static final class Events {
        /**
         * An event.
         *
         * @param at When.
         * @param order Breaks ties in the order events were added.
         * @param action What happens.
         */
        private record Event(long at, long order, Runnable action) {}

        /** The events to come. */
        private final PriorityQueue<Event> queue =
                new PriorityQueue<>(
                        (a, b) ->
                                a.at() != b.at()
                                        ? Long.compare(a.at(), b.at())
                                        : Long.compare(a.order(), b.order()));

        /** The time now. */
        long now;

        /** The next order number. */
        private long order;

        /**
         * Add an event.
         *
         * @param at When.
         * @param action What happens.
         */
        void at(long at, Runnable action) {
            queue.add(new Event(at, order++, action));
        }

        /**
         * Run events up to a time.
         *
         * @param end The time.
         */
        void runUntil(long end) {
            while (!queue.isEmpty() && queue.peek().at() <= end) {
                final Event event = queue.poll();
                now = event.at();
                event.action().run();
            }
            now = end;
        }
    }

    /** The stand-in server. */
    private static final class Server {
        /** The frame to run next. */
        long next = 500;

        /** Inputs waiting, by frame. */
        final TreeMap<Long, PlayerInput> inputs = new TreeMap<>();

        /** The last input used. */
        PlayerInput last = PlayerInput.NONE;

        /** Whether inputs have started arriving. */
        boolean started;

        /** Frames run with no input, since inputs started. */
        long misses;

        /** The player now. */
        Movement movement = START;

        /** The player after each frame. */
        final Map<Long, Movement> results = new HashMap<>();

        /** When the newest input message was sent, or 0. */
        long echo;

        /** When it arrived. */
        long echoArrived;

        /**
         * Take inputs from the client.
         *
         * @param message The message.
         * @param now The time.
         */
        void receive(PlayerInputs message, long now) {
            if (message.getSentNanos() > echo) {
                echo = message.getSentNanos();
                echoArrived = now;
            }
            final List<PlayerInput> list = message.inputs();
            for (int i = 0; i < list.size(); ++i) {
                final long frame = message.getFirstFrame() + i;
                if (frame >= next && frame < next + MAX_AHEAD) {
                    inputs.put(frame, list.get(i));
                    started = true;
                }
            }
        }

        /**
         * Run a frame.
         *
         * @param now The time.
         * @return The state to send.
         */
        PlayerState frame(long now) {
            final PlayerInput input = inputs.remove(next);
            if (input != null) {
                last = input;
            } else if (started) {
                ++misses;
            }
            movement = PlayerSimulation.step(movement, last, FLOOR, GameMode.SURVIVAL);
            results.put(next, movement);
            final int buffered = inputs.subMap(next + 1, next + MAX_AHEAD + 1).size();
            final PlayerState state =
                    PlayerState.of(next, movement, GameMode.SURVIVAL, buffered, misses);
            if (echo != 0) {
                state.setEchoNanos(echo);
                state.setHeldNanos(now - echoArrived);
            }
            ++next;
            return state;
        }
    }

    /**
     * The player's input on a frame: always moving, turning steadily, changing keys now and then,
     * so a repeated input on the server is always wrong.
     *
     * @param frame The frame.
     * @return The input.
     */
    private static PlayerInput inputFor(long frame) {
        final int buttons = PlayerInput.FORWARD | ((frame / 37) % 2 == 0 ? PlayerInput.RIGHT : 0);
        return new PlayerInput(buttons, Angle.wrap(frame * 50), 0);
    }

    /**
     * Play over a network.
     *
     * @param network The network.
     * @param seconds How long to play.
     * @param warmUpSeconds How long before counting misses and mispredicts.
     * @param stallAt When the client stops drawing frames, or -1 for never.
     * @param stallMs For how long.
     * @return What happened.
     */
    private static Result play(
            Network network, int seconds, int warmUpSeconds, long stallAt, long stallMs) {
        final Events events = new Events();
        final SplittableRandom random = new SplittableRandom(42);
        final Server server = new Server();
        final Prediction[] prediction = new Prediction[1];
        final FrameClock clock = new FrameClock();
        final long[] atWarmUp = new long[3];

        final java.util.function.LongSupplier travel =
                () ->
                        network.delayMs() * MS
                                + (network.jitterMs() == 0
                                        ? 0
                                        : random.nextLong(network.jitterMs() * MS + 1));

        // The server runs frames on its own clock and answers each one
        for (long t = 0; t < seconds * SECOND; t += Frames.FRAME_NANOS) {
            events.at(
                    t,
                    () -> {
                        final PlayerState state = server.frame(events.now);
                        if (prediction[0] == null || random.nextDouble() < network.loss()) {
                            return;
                        }
                        events.at(
                                events.now + travel.getAsLong(),
                                () -> {
                                    final Prediction p = prediction[0];
                                    final long clientFrame = p.getFrame();
                                    p.onServerState(
                                            state.getFrame(),
                                            state.movement(),
                                            GameMode.SURVIVAL,
                                            state.getBuffered(),
                                            state.getMissed());
                                    final long jump =
                                            clock.onServer(
                                                    state.getFrame(),
                                                    clientFrame,
                                                    state.getBuffered(),
                                                    state.getMissed(),
                                                    state.getEchoNanos(),
                                                    state.getHeldNanos(),
                                                    events.now);
                                    if (jump != FrameClock.NO_RESYNC) {
                                        p.resync(jump);
                                    }
                                });
                    });
        }
        // Joining is reliable: it arrives, late
        final long joinFrame = server.next;
        events.at(
                network.delayMs() * MS + network.jitterMs() * MS,
                () -> {
                    prediction[0] = new Prediction(joinFrame, START, GameMode.SURVIVAL, FLOOR);
                    clock.start(events.now);
                });
        // The client draws frames every 7 to 10 ms, except while stalled
        for (long t = 1; t < seconds * SECOND; t += 7 * MS + random.nextLong(3 * MS)) {
            if (stallAt >= 0 && t >= stallAt && t < stallAt + stallMs * MS) {
                continue;
            }
            events.at(
                    t,
                    () -> {
                        final Prediction p = prediction[0];
                        if (p == null) {
                            return;
                        }
                        final int due = clock.due(events.now, 8);
                        for (int i = 0; i < due; ++i) {
                            p.step(inputFor(p.getFrame() + 1));
                        }
                        final PlayerInputs window = due > 0 ? p.unacknowledged() : null;
                        if (window == null || random.nextDouble() < network.loss()) {
                            return;
                        }
                        window.setSentNanos(events.now);
                        events.at(
                                events.now + travel.getAsLong(),
                                () -> server.receive(window, events.now));
                    });
        }
        events.at(
                warmUpSeconds * SECOND,
                () -> {
                    atWarmUp[0] = server.next;
                    atWarmUp[1] = server.misses;
                    atWarmUp[2] = prediction[0].getMispredicts();
                });
        events.runUntil(seconds * SECOND);

        final Prediction p = prediction[0];
        final long answered = p.getAcknowledged();
        final Result result =
                new Result(
                        server.next - atWarmUp[0],
                        server.misses - atWarmUp[1],
                        p.getMispredicts() - atWarmUp[2],
                        clock.getResyncs(),
                        clock.roundTripMillis(),
                        clock.rate(),
                        server.results.get(answered).equals(p.movement(answered)));
        log.info("{}: {}", network, result);
        return result;
    }

    /** On a good network nothing is missed once settled, and the clock barely dilates. */
    @Test
    void testGoodNetwork() {
        final Result result = play(new Network(20, 4, 0), 20, 5, -1, 0);
        assertEquals(0, result.misses(), result.toString());
        assertEquals(0, result.mispredicts(), result.toString());
        assertTrue(result.agree());
        assertTrue(
                result.roundTripMillis() > 40 && result.roundTripMillis() < 70, result.toString());
        assertTrue(Math.abs(result.rate() - 1) < 0.03, result.toString());
    }

    /**
     * On a bad network (160 ms round trip, jitter, 5% loss each way) the sliding window and the
     * buffer keep misses rare, and the client still ends in agreement with the server.
     */
    @Test
    void testBadNetwork() {
        final Result result = play(new Network(80, 20, 0.05), 30, 5, -1, 0);
        assertTrue(result.misses() <= result.frames() / 50, result.toString());
        assertTrue(result.mispredicts() <= result.misses() + 2, result.toString());
        assertTrue(result.resyncs() <= 2, result.toString());
        assertTrue(result.agree());
        assertTrue(
                result.roundTripMillis() > 160 && result.roundTripMillis() < 230,
                result.toString());
    }

    /**
     * A client that stalls for a second recovers: it resyncs or catches up, then misses nothing.
     */
    @Test
    void testStall() {
        final Result result = play(new Network(30, 5, 0), 25, 15, 10 * SECOND, 1000);
        assertEquals(0, result.misses(), result.toString());
        assertTrue(result.agree());
    }
}
