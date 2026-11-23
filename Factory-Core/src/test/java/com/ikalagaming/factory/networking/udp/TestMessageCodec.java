package com.ikalagaming.factory.networking.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.networking.RequestRegistry;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.factory.networking.request.clientbound.ChunkData;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.serverbound.Hello;
import com.ikalagaming.factory.networking.serialization.RequestEncoder;
import com.ikalagaming.factory.sim.GameMode;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.world.Block;
import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkCodec;
import com.ikalagaming.factory.world.World;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
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

    /** Other requests go as their id and a tree. */
    @Test
    void testGeneral() {
        final Hello join = new Hello("Somebody");
        assertEquals(join, roundTrip(join, RequestDirection.SERVER_BOUND));
        final Joined joined = new Joined("id", 1.5, 2.5, -3.5, 0.25f, -0.5f, 8, 4, 1234, 0);
        assertEquals(joined, roundTrip(joined, RequestDirection.CLIENT_BOUND));
    }

    /**
     * A chunk with a few kinds of block in a pattern, like terrain.
     *
     * @return The chunk.
     */
    private static Chunk terrain() {
        final Block stone = new Block("t:stone", null);
        final Block dirt = new Block("t:dirt", null);
        final Block grass = new Block("t:grass", null);
        final Chunk chunk = new Chunk(World.AIR, "t:plains");
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                final int top = 6 + (x * 7 + z * 3) % 5;
                for (int y = 0; y < top; ++y) {
                    chunk.setBlock(x, y, z, y == top - 1 ? grass : y > top - 4 ? dirt : stone);
                }
            }
        }
        return chunk;
    }

    /**
     * The bytes of a request uncompressed, as its id and tree.
     *
     * @param request The request.
     * @return The bytes.
     */
    private static int treeSize(Request request) {
        final ByteBuf buffer = Unpooled.buffer();
        try {
            RequestEncoder.encode(request, RequestDirection.CLIENT_BOUND, buffer);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }

    /** Chunks are compressed and come back the same; small requests are not compressed. */
    @Test
    void testCompressed() {
        final Chunk chunk = terrain();
        final ChunkData data = new ChunkData(1, -2, 3, ChunkCodec.encode(chunk));
        final byte[] bytes = MessageCodec.encode(data, RequestDirection.CLIENT_BOUND);
        assertEquals(MessageCodec.DEFLATED, bytes[0]);
        final int plain = treeSize(data);
        assertTrue(
                bytes.length * 3 < plain,
                () -> "a chunk of " + plain + " bytes compressed to " + bytes.length);
        final ChunkData back = (ChunkData) roundTrip(data, RequestDirection.CLIENT_BOUND);
        assertEquals(List.of(1, -2, 3), List.of(back.getX(), back.getY(), back.getZ()));
        final Chunk backChunk = ChunkCodec.decode(back.getChunk());
        for (int x = 0; x < 16; ++x) {
            for (int y = 0; y < 16; ++y) {
                for (int z = 0; z < 16; ++z) {
                    assertEquals(chunk.getBlock(x, y, z), backChunk.getBlock(x, y, z));
                }
            }
        }
        assertEquals(
                MessageCodec.GENERAL,
                MessageCodec.encode(new Hello("Somebody"), RequestDirection.SERVER_BOUND)[0]);
    }

    /**
     * Compressed bytes that are cut off, lie about their length or inflate too far give nothing.
     */
    @Test
    void testBadCompressed() {
        final byte[] bytes =
                MessageCodec.encode(
                        new ChunkData(0, 0, 0, ChunkCodec.encode(terrain())),
                        RequestDirection.CLIENT_BOUND);
        final List<byte[]> bad = new ArrayList<>();
        bad.add(Arrays.copyOf(bytes, bytes.length - 1));
        bad.add(Arrays.copyOf(bytes, bytes.length + 1));
        bad.add(Arrays.copyOf(bytes, 3));
        for (int change : new int[] {-1, 1, MessageCodec.MAX_INFLATED}) {
            final byte[] lying = bytes.clone();
            final ByteBuffer length = ByteBuffer.wrap(lying, 1, Integer.BYTES);
            length.putInt(1, length.getInt(1) + change);
            bad.add(lying);
        }
        final byte[] junk = new byte[64];
        junk[0] = MessageCodec.DEFLATED;
        ByteBuffer.wrap(junk).putInt(1, 1000);
        Arrays.fill(junk, 5, junk.length, (byte) 0x5A);
        bad.add(junk);
        for (byte[] data : bad) {
            assertTrue(MessageCodec.decode(data, RequestDirection.CLIENT_BOUND).isEmpty());
        }
    }

    /** Inputs and states are left to the connection's {@link FrameCodec}. */
    @Test
    void testPerFrameRefused() {
        final PlayerState state =
                PlayerState.of(1, Movement.at(MoveMode.FLYING, 0, 0, 0), GameMode.SURVIVAL, 0, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageCodec.encode(state, RequestDirection.CLIENT_BOUND));
    }

    /** Junk, cut-off and padded bytes give nothing. */
    @Test
    void testJunk() {
        assertTrue(MessageCodec.decode(new byte[0], RequestDirection.CLIENT_BOUND).isEmpty());
        assertTrue(
                MessageCodec.decode(new byte[] {9, 1, 2}, RequestDirection.CLIENT_BOUND).isEmpty());
        assertTrue(
                MessageCodec.decode(
                                new byte[] {MessageCodec.STATE, 1}, RequestDirection.CLIENT_BOUND)
                        .isEmpty());
        final byte[] general = MessageCodec.encode(new Hello("x"), RequestDirection.SERVER_BOUND);
        assertTrue(
                MessageCodec.decode(
                                Arrays.copyOf(general, general.length - 2),
                                RequestDirection.SERVER_BOUND)
                        .isEmpty());
    }
}
