package com.ikalagaming.factory.networking.udp;

import com.ikalagaming.factory.networking.base.RequestDirection;
import com.ikalagaming.factory.networking.base.RequestHandler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A server's UDP socket: it answers the connect handshake and runs every connection's packets on
 * one network thread. See {@link PacketFormat} for the handshake.
 */
@Slf4j
public final class UdpServer implements AutoCloseable {

    /**
     * The system property for the share of outgoing packets to drop at both ends, for testing the
     * reliability layer over a perfect network.
     */
    public static final String PACKET_LOSS_PROPERTY = "ikala.net.packetLoss";

    /** How often connections send what is due, in milliseconds. */
    static final long TICK_MILLIS = 5;

    /** How long a challenge token stays good: the current time bucket and the one before. */
    private static final long TOKEN_BUCKET_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** The token's MAC algorithm. */
    private static final String MAC_ALGORITHM = "HmacSHA256";

    /** Runs the socket and every connection. */
    private final EventLoopGroup group;

    /** The socket. */
    private final Channel channel;

    /** Makes a connection's local handler; told on the network thread. */
    private final Function<UdpConnection, RequestHandler> acceptor;

    /** Told when a connection closes, on the network thread. */
    private final Consumer<UdpConnection> onClose;

    /** How fast each connection's reliable traffic may go. */
    private final PacketEndpoint.Settings settings;

    /** The share of outgoing packets to drop, for testing. */
    private final double dropShare;

    /** Connections by address. Network thread only. */
    private final Map<InetSocketAddress, UdpConnection> connections = new HashMap<>();

    /** The salt each connection's client sent, by address. Network thread only. */
    private final Map<InetSocketAddress, Long> salts = new HashMap<>();

    /** Signs challenge tokens. Network thread only. */
    private final Mac mac;

    /** Makes connection ids. */
    private final SecureRandom random = new SecureRandom();

    /**
     * Start listening.
     *
     * @param bind The address and port to listen on; port 0 picks a free one.
     * @param acceptor Makes each new connection's handler for the requests that arrive, on the
     *     network thread.
     * @param onClose Told when a connection closes, on the network thread.
     * @param settings How fast each connection's reliable traffic may go.
     * @param dropShare The share of outgoing packets to drop, for testing; 0 normally.
     * @throws IllegalStateException If the socket can't be opened.
     */
    public UdpServer(
            @NonNull InetSocketAddress bind,
            @NonNull Function<UdpConnection, RequestHandler> acceptor,
            @NonNull Consumer<UdpConnection> onClose,
            @NonNull PacketEndpoint.Settings settings,
            double dropShare) {
        this.acceptor = acceptor;
        this.onClose = onClose;
        this.settings = settings;
        this.dropShare = dropShare;
        try {
            final byte[] secret = new byte[32];
            random.nextBytes(secret);
            mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, MAC_ALGORITHM));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No " + MAC_ALGORITHM + " for connect tokens", e);
        }
        group = new NioEventLoopGroup(1, UdpServer::thread);
        try {
            channel =
                    new Bootstrap()
                            .group(group)
                            .channel(NioDatagramChannel.class)
                            .handler(new Handler())
                            .bind(bind)
                            .sync()
                            .channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            group.shutdownGracefully();
            throw new IllegalStateException("Interrupted while binding " + bind, e);
        } catch (RuntimeException e) {
            group.shutdownGracefully();
            throw new IllegalStateException("Could not listen on " + bind, e);
        }
        channel.eventLoop()
                .scheduleAtFixedRate(this::tick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
        log.info("Listening for players on UDP {}", channel.localAddress());
    }

    /**
     * The share of outgoing packets to drop, from {@value #PACKET_LOSS_PROPERTY}.
     *
     * @return The share, 0 unless set.
     */
    public static double packetLossFromProperties() {
        try {
            return Math.clamp(
                    Double.parseDouble(System.getProperty(PACKET_LOSS_PROPERTY, "0")), 0, 1);
        } catch (NumberFormatException e) {
            log.warn("Ignoring {}: not a number", PACKET_LOSS_PROPERTY);
            return 0;
        }
    }

    /**
     * Make the network thread.
     *
     * @param task What it runs.
     * @return The thread.
     */
    private static Thread thread(Runnable task) {
        final Thread thread = new Thread(task, "UDP server");
        thread.setDaemon(true);
        return thread;
    }

    /**
     * The port the server listens on.
     *
     * @return The port.
     */
    public int getPort() {
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    /** Close every connection, telling the clients, and stop listening. */
    @Override
    public void close() {
        try {
            channel.eventLoop()
                    .submit(
                            () -> {
                                for (UdpConnection connection : List.copyOf(connections.values())) {
                                    connection.close(true);
                                }
                            })
                    .await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        channel.close();
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
    }

    /**
     * How many clients are connected.
     *
     * @return The count, which may be a moment out of date.
     */
    public int connectionCount() {
        return connections.size();
    }

    /** Send what every connection has due, and drop those that went quiet. */
    private void tick() {
        final long now = System.nanoTime();
        final Collection<UdpConnection> all = List.copyOf(connections.values());
        for (UdpConnection connection : all) {
            connection.tick(now);
        }
    }

    /**
     * Forget a connection that closed, and pass the news on.
     *
     * @param connection The connection.
     */
    private void closed(UdpConnection connection) {
        if (connections.get(connection.getRemote()) == connection) {
            connections.remove(connection.getRemote());
            salts.remove(connection.getRemote());
        }
        try {
            onClose.accept(connection);
        } catch (RuntimeException e) {
            log.warn("Handling the close of {} failed", connection.getRemote(), e);
        }
    }

    /**
     * The challenge token for an address and salt in a time bucket.
     *
     * @param address The client's address.
     * @param salt The client's salt.
     * @param bucket The time bucket.
     * @return The token.
     */
    private long token(InetSocketAddress address, long salt, long bucket) {
        final byte[] host = address.getAddress().getAddress();
        final ByteBuffer input = ByteBuffer.allocate(host.length + Integer.BYTES + 2 * Long.BYTES);
        input.put(host).putInt(address.getPort()).putLong(salt).putLong(bucket);
        return ByteBuffer.wrap(mac.doFinal(input.array())).getLong();
    }

    /**
     * Send a small packet: the prefix, the salt, and one more long.
     *
     * @param type The packet type.
     * @param salt The client's salt.
     * @param value The token or connection id.
     * @param to Where to.
     */
    private void reply(byte type, long salt, long value, InetSocketAddress to) {
        final ByteBuffer packet = ByteBuffer.allocate(PacketFormat.PREFIX + 2 * Long.BYTES);
        packet.putInt(PacketFormat.MAGIC).put(type).putLong(salt).putLong(value);
        channel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(packet.array()), to));
    }

    /** Reads packets on the network thread. */
    private final class Handler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            final ByteBuf content = packet.content();
            if (content.readableBytes() < PacketFormat.PREFIX
                    || content.readableBytes() > PacketFormat.MAX_PACKET) {
                return;
            }
            final byte[] bytes = new byte[content.readableBytes()];
            content.readBytes(bytes);
            final ByteBuffer in = ByteBuffer.wrap(bytes);
            if (in.getInt() != PacketFormat.MAGIC) {
                return;
            }
            final InetSocketAddress from = packet.sender();
            final long now = System.nanoTime();
            final byte type = in.get();
            if (type == PacketFormat.DATA) {
                final UdpConnection connection = connections.get(from);
                if (connection != null) {
                    connection.onData(bytes, now);
                }
            } else if (type == PacketFormat.HELLO) {
                if (bytes.length >= PacketFormat.HELLO_SIZE) {
                    final long salt = in.getLong();
                    reply(
                            PacketFormat.CHALLENGE,
                            salt,
                            token(from, salt, now / TOKEN_BUCKET_NANOS),
                            from);
                }
            } else if (type == PacketFormat.RESPONSE) {
                if (in.remaining() >= 2 * Long.BYTES) {
                    respond(from, in.getLong(), in.getLong(), now);
                }
            } else if (type == PacketFormat.DISCONNECT) {
                final UdpConnection connection = connections.get(from);
                if (in.remaining() >= Long.BYTES
                        && connection != null
                        && connection.getConnectionId() == in.getLong()) {
                    log.info("{} disconnected", from);
                    connection.close(false);
                }
            }
        }

        /**
         * Take a client's answer to the challenge: accept it if the token is good.
         *
         * @param from The client's address.
         * @param salt The client's salt.
         * @param token The token it sent back.
         * @param now The time.
         */
        private void respond(InetSocketAddress from, long salt, long token, long now) {
            final long bucket = now / TOKEN_BUCKET_NANOS;
            if (token != token(from, salt, bucket) && token != token(from, salt, bucket - 1)) {
                return;
            }
            final UdpConnection existing = connections.get(from);
            if (existing != null && salts.get(from) == salt) {
                // The accept was lost; send it again
                reply(PacketFormat.ACCEPT, salt, existing.getConnectionId(), from);
                return;
            }
            if (existing != null) {
                existing.close(false);
            }
            final long id = random.nextLong();
            final UdpConnection connection =
                    new UdpConnection(
                            channel,
                            from,
                            new PacketEndpoint(id, settings, now),
                            RequestDirection.CLIENT_BOUND,
                            dropShare,
                            UdpServer.this::closed);
            connection.setLocal(acceptor.apply(connection));
            connections.put(from, connection);
            salts.put(from, salt);
            reply(PacketFormat.ACCEPT, salt, id, from);
            log.info("{} connected", from);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("The UDP server's socket had a problem", cause);
        }
    }
}
