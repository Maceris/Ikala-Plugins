package com.ikalagaming.factory;

import com.ikalagaming.factory.client.ClientSession;
import com.ikalagaming.factory.networking.base.Connection;
import com.ikalagaming.factory.networking.request.clientbound.Joined;
import com.ikalagaming.factory.render.SkyLight;
import com.ikalagaming.factory.render.WorldRenderer;
import com.ikalagaming.factory.save.SaveSummary;
import com.ikalagaming.factory.saves.SaveUtil;
import com.ikalagaming.factory.world.WorldTime;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.launcher.Launcher;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Starts a single player game: opens the save on the local server, joins it, and keeps telling the
 * server where the player is, so it sends the chunks around them. The player flies: the camera is
 * the player's eye. See the Starting a Game design doc.
 */
@Slf4j
public final class SinglePlayerGame {

    /** How high the eye is above the feet, in blocks. */
    public static final double EYE_HEIGHT = 1.62;

    /** The name single player joins as, until accounts exist. */
    private static final String PLAYER_NAME = "Player";

    /** How often the player's position is sent, at most, in milliseconds. */
    private static final long MOVE_INTERVAL_MILLIS = 100;

    /** How far the player has to move before it is sent again, in blocks. */
    private static final double MOVE_THRESHOLD = 0.05;

    /** How often the client's chunk count is logged when it changes, in milliseconds. */
    private static final long REPORT_INTERVAL_MILLIS = 5000;

    /** The session of the game being played, or null. Main thread only. */
    private static ClientSession session;

    /** Draws the chunks the server sends, or null. */
    private static WorldRenderer renderer;

    /** Lights the world by its clock, or null. */
    private static SkyLight sky;

    /** The main loop stage that sends the player's position, or null. */
    private static UUID moveStage;

    /** Where the player was last sent. */
    private static final Vector3d lastSent = new Vector3d(Double.NaN);

    /** When the position was last sent, in milliseconds. */
    private static long lastSentMillis;

    /** When the chunk count was last logged, in milliseconds. */
    private static long lastReportMillis;

    /** The chunk count last logged. */
    private static int lastReported = -1;

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
        if (moveStage != null) {
            Launcher.removeMainThreadStage(moveStage);
            moveStage = null;
        }
        if (session != null) {
            session.close();
            session = null;
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
        lastSent.set(Double.NaN);
        moveStage = Launcher.addMainThreadStage(SinglePlayerGame::sendMove);
    }

    /**
     * Put the camera at the player's eye once the server says where the player is.
     *
     * @param joined The server's answer.
     */
    private static void onJoined(@NonNull Joined joined) {
        log.info(
                "Joined as {} at {}, {}, {}",
                joined.getPlayerId(),
                joined.getX(),
                joined.getY(),
                joined.getZ());
        final Camera camera = GraphicsManager.getScene().getCamera();
        camera.setPosition(joined.getX(), joined.getY() + EYE_HEIGHT, joined.getZ());
        camera.setRotation(joined.getPitch(), joined.getYaw());
    }

    /**
     * Send the player's position when it moved, at most every {@value #MOVE_INTERVAL_MILLIS} ms. A
     * main loop stage.
     *
     * @return The stage status.
     */
    private static int sendMove() {
        final ClientSession playing = session;
        if (playing == null || !playing.isJoined()) {
            return 0;
        }
        if (sky != null && playing.getClock().isKnown()) {
            sky.update(playing.getClock().now());
        }
        final long now = System.currentTimeMillis();
        if (now - lastSentMillis < MOVE_INTERVAL_MILLIS) {
            return 0;
        }
        final Camera camera = GraphicsManager.getScene().getCamera();
        final Vector3d eye = camera.getPosition();
        if (Double.isNaN(lastSent.x) || eye.distance(lastSent) > MOVE_THRESHOLD) {
            lastSent.set(eye);
            lastSentMillis = now;
            playing.sendMove(
                    eye.x,
                    eye.y - EYE_HEIGHT,
                    eye.z,
                    camera.getRotation().y,
                    camera.getRotation().x);
        }
        final int chunks = playing.getWorld().size();
        if (chunks != lastReported && now - lastReportMillis >= REPORT_INTERVAL_MILLIS) {
            lastReported = chunks;
            lastReportMillis = now;
            log.info(
                    "The client has {} chunks, {} with sections; it is {} o'clock",
                    chunks,
                    renderer == null ? 0 : renderer.sectionCount(),
                    String.format("%.2f", WorldTime.hour(playing.getClock().now())));
        }
        return 0;
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
