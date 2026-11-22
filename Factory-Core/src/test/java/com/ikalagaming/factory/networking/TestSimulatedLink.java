package com.ikalagaming.factory.networking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;

import lombok.NonNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Tests for the simulated bad network. */
class TestSimulatedLink {

    /**
     * A numbered request.
     *
     * @param number Its number.
     * @param reliable Whether it must arrive.
     * @param sentNanos When it was sent.
     */
    private record Numbered(int number, boolean reliable, long sentNanos) implements Request {
        @Override
        public boolean isReliable() {
            return reliable;
        }
    }

    /** Records what arrives. */
    private static final class Sink implements Connection {
        /** What arrived, in order. */
        final List<Numbered> arrived = new CopyOnWriteArrayList<>();

        /** When each arrived. */
        final List<Long> arrivedNanos = new CopyOnWriteArrayList<>();

        /** Counts arrivals down. */
        CountDownLatch expected;

        @Override
        public void connect() {}

        @Override
        public void disconnect() {}

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public void receive(@NonNull Request request) {}

        @Override
        public void send(@NonNull Request request) {
            arrived.add((Numbered) request);
            arrivedNanos.add(System.nanoTime());
            if (expected != null) {
                expected.countDown();
            }
        }
    }

    /**
     * Reliable requests all arrive, in order, after at least the delay, despite jitter and loss.
     */
    @Test
    void testReliable() throws InterruptedException {
        final Sink sink = new Sink();
        final int count = 200;
        sink.expected = new CountDownLatch(count);
        final SimulatedLink link =
                new SimulatedLink(sink, new SimulatedLink.Settings(20, 15, 0.5), 1);
        for (int i = 0; i < count; ++i) {
            link.send(new Numbered(i, true, System.nanoTime()));
        }
        assertTrue(sink.expected.await(5, TimeUnit.SECONDS), "not all arrived");
        for (int i = 0; i < count; ++i) {
            final Numbered got = sink.arrived.get(i);
            assertEquals(i, got.number());
            assertTrue(
                    sink.arrivedNanos.get(i) - got.sentNanos() >= TimeUnit.MILLISECONDS.toNanos(20),
                    "arrived early");
        }
        assertEquals(0, link.getLost());
        link.disconnect();
    }

    /** About the set share of unreliable requests is lost, and the rest arrive. */
    @Test
    void testUnreliable() throws InterruptedException {
        final Sink sink = new Sink();
        final int count = 2000;
        final SimulatedLink link =
                new SimulatedLink(sink, new SimulatedLink.Settings(1, 4, 0.25), 2);
        for (int i = 0; i < count; ++i) {
            link.send(new Numbered(i, false, System.nanoTime()));
        }
        final long lost = link.getLost();
        assertTrue(lost > count * 0.2 && lost < count * 0.3, lost + " lost");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (sink.arrived.size() < count - lost && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertEquals(count - lost, sink.arrived.size());
        boolean reordered = false;
        for (int i = 1; i < sink.arrived.size(); ++i) {
            reordered |= sink.arrived.get(i).number() < sink.arrived.get(i - 1).number();
        }
        assertTrue(reordered, "jitter never reordered anything");
        link.disconnect();
    }

    /** Settings are checked, and a perfect link is not active. */
    @Test
    void testSettings() {
        assertTrue(!SimulatedLink.Settings.PERFECT.isActive());
        assertTrue(new SimulatedLink.Settings(0, 0, 0.01).isActive());
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new SimulatedLink.Settings(-1, 0, 0));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> new SimulatedLink.Settings(0, 0, 1.5));
    }
}
