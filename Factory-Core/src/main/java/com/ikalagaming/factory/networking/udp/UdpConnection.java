package com.ikalagaming.factory.networking.udp;

import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.factory.networking.base.RequestHandler;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Consumer;

/**
 * One connection over UDP, at either end. Requests sent through it are written on the caller's
 * thread and queued on the network thread, which owns the {@link PacketEndpoint}; requests that
 * arrive go to the local handler from the network thread.
 */
@Slf4j
public final class UdpConnection implements Connection {

    /** How many times a disconnect is sent, since it gets no answer. */
    private static final int DISCONNECT_COPIES = 3;

    /** The socket. */
    private final Channel channel;

    /**
     * The far end's address.
     *
     * @return The address.
     */
    @Getter private final InetSocketAddress remote;

    /** The reliability layer. Network thread only. */
    private final PacketEndpoint endpoint;

    /** Which way requests sent through this go. */
    private final RequestDirection outgoing;

    /** Which way requests that arrive came. */
    private final RequestDirection incoming;

    /** The share of outgoing packets to drop, for testing over a perfect network; 0 normally. */
    private final double dropShare;

    /** Decides which packets to drop. Network thread only. */
    private final SplittableRandom random = new SplittableRandom();

    /** Told once when the connection closes, from the network thread. */
    private final Consumer<UdpConnection> onClose;

    /**
     * Handles requests that arrive.
     *
     * @return The handler, or null until set.
     */
    @Getter private volatile RequestHandler local;

    /** Whether the connection has closed. */
    private volatile boolean closed;

    /**
     * A connection.
     *
     * @param channel The socket.
     * @param remote The far end's address.
     * @param endpoint The reliability layer, with the connection's id.
     * @param outgoing Which way requests sent through this go.
     * @param dropShare The share of outgoing packets to drop, for testing; 0 normally.
     * @param onClose Told once when the connection closes.
     */
    UdpConnection(
            @NonNull Channel channel,
            @NonNull InetSocketAddress remote,
            @NonNull PacketEndpoint endpoint,
            @NonNull RequestDirection outgoing,
            double dropShare,
            @NonNull Consumer<UdpConnection> onClose) {
        this.channel = channel;
        this.remote = remote;
        this.endpoint = endpoint;
        this.outgoing = outgoing;
        incoming =
                outgoing == RequestDirection.SERVER_BOUND
                        ? RequestDirection.CLIENT_BOUND
                        : RequestDirection.SERVER_BOUND;
        this.dropShare = dropShare;
        this.onClose = onClose;
    }

    /**
     * Set what handles requests that arrive. Must be set before any data arrives.
     *
     * @param handler The handler.
     */
    void setLocal(@NonNull RequestHandler handler) {
        local = handler;
    }

    /**
     * The connection's id.
     *
     * @return The id.
     */
    public long getConnectionId() {
        return endpoint.getConnectionId();
    }

    @Override
    public void connect() {
        // Connected by the handshake before this exists
    }

    @Override
    public void disconnect() {
        channel.eventLoop().execute(() -> close(true));
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public void receive(@NonNull Request request) {
        final RequestHandler handler = local;
        if (handler != null) {
            handler.process(request);
        }
    }

    @Override
    public void send(@NonNull Request request) {
        if (closed) {
            return;
        }
        final byte[] bytes;
        try {
            bytes = MessageCodec.encode(request, outgoing);
        } catch (RuntimeException e) {
            log.warn("Could not write {} to {}", request.getClass().getSimpleName(), remote, e);
            return;
        }
        final boolean reliable = request.isReliable();
        channel.eventLoop()
                .execute(
                        () -> {
                            if (closed) {
                                return;
                            }
                            try {
                                endpoint.queue(bytes, reliable);
                            } catch (IllegalArgumentException e) {
                                log.warn(
                                        "Could not send {} to {}",
                                        request.getClass().getSimpleName(),
                                        remote,
                                        e);
                                return;
                            }
                            flush(System.nanoTime());
                        });
    }

    /**
     * Whether the connection has closed.
     *
     * @return True once closed.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Take a data packet. Network thread only.
     *
     * @param packet The packet.
     * @param now The time.
     */
    void onData(byte[] packet, long now) {
        if (closed) {
            return;
        }
        for (PacketEndpoint.Delivered delivered : endpoint.receive(packet, now)) {
            final Optional<Request> request = MessageCodec.decode(delivered.data(), incoming);
            if (request.isEmpty()) {
                log.warn("{} sent a message this can't read; closing", remote);
                close(true);
                return;
            }
            receive(request.get());
        }
        if (endpoint.isBroken()) {
            log.warn("{} sent packets this can't make sense of; closing", remote);
            close(true);
        }
    }

    /**
     * Send what is due, and close the connection if the far end has gone quiet. Network thread
     * only.
     *
     * @param now The time.
     */
    void tick(long now) {
        if (closed) {
            return;
        }
        if (endpoint.isTimedOut(now)) {
            log.info("{} timed out", remote);
            close(false);
            return;
        }
        flush(now);
    }

    /**
     * Send the packets the endpoint has ready. Network thread only.
     *
     * @param now The time.
     */
    private void flush(long now) {
        for (byte[] packet : endpoint.poll(now)) {
            write(packet);
        }
    }

    /**
     * Put a packet on the wire, unless testing drops it.
     *
     * @param packet The packet.
     */
    private void write(byte[] packet) {
        if (dropShare > 0 && random.nextDouble() < dropShare) {
            return;
        }
        channel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(packet), remote));
    }

    /**
     * Close the connection. Network thread only.
     *
     * @param tellFarEnd Whether to send a disconnect.
     */
    void close(boolean tellFarEnd) {
        if (closed) {
            return;
        }
        closed = true;
        if (tellFarEnd) {
            final ByteBuffer packet = ByteBuffer.allocate(PacketFormat.PREFIX + Long.BYTES);
            packet.putInt(PacketFormat.MAGIC)
                    .put(PacketFormat.DISCONNECT)
                    .putLong(endpoint.getConnectionId());
            for (int i = 0; i < DISCONNECT_COPIES; ++i) {
                channel.writeAndFlush(
                        new DatagramPacket(Unpooled.wrappedBuffer(packet.array()), remote));
            }
        }
        onClose.accept(this);
    }

    /**
     * The round trip the reliability layer measures.
     *
     * @return The round trip in milliseconds, or -1 before one is measured.
     */
    public double roundTripMillis() {
        return endpoint.roundTripMillis();
    }

    /**
     * Packets sent.
     *
     * @return The count.
     */
    public long packetsSent() {
        return endpoint.getPacketsSent();
    }

    /**
     * Packets sent that were never acked.
     *
     * @return The count.
     */
    public long packetsLost() {
        return endpoint.getPacketsLost();
    }

    /**
     * Reliable fragments sent again.
     *
     * @return The count.
     */
    public long resends() {
        return endpoint.getResends();
    }
}
