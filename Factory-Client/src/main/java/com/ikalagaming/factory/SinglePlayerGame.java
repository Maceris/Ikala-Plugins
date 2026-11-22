package com.ikalagaming.factory;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import com.ikalagaming.factory.client.ClientSession;
import com.ikalagaming.factory.client.Prediction;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.render.SkyLight;
import com.ikalagaming.factory.render.WorldRenderer;
import com.ikalagaming.factory.save.SaveSummary;
import com.ikalagaming.factory.saves.SaveUtil;
import com.ikalagaming.factory.sim.Angle;
import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.Frames;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerInput;
import com.ikalagaming.factory.world.WorldTime;
import com.ikalagaming.graphics.CameraManager;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Starts a single player game: opens the save on the local server, joins it, and plays: each
 * command frame it samples the keys, predicts the player's movement and sends the inputs to the
 * server, and each drawn frame it puts the camera at the player's eye. The player flies. See the
 * Starting a Game and Command Frames design docs.
 */
@Slf4j
public final class SinglePlayerGame {

    /** How high the eye is above the feet, in blocks. */
    public static final double EYE_HEIGHT = 1.62;

    /** The name single player joins as, until accounts exist. */
    private static final String PLAYER_NAME = "Player";

    /** How often the client's chunk count is logged when it changes, in milliseconds. */
    private static final long REPORT_INTERVAL_MILLIS = 5000;

    /** The most command frames run in one drawn frame; any more wait for the next one. */
    private static final int MAX_FRAMES_PER_UPDATE = 8;

    /** Radians in a turn. */
    private static final double TURN_RADIANS = 2 * Math.PI;

    /** The session of the game being played, or null. Main thread only. */
    private static ClientSession session;

    /** Draws the chunks the server sends, or null. */
    private static WorldRenderer renderer;

    /** Lights the world by its clock, or null. */
    private static SkyLight sky;

    /** The main loop stage that plays, or null. */
    private static UUID playStage;

    /** When the next command frame is due, from {@link System#nanoTime()}. */
    private static long nextFrameNanos;

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

    /** Leave the game being played, if any. Main thread only. */
    public static void stop() {
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
     * The session of the game being played.
     *
     * @return The session, or null when not playing.
     */
    public static ClientSession getSession() {
        return session;
    }

    /** Connect to the local server and join. Main thread only. */
    private static void join() {
        final ClientSession joining =
                new ClientSession(GraphicsManager::runOnMainThread, SinglePlayerGame::onJoined);
        renderer = new WorldRenderer(GraphicsManager.forPlugin(FactoryClientPlugin.PLUGIN_NAME));
        joining.getWorld().addListener(renderer);
        final Connection connection = LocalServer.connect(joining);
        session = joining;
        joining.join(connection, PLAYER_NAME);
        playStage = Launcher.addMainThreadStage(SinglePlayerGame::play);
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
        nextFrameNanos = System.nanoTime();
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
        final Prediction prediction = playing.getPrediction();
        final long now = System.nanoTime();
        int run = 0;
        while (now - nextFrameNanos >= 0 && run < MAX_FRAMES_PER_UPDATE) {
            prediction.step(sampleInput());
            nextFrameNanos += Frames.FRAME_NANOS;
            ++run;
        }
        if (run > 0) {
            playing.sendInputs();
        }
        placeCamera(prediction, now);
        report(playing);
        return 0;
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
            buttons |= window.isKeyPressed(GLFW_KEY_W) ? PlayerInput.FORWARD : 0;
            buttons |= window.isKeyPressed(GLFW_KEY_S) ? PlayerInput.BACK : 0;
            buttons |= window.isKeyPressed(GLFW_KEY_A) ? PlayerInput.LEFT : 0;
            buttons |= window.isKeyPressed(GLFW_KEY_D) ? PlayerInput.RIGHT : 0;
            buttons |= window.isKeyPressed(GLFW_KEY_SPACE) ? PlayerInput.UP : 0;
            buttons |= window.isKeyPressed(GLFW_KEY_LEFT_SHIFT) ? PlayerInput.DOWN : 0;
            buttons |= window.isKeyPressed(GLFW_KEY_LEFT_CONTROL) ? PlayerInput.SPRINT : 0;
        }
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
     * @param now The time, from {@link System#nanoTime()}.
     */
    private static void placeCamera(@NonNull Prediction prediction, long now) {
        final long frame = prediction.getFrame();
        final Movement before = prediction.movement(frame - 1);
        final Movement after = prediction.movement(frame);
        final long frameStart = nextFrameNanos - Frames.FRAME_NANOS;
        final double t = Math.clamp((double) (now - frameStart) / Frames.FRAME_NANOS, 0, 1);
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
        if ((chunks == lastReported && !moved)
                || nowMillis - lastReportMillis < REPORT_INTERVAL_MILLIS) {
            return;
        }
        lastReported = chunks;
        lastReportMillis = nowMillis;
        lastReportedMovement = movement;
        log.info(
                "The client has {} chunks, {} with sections; it is {} o'clock; the player is at"
                        + " {}, {}, {} on frame {}, {} mispredicts, server holds {} inputs and"
                        + " missed {}",
                chunks,
                renderer == null ? 0 : renderer.sectionCount(),
                String.format("%.2f", WorldTime.hour(playing.getClock().now())),
                String.format("%.2f", (double) movement.x() / Fixed.ONE),
                String.format("%.2f", (double) movement.y() / Fixed.ONE),
                String.format("%.2f", (double) movement.z() / Fixed.ONE),
                prediction.getFrame(),
                prediction.getMispredicts(),
                prediction.getServerBuffered(),
                prediction.getServerMisses());
    }

    /** Light the scene by the world's clock and switch to drawing the world. */
    private static void setUpScene() {
        sky = new SkyLight(GraphicsManager.getScene());
        sky.update(WorldTime.START_TICKS);
        GraphicsManager.swapPipeline(RenderConfig.WORLD);
    }

    /** Private constructor so this class is not instantiated. */
    private SinglePlayerGame() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
