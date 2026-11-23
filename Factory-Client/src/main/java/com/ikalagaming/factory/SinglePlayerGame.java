package com.ikalagaming.factory;

import com.ikalagaming.factory.client.ClientSession;
import com.ikalagaming.factory.client.FrameClock;
import com.ikalagaming.factory.client.GameControls;
import com.ikalagaming.factory.client.Prediction;
import com.ikalagaming.factory.gui.ChatBox;
import com.ikalagaming.factory.gui.GameMenus;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.networking.udp.PacketEndpoint;
import com.ikalagaming.factory.networking.udp.UdpClient;
import com.ikalagaming.factory.networking.udp.UdpConnection;
import com.ikalagaming.factory.networking.udp.UdpServer;
import com.ikalagaming.factory.render.SkyLight;
import com.ikalagaming.factory.render.WorldRenderer;
import com.ikalagaming.factory.save.SaveSummary;
import com.ikalagaming.factory.saves.SaveUtil;
import com.ikalagaming.factory.saves.UserDataUtil;
import com.ikalagaming.factory.sim.Angle;
import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.GameMode;
import com.ikalagaming.factory.sim.MoveMode;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;
import com.ikalagaming.factory.world.WorldTime;
import com.ikalagaming.graphics.CameraManager;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.input.Controls;
import com.ikalagaming.graphics.input.KeyBinding;
import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Starts a single player game: opens the save on the local server, joins it, and plays: each
 * command frame it samples the keys, predicts the player's movement and sends the inputs to the
 * server, and each drawn frame it puts the camera at the player's eye. See the Starting a Game and
 * Command Frames design docs.
 */
@Slf4j
public final class SinglePlayerGame {

    /** How high the eye is above the feet, in blocks. */
    public static final double EYE_HEIGHT = 1.62;

    /** The system property naming a server to join at start, as host:port. */
    public static final String CONNECT_PROPERTY = "ikala.connect";

    /** The name single player joins as, until accounts exist. */
    private static final String PLAYER_NAME = "Player";

    /** How often the client's chunk count is logged when it changes, in milliseconds. */
    private static final long REPORT_INTERVAL_MILLIS = 5000;

    /** How often the client's state is logged when nothing changes, in milliseconds. */
    private static final long IDLE_REPORT_MILLIS = 30_000;

    /** The most command frames run in one drawn frame; any more wait for the next one. */
    private static final int MAX_FRAMES_PER_UPDATE = 8;

    /** Radians in a turn. */
    private static final double TURN_RADIANS = 2 * Math.PI;

    /** The session of the game being played, or null. Main thread only. */
    private static ClientSession session;

    /** Whether the game being played is on the local server, which can pause. */
    private static boolean local;

    /** Whether the local server is paused for the pause menu. */
    private static boolean paused;

    /** The menus, for the pause menu, or null. Main thread only. */
    private static GameMenus menus;

    /** The chat box, made with the first game. Main thread only. */
    private static ChatBox chat;

    /**
     * Whether the player has creative flight on, toggled by tapping jump twice. Main thread only.
     */
    private static boolean flying;

    /** Whether jump was down when the last input was sampled, to see presses. */
    private static boolean jumpWasDown;

    /** The command frame jump was last pressed on, to see double taps. */
    private static long lastJumpPress = Long.MIN_VALUE / 2;

    /** How many command frames apart two jump presses can be to toggle flight: about 0.3 s. */
    private static final int DOUBLE_TAP_FRAMES = 18;

    /** Draws the chunks the server sends, or null. */
    private static WorldRenderer renderer;

    /** Lights the world by its clock, or null. */
    private static SkyLight sky;

    /** The main loop stage that plays, or null. */
    private static UUID playStage;

    /** When the chunk count was last logged, in milliseconds. */
    private static long lastReportMillis;

    /** The chunk count last logged. */
    private static int lastReported = -1;

    /** The player's movement last logged, or null. */
    private static Movement lastReportedMovement;

    /**
     * Start playing a save.
     *
     * @param save The save.
     */
    public static void start(@NonNull SaveSummary save) {
        log.info(
                "Starting {} ({}, {} seed {})",
                save.name(),
                save.folder(),
                save.worldType(),
                save.seed());
        stop();
        setUpScene();
        LocalServer.open(SaveUtil.savesFolder().resolve(save.folder()))
                .whenComplete(
                        (spawn, error) -> {
                            if (error != null) {
                                log.warn(
                                        SafeResourceLoader.getString(
                                                "NO_LOCAL_SERVER",
                                                FactoryClientPlugin.getResourceBundle()),
                                        error);
                                return;
                            }
                            log.info(
                                    "Opened {}; new players start at {}, {}, {}",
                                    save.name(),
                                    spawn.x(),
                                    spawn.y(),
                                    spawn.z());
                            GraphicsManager.runOnMainThread(SinglePlayerGame::join);
                        });
    }

    /**
     * Use the game's menus for the pause menu.
     *
     * @param gameMenus The menus, or null to have none.
     */
    public static void setMenus(GameMenus gameMenus) {
        menus = gameMenus;
        if (gameMenus == null) {
            // Its frame task went with the plugin's graphics
            chat = null;
        }
        if (gameMenus != null) {
            gameMenus.setGameActions(
                    new GameMenus.GameActions() {
                        @Override
                        public void resume() {
                            SinglePlayerGame.resume();
                        }

                        @Override
                        public void exitToMainMenu() {
                            SinglePlayerGame.exitToMainMenu();
                        }
                    });
        }
    }

    /** Open the pause menu, and pause the world if it is single player. Main thread only. */
    public static void pause() {
        if (session == null || menus == null) {
            return;
        }
        menus.showPause();
        if (local && !paused && LocalServer.setPaused(true)) {
            paused = true;
            session.getClock().setPaused(true);
        }
    }

    /** Close the pause menu and carry on. Main thread only. */
    public static void resume() {
        if (menus != null) {
            menus.hideAll();
        }
        unpause();
    }

    /** Carry on the world if the pause menu paused it. */
    private static void unpause() {
        if (!paused) {
            return;
        }
        paused = false;
        LocalServer.setPaused(false);
        if (session != null) {
            session.getClock().setPaused(false);
            // Frames don't catch up on the time spent paused, on either side
            session.getFrameClock().start(System.nanoTime());
        }
    }

    /** Leave the game, save the world and show the main menu. Main thread only. */
    public static void exitToMainMenu() {
        final boolean wasLocal = local;
        stop();
        if (wasLocal) {
            CompletableFuture.runAsync(LocalServer::closeWorld);
        }
        if (menus != null) {
            menus.showMainMenu();
        }
    }

    /** Leave the game being played, if any. Main thread only. */
    public static void stop() {
        unpause();
        local = false;
        flying = false;
        jumpWasDown = false;
        lastJumpPress = Long.MIN_VALUE / 2;
        if (playStage != null) {
            Launcher.removeMainThreadStage(playStage);
            playStage = null;
        }
        final CameraManager cameras = GraphicsManager.getCameraManager();
        if (cameras != null) {
            cameras.setMoving(true);
        }
        if (session != null) {
            session.close();
            session = null;
        }
        lastReportedMovement = null;
        lastReported = -1;
        if (chat != null) {
            chat.clear();
        }
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (sky != null) {
            sky.restore();
            sky = null;
        }
    }

    /**
     * The renderer drawing the world being played.
     *
     * @return The renderer, or null when not playing.
     */
    public static WorldRenderer getRenderer() {
        return renderer;
    }

    /**
     * Whether the local server is paused for the pause menu.
     *
     * @return True while paused.
     */
    public static boolean isPaused() {
        return paused;
    }

    /**
     * Whether the player has flight on.
     *
     * @return True while flying.
     */
    public static boolean isFlying() {
        return flying;
    }

    /**
     * The session of the game being played.
     *
     * @return The session, or null when not playing.
     */
    public static ClientSession getSession() {
        return session;
    }

    /** Connect to the local server and join. Main thread only. */
    private static void join() {
        final ClientSession joining = begin();
        local = true;
        final Connection connection = LocalServer.connect(joining);
        joining.join(connection, PLAYER_NAME);
    }

    /**
     * Join a server over the network. Until there is a menu for it, {@value #CONNECT_PROPERTY} does
     * this at start. Main thread only.
     *
     * @param address The server's address.
     */
    public static void joinServer(@NonNull InetSocketAddress address) {
        log.info("Joining {}", address);
        stop();
        setUpScene();
        final ClientSession joining = begin();
        UdpClient.connect(
                        address,
                        joining,
                        connection -> GraphicsManager.runOnMainThread(() -> lost(joining)),
                        PacketEndpoint.Settings.DEFAULT,
                        UdpServer.packetLossFromProperties())
                .whenComplete(
                        (connection, error) ->
                                GraphicsManager.runOnMainThread(
                                        () -> {
                                            if (error != null) {
                                                log.warn("Could not join {}", address, error);
                                                lost(joining);
                                            } else if (session != joining) {
                                                connection.disconnect();
                                            } else {
                                                joining.join(connection, PLAYER_NAME);
                                            }
                                        }));
    }

    /**
     * Start a session: the client's world, its renderer, and the stage that plays. Main thread
     * only.
     *
     * @return The session, not yet connected.
     */
    private static ClientSession begin() {
        final ClientSession[] self = new ClientSession[1];
        final ClientSession joining =
                new ClientSession(
                        GraphicsManager::runOnMainThread,
                        UserDataUtil.identity(),
                        UserDataUtil.knownHosts(),
                        SinglePlayerGame::onJoined,
                        reason -> refused(self[0], reason));
        self[0] = joining;
        if (chat == null) {
            chat =
                    new ChatBox(
                            GraphicsManager.forPlugin(FactoryClientPlugin.PLUGIN_NAME),
                            text -> {
                                if (session != null) {
                                    session.sendChat(text);
                                }
                            });
        }
        joining.setOnChat(chat::add);
        renderer = new WorldRenderer(GraphicsManager.forPlugin(FactoryClientPlugin.PLUGIN_NAME));
        joining.getWorld().addListener(renderer);
        session = joining;
        playStage = Launcher.addMainThreadStage(SinglePlayerGame::play);
        return joining;
    }

    /**
     * The server turned the player away, or couldn't prove who it is: stop and show why. Main
     * thread only.
     *
     * @param ended The session.
     * @param reason Why.
     */
    private static void refused(ClientSession ended, @NonNull String reason) {
        if (session != ended || session == null) {
            return;
        }
        stop();
        if (menus != null) {
            menus.showMainMenu(reason);
        }
    }

    /**
     * A session's connection is gone: stop playing, if it is still the one being played. Main
     * thread only.
     *
     * @param ended The session.
     */
    private static void lost(@NonNull ClientSession ended) {
        if (session == ended) {
            log.warn("The connection to the server is gone");
            stop();
            if (menus != null) {
                menus.showMainMenu();
            }
        }
    }

    /**
     * Put the camera at the player's eye once the server says where the player is, and start
     * stepping frames. The keys move the player from now on, not the camera.
     *
     * @param joined The server's answer.
     */
    private static void onJoined(@NonNull Joined joined) {
        log.info(
                "Joined as {} at {}, {}, {} on frame {}",
                joined.getPlayerId(),
                joined.getX(),
                joined.getY(),
                joined.getZ(),
                joined.getFrame());
        final Camera camera = GraphicsManager.getScene().getCamera();
        camera.setPosition(joined.getX(), joined.getY() + EYE_HEIGHT, joined.getZ());
        camera.setRotation(joined.getPitch(), joined.getYaw());
        final CameraManager cameras = GraphicsManager.getCameraManager();
        if (cameras != null) {
            cameras.setMoving(false);
        }
    }

    /**
     * Run the command frames that are due, send their inputs, and place the camera. A main loop
     * stage.
     *
     * @return The stage status.
     */
    private static int play() {
        final ClientSession playing = session;
        if (playing == null || playing.getPrediction() == null) {
            return 0;
        }
        if (sky != null && playing.getClock().isKnown()) {
            sky.update(playing.getClock().now());
        }
        handleMenuKeys();
        if (session != playing) {
            // Left the game from the menu
            return 0;
        }
        final Prediction prediction = playing.getPrediction();
        final FrameClock clock = playing.getFrameClock();
        final long now = System.nanoTime();
        if (paused) {
            placeCamera(prediction, clock, clock.frameStartNanos() + clock.getFrameNanos());
            return 0;
        }
        final int due = clock.due(now, MAX_FRAMES_PER_UPDATE);
        final boolean inMenu =
                (menus != null && menus.isShowing()) || (chat != null && chat.isOpen());
        for (int i = 0; i < due; ++i) {
            prediction.step(inMenu ? stillInput() : sampleInput());
        }
        if (due > 0) {
            playing.sendInputs();
        }
        placeCamera(prediction, clock, now);
        report(playing);
        return 0;
    }

    /** Open or step back through the pause menu when the pause key is pressed. */
    private static void handleMenuKeys() {
        if (menus == null || (chat != null && chat.isOpen())) {
            // The chat box handles its own keys
            return;
        }
        final Controls controls = GraphicsManager.getControls();
        if (controls.wasPressed(GameControls.PAUSE)) {
            if (menus.isShowing()) {
                menus.back();
            } else {
                pause();
            }
            return;
        }
        if (menus.isShowing() || chat == null) {
            return;
        }
        if (controls.wasPressed(GameControls.COMMAND)) {
            chat.open("/", typedBy(GameControls.COMMAND));
        } else if (controls.wasPressed(GameControls.CHAT)) {
            chat.open("", typedBy(GameControls.CHAT));
        } else if (controls.wasPressed(GameControls.CHAT_ALTERNATE)) {
            chat.open("", typedBy(GameControls.CHAT_ALTERNATE));
        }
    }

    /**
     * The character a key bound to an action types, if it is a plain letter, digit or slash, so the
     * chat box can undo it when the key that opened the box types it too.
     *
     * @param action The action.
     * @return The character, or blank.
     */
    private static String typedBy(@NonNull String action) {
        final KeyBinding binding = GraphicsManager.getControls().binding(action);
        if (binding.ctrl() || binding.alt()) {
            return "";
        }
        final String name = binding.key().name();
        if (name.length() == 1) {
            final String letter = name.toLowerCase(java.util.Locale.ROOT);
            return binding.shift() ? letter.toUpperCase(java.util.Locale.ROOT) : letter;
        }
        return binding.key() == Key.FORWARD_SLASH ? "/" : "";
    }

    /**
     * No buttons, looking where the camera looks, for frames while a menu has the keys.
     *
     * @return The input.
     */
    private static PlayerInput stillInput() {
        // Flight stays as it was, so a flying player doesn't drop while in a menu
        return sampleLook(flying ? PlayerInput.FLY : 0);
    }

    /**
     * What the player is doing now: the keys held in the scene window, unless a window is taking
     * the keyboard, and where the camera looks.
     *
     * @return The input.
     */
    private static PlayerInput sampleInput() {
        int buttons = 0;
        final Window window = GraphicsManager.getSceneWindow();
        if (window != null && !IkGui.getIO().wantCaptureKeyboard) {
            final Controls controls = GraphicsManager.getControls();
            buttons |= controls.isDown(GameControls.FORWARD) ? PlayerInput.FORWARD : 0;
            buttons |= controls.isDown(GameControls.BACK) ? PlayerInput.BACK : 0;
            buttons |= controls.isDown(GameControls.LEFT) ? PlayerInput.LEFT : 0;
            buttons |= controls.isDown(GameControls.RIGHT) ? PlayerInput.RIGHT : 0;
            buttons |= controls.isDown(GameControls.JUMP) ? PlayerInput.UP : 0;
            buttons |= controls.isDown(GameControls.SNEAK) ? PlayerInput.DOWN : 0;
            buttons |= controls.isDown(GameControls.SPRINT) ? PlayerInput.SPRINT : 0;
            updateFlight(controls.isDown(GameControls.JUMP));
        }
        buttons |= flying ? PlayerInput.FLY : 0;
        return sampleLook(buttons);
    }

    /**
     * Toggle creative flight on a double tap of jump, and let go of it on landing or outside
     * creative, from the predicted state before the frame being sampled.
     *
     * @param jump Whether jump is held.
     */
    private static void updateFlight(boolean jump) {
        final Prediction prediction = session == null ? null : session.getPrediction();
        if (prediction == null) {
            return;
        }
        final long frame = prediction.getFrame() + 1;
        final boolean creative = prediction.gameMode() == GameMode.CREATIVE;
        if (jump && !jumpWasDown) {
            if (creative && frame - lastJumpPress <= DOUBLE_TAP_FRAMES) {
                flying = !flying;
                lastJumpPress = Long.MIN_VALUE / 2;
            } else {
                lastJumpPress = frame;
            }
        }
        jumpWasDown = jump;
        final Movement now = prediction.movement(prediction.getFrame());
        if (!creative || (flying && now.mode() == MoveMode.FLYING && now.onGround())) {
            flying = false;
        }
    }

    /**
     * An input with buttons, looking where the camera looks.
     *
     * @param buttons The buttons held.
     * @return The input.
     */
    private static PlayerInput sampleLook(int buttons) {
        final Camera camera = GraphicsManager.getScene().getCamera();
        final int yaw = Angle.wrap(Math.round(camera.getRotation().y / TURN_RADIANS * Angle.TURN));
        final int pitch =
                (int)
                        Math.clamp(
                                Math.round(camera.getRotation().x / TURN_RADIANS * Angle.TURN),
                                -Angle.QUARTER,
                                Angle.QUARTER);
        return new PlayerInput(buttons, yaw, pitch);
    }

    /**
     * Put the camera at the player's eye, between the last two frames by how far the clock is into
     * the next one, so it moves smoothly at any drawing rate.
     *
     * @param prediction The prediction.
     * @param clock When frames run.
     * @param now The time, from {@link System#nanoTime()}.
     */
    private static void placeCamera(
            @NonNull Prediction prediction, @NonNull FrameClock clock, long now) {
        final long frame = prediction.getFrame();
        final Movement before = prediction.movement(frame - 1);
        final Movement after = prediction.movement(frame);
        final double t =
                Math.clamp((double) (now - clock.frameStartNanos()) / clock.getFrameNanos(), 0, 1);
        final double x = (before.x() + (after.x() - before.x()) * t) / Fixed.ONE;
        final double y = (before.y() + (after.y() - before.y()) * t) / Fixed.ONE;
        final double z = (before.z() + (after.z() - before.z()) * t) / Fixed.ONE;
        GraphicsManager.getScene().getCamera().setPosition(x, y + EYE_HEIGHT, z);
    }

    /**
     * Log the client's chunks, the time and the player now and then, when the count changes or the
     * player moved.
     *
     * @param playing The session.
     */
    private static void report(@NonNull ClientSession playing) {
        final long nowMillis = System.currentTimeMillis();
        final int chunks = playing.getWorld().size();
        final Prediction prediction = playing.getPrediction();
        final Movement movement = prediction.movement(prediction.getFrame());
        final boolean moved =
                lastReportedMovement == null
                        || movement.x() != lastReportedMovement.x()
                        || movement.y() != lastReportedMovement.y()
                        || movement.z() != lastReportedMovement.z();
        final long since = nowMillis - lastReportMillis;
        if (since < REPORT_INTERVAL_MILLIS
                || (chunks == lastReported && !moved && since < IDLE_REPORT_MILLIS)) {
            return;
        }
        lastReported = chunks;
        lastReportMillis = nowMillis;
        lastReportedMovement = movement;
        log.info(
                "The client has {} chunks, {} with sections; it is {} o'clock; the player is at"
                        + " {}, {}, {} on frame {}, {} mispredicts, server holds {} inputs and"
                        + " missed {}; round trip {} ms, {} frames ahead, aiming for {}, rate {}%,"
                        + " {} resyncs",
                chunks,
                renderer == null ? 0 : renderer.sectionCount(),
                String.format("%.2f", WorldTime.hour(playing.getClock().now())),
                String.format("%.2f", (double) movement.x() / Fixed.ONE),
                String.format("%.2f", (double) movement.y() / Fixed.ONE),
                String.format("%.2f", (double) movement.z() / Fixed.ONE),
                prediction.getFrame(),
                prediction.getMispredicts(),
                prediction.getServerBuffered(),
                prediction.getServerMisses(),
                String.format("%.1f", playing.getFrameClock().roundTripMillis()),
                prediction.getFrame() - prediction.getAcknowledged(),
                playing.getFrameClock().getTarget(),
                String.format("%.1f", playing.getFrameClock().rate() * 100),
                playing.getFrameClock().getResyncs());
        if (playing.getConnection() instanceof UdpConnection udp) {
            log.info(
                    "UDP traffic: {} B/s up, {} B/s down",
                    Math.round(udp.sendRate()),
                    Math.round(udp.receiveRate()));
        }
    }

    /** Light the scene by the world's clock and switch to drawing the world. */
    private static void setUpScene() {
        sky = new SkyLight(GraphicsManager.getScene());
        sky.update(WorldTime.START_TICKS);
        // The debug stage draws nothing until something asks for debug lines, like the overlay
        GraphicsManager.swapPipeline(RenderConfig.WORLD.withDebug(true));
    }

    /** Private constructor so this class is not instantiated. */
    private SinglePlayerGame() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
