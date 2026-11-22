package com.ikalagaming.factory.networking;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.SplittableRandom;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A connection that behaves like a bad network, for testing the netcode without one. Each request
 * sent through it is held for a delay plus a random jitter before it goes on to the connection it
 * wraps. Unreliable requests may also be lost, and jitter can reorder them; reliable requests are
 * never lost and always arrive in order, as a real transport would make them.
 *
 * <p>It covers one direction: wrap each end's connection to make both directions bad. {@link
 * #disconnect()} stops it, dropping anything still on the way.
 */
@Slf4j
public final class SimulatedLink implements Connection {

    /**
     * How bad the link is.
     *
     * @param delayMillis The least time a request takes, one way, in milliseconds.
     * @param jitterMillis The most extra time a request can take, spread evenly, in milliseconds.
     * @param loss The share of unreliable requests lost, from 0 to 1.
     */
    public record Settings(long delayMillis, long jitterMillis, double loss) {

        /** The system property for {@link #delayMillis}. */
        public static final String DELAY_PROPERTY = "ikala.net.delay";

        /** The system property for {@link #jitterMillis}. */
        public static final String JITTER_PROPERTY = "ikala.net.jitter";

        /** The system property for {@link #loss}. */
        public static final String LOSS_PROPERTY = "ikala.net.loss";

        /** A perfect link. */
        public static final Settings PERFECT = new Settings(0, 0, 0);

        /**
         * Settings.
         *
         * @param delayMillis The least time a request takes, one way, in milliseconds.
         * @param jitterMillis The most extra time a request can take, in milliseconds.
         * @param loss The share of unreliable requests lost, from 0 to 1.
         * @throws IllegalArgumentException If a value is out of range.
         */
        public Settings {
            if (delayMillis < 0 || jitterMillis < 0 || !(loss >= 0 && loss <= 1)) {
                throw new IllegalArgumentException(
                        "A link needs a delay and jitter of 0 or more and a loss from 0 to 1, not "
                                + delayMillis
                                + ", "
                                + jitterMillis
                                + ", "
                                + loss);
            }
        }

        /**
         * The settings the system properties ask for, a perfect link if none are set.
         *
         * @return The settings.
         */
        public static Settings fromProperties() {
            try {
                return new Settings(
                        Long.getLong(DELAY_PROPERTY, 0),
                        Long.getLong(JITTER_PROPERTY, 0),
                        Double.parseDouble(System.getProperty(LOSS_PROPERTY, "0")));
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring the simulated link settings: {}", e.getMessage());
                return PERFECT;
            }
        }

        /**
         * Whether the link is anything but perfect.
         *
         * @return True if requests are held or lost.
         */
        public boolean isActive() {
            return delayMillis > 0 || jitterMillis > 0 || loss > 0;
        }
    }

    /** The connection requests go on to. */
    private final Connection inner;

    /** How bad the link is. */
    private final Settings settings;

    /**
     * A request on its way.
     *
     * @param request The request.
     * @param due When it arrives, from {@link System#nanoTime()}.
     * @param order Breaks ties in the order requests were sent, keeping reliable order.
     */
    private record InFlight(Request request, long due, long order) implements Delayed {
        @Override
        public long getDelay(@NonNull TimeUnit unit) {
            return unit.convert(due - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(@NonNull Delayed other) {
            final InFlight that = (InFlight) other;
            if (due != that.due) {
                return due - that.due < 0 ? -1 : 1;
            }
            return Long.compare(order, that.order);
        }
    }

    /** Requests on their way, soonest first. */
    private final DelayQueue<InFlight> inFlight = new DelayQueue<>();

    /** Hands requests on as they come due. */
    private final Thread delivery;

    /** The next order number. Guarded by {@link #lock}. */
    private long nextOrder;

    /** Guards {@link #random}, {@link #lastReliableNanos} and {@link #nextOrder}. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Decides delays and losses. */
    private final SplittableRandom random;

    /** When the last reliable request is due, so later ones never overtake it. */
    private long lastReliableNanos = Long.MIN_VALUE;

    /** Requests sent through the link. */
    private final AtomicLong sent = new AtomicLong();

    /** Requests lost on the way. */
    private final AtomicLong lost = new AtomicLong();

    /**
     * A link.
     *
     * @param inner The connection requests go on to.
     * @param settings How bad the link is.
     * @param seed Seeds the delays and losses, so a test can repeat them.
     */
    public SimulatedLink(@NonNull Connection inner, @NonNull Settings settings, long seed) {
        this.inner = inner;
        this.settings = settings;
        random = new SplittableRandom(seed);
        delivery = new Thread(this::deliverAll, "Simulated link");
        delivery.setDaemon(true);
        delivery.start();
    }

    @Override
    public void connect() {
        inner.connect();
    }

    @Override
    public void disconnect() {
        delivery.interrupt();
        inFlight.clear();
        inner.disconnect();
    }

    @Override
    public boolean isLocal() {
        return inner.isLocal();
    }

    @Override
    public void receive(@NonNull Request request) {
        inner.receive(request);
    }

    @Override
    public void send(@NonNull Request request) {
        sent.incrementAndGet();
        final long now = System.nanoTime();
        lock.lock();
        try {
            if (!request.isReliable() && random.nextDouble() < settings.loss()) {
                lost.incrementAndGet();
                return;
            }
            final long jitter =
                    settings.jitterMillis() == 0
                            ? 0
                            : random.nextLong(
                                    TimeUnit.MILLISECONDS.toNanos(settings.jitterMillis()) + 1);
            long at = now + TimeUnit.MILLISECONDS.toNanos(settings.delayMillis()) + jitter;
            if (request.isReliable()) {
                if (lastReliableNanos != Long.MIN_VALUE && at - lastReliableNanos < 0) {
                    at = lastReliableNanos;
                }
                lastReliableNanos = at;
            }
            if (delivery.isAlive()) {
                inFlight.add(new InFlight(request, at, nextOrder++));
            }
        } finally {
            lock.unlock();
        }
    }

    /** Hand requests on as they come due, until disconnected. */
    private void deliverAll() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                deliver(inFlight.take().request());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Pass a request on, keeping the delivery thread alive if the receiver throws.
     *
     * @param request The request.
     */
    private void deliver(Request request) {
        try {
            inner.send(request);
        } catch (RuntimeException e) {
            log.warn(
                    "Delivering {} over a simulated link failed",
                    request.getClass().getSimpleName(),
                    e);
        }
    }

    /**
     * How many requests were sent through the link.
     *
     * @return The count.
     */
    public long getSent() {
        return sent.get();
    }

    /**
     * How many requests the link lost.
     *
     * @return The count.
     */
    public long getLost() {
        return lost.get();
    }
}
