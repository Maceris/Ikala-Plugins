package com.ikalagaming.factory.gui;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.SinglePlayerGame;
import com.ikalagaming.factory.client.ClientSession;
import com.ikalagaming.factory.client.FrameClock;
import com.ikalagaming.factory.client.Prediction;
import com.ikalagaming.factory.lab.LabTool;
import com.ikalagaming.factory.networking.udp.UdpConnection;
import com.ikalagaming.factory.render.WorldRenderer;
import com.ikalagaming.factory.sim.Angle;
import com.ikalagaming.factory.sim.Fixed;
import com.ikalagaming.factory.sim.Frames;
import com.ikalagaming.factory.sim.Movement;
import com.ikalagaming.factory.sim.PlayerSimulation;
import com.ikalagaming.factory.sim.WorldClock;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.WorldTime;
import com.ikalagaming.graphics.DebugDraw;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.gui.WindowManager;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.windows.IkGuiDemo;
import com.ikalagaming.graphics.gui.windows.IkScriptDebugger;
import com.ikalagaming.graphics.gui.windows.ScriptMonitor;
import com.ikalagaming.graphics.scene.debug.DebugArrow;
import com.ikalagaming.graphics.scene.debug.DebugBox;
import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.plugins.PluginInfo;
import com.ikalagaming.plugins.PluginManager;

import lombok.NonNull;
import org.joml.Vector3d;

import java.util.Locale;

/**
 * Lotomation's sections of the debug overlay, and the debug lines they switch on: chunk borders,
 * and the player's box and look direction. Game and Player start open.
 */
public final class GameOverlay {

    /** Section IDs, and where each goes. */
    static final String GAME = "lotomation:game";

    static final String PLAYER = "lotomation:player";
    static final String NETWORK = "lotomation:network";
    static final String WORLD = "lotomation:world";
    static final String LINES = "lotomation:lines";
    static final String TOOLS = "lotomation:tools";

    /** Shown when there is no game. */
    private static final String NOT_PLAYING = "Not playing";

    /** How far the look arrow reaches, in blocks. */
    private static final double LOOK_LENGTH = 2;

    /** The grid on the current chunk's faces, in blocks. */
    private static final int GRID = 4;

    private static final int CHUNK_COLOR = Color.rgba(255, 220, 40, 255);
    private static final int GRID_COLOR = Color.rgba(255, 220, 40, 110);
    private static final int NEIGHBOR_COLOR = Color.rgba(255, 60, 60, 200);
    private static final int PLAYER_COLOR = Color.rgba(255, 255, 255, 255);
    private static final int SERVER_COLOR = Color.rgba(255, 140, 0, 255);
    private static final int LOOK_COLOR = Color.rgba(60, 140, 255, 255);

    /** The plugin's graphics, which draw the lines. */
    private final GraphicsContext graphics;

    /** Whether chunk borders are drawn. */
    private volatile boolean chunkBorders;

    /** Whether the player's box and look direction are drawn. */
    private volatile boolean entityBoxes;

    /** Draws the lines each frame. */
    private final Runnable drawTask = this::drawLines;

    /**
     * Lotomation's overlay sections.
     *
     * @param graphics The plugin's graphics.
     */
    public GameOverlay(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /** Add the sections and start drawing the lines they switch on. */
    public void install() {
        graphics.debugSection(GAME, "Game", 100, true)
                .line("version", GameOverlay::version)
                .line("fps", () -> GraphicsManager.getLastFPS() + " FPS")
                .line("time", GameOverlay::time);
        graphics.debugSection(PLAYER, "Player", 110, true)
                .line("position", () -> player(GameOverlay::position));
        graphics.debugSection(NETWORK, "Network", 200, false).line("network", GameOverlay::network);
        graphics.debugSection(WORLD, "World", 300, false).line("chunks", GameOverlay::world);
        graphics.debugSection(LINES, "Debug lines", 400, false)
                .toggle("chunks", "Chunk borders", () -> chunkBorders, on -> chunkBorders = on)
                .toggle(
                        "entities",
                        "Player box and look direction",
                        () -> entityBoxes,
                        on -> entityBoxes = on);
        final WindowManager windows = GraphicsManager.getWindowManager();
        graphics.debugSection(TOOLS, "Tools", 800, false)
                .toggle(
                        "ikgui-demo",
                        "IkGui demo",
                        () -> windows.isVisible(IkGuiDemo.WINDOW_NAME),
                        show -> windows.setVisible(IkGuiDemo.WINDOW_NAME, show))
                .toggle(
                        "script-debugger",
                        "Script debugger",
                        () -> windows.isVisible(IkScriptDebugger.WINDOW_NAME),
                        show -> windows.setVisible(IkScriptDebugger.WINDOW_NAME, show))
                .toggle(
                        "script-monitor",
                        "Script monitor",
                        () -> windows.isVisible(ScriptMonitor.WINDOW_NAME),
                        show -> windows.setVisible(ScriptMonitor.WINDOW_NAME, show))
                .toggle("worldgen-lab", "Worldgen Lab", GameOverlay::labOpen, GameOverlay::showLab);
        graphics.addFrameTask(drawTask);
    }

    /**
     * Whether chunk borders are drawn.
     *
     * @return True if drawn.
     */
    public boolean isChunkBorders() {
        return chunkBorders;
    }

    /**
     * Draw chunk borders or not.
     *
     * @param show True to draw them.
     */
    public void setChunkBorders(boolean show) {
        chunkBorders = show;
    }

    /**
     * Whether the player's box and look direction are drawn.
     *
     * @return True if drawn.
     */
    public boolean isEntityBoxes() {
        return entityBoxes;
    }

    /**
     * Draw the player's box and look direction or not.
     *
     * @param show True to draw them.
     */
    public void setEntityBoxes(boolean show) {
        entityBoxes = show;
    }

    /**
     * The game's version.
     *
     * @return The line.
     */
    private static String version() {
        return "Lotomation "
                + PluginManager.getInstance()
                        .getInfo(FactoryPlugin.PLUGIN_NAME)
                        .map(PluginInfo::getVersion)
                        .orElse("?");
    }

    /**
     * The world's day and time.
     *
     * @return The line.
     */
    private static String time() {
        final ClientSession session = SinglePlayerGame.getSession();
        if (session == null || !session.getClock().isKnown()) {
            return "No world time";
        }
        final double ticks = session.getClock().now();
        final double hour = WorldTime.hour(ticks);
        final int whole = (int) hour;
        final int minute = (int) ((hour - whole) * 60);
        final int rate = session.getClock().getTicksPerSecond();
        final String state;
        if (SinglePlayerGame.isPaused()) {
            state = " (paused)";
        } else if (rate == 0) {
            state = " (world frozen)";
        } else if (rate != WorldClock.NORMAL_RATE) {
            state = " (" + rate + " ticks/s)";
        } else {
            state = "";
        }
        return String.format(
                Locale.ROOT,
                "Day %d, %02d:%02d%s",
                WorldTime.day((long) ticks),
                whole,
                minute,
                state);
    }

    /**
     * Format something about the predicted player.
     *
     * @param format Formats the movement and the session.
     * @return The text, or a note that there's no game.
     */
    private static String player(@NonNull PlayerFormat format) {
        final ClientSession session = SinglePlayerGame.getSession();
        final Prediction prediction = session == null ? null : session.getPrediction();
        if (prediction == null) {
            return NOT_PLAYING;
        }
        return format.format(prediction.movement(prediction.getFrame()), prediction);
    }

    /** Formats the player's movement. */
    @FunctionalInterface
    private interface PlayerFormat {
        /**
         * Format.
         *
         * @param movement The predicted movement.
         * @param prediction The prediction.
         * @return The text.
         */
        String format(@NonNull Movement movement, @NonNull Prediction prediction);
    }

    /**
     * Where the player is, which way they face and how they move.
     *
     * @param movement The movement.
     * @param prediction The prediction.
     * @return The lines.
     */
    private static String position(@NonNull Movement movement, @NonNull Prediction prediction) {
        final double x = (double) movement.x() / Fixed.ONE;
        final double y = (double) movement.y() / Fixed.ONE;
        final double z = (double) movement.z() / Fixed.ONE;
        final long blockX = Math.floorDiv(movement.x(), Fixed.ONE);
        final long blockY = Math.floorDiv(movement.y(), Fixed.ONE);
        final long blockZ = Math.floorDiv(movement.z(), Fixed.ONE);
        final ChunkPos chunk = ChunkPos.containing(blockX, blockY, blockZ);
        final double yawDegrees = movement.yaw() * 360.0 / Angle.TURN;
        final double pitchDegrees = movement.pitch() * 360.0 / Angle.TURN;
        final double speed =
                (double) Fixed.length(movement.vx(), movement.vy(), movement.vz())
                        / Fixed.ONE
                        * 1000
                        / Frames.FRAME_MILLIS;
        return String.format(
                Locale.ROOT,
                "XYZ: %.3f / %.3f / %.3f%n"
                        + "Block: %d %d %d, in chunk %d %d %d at %d %d %d%n"
                        + "Facing %s (yaw %.1f, pitch %.1f)%n"
                        + "Speed: %.2f m/s, %s, %s%s",
                x,
                y,
                z,
                blockX,
                blockY,
                blockZ,
                chunk.x(),
                chunk.y(),
                chunk.z(),
                Math.floorMod(blockX, World.CHUNK_SIZE),
                Math.floorMod(blockY, World.CHUNK_SIZE),
                Math.floorMod(blockZ, World.CHUNK_SIZE),
                facing(movement.yaw()),
                yawDegrees,
                pitchDegrees,
                speed,
                prediction.gameMode().id(),
                movement.mode().name().toLowerCase(Locale.ROOT),
                movement.onGround() ? ", on the ground" : "");
    }

    /**
     * The compass direction for a yaw: forward is -z at yaw 0, and +x a quarter turn later.
     *
     * @param yaw The yaw, in {@link Angle} units.
     * @return The direction and the axis it goes along.
     */
    static String facing(int yaw) {
        final int quadrant = Math.floorMod(Math.round(yaw / (float) Angle.QUARTER), 4);
        return switch (quadrant) {
            case 0 -> "north (-z)";
            case 1 -> "east (+x)";
            case 2 -> "south (+z)";
            default -> "west (-x)";
        };
    }

    /**
     * The netcode's state.
     *
     * @return The lines.
     */
    private static String network() {
        final ClientSession session = SinglePlayerGame.getSession();
        final Prediction prediction = session == null ? null : session.getPrediction();
        final FrameClock clock = session == null ? null : session.getFrameClock();
        if (prediction == null || clock == null) {
            return NOT_PLAYING;
        }
        final double roundTrip = clock.roundTripMillis();
        final StringBuilder text = new StringBuilder();
        text.append(
                        roundTrip < 0
                                ? "Round trip: not measured"
                                : String.format(Locale.ROOT, "Round trip: %.1f ms", roundTrip))
                .append(
                        String.format(
                                Locale.ROOT,
                                "%nFrame %d, %d ahead of the server's last answer",
                                prediction.getFrame(),
                                prediction.getFrame() - prediction.getAcknowledged()))
                .append(
                        String.format(
                                Locale.ROOT,
                                "%nServer buffer: %d (smoothed %.1f), aiming for %d",
                                prediction.getServerBuffered(),
                                clock.getBuffered(),
                                clock.getTarget()))
                .append(
                        String.format(
                                Locale.ROOT, "%nFrame rate: %.1f%% of normal", clock.rate() * 100))
                .append(
                        String.format(
                                Locale.ROOT,
                                "%nMissed inputs: %d, mispredicts: %d, resyncs: %d",
                                prediction.getServerMisses(),
                                prediction.getMispredicts(),
                                clock.getResyncs()));
        if (session.getConnection() instanceof UdpConnection udp) {
            text.append(
                            String.format(
                                    Locale.ROOT,
                                    "%nUDP: %d packets sent, %d lost, %d resends",
                                    udp.packetsSent(),
                                    udp.packetsLost(),
                                    udp.resends()))
                    .append(
                            String.format(
                                    Locale.ROOT,
                                    "%nTraffic: %.1f KB/s up, %.1f KB/s down",
                                    udp.sendRate() / 1000,
                                    udp.receiveRate() / 1000));
        } else {
            text.append("\nLocal connection, no network");
        }
        return text.toString();
    }

    /**
     * The client's chunks and how drawing them is going.
     *
     * @return The lines.
     */
    private static String world() {
        final ClientSession session = SinglePlayerGame.getSession();
        final WorldRenderer renderer = SinglePlayerGame.getRenderer();
        if (session == null || renderer == null) {
            return NOT_PLAYING;
        }
        return String.format(
                Locale.ROOT,
                "Chunks: %d received, %d drawn%nDrawing: %d waiting, %d done, %.2f ms each",
                session.getWorld().size(),
                renderer.sectionCount(),
                renderer.queuedCount(),
                renderer.builtCount(),
                renderer.buildMillis());
    }

    /**
     * Whether the Worldgen Lab is open.
     *
     * @return True if open.
     */
    private static boolean labOpen() {
        final LabTool lab = FactoryClientPlugin.getLabTool();
        return lab != null && lab.isOpen();
    }

    /**
     * Open or hide the Worldgen Lab.
     *
     * @param show True to open it.
     */
    private static void showLab(boolean show) {
        final LabTool lab = FactoryClientPlugin.getLabTool();
        if (lab == null) {
            return;
        }
        if (show) {
            lab.open();
        } else {
            lab.hide();
        }
    }

    /** Draw the lines that are switched on, for the next frame. */
    private void drawLines() {
        if (graphics.isClosed() || (!chunkBorders && !entityBoxes)) {
            return;
        }
        final ClientSession session = SinglePlayerGame.getSession();
        final Prediction prediction = session == null ? null : session.getPrediction();
        if (prediction == null) {
            return;
        }
        final DebugDraw draw = graphics.debug();
        final Movement movement = prediction.movement(prediction.getFrame());
        if (chunkBorders) {
            drawChunks(draw, movement);
        }
        if (entityBoxes) {
            drawPlayer(draw, movement, PLAYER_COLOR);
            final Movement confirmed = prediction.movement(prediction.getAcknowledged());
            if (!confirmed.equals(movement)) {
                drawPlayer(draw, confirmed, SERVER_COLOR);
            }
            drawLook(draw, movement);
        }
    }

    /**
     * Draw the chunk the player is in with a grid on its faces, and the corners of the chunks
     * around it.
     *
     * @param draw Draws.
     * @param movement Where the player is.
     */
    private static void drawChunks(@NonNull DebugDraw draw, @NonNull Movement movement) {
        final ChunkPos chunk =
                ChunkPos.containing(
                        Math.floorDiv(movement.x(), Fixed.ONE),
                        Math.floorDiv(movement.y(), Fixed.ONE),
                        Math.floorDiv(movement.z(), Fixed.ONE));
        final double size = World.CHUNK_SIZE;
        final double x0 = chunk.blockX();
        final double y0 = chunk.blockY();
        final double z0 = chunk.blockZ();
        draw.draw(
                DebugBox.fromCorners(
                        new Vector3d(x0, y0, z0),
                        new Vector3d(x0 + size, y0 + size, z0 + size),
                        CHUNK_COLOR));
        for (int step = GRID; step < World.CHUNK_SIZE; step += GRID) {
            for (double side : new double[] {0, size}) {
                // Lines across each pair of opposite faces, along both of their axes
                line(draw, x0 + step, y0, z0 + side, x0 + step, y0 + size, z0 + side, GRID_COLOR);
                line(draw, x0, y0 + step, z0 + side, x0 + size, y0 + step, z0 + side, GRID_COLOR);
                line(draw, x0 + side, y0 + step, z0, x0 + side, y0 + step, z0 + size, GRID_COLOR);
                line(draw, x0 + side, y0, z0 + step, x0 + side, y0 + size, z0 + step, GRID_COLOR);
                line(draw, x0 + step, y0 + side, z0, x0 + step, y0 + side, z0 + size, GRID_COLOR);
                line(draw, x0, y0 + side, z0 + step, x0 + size, y0 + side, z0 + step, GRID_COLOR);
            }
        }
        // The vertical edges of the chunks around, a chunk above and below
        for (int dx = -1; dx <= 2; ++dx) {
            for (int dz = -1; dz <= 2; ++dz) {
                if ((dx == 0 || dx == 1) && (dz == 0 || dz == 1)) {
                    continue;
                }
                final double x = x0 + dx * size;
                final double z = z0 + dz * size;
                line(draw, x, y0 - size, z, x, y0 + 2 * size, z, NEIGHBOR_COLOR);
            }
        }
    }

    /**
     * Draw a player's box.
     *
     * @param draw Draws.
     * @param movement Where the player is.
     * @param color The color.
     */
    private static void drawPlayer(@NonNull DebugDraw draw, @NonNull Movement movement, int color) {
        final double x = (double) movement.x() / Fixed.ONE;
        final double y = (double) movement.y() / Fixed.ONE;
        final double z = (double) movement.z() / Fixed.ONE;
        final double half = (double) PlayerSimulation.HALF_WIDTH / Fixed.ONE;
        final double height = (double) PlayerSimulation.HEIGHT / Fixed.ONE;
        draw.draw(
                DebugBox.fromCorners(
                        new Vector3d(x - half, y, z - half),
                        new Vector3d(x + half, y + height, z + half),
                        color));
    }

    /**
     * Draw where the player looks, from the eye.
     *
     * @param draw Draws.
     * @param movement Where the player is and looks.
     */
    private static void drawLook(@NonNull DebugDraw draw, @NonNull Movement movement) {
        final double yaw = movement.yaw() * 2 * Math.PI / Angle.TURN;
        final double pitch = movement.pitch() * 2 * Math.PI / Angle.TURN;
        final Vector3d eye =
                new Vector3d(
                        (double) movement.x() / Fixed.ONE,
                        (double) movement.y() / Fixed.ONE + SinglePlayerGame.EYE_HEIGHT,
                        (double) movement.z() / Fixed.ONE);
        // Forward is (sin, -cos) in x and z at yaw 0; a positive pitch looks down
        final Vector3d to =
                new Vector3d(
                                Math.sin(yaw) * Math.cos(pitch),
                                -Math.sin(pitch),
                                -Math.cos(yaw) * Math.cos(pitch))
                        .mul(LOOK_LENGTH)
                        .add(eye);
        draw.draw(new DebugArrow(eye, to, LOOK_COLOR));
    }

    /**
     * Draw a line.
     *
     * @param draw Draws.
     * @param x0 Start x.
     * @param y0 Start y.
     * @param z0 Start z.
     * @param x1 End x.
     * @param y1 End y.
     * @param z1 End z.
     * @param color The color.
     */
    private static void line(
            @NonNull DebugDraw draw,
            double x0,
            double y0,
            double z0,
            double x1,
            double y1,
            double z1,
            int color) {
        draw.draw(new DebugLine(new Vector3d(x0, y0, z0), new Vector3d(x1, y1, z1), color));
    }
}
