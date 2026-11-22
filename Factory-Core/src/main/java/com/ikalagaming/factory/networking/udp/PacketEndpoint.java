package com.ikalagaming.factory.networking.udp;

import lombok.Getter;
import lombok.NonNull;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * One end of a connection's data packets: the reliability layer on top of UDP, with no sockets in
 * it, so it can be tested in any time. Not safe for more than one thread; the network thread owns
 * it. Times are from {@link System#nanoTime()}.
 *
 * <ul>
 *   <li><b>Acks:</b> every packet carries the newest sequence received and a 32-bit field of the
 *       ones before it. A packet acked is never resent; its reliable fragments are done.
 *   <li><b>Reliable messages</b> are split into fragments with consecutive ids. A fragment is sent
 *       again if its packet isn't acked within the resend time (about one and a half round trips).
 *       The far end hands messages over whole and in order.
 *   <li><b>Unreliable messages</b> go once, first, in whatever packet goes next.
 *   <li><b>Pacing:</b> reliable fragments spend a byte budget that refills at a set rate, so a
 *       burst of chunks can't crowd out inputs and states.
 *   <li><b>Acks without data:</b> if packets arrive and nothing goes back for a while, a packet
 *       with no entries carries the acks; one also goes every so often to keep the connection
 *       alive.
 * </ul>
 */
public final class PacketEndpoint {

    /**
     * How fast reliable traffic may go.
     *
     * @param bytesPerSecond The rate the budget refills at.
     * @param burstBytes The most the budget holds.
     */
    public record Settings(long bytesPerSecond, long burstBytes) {
        /** 4 MB/s, in bursts of up to 64 KB. */
        public static final Settings DEFAULT = new Settings(4_000_000, 64 * 1024);
    }

    /**
     * A message that arrived.
     *
     * @param data The message's bytes.
     * @param reliable Whether it came reliably.
     */
    public record Delivered(byte[] data, boolean reliable) {}

    /** How many sent packets are remembered for acks. */
    private static final int SENT_SLOTS = 1024;

    /** How many reliable fragments may be unacked at once. */
    static final int SEND_WINDOW = 1024;

    /** How many fragment ids ahead the receiver holds; at least {@link #SEND_WINDOW}. */
    static final int RECEIVE_WINDOW = 4096;

    /** Packets received since the last one sent, after which acks go back at once. */
    private static final int ACK_EVERY = 16;

    /**
     * How long acks wait for a packet to ride on, from the last packet sent. Longer than a command
     * frame, so the per-frame messages each way carry them and no packet goes for acks alone.
     */
    private static final long ACK_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(25);

    /** How long between packets when there is nothing to say. */
    private static final long KEEPALIVE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    /** How long without a packet before the connection is dead. */
    static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** The resend time before a round trip is measured. */
    private static final long FIRST_RESEND_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    /** The shortest resend time. */
    private static final long MIN_RESEND_NANOS = TimeUnit.MILLISECONDS.toNanos(40);

    /** The longest resend time. */
    private static final long MAX_RESEND_NANOS = TimeUnit.SECONDS.toNanos(1);

    /** How much each round trip sample moves the estimate. */
    private static final double SMOOTHING = 0.1;

    /** A fragment never sent. */
    private static final long NEVER = Long.MIN_VALUE;

    /** A reliable fragment on the sending side. */
    private static final class Fragment {
        /** Its id, once it is in the window. */
        int id;

        /** Its place in its message. */
        final int index;

        /** How many fragments its message has. */
        final int count;

        /** Its bytes. */
        final byte[] data;

        /** When it was last sent. */
        long lastSent = NEVER;

        /** Whether a packet holding it was acked. */
        boolean acked;

        /**
         * A fragment.
         *
         * @param index Its place in its message.
         * @param count How many fragments its message has.
         * @param data Its bytes.
         */
        Fragment(int index, int count, byte[] data) {
            this.index = index;
            this.count = count;
            this.data = data;
        }
    }

    /** A packet sent, remembered until acked or forgotten. */
    private static final class SentPacket {
        /** Its sequence. */
        final int sequence;

        /** When it went. */
        final long sentNanos;

        /** The reliable fragments in it. */
        final int[] fragments;

        /** Whether it was acked. */
        boolean acked;

        /**
         * A record.
         *
         * @param sequence Its sequence.
         * @param sentNanos When it went.
         * @param fragments The reliable fragments in it.
         */
        SentPacket(int sequence, long sentNanos, int[] fragments) {
            this.sequence = sequence;
            this.sentNanos = sentNanos;
            this.fragments = fragments;
        }
    }

    /**
     * A reliable fragment that arrived.
     *
     * @param id Its id.
     * @param index Its place in its message.
     * @param count How many fragments its message has.
     * @param data Its bytes.
     */
    private record Incoming(int id, int index, int count, byte[] data) {}

    /** The connection's id, in every data packet. */
    @Getter private final long connectionId;

    /** How fast reliable traffic may go. */
    private final Settings settings;

    /** The next packet's sequence. */
    private int nextSequence;

    /** Packets sent, by sequence slot. */
    private final SentPacket[] sent = new SentPacket[SENT_SLOTS];

    /** Unreliable messages waiting for the next packet. */
    private final ArrayDeque<byte[]> unreliable = new ArrayDeque<>();

    /** Reliable fragments waiting for room in the window. */
    private final ArrayDeque<Fragment> waiting = new ArrayDeque<>();

    /** Fragments in the window, by id slot. */
    private final Fragment[] window = new Fragment[SEND_WINDOW];

    /** The oldest fragment id still in the window. */
    private int oldestId;

    /** The next fragment id to hand out. */
    private int nextId;

    /** The reliable budget, in bytes. */
    private double budget;

    /** When the budget last refilled. */
    private long budgetNanos;

    /** The newest sequence received, or -1 before any. */
    private int latestReceived = -1;

    /** Which of the 32 sequences before {@link #latestReceived} arrived. */
    private int receivedBits;

    /** Packets received since the last one sent. */
    private int receivedSinceSend;

    /** When a packet was last sent. */
    private long lastSendNanos;

    /** When a packet last arrived. */
    private long lastReceiveNanos;

    /** Fragments that arrived, by id slot. */
    private final Incoming[] incoming = new Incoming[RECEIVE_WINDOW];

    /** The next fragment id to hand over. */
    private int expectedId;

    /** The round trip, smoothed, in nanoseconds; negative until measured. */
    private double roundTripNanos = -1;

    /**
     * Packets sent.
     *
     * @return The count.
     */
    @Getter private long packetsSent;

    /**
     * Packets sent that were forgotten without an ack: lost, or acked too late to count.
     *
     * @return The count.
     */
    @Getter private long packetsLost;

    /**
     * Reliable fragments sent again.
     *
     * @return The count.
     */
    @Getter private long resends;

    /**
     * Bytes of packets sent, without the IP and UDP headers.
     *
     * @return The count.
     */
    @Getter private long bytesSent;

    /**
     * Bytes of packets received for this connection, without the IP and UDP headers.
     *
     * @return The count.
     */
    @Getter private long bytesReceived;

    /**
     * Whether the far end sent something this can't make sense of, so the connection should close.
     *
     * @return True if it did.
     */
    @Getter private boolean broken;

    /**
     * An endpoint.
     *
     * @param connectionId The connection's id.
     * @param settings How fast reliable traffic may go.
     * @param now The time, which counts as hearing from the far end.
     */
    public PacketEndpoint(long connectionId, @NonNull Settings settings, long now) {
        this.connectionId = connectionId;
        this.settings = settings;
        budget = settings.burstBytes();
        budgetNanos = now;
        lastReceiveNanos = now;
        lastSendNanos = now;
    }

    /**
     * Queue a message.
     *
     * @param message The message's bytes, which nothing may change afterward.
     * @param reliable Whether it must arrive.
     * @throws IllegalArgumentException If an unreliable message doesn't fit one packet, or a
     *     reliable one is over {@value PacketFormat#MAX_FRAGMENTS} fragments.
     */
    public void queue(byte @NonNull [] message, boolean reliable) {
        if (!reliable) {
            if (message.length > PacketFormat.MAX_UNRELIABLE) {
                throw new IllegalArgumentException(
                        "An unreliable message of "
                                + message.length
                                + " bytes doesn't fit a packet; at most "
                                + PacketFormat.MAX_UNRELIABLE);
            }
            unreliable.add(message);
            return;
        }
        final int count = Math.max(1, Math.ceilDiv(message.length, PacketFormat.MAX_FRAGMENT));
        if (count > PacketFormat.MAX_FRAGMENTS) {
            throw new IllegalArgumentException(
                    "A reliable message of " + message.length + " bytes is too big to send");
        }
        for (int i = 0; i < count; ++i) {
            final int from = i * PacketFormat.MAX_FRAGMENT;
            final int to = Math.min(message.length, from + PacketFormat.MAX_FRAGMENT);
            waiting.add(new Fragment(i, count, Arrays.copyOfRange(message, from, to)));
        }
    }

    /**
     * The packets to send now: queued unreliable messages, reliable fragments that are new or due
     * again as far as the budget allows, and acks or a keepalive when due.
     *
     * @param now The time.
     * @return The packets, possibly none.
     */
    public List<byte[]> poll(long now) {
        refill(now);
        admit();
        final List<byte[]> out = new ArrayList<>();
        final long resendAfter = resendNanos();
        int scan = oldestId;
        while (true) {
            final ByteBuffer packet = ByteBuffer.allocate(PacketFormat.MAX_PACKET);
            final int sequence = nextSequence;
            packet.putInt(PacketFormat.MAGIC)
                    .put(PacketFormat.DATA)
                    .putLong(connectionId)
                    .putShort((short) sequence)
                    .put((byte) (latestReceived < 0 ? 0 : 1))
                    .putShort((short) Math.max(0, latestReceived))
                    .putInt(latestReceived < 0 ? 0 : receivedBits);
            boolean payload = false;
            while (!unreliable.isEmpty()
                    && packet.remaining()
                            >= PacketFormat.UNRELIABLE_HEADER + unreliable.peek().length) {
                final byte[] message = unreliable.poll();
                packet.put(PacketFormat.UNRELIABLE).putShort((short) message.length).put(message);
                payload = true;
            }
            final List<Integer> ids = new ArrayList<>();
            while (scan != nextId) {
                final Fragment fragment = window[scan & (SEND_WINDOW - 1)];
                if (fragment == null
                        || fragment.acked
                        || (fragment.lastSent != NEVER && now - fragment.lastSent < resendAfter)) {
                    scan = Sequences.add(scan, 1);
                    continue;
                }
                final int size = PacketFormat.FRAGMENT_HEADER + fragment.data.length;
                if (packet.remaining() < size || budget < fragment.data.length) {
                    break;
                }
                packet.put(PacketFormat.RELIABLE)
                        .putShort((short) fragment.id)
                        .putShort((short) fragment.index)
                        .putShort((short) fragment.count)
                        .putShort((short) fragment.data.length)
                        .put(fragment.data);
                if (fragment.lastSent != NEVER) {
                    ++resends;
                }
                fragment.lastSent = now;
                budget -= fragment.data.length;
                ids.add(fragment.id);
                payload = true;
                scan = Sequences.add(scan, 1);
            }
            final boolean ackDue =
                    receivedSinceSend >= ACK_EVERY
                            || (receivedSinceSend > 0 && now - lastSendNanos >= ACK_DELAY_NANOS)
                            || now - lastSendNanos >= KEEPALIVE_NANOS;
            if (!payload && !ackDue) {
                break;
            }
            final int slot = sequence & (SENT_SLOTS - 1);
            if (sent[slot] != null && !sent[slot].acked) {
                ++packetsLost;
            }
            sent[slot] =
                    new SentPacket(
                            sequence, now, ids.stream().mapToInt(Integer::intValue).toArray());
            nextSequence = Sequences.add(sequence, 1);
            ++packetsSent;
            bytesSent += packet.position();
            receivedSinceSend = 0;
            lastSendNanos = now;
            out.add(Arrays.copyOf(packet.array(), packet.position()));
            if (!payload) {
                break;
            }
        }
        return out;
    }

    /**
     * Take a data packet from the far end.
     *
     * @param packet The packet, already known to be a data packet for this connection.
     * @param now The time.
     * @return The messages it completed: unreliable ones at once, reliable ones whole and in order.
     */
    public List<Delivered> receive(byte @NonNull [] packet, long now) {
        final List<Delivered> out = new ArrayList<>();
        bytesReceived += packet.length;
        final ByteBuffer in = ByteBuffer.wrap(packet);
        try {
            if (in.getInt() != PacketFormat.MAGIC
                    || in.get() != PacketFormat.DATA
                    || in.getLong() != connectionId) {
                return out;
            }
            final int sequence = Short.toUnsignedInt(in.getShort());
            final boolean hasAcks = in.get() != 0;
            final int ack = Short.toUnsignedInt(in.getShort());
            final int ackBits = in.getInt();
            lastReceiveNanos = now;
            ++receivedSinceSend;
            noteReceived(sequence);
            if (hasAcks) {
                takeAcks(ack, ackBits, now);
            }
            while (in.hasRemaining()) {
                final byte kind = in.get();
                if (kind == PacketFormat.UNRELIABLE) {
                    final byte[] data = new byte[Short.toUnsignedInt(in.getShort())];
                    in.get(data);
                    out.add(new Delivered(data, false));
                } else if (kind == PacketFormat.RELIABLE) {
                    final int id = Short.toUnsignedInt(in.getShort());
                    final int index = Short.toUnsignedInt(in.getShort());
                    final int count = Short.toUnsignedInt(in.getShort());
                    final byte[] data = new byte[Short.toUnsignedInt(in.getShort())];
                    in.get(data);
                    if (count == 0 || count > PacketFormat.MAX_FRAGMENTS || index >= count) {
                        broken = true;
                        return out;
                    }
                    if (Sequences.distance(id, expectedId) < RECEIVE_WINDOW) {
                        incoming[id & (RECEIVE_WINDOW - 1)] = new Incoming(id, index, count, data);
                    }
                } else {
                    broken = true;
                    return out;
                }
            }
        } catch (BufferUnderflowException e) {
            broken = true;
            return out;
        }
        handOver(out);
        return out;
    }

    /**
     * Whether nothing has come from the far end for too long.
     *
     * @param now The time.
     * @return True if the connection should be dropped.
     */
    public boolean isTimedOut(long now) {
        return now - lastReceiveNanos > TIMEOUT_NANOS;
    }

    /**
     * The round trip, smoothed.
     *
     * @return The round trip in milliseconds, or -1 before one is measured.
     */
    public double roundTripMillis() {
        return roundTripNanos < 0 ? -1 : roundTripNanos / 1_000_000;
    }

    /**
     * How many reliable fragments are not yet acked, waiting or in flight.
     *
     * @return The count.
     */
    public int pendingFragments() {
        return waiting.size() + Sequences.distance(nextId, oldestId);
    }

    /**
     * Refill the reliable budget for the time passed.
     *
     * @param now The time.
     */
    private void refill(long now) {
        final long elapsed = now - budgetNanos;
        if (elapsed > 0) {
            budget =
                    Math.min(
                            settings.burstBytes(),
                            budget + elapsed * (double) settings.bytesPerSecond() / 1e9);
            budgetNanos = now;
        }
    }

    /** Move waiting fragments into the window while it has room, giving them ids. */
    private void admit() {
        while (!waiting.isEmpty() && Sequences.distance(nextId, oldestId) < SEND_WINDOW) {
            final Fragment fragment = waiting.poll();
            fragment.id = nextId;
            window[nextId & (SEND_WINDOW - 1)] = fragment;
            nextId = Sequences.add(nextId, 1);
        }
    }

    /**
     * How long a fragment waits for its ack before it goes again.
     *
     * @return The time, in nanoseconds.
     */
    private long resendNanos() {
        if (roundTripNanos < 0) {
            return FIRST_RESEND_NANOS;
        }
        return Math.clamp(
                (long) (roundTripNanos * 1.5) + TimeUnit.MILLISECONDS.toNanos(20),
                MIN_RESEND_NANOS,
                MAX_RESEND_NANOS);
    }

    /**
     * Note a sequence received, for the acks sent back.
     *
     * @param sequence The sequence.
     */
    private void noteReceived(int sequence) {
        if (latestReceived < 0) {
            latestReceived = sequence;
            receivedBits = 0;
            return;
        }
        if (Sequences.newer(sequence, latestReceived)) {
            final int distance = Sequences.distance(sequence, latestReceived);
            if (distance < Integer.SIZE) {
                receivedBits = (receivedBits << distance) | (1 << (distance - 1));
            } else if (distance == Integer.SIZE) {
                receivedBits = 1 << (Integer.SIZE - 1);
            } else {
                receivedBits = 0;
            }
            latestReceived = sequence;
            return;
        }
        final int distance = Sequences.distance(latestReceived, sequence);
        if (distance >= 1 && distance <= Integer.SIZE) {
            receivedBits |= 1 << (distance - 1);
        }
    }

    /**
     * Mark the packets the far end acked, and the fragments in them.
     *
     * @param ack The newest sequence the far end received.
     * @param ackBits Which of the 32 before it arrived.
     * @param now The time.
     */
    private void takeAcks(int ack, int ackBits, long now) {
        for (int i = 0; i <= Integer.SIZE; ++i) {
            if (i > 0 && (ackBits & (1 << (i - 1))) == 0) {
                continue;
            }
            final int sequence = Sequences.add(ack, -i);
            final SentPacket packet = sent[sequence & (SENT_SLOTS - 1)];
            if (packet == null || packet.sequence != sequence || packet.acked) {
                continue;
            }
            packet.acked = true;
            final double sample = now - packet.sentNanos;
            roundTripNanos =
                    roundTripNanos < 0
                            ? sample
                            : roundTripNanos + SMOOTHING * (sample - roundTripNanos);
            for (int id : packet.fragments) {
                final Fragment fragment = window[id & (SEND_WINDOW - 1)];
                if (fragment != null && fragment.id == id) {
                    fragment.acked = true;
                }
            }
        }
        while (oldestId != nextId) {
            final int slot = oldestId & (SEND_WINDOW - 1);
            if (window[slot] != null && !window[slot].acked) {
                break;
            }
            window[slot] = null;
            oldestId = Sequences.add(oldestId, 1);
        }
    }

    /**
     * Hand over the reliable messages that are complete, in order.
     *
     * @param out Where to add them.
     */
    private void handOver(List<Delivered> out) {
        while (true) {
            final Incoming first = incoming[expectedId & (RECEIVE_WINDOW - 1)];
            if (first == null || first.id() != expectedId) {
                return;
            }
            if (first.index() != 0) {
                broken = true;
                return;
            }
            int length = 0;
            for (int i = 0; i < first.count(); ++i) {
                final int id = Sequences.add(expectedId, i);
                final Incoming part = incoming[id & (RECEIVE_WINDOW - 1)];
                if (part == null || part.id() != id) {
                    return;
                }
                if (part.index() != i || part.count() != first.count()) {
                    broken = true;
                    return;
                }
                length += part.data().length;
            }
            final byte[] message = new byte[length];
            int at = 0;
            for (int i = 0; i < first.count(); ++i) {
                final int slot = Sequences.add(expectedId, i) & (RECEIVE_WINDOW - 1);
                final byte[] data = incoming[slot].data();
                System.arraycopy(data, 0, message, at, data.length);
                at += data.length;
                incoming[slot] = null;
            }
            expectedId = Sequences.add(expectedId, first.count());
            out.add(new Delivered(message, true));
        }
    }
}
