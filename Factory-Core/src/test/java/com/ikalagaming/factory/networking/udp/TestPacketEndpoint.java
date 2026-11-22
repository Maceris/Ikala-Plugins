package com.ikalagaming.factory.networking.udp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.SplittableRandom;

/**
 * Tests for the reliability layer: two endpoints in virtual time over a network that delays,
 * reorders, duplicates and loses packets.
 */
class TestPacketEndpoint {

    /** A millisecond in nanoseconds. */
    private static final long MS = 1_000_000;

    /** The connection id both ends use. */
    private static final long ID = 0x1234_5678_9ABCL;

    /**
     * A bad network.
     *
     * @param delayMs The least one-way time.
     * @param jitterMs The most extra time.
     * @param loss The share of packets lost.
     * @param duplicate The share of packets that arrive twice.
     */
    private record Network(long delayMs, long jitterMs, double loss, double duplicate) {}

    /**
     * A packet on its way.
     *
     * @param at When it arrives.
     * @param order Breaks ties.
     * @param data Its bytes.
     * @param toB Whether it goes to B (else to A).
     */
    private record InFlight(long at, long order, byte[] data, boolean toB) {}

    /** Two endpoints and the network between them. */
    private static final class Pair {
        final PacketEndpoint a;
        final PacketEndpoint b;
        final Network network;
        final SplittableRandom random = new SplittableRandom(9);
        final PriorityQueue<InFlight> wire =
                new PriorityQueue<>(
                        (x, y) ->
                                x.at() != y.at()
                                        ? Long.compare(x.at(), y.at())
                                        : Long.compare(x.order(), y.order()));
        final List<PacketEndpoint.Delivered> atA = new ArrayList<>();
        final List<PacketEndpoint.Delivered> atB = new ArrayList<>();
        long now;
        long order;
        long packetsOnWire;

        Pair(Network network, PacketEndpoint.Settings settings) {
            this.network = network;
            a = new PacketEndpoint(ID, settings, 0);
            b = new PacketEndpoint(ID, settings, 0);
        }

        void send(List<byte[]> packets, boolean toB) {
            for (byte[] packet : packets) {
                ++packetsOnWire;
                assertTrue(packet.length <= PacketFormat.MAX_PACKET, "packet too big");
                if (random.nextDouble() < network.loss()) {
                    continue;
                }
                final int copies = random.nextDouble() < network.duplicate() ? 2 : 1;
                for (int i = 0; i < copies; ++i) {
                    final long jitter =
                            network.jitterMs() == 0 ? 0 : random.nextLong(network.jitterMs() * MS);
                    wire.add(
                            new InFlight(
                                    now + network.delayMs() * MS + jitter, order++, packet, toB));
                }
            }
        }

        /** Run for a time, one millisecond at a time. */
        void run(long ms) {
            final long end = now + ms * MS;
            while (now < end) {
                now += MS;
                while (!wire.isEmpty() && wire.peek().at() <= now) {
                    final InFlight packet = wire.poll();
                    if (packet.toB()) {
                        atB.addAll(b.receive(packet.data(), now));
                    } else {
                        atA.addAll(a.receive(packet.data(), now));
                    }
                }
                send(a.poll(now), true);
                send(b.poll(now), false);
            }
        }
    }

    /**
     * A message of a size, numbered so order shows.
     *
     * @param number Its number.
     * @param size Its size, at least 4.
     * @return The bytes.
     */
    private static byte[] message(int number, int size) {
        final ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putInt(number);
        for (int i = 4; i < size; ++i) {
            buffer.put((byte) (number * 31 + i));
        }
        return buffer.array();
    }

    /**
     * Reliable messages of all sizes, from empty to many fragments, arrive once each, whole and in
     * order, over a network that loses a fifth of its packets and reorders and duplicates others.
     */
    @Test
    void testReliable() {
        final Pair pair = new Pair(new Network(30, 20, 0.2, 0.05), PacketEndpoint.Settings.DEFAULT);
        final List<byte[]> sent = new ArrayList<>();
        final SplittableRandom sizes = new SplittableRandom(3);
        for (int i = 0; i < 300; ++i) {
            final int size = i % 50 == 0 ? 4 + sizes.nextInt(200_000) : 4 + sizes.nextInt(3000);
            sent.add(message(i, size));
        }
        sent.add(new byte[0]);
        for (byte[] message : sent) {
            pair.a.queue(message, true);
        }
        pair.run(20_000);
        final List<PacketEndpoint.Delivered> got = pair.atB;
        assertEquals(sent.size(), got.size());
        for (int i = 0; i < sent.size(); ++i) {
            assertTrue(got.get(i).reliable());
            assertArrayEquals(sent.get(i), got.get(i).data(), "message " + i);
        }
        assertEquals(0, pair.a.pendingFragments());
        assertTrue(pair.a.getResends() > 0, "nothing was resent at 20% loss");
        assertFalse(pair.a.isBroken() || pair.b.isBroken());
    }

    /** Unreliable messages arrive about as often as packets survive, and go first. */
    @Test
    void testUnreliable() {
        final Pair pair = new Pair(new Network(10, 5, 0.1, 0), PacketEndpoint.Settings.DEFAULT);
        int sent = 0;
        for (int i = 0; i < 1000; ++i) {
            pair.a.queue(message(i, 100), false);
            ++sent;
            pair.run(5);
        }
        pair.run(100);
        final int got = pair.atB.size();
        assertTrue(got > sent * 0.85 && got <= sent, got + " of " + sent);
        assertThrows(
                IllegalArgumentException.class,
                () -> pair.a.queue(new byte[PacketFormat.MAX_UNRELIABLE + 1], false));
    }

    /** Both ends measure the round trip from acks, with only one side sending data. */
    @Test
    void testRoundTripAndAcks() {
        final Pair pair = new Pair(new Network(40, 0, 0, 0), PacketEndpoint.Settings.DEFAULT);
        for (int i = 0; i < 200; ++i) {
            pair.a.queue(message(i, 1000), true);
            pair.run(16);
        }
        pair.run(500);
        assertEquals(200, pair.atB.size());
        assertEquals(0, pair.a.pendingFragments());
        // 80 ms each way and back, plus up to the 10 ms ack delay and a millisecond of stepping
        assertTrue(
                pair.a.roundTripMillis() >= 80 && pair.a.roundTripMillis() < 95,
                "round trip " + pair.a.roundTripMillis());
    }

    /** The budget paces reliable traffic, while unreliable messages still go at once. */
    @Test
    void testPacing() {
        final Pair pair =
                new Pair(new Network(5, 0, 0, 0), new PacketEndpoint.Settings(100_000, 10_000));
        for (int i = 0; i < 100; ++i) {
            pair.a.queue(message(i, 5000), true);
        }
        pair.run(1000);
        // 500 KB at 100 KB/s, after a 10 KB burst: about a fifth in the first second
        final int afterOneSecond = pair.atB.size();
        assertTrue(afterOneSecond >= 15 && afterOneSecond <= 25, afterOneSecond + " messages");
        pair.a.queue(message(-1, 50), false);
        pair.run(20);
        assertTrue(
                pair.atB.stream().anyMatch(d -> !d.reliable()),
                "the unreliable message waited behind the budget");
        pair.run(5000);
        assertEquals(101, pair.atB.size());
    }

    /** Silence times a connection out; keepalives stop that while both ends run. */
    @Test
    void testTimeout() {
        final Pair pair = new Pair(new Network(10, 0, 0, 0), PacketEndpoint.Settings.DEFAULT);
        pair.run(15_000);
        assertFalse(pair.a.isTimedOut(pair.now));
        assertFalse(pair.b.isTimedOut(pair.now));
        assertTrue(pair.a.isTimedOut(pair.now + PacketEndpoint.TIMEOUT_NANOS + MS));
    }

    /** Packets for another connection are ignored, and nonsense marks the connection broken. */
    @Test
    void testBadPackets() {
        final PacketEndpoint endpoint = new PacketEndpoint(ID, PacketEndpoint.Settings.DEFAULT, 0);
        final PacketEndpoint other = new PacketEndpoint(ID + 1, PacketEndpoint.Settings.DEFAULT, 0);
        other.queue(message(1, 10), false);
        assertTrue(endpoint.receive(other.poll(MS).get(0), MS).isEmpty());
        assertFalse(endpoint.isBroken());
        final ByteBuffer junk = ByteBuffer.allocate(PacketFormat.DATA_HEADER + 3);
        junk.putInt(PacketFormat.MAGIC).put(PacketFormat.DATA).putLong(ID);
        junk.putShort((short) 0).put((byte) 0).putShort((short) 0).putInt(0);
        junk.put((byte) 99);
        endpoint.receive(junk.array(), MS);
        assertTrue(endpoint.isBroken());
    }
}
