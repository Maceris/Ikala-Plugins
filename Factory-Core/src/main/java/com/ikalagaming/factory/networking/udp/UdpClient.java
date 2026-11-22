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
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Connects to a server over UDP: says hello, answers the challenge, and once accepted runs the
 * connection on its own network thread, which stops when the connection closes.
 */
@Slf4j
public final class UdpClient {

    /** How often the handshake is sent again until answered. */
    private static final long RETRY_MILLIS = 250;

    /** How long the handshake may take. */
    static final long CONNECT_TIMEOUT_MILLIS = 10_000;

    /** Runs the socket and the connection. */
    private final EventLoopGroup group = new NioEventLoopGroup(1, UdpClient::thread);

    /** The server's address. */
    private final InetSocketAddress server;

    /** Handles the requests the server sends. */
    private final RequestHandler local;

    /** Told when the connection closes, on the network thread. */
    private final Consumer<UdpConnection> onClose;

    /** How fast reliable traffic may go. */
    private final PacketEndpoint.Settings settings;

    /** The share of outgoing packets to drop, for testing. */
    private final double dropShare;

    /** This client's salt, which the server's answers must carry. */
    private final long salt = new SecureRandom().nextLong();

    /** Completes with the connection once accepted. */
    private final CompletableFuture<UdpConnection> result = new CompletableFuture<>();

    /** The socket. */
    private Channel channel;

    /** The server's token, once it challenged. Network thread only. */
    private Long token;

    /** The connection, once accepted. Network thread only. */
    private UdpConnection connection;

    /** Resends the handshake. */
    private ScheduledFuture<?> retry;

    /**
     * A client.
     *
     * @param server The server's address.
     * @param local Handles the requests the server sends.
     * @param onClose Told when the connection closes.
     * @param settings How fast reliable traffic may go.
     * @param dropShare The share of outgoing packets to drop, for testing.
     */
    private UdpClient(
            InetSocketAddress server,
            RequestHandler local,
            Consumer<UdpConnection> onClose,
            PacketEndpoint.Settings settings,
            double dropShare) {
        this.server = server;
        this.local = local;
        this.onClose = onClose;
        this.settings = settings;
        this.dropShare = dropShare;
    }

    /**
     * Connect to a server.
     *
     * @param server The server's address.
     * @param local Handles the requests the server sends, from the network thread.
     * @param onClose Told when the connection closes, from the network thread.
     * @param settings How fast reliable traffic may go.
     * @param dropShare The share of outgoing packets to drop, for testing; 0 normally.
     * @return Completes with the connection once the server accepts it, or fails if it doesn't
     *     answer in time.
     */
    public static CompletableFuture<UdpConnection> connect(
            @NonNull InetSocketAddress server,
            @NonNull RequestHandler local,
            @NonNull Consumer<UdpConnection> onClose,
            @NonNull PacketEndpoint.Settings settings,
            double dropShare) {
        final UdpClient client = new UdpClient(server, local, onClose, settings, dropShare);
        client.start();
        return client.result;
    }

    /**
     * Make the network thread.
     *
     * @param task What it runs.
     * @return The thread.
     */
    private static Thread thread(Runnable task) {
        final Thread thread = new Thread(task, "UDP client");
        thread.setDaemon(true);
        return thread;
    }

    /** Open a socket and start the handshake. */
    private void start() {
        new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(new Handler())
                .bind(0)
                .addListener(
                        future -> {
                            if (!future.isSuccess()) {
                                fail(future.cause());
                                return;
                            }
                            channel = ((io.netty.channel.ChannelFuture) future).channel();
                            retry =
                                    channel.eventLoop()
                                            .scheduleAtFixedRate(
                                                    this::handshake,
                                                    0,
                                                    RETRY_MILLIS,
                                                    TimeUnit.MILLISECONDS);
                            channel.eventLoop()
                                    .schedule(
                                            () ->
                                                    fail(
                                                            new TimeoutException(
                                                                    server
                                                                            + " didn't answer in "
                                                                            + CONNECT_TIMEOUT_MILLIS
                                                                            + " ms")),
                                            CONNECT_TIMEOUT_MILLIS,
                                            TimeUnit.MILLISECONDS);
                        });
    }

    /** Send the next handshake packet: hello until challenged, then the response. */
    private void handshake() {
        if (connection != null || result.isDone()) {
            return;
        }
        final ByteBuffer packet;
        if (token == null) {
            packet = ByteBuffer.allocate(PacketFormat.HELLO_SIZE);
            packet.putInt(PacketFormat.MAGIC).put(PacketFormat.HELLO).putLong(salt);
        } else {
            packet = ByteBuffer.allocate(PacketFormat.PREFIX + 2 * Long.BYTES);
            packet.putInt(PacketFormat.MAGIC)
                    .put(PacketFormat.RESPONSE)
                    .putLong(salt)
                    .putLong(token);
        }
        channel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(packet.array()), server));
    }

    /**
     * Give up connecting.
     *
     * @param cause Why.
     */
    private void fail(Throwable cause) {
        if (result.completeExceptionally(cause)) {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * The connection closed: stop the network thread and pass the news on.
     *
     * @param closed The connection.
     */
    private void closed(UdpConnection closed) {
        try {
            onClose.accept(closed);
        } finally {
            channel.close();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }

    /** Reads packets on the network thread. */
    private final class Handler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            if (!packet.sender().equals(server)) {
                return;
            }
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
            final byte type = in.get();
            final long now = System.nanoTime();
            if (type == PacketFormat.DATA) {
                if (connection != null) {
                    connection.onData(bytes, now);
                }
                return;
            }
            if (type == PacketFormat.DISCONNECT) {
                if (connection != null
                        && in.remaining() >= Long.BYTES
                        && in.getLong() == connection.getConnectionId()) {
                    log.info("{} closed the connection", server);
                    connection.close(false);
                }
                return;
            }
            if (in.remaining() < 2 * Long.BYTES || in.getLong() != salt) {
                return;
            }
            final long value = in.getLong();
            if (type == PacketFormat.CHALLENGE && token == null) {
                token = value;
                handshake();
            } else if (type == PacketFormat.ACCEPT && connection == null && !result.isDone()) {
                retry.cancel(false);
                connection =
                        new UdpConnection(
                                channel,
                                server,
                                new PacketEndpoint(value, settings, now),
                                RequestDirection.SERVER_BOUND,
                                dropShare,
                                UdpClient.this::closed);
                connection.setLocal(local);
                channel.eventLoop()
                        .scheduleAtFixedRate(
                                () -> connection.tick(System.nanoTime()),
                                UdpServer.TICK_MILLIS,
                                UdpServer.TICK_MILLIS,
                                TimeUnit.MILLISECONDS);
                log.info("Connected to {}", server);
                result.complete(connection);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("The UDP client's socket had a problem", cause);
        }
    }
}
