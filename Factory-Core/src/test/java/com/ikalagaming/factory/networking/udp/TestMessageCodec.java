package com.ikalagaming.factory.networking.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.networking.RequestRegistry;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.serverbound.Join;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.sim.Angle;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Tests for writing requests into packets. */
class TestMessageCodec {

    /** Register the requests. */
    @BeforeAll
    static void setUp() {
        RequestRegistry.registerDefaults();
    }

    /**
     * Write and read a request.
     *
     * @param request The request.
     * @param direction Which way it goes.
     * @return What came back.
     */
    private static Request roundTrip(Request request, RequestDirection direction) {
        final Optional<Request> back =
                MessageCodec.decode(MessageCodec.encode(request, direction), direction);
        assertTrue(back.isPresent(), () -> request + " didn't come back");
        return back.get();
    }

    /** Inputs come back the same, extremes included, and stay small. */
    @Test
    void testInputs() {
        final List<PlayerInput> inputs = new ArrayList<>();
        for (int i = 0; i < PlayerInputs.MAX_FRAMES; ++i) {
            inputs.add(
                    new PlayerInput(
                            i % 3 == 0 ? PlayerInput.ALL_BUTTONS : i % 5,
                            i < 10 ? Angle.TURN - 1 : 0,
                            i % 2 == 0 ? -Angle.QUARTER : Angle.QUARTER));
        }
        final PlayerInputs message = PlayerInputs.of(123_456_789_012L, inputs);
        message.setSentNanos(-987_654_321L);
        final PlayerInputs back = (PlayerInputs) roundTrip(message, RequestDirection.SERVER_BOUND);
        assertEquals(inputs, back.inputs());
        assertEquals(message.getFirstFrame(), back.getFirstFrame());
        assertEquals(message.getSentNanos(), back.getSentNanos());
        final PlayerInputs held =
                PlayerInputs.of(5, List.of(PlayerInput.NONE, PlayerInput.NONE, PlayerInput.NONE));
        assertTrue(MessageCodec.encode(held, RequestDirection.SERVER_BOUND).length < 30);
    }

    /** States come back the same. */
    @Test
    void testState() {
        final PlayerState state =
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
                        31,
                        1_000_000_000_000L);
        state.setEchoNanos(-42);
        state.setHeldNanos(99);
        assertEquals(state, roundTrip(state, RequestDirection.CLIENT_BOUND));
    }

    /** Other requests go as their id and a tree. */
    @Test
    void testGeneral() {
        final Join join = new Join("Somebody");
        assertEquals(join, roundTrip(join, RequestDirection.SERVER_BOUND));
        final Joined joined = new Joined("id", 1.5, 2.5, -3.5, 0.25f, -0.5f, 8, 4, 1234);
        assertEquals(joined, roundTrip(joined, RequestDirection.CLIENT_BOUND));
    }

    /** Junk, cut-off and padded bytes give nothing. */
    @Test
    void testJunk() {
        assertTrue(MessageCodec.decode(new byte[0], RequestDirection.CLIENT_BOUND).isEmpty());
        assertTrue(
                MessageCodec.decode(new byte[] {9, 1, 2}, RequestDirection.CLIENT_BOUND).isEmpty());
        final byte[] state =
                MessageCodec.encode(
                        PlayerState.of(1, Movement.at(MoveMode.FLYING, 0, 0, 0), 0, 0),
                        RequestDirection.CLIENT_BOUND);
        assertTrue(
                MessageCodec.decode(
                                Arrays.copyOf(state, state.length - 1),
                                RequestDirection.CLIENT_BOUND)
                        .isEmpty());
        assertTrue(
                MessageCodec.decode(
                                Arrays.copyOf(state, state.length + 1),
                                RequestDirection.CLIENT_BOUND)
                        .isEmpty());
        final byte[] general = MessageCodec.encode(new Join("x"), RequestDirection.SERVER_BOUND);
        assertTrue(
                MessageCodec.decode(
                                Arrays.copyOf(general, general.length - 2),
                                RequestDirection.SERVER_BOUND)
                        .isEmpty());
    }
}
