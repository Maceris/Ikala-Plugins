package com.ikalagaming.factory.networking.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.sim.Angle;
import com.ikalagaming.factory.sim.GameMode;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Tests for packing inputs and states. */
class TestFrameCodec {

    /** One meter in fixed point. */
    private static final long METER = 65_536;

    /** The client's clock in these tests. */
    private static final long NOW = 5_000_000_000_000L;

    /** The server's end. */
    private final FrameCodec server = new FrameCodec();

    /** The client's end. */
    private final FrameCodec client = new FrameCodec();

    /**
     * Pack at one end and unpack at the other.
     *
     * @param from The sending end.
     * @param to The receiving end.
     * @param request The message.
     * @return What came out, if anything.
     */
    private static Optional<Request> send(FrameCodec from, FrameCodec to, Request request) {
        return to.decode(from.encode(request), NOW);
    }

    /**
     * A state, as walking along x would give.
     *
     * @param frame The frame.
     * @return The state, echoing a send a millisecond before {@link #NOW}.
     */
    private static PlayerState walking(long frame) {
        final PlayerState state =
                PlayerState.of(
                        frame,
                        new Movement(
                                MoveMode.WALKING,
                                1234 * METER + frame * METER / 14,
                                64 * METER,
                                -5678 * METER,
                                METER / 14,
                                0,
                                0,
                                Angle.QUARTER,
                                -100,
                                true),
                        GameMode.SURVIVAL,
                        2,
                        3);
        state.setEchoNanos(NOW - 1_000_000);
        state.setHeldNanos(4_000_000);
        return state;
    }

    /** Inputs come back the same, extremes included, and stay small. */
    @Test
    void testInputs() {
        final List<PlayerInput> inputs = new ArrayList<>();
        for (int i = 0; i < PlayerInputs.MAX_FRAMES; ++i) {
            inputs.add(
                    new PlayerInput(
                            i % 3 == 0 ? PlayerInput.ALL_BUTTONS : i % 5,
                            i < 10 ? Angle.TURN - 1 - i : i,
                            i % 2 == 0 ? -Angle.QUARTER : Angle.QUARTER));
        }
        final PlayerInputs message = PlayerInputs.of(123_456_789_012L, inputs);
        message.setSentNanos(NOW);
        final PlayerInputs back = (PlayerInputs) send(client, server, message).orElseThrow();
        assertEquals(inputs, back.inputs());
        assertEquals(message.getFirstFrame(), back.getFirstFrame());
        assertEquals((int) NOW, (int) back.getSentNanos());

        final PlayerInputs held =
                PlayerInputs.of(5, List.of(PlayerInput.NONE, PlayerInput.NONE, PlayerInput.NONE));
        held.setSentNanos(NOW);
        assertTrue(client.encode(held).length <= 12, "held keys cost a few bytes");

        final List<PlayerInput> turning = new ArrayList<>();
        for (int i = 0; i < 8; ++i) {
            turning.add(new PlayerInput(1, Angle.QUARTER + i * 20, -300 + i * 5));
        }
        final PlayerInputs turn = PlayerInputs.of(1_000_000, turning);
        turn.setSentNanos(NOW);
        assertTrue(
                client.encode(turn).length <= 45,
                () -> "8 frames of turning took " + client.encode(turn).length);
    }

    /** Send times stay in order across the 32-bit wrap, and the echo comes back whole. */
    @Test
    void testTimes() {
        long previous = Long.MIN_VALUE;
        for (long sent = (1L << 32) - 300_000_000L;
                sent < (1L << 32) + 300_000_000L;
                sent += 16_000_000L) {
            final PlayerInputs inputs = PlayerInputs.of(1, List.of(PlayerInput.NONE));
            inputs.setSentNanos(sent);
            final long back =
                    ((PlayerInputs) send(client, server, inputs).orElseThrow()).getSentNanos();
            assertTrue(back > previous, "send times stay in order");
            assertEquals((int) sent, (int) back);
            previous = back;

            final PlayerState state = walking(1);
            state.setEchoNanos(back);
            final long echo =
                    ((PlayerState)
                                    client.decode(server.encode(state), sent + 80_000_000L)
                                            .orElseThrow())
                            .getEchoNanos();
            assertEquals(sent, echo, "the client gets its own send time back");
        }
    }

    /** States come back the same, whole or as changes, and changes are small. */
    @Test
    void testStates() {
        final PlayerState extreme =
                PlayerState.of(
                        77,
                        new Movement(
                                MoveMode.WALKING,
                                Long.MIN_VALUE,
                                -5,
                                Long.MAX_VALUE,
                                -1,
                                2,
                                -3,
                                Angle.TURN - 1,
                                -Angle.QUARTER,
                                true),
                        GameMode.SURVIVAL,
                        31,
                        1_000_000_000_000L);
        extreme.setEchoNanos(NOW - 5);
        extreme.setHeldNanos(99_000);
        assertEquals(extreme, send(server, client, extreme).orElseThrow());

        final PlayerState first = walking(100);
        final int whole = server.encode(first).length;
        assertEquals(first, client.decode(server.encode(first), NOW).orElseThrow());
        final PlayerInputs ack = PlayerInputs.of(103, List.of(PlayerInput.NONE));
        ack.setSentNanos(NOW);
        send(client, server, ack);
        for (long frame = 101; frame < 110; ++frame) {
            final PlayerState state = walking(frame);
            final byte[] packed = server.encode(state);
            assertTrue(packed.length < whole, "changes are smaller than the whole state");
            assertTrue(packed.length <= 16, () -> "walking took " + packed.length);
            assertEquals(state, client.decode(packed, NOW).orElseThrow());
        }
    }

    /** A state packed against one the client no longer holds is dropped, not misread. */
    @Test
    void testBaselineGone() {
        final FrameCodec otherClient = new FrameCodec();
        send(server, otherClient, walking(10));
        final PlayerInputs ack = PlayerInputs.of(12, List.of(PlayerInput.NONE));
        ack.setSentNanos(NOW);
        send(otherClient, server, ack);
        assertTrue(client.decode(server.encode(walking(11)), NOW).isEmpty());
        assertTrue(otherClient.decode(server.encode(walking(12)), NOW).isPresent());
    }

    /** Cut-off, padded and foreign bytes are refused. */
    @Test
    void testJunk() {
        final byte[] state = server.encode(walking(3));
        final byte[] inputs = client.encode(PlayerInputs.of(3, List.of(PlayerInput.NONE)));
        for (byte[] bad :
                List.of(
                        Arrays.copyOf(state, state.length - 1),
                        Arrays.copyOf(state, state.length + 1),
                        Arrays.copyOf(inputs, inputs.length - 1),
                        Arrays.copyOf(inputs, inputs.length + 1),
                        new byte[] {MessageCodec.STATE},
                        new byte[] {MessageCodec.STATE, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},
                        new byte[] {MessageCodec.GENERAL, 1})) {
            assertThrows(IllegalArgumentException.class, () -> client.decode(bad, NOW));
        }
    }
}
