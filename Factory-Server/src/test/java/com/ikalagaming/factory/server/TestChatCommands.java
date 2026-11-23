package com.ikalagaming.factory.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.chat.ChatText;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.base.Request;
import com.ikalagaming.factory.networking.request.clientbound.ChatMessage;
import com.ikalagaming.factory.save.Accounts;
import com.ikalagaming.factory.sim.GameMode;
import com.ikalagaming.factory.world.gen.SpawnFinder;
import com.ikalagaming.permissions.PermissionDefault;
import com.ikalagaming.permissions.PermissionPlugin;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Tests for chat, commands and the permissions they need. */
class TestChatCommands {

    /** Who declares the nodes in these tests. */
    private static final String OWNER = "TestChatCommands";

    /** Records what a client is sent. */
    private static final class Recorder implements Connection {
        final List<Request> sent = new CopyOnWriteArrayList<>();

        @Override
        public void connect() {}

        @Override
        public void disconnect() {}

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public void receive(Request request) {}

        @Override
        public void send(Request request) {
            sent.add(request);
        }

        /**
         * The chat lines sent, as plain text.
         *
         * @return The lines.
         */
        List<String> chat() {
            return sent.stream()
                    .filter(ChatMessage.class::isInstance)
                    .map(message -> ((ChatMessage) message).text().plain())
                    .toList();
        }

        /**
         * The last chat line sent.
         *
         * @return The line.
         */
        ChatText last() {
            for (int i = sent.size() - 1; i >= 0; --i) {
                if (sent.get(i) instanceof ChatMessage message) {
                    return message.text();
                }
            }
            throw new AssertionError("No chat sent");
        }
    }

    /** Declare the plugin's nodes, as it does when enabled. */
    @BeforeAll
    static void declare() throws Exception {
        try (InputStream in = TestChatCommands.class.getResourceAsStream("/permissions.yml")) {
            final Map<?, ?> nodes = new Yaml().load(in);
            PermissionPlugin.getRegistry().registerAll(OWNER, nodes, PermissionDefault.OP);
        }
    }

    /** Forget the nodes. */
    @AfterAll
    static void forget() {
        PermissionPlugin.getRegistry().removeAllOwnedBy(OWNER);
    }

    /** Chat reaches everyone; commands check permissions, change game modes and roles. */
    @Test
    void testChatAndCommands() throws Exception {
        final SpawnFinder.Spawn spawn = new SpawnFinder.Spawn(0, 64, 0, false);
        try (ServerWorld world =
                new ServerWorld(
                        Path.of("save"), "world", TestServerWorld.emptyWorld(), spawn, 1, 0)) {
            final Accounts accounts = world.getAccess().accounts();
            final Accounts.Account ownerAccount =
                    accounts.register(
                            "Owner", new byte[] {1}, Accounts.Role.OWNER, false, Clock.systemUTC());
            final Accounts.Account bobAccount =
                    accounts.register(
                            "Bob", new byte[] {2}, Accounts.Role.PLAYER, false, Clock.systemUTC());
            accounts.register(
                    "Waiting", new byte[] {3}, Accounts.Role.PLAYER, true, Clock.systemUTC());
            final Recorder ownerClient = new Recorder();
            final Recorder bobClient = new Recorder();
            final ServerPlayer[] players = new ServerPlayer[2];
            world.post(
                    () -> {
                        players[0] = world.join(ownerAccount, ownerClient);
                        players[1] = world.join(bobAccount, bobClient);
                    });
            world.tick();
            final ServerPlayer owner = players[0];
            final ServerPlayer bob = players[1];
            assertTrue(ownerClient.chat().contains("Bob joined the game"));

            run(world, () -> world.chat(bob, "hello there"));
            assertEquals("<Bob> hello there", ownerClient.last().plain());
            assertEquals("[Owner] <Owner> hi", say(world, owner, "hi", bobClient).plain());
            assertEquals(ChatText.GOLD, bobClient.last().spans().get(0).color());

            say(world, bob, "/gamemode creative", bobClient);
            assertEquals("You don't have permission to do that.", bobClient.last().plain());
            assertEquals(GameMode.SURVIVAL, gameMode(world, bob));

            say(world, owner, "/gamemode c Bob", ownerClient);
            assertEquals(GameMode.CREATIVE, gameMode(world, bob));
            assertEquals("Your game mode is now creative.", bobClient.last().plain());

            say(world, owner, "/op bob", ownerClient);
            assertEquals(Accounts.Role.OPERATOR, bob.getAccount().role());
            say(world, bob, "/gamemode spectator", bobClient);
            assertEquals(GameMode.SPECTATOR, gameMode(world, bob));

            say(world, bob, "/deop Owner", bobClient);
            assertEquals("The owner can't be made a player.", bobClient.last().plain());
            say(world, owner, "/deop Bob", ownerClient);
            assertEquals(Accounts.Role.PLAYER, accounts.byName("Bob").orElseThrow().role());

            say(world, owner, "/accept waiting", ownerClient);
            assertFalse(accounts.byName("Waiting").orElseThrow().pending());

            say(world, owner, "/nonsense", ownerClient);
            assertEquals("Unknown command. Type /help for a list.", ownerClient.last().plain());
            say(world, bob, "/help", bobClient);
            assertTrue(bobClient.chat().contains("/help - List the commands you can use"));
            assertFalse(bobClient.chat().stream().anyMatch(line -> line.startsWith("/op")));

            world.post(() -> world.commands().dispatch(consoleSender(), "say Hello"));
            world.tick();
            assertEquals("[Server] Hello", bobClient.last().plain());
        }
    }

    /**
     * Say something as a player and return the last line a client got.
     *
     * @param world The world.
     * @param player Who says it.
     * @param text What they say.
     * @param watching The client to read.
     * @return Its last line.
     */
    private static ChatText say(
            ServerWorld world, ServerPlayer player, String text, Recorder watching) {
        run(world, () -> world.chat(player, text));
        return watching.last();
    }

    /**
     * Run something on the tick thread and wait for it.
     *
     * @param world The world.
     * @param task The work.
     */
    private static void run(ServerWorld world, Runnable task) {
        world.post(task);
        world.tick();
    }

    /**
     * A player's game mode.
     *
     * @param world The world.
     * @param player The player.
     * @return The mode.
     */
    private static GameMode gameMode(ServerWorld world, ServerPlayer player) {
        final GameMode[] mode = new GameMode[1];
        run(world, () -> mode[0] = world.gameMode(player));
        return mode[0];
    }

    /**
     * The console, for commands.
     *
     * @return A sender with every permission.
     */
    private static CommandSender consoleSender() {
        return new CommandSender() {
            @Override
            public String name() {
                return "Console";
            }

            @Override
            public com.ikalagaming.permissions.Subject subject() {
                return com.ikalagaming.permissions.Subject.CONSOLE;
            }

            @Override
            public void reply(ChatText text) {
                // Not watched
            }

            @Override
            public ServerPlayer player() {
                return null;
            }
        };
    }
}
