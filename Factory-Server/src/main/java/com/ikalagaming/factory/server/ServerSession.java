package com.ikalagaming.factory.server;

import com.ikalagaming.factory.auth.Ed25519;
import com.ikalagaming.factory.auth.HostIdentity;
import com.ikalagaming.factory.auth.SignInProof;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.Disconnect;
import com.ikalagaming.factory.networking.request.clientbound.ServerHello;
import com.ikalagaming.factory.networking.request.serverbound.ChatSend;
import com.ikalagaming.factory.networking.request.serverbound.Hello;
import com.ikalagaming.factory.networking.request.serverbound.PlayerInputs;
import com.ikalagaming.factory.networking.request.serverbound.ServerBoundRequestHandler;
import com.ikalagaming.factory.networking.request.serverbound.SignIn;
import com.ikalagaming.factory.save.Accounts;
import com.ikalagaming.factory.save.Registration;
import com.ikalagaming.factory.sim.PlayerInput;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * The server's end of one client's connection. Requests arrive on whatever thread the client sent
 * them from, and are handled on the world's tick thread, so the world is only ever changed there.
 */
@Slf4j
final class ServerSession implements ServerBoundRequestHandler {

    /** Makes nonces. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder BASE64 = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    /** The world the client joins. */
    private final ServerWorld world;

    /** Sends requests to the client; set once the connection is made. */
    private volatile Connection toClient;

    /** The client's player, once it has joined. Tick thread only. */
    private ServerPlayer player;

    /** Whether the client is in this process: single player, which can be the save's owner. */
    private final boolean local;

    /** The client's nonce from {@code Hello}, or null before it. Tick thread only. */
    private byte[] clientNonce;

    /** This session's nonce, sent in {@code ServerHello}. Tick thread only. */
    private byte[] serverNonce;

    /** Whether the client was turned away, so nothing more is handled. Tick thread only. */
    private boolean refused;

    /**
     * A session for one client.
     *
     * @param world The world the client joins.
     * @param local Whether the client is in this process.
     */
    ServerSession(@NonNull ServerWorld world, boolean local) {
        this.world = world;
        this.local = local;
    }

    /**
     * Set how to reach the client.
     *
     * @param connection Sends requests to the client.
     */
    void setConnection(@NonNull Connection connection) {
        toClient = connection;
    }

    @Override
    public void process(@NonNull Request request) {
        world.post(() -> request.handleUsing(this));
    }

    /** The client left: its player leaves the world, on the tick thread. */
    void leave() {
        world.post(
                () -> {
                    if (player != null) {
                        world.leave(player);
                        player = null;
                    }
                });
    }

    @Override
    public void handle(@NonNull Hello request) {
        if (refused || clientNonce != null) {
            return;
        }
        try {
            clientNonce = BASE64_DECODER.decode(request.getClientNonce());
        } catch (IllegalArgumentException | NullPointerException e) {
            clientNonce = null;
        }
        if (clientNonce == null || clientNonce.length != SignInProof.NONCE_BYTES) {
            refuse("The greeting was malformed");
            return;
        }
        serverNonce = new byte[SignInProof.NONCE_BYTES];
        RANDOM.nextBytes(serverNonce);
        final HostIdentity host = world.getAccess().host();
        final byte[] serverId = host.getServerId();
        final byte[] hostKey = host.publicKey();
        final byte[] signature =
                Ed25519.sign(
                        host.getHostKey().getPrivate(),
                        SignInProof.host(serverId, hostKey, clientNonce, serverNonce));
        toClient.send(
                new ServerHello(
                        BASE64.encodeToString(serverId),
                        BASE64.encodeToString(hostKey),
                        BASE64.encodeToString(serverNonce),
                        BASE64.encodeToString(signature)));
    }

    @Override
    public void handle(@NonNull SignIn request) {
        if (refused) {
            return;
        }
        if (player != null) {
            log.warn("{} tried to sign in twice", player.getName());
            return;
        }
        if (serverNonce == null) {
            refuse("Sign in after saying hello");
            return;
        }
        final String username = request.getUsername();
        final byte[] publicKey;
        final boolean proven;
        final ServerWorld.Access access = world.getAccess();
        try {
            publicKey = BASE64_DECODER.decode(request.getPublicKey());
            final byte[] signature = BASE64_DECODER.decode(request.getSignature());
            proven =
                    username != null
                            && Ed25519.verify(
                                    Ed25519.publicKey(publicKey),
                                    SignInProof.signIn(
                                            access.host().getServerId(),
                                            access.host().publicKey(),
                                            clientNonce,
                                            serverNonce,
                                            username,
                                            publicKey),
                                    signature);
        } catch (IllegalArgumentException | NullPointerException e) {
            refuse("The sign-in was malformed");
            return;
        }
        if (!proven) {
            refuse("The sign-in's signature didn't check out");
            return;
        }
        final Accounts accounts = access.accounts();
        Optional<Accounts.Account> account = accounts.byKey(publicKey);
        if (account.isEmpty()) {
            account = register(access, username, publicKey);
            if (account.isEmpty()) {
                return;
            }
        }
        final Accounts.Account found = account.get();
        if (found.banned()) {
            refuse("You are banned from this server");
            return;
        }
        if (found.pending()) {
            refuse("Your request to join is waiting for an operator to accept it");
            log.info(
                    "{} is still waiting to be accepted; an operator can accept them with"
                            + " /accept {}",
                    found.username(),
                    found.username());
            return;
        }
        if (world.player(found.id()) != null) {
            refuse("You are already playing on this server");
            return;
        }
        player = world.join(found, toClient);
    }

    /**
     * Make an account for a new key, as the save's registration policy allows. The first player in
     * a single-player save becomes its owner.
     *
     * @param access Who can sign in.
     * @param username The name they asked for.
     * @param publicKey Their key.
     * @return The account, or empty if they were turned away.
     */
    private Optional<Accounts.Account> register(
            @NonNull ServerWorld.Access access,
            @NonNull String username,
            byte @NonNull [] publicKey) {
        final Accounts accounts = access.accounts();
        if (!Accounts.isValidUsername(username)) {
            refuse("Usernames are 3 to 16 letters, digits or underscores");
            return Optional.empty();
        }
        if (accounts.byName(username).isPresent()) {
            refuse("Someone else already has the name " + username + " on this server");
            return Optional.empty();
        }
        final boolean owner = local && !accounts.hasOwner();
        final boolean pending = !owner && access.registration() != Registration.OPEN;
        final Accounts.Account made;
        try {
            made =
                    accounts.register(
                            username,
                            publicKey,
                            owner ? Accounts.Role.OWNER : Accounts.Role.PLAYER,
                            pending,
                            Clock.systemUTC());
        } catch (IllegalArgumentException e) {
            refuse(e.getMessage());
            return Optional.empty();
        }
        if (owner) {
            log.info("{} registered as the owner of this save", username);
        } else if (pending) {
            log.info(
                    "{} asks to join; an operator can accept them with /accept {}",
                    username,
                    username);
        } else {
            log.info("{} registered", username);
        }
        return Optional.of(made);
    }

    /**
     * Turn the client away, telling it why.
     *
     * @param reason Why, to show the player.
     */
    private void refuse(@NonNull String reason) {
        refused = true;
        log.info("Turned a client away: {}", reason);
        toClient.send(new Disconnect(reason));
    }

    @Override
    public void handle(@NonNull ChatSend request) {
        if (player == null || request.getText() == null) {
            return;
        }
        final String text = request.getText();
        world.chat(
                player,
                text.length() > ChatSend.MAX_LENGTH
                        ? text.substring(0, ChatSend.MAX_LENGTH)
                        : text);
    }

    @Override
    public void handle(@NonNull PlayerInputs request) {
        if (player == null) {
            return;
        }
        if (request.getSentNanos() != 0 && request.getSentNanos() - player.echoNanos > 0) {
            player.echoNanos = request.getSentNanos();
            player.echoReceivedNanos = System.nanoTime();
        }
        final List<PlayerInput> inputs = request.inputs();
        final long next = world.getFrames();
        for (int i = 0; i < inputs.size(); ++i) {
            player.inputs.offer(next, request.getFirstFrame() + i, inputs.get(i).sanitized());
        }
    }
}
