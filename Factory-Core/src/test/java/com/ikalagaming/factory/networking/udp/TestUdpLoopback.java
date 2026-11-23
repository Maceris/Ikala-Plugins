package com.ikalagaming.factory.networking.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.networking.RequestRegistry;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.PlayerState;
import com.ikalagaming.factory.networking.request.clientbound.UpdateTagRegistry;
import com.ikalagaming.factory.networking.request.serverbound.Hello;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.sim.GameMode;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Tests for real UDP sockets over loopback, with packets dropped to exercise the reliability. */
class TestUdpLoopback {

    /** How long to wait for anything. */
    private static final long WAIT_SECONDS = 10;

    /** Register the requests. */
    @BeforeAll
    static void setUp() {
        RequestRegistry.registerDefaults();
    }

    /**
     * Take requests until one of a type arrives.
     *
     * @param <T> The type.
     * @param queue The requests.
     * @param type The type.
     * @return The request.
     * @throws InterruptedException If interrupted.
     */
    private static <T extends Request> T next(BlockingQueue<Request> queue, Class<T> type)
            throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            final Request request = queue.poll(100, TimeUnit.MILLISECONDS);
            if (type.isInstance(request)) {
                return type.cast(request);
            }
        }
        throw new AssertionError("No " + type.getSimpleName() + " arrived");
    }

    /**
     * Connect, send both ways with a fifth of all packets dropped, and disconnect from each side:
     * reliable messages arrive whole and in order, unreliable ones mostly arrive, and each side
     * hears when the other leaves.
     */
    @Test
    void testConnectSendAndLeave() throws Exception {
        final BlockingQueue<Request> atServer = new LinkedBlockingQueue<>();
        final BlockingQueue<Request> atClient = new LinkedBlockingQueue<>();
        final AtomicReference<UdpConnection> serverSide = new AtomicReference<>();
        final CountDownLatch serverSawClose = new CountDownLatch(1);
        final CountDownLatch clientSawClose = new CountDownLatch(1);
        try (UdpServer server =
                new UdpServer(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                        connection -> {
                            serverSide.set(connection);
                            return atServer::add;
                        },
                        connection -> serverSawClose.countDown(),
                        PacketEndpoint.Settings.DEFAULT,
                        0.2)) {
            final UdpConnection client =
                    UdpClient.connect(
                                    new InetSocketAddress(
                                            InetAddress.getLoopbackAddress(), server.getPort()),
                                    atClient::add,
                                    connection -> clientSawClose.countDown(),
                                    PacketEndpoint.Settings.DEFAULT,
                                    0.2)
                            .get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertEquals(1, server.connectionCount());

            // Reliable, client to server, in order
            for (int i = 0; i < 50; ++i) {
                client.send(new Hello("Player " + i));
            }
            for (int i = 0; i < 50; ++i) {
                assertEquals("Player " + i, next(atServer, Hello.class).getClientNonce());
            }

            // A big reliable message, server to client, in many fragments
            final List<String> tags = new ArrayList<>();
            for (int i = 0; i < 5000; ++i) {
                tags.add("lotomation:a_fairly_long_tag_name_" + i);
            }
            final UdpConnection toClient = serverSide.get();
            assertNotNull(toClient);
            final UpdateTagRegistry update = new UpdateTagRegistry();
            update.setUpdateType(UpdateTagRegistry.UpdateType.REPLACE);
            update.setTags(tags);
            toClient.send(update);
            assertEquals(tags, next(atClient, UpdateTagRegistry.class).getTags());

            // Unreliable both ways: most arrive
            for (int i = 0; i < 100; ++i) {
                client.send(PlayerInputs.of(i, List.of(PlayerInput.NONE)));
                toClient.send(
                        PlayerState.of(
                                i, Movement.at(MoveMode.FLYING, i, 0, 0), GameMode.SURVIVAL, 0, 0));
                TimeUnit.MILLISECONDS.sleep(2);
            }
            TimeUnit.MILLISECONDS.sleep(300);
            final long inputs = atServer.stream().filter(PlayerInputs.class::isInstance).count();
            final long states = atClient.stream().filter(PlayerState.class::isInstance).count();
            assertTrue(inputs > 60 && inputs <= 100, inputs + " inputs arrived");
            assertTrue(states > 60 && states <= 100, states + " states arrived");
            assertTrue(client.resends() > 0 || toClient.resends() > 0, "nothing was resent");
            assertTrue(client.roundTripMillis() >= 0, "no round trip measured");

            // The client leaves; the server hears
            client.disconnect();
            assertTrue(serverSawClose.await(WAIT_SECONDS, TimeUnit.SECONDS));
            assertTrue(clientSawClose.await(WAIT_SECONDS, TimeUnit.SECONDS));
        }
    }

    /** Closing the server tells connected clients. */
    @Test
    void testServerCloses() throws Exception {
        final CountDownLatch clientSawClose = new CountDownLatch(1);
        final UdpServer server =
                new UdpServer(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                        connection -> request -> {},
                        connection -> {},
                        PacketEndpoint.Settings.DEFAULT,
                        0);
        UdpClient.connect(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getPort()),
                        request -> {},
                        connection -> clientSawClose.countDown(),
                        PacketEndpoint.Settings.DEFAULT,
                        0)
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
        server.close();
        assertTrue(clientSawClose.await(WAIT_SECONDS, TimeUnit.SECONDS));
    }

    /** Connecting where nothing listens fails once the handshake times out. */
    @Test
    void testNobodyThere() throws Exception {
        final long started = System.nanoTime();
        final var future =
                UdpClient.connect(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 1),
                        request -> {},
                        connection -> {},
                        PacketEndpoint.Settings.DEFAULT,
                        0);
        try {
            future.get(UdpClient.CONNECT_TIMEOUT_MILLIS + 5000, TimeUnit.MILLISECONDS);
            throw new AssertionError("connected to nothing");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(9));
        }
    }
}
