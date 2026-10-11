package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.factory.world.gen.BiomeSelector;
import com.ikalagaming.factory.world.gen.BlockRules;
import com.ikalagaming.factory.world.gen.Stage;
import com.ikalagaming.factory.world.gen.data.Diagnostic;
import com.ikalagaming.factory.world.gen.debug.SliceImages;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;
import com.ikalagaming.factory.world.gen.density.Box;
import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.*;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/**
 * The Worldgen Lab: a tool for writing and tuning world generation data without a game, built on
 * the same code the game generates with. A slice view of any noise, density, parameter, node, the
 * biome map or generated blocks, which pans and zooms; a readout of everything at the cursor; a
 * trace of every node at a pinned point; the target's node tree with live thumbnails; the data's
 * problems; bookmarks; exports; a 3D preview of generated chunks; and the section corpus export.
 *
 * <p>Data reloads when its files change. If an edit breaks it, the problems are listed and the last
 * good world stays on screen, marked stale.
 *
 * <p>All heavy work runs on worker threads; results come back to the render thread through the UI's
 * post queue. Closing the Lab stops every thread and releases every texture and section.
 */
@Slf4j
public final class WorldgenLab implements AutoCloseable {

    /** The Lab's surface, and its tool, ID. */
    public static final String SURFACE_ID = "factory/worldgen-lab";

    /** The height of the controls, in UI units. */
    private static final float CONTROLS_HEIGHT = 70;

    /** The width of the side panel, in UI units. */
    private static final float SIDE_WIDTH = 440;

    /** How often data folders are checked for changes, in milliseconds. */
    private static final long WATCH_MILLIS = 1000;

    /** The smallest drawn chunk, in pixels, at which the chunk grid shows. */
    private static final double GRID_MIN_PIXELS = 12;

    /** The chunk grid's color. */
    private static final int GRID_COLOR = Color.rgba(0, 0, 0, 0.35f);

    /** The biome cell grid's color. */
    private static final int CELL_COLOR = Color.rgba(0, 0, 0, 0.15f);

    /** The world border's color. */
    private static final int BORDER_COLOR = Color.rgba(1, 0.2f, 0.2f, 0.9f);

    /** The pinned point's marker color. */
    private static final int PIN_COLOR = Color.rgba(1, 1, 0, 1);

    /** The pinned point's marker size, in pixels. */
    private static final float PIN_SIZE = 6;

    /** How far the slice moves per step of its buttons, in blocks. */
    private static final int SLICE_STEP = 16;

    /** Bits to shift for the red channel of a packed color. */
    private static final int RED_SHIFT = 16;

    /** Bits to shift for the green channel of a packed color. */
    private static final int GREEN_SHIFT = 8;

    /** The mask of one color channel. */
    private static final int CHANNEL_MASK = 0xff;

    /** How many earlier targets Back remembers. */
    private static final int MAX_HISTORY = 50;

    /** The number of axes in a position. */
    private static final int AXES = 3;

    /** The width of wide text fields, in UI units. */
    private static final float WIDE_FIELD = 320;

    /** The width of medium text fields, in UI units. */
    private static final float MEDIUM_FIELD = 200;

    /** The width of narrow fields, in UI units. */
    private static final float NARROW_FIELD = 120;

    /** The size of a legend swatch, in UI units. */
    private static final float SWATCH_SIZE = 12;

    /** The space between a legend swatch and its label, in UI units. */
    private static final float SWATCH_GAP = 6;

    /** The colormaps the controls offer, in combo order. */
    private static final String[] COLORMAPS = {"diverging", "sequential"};

    /** The stages the controls offer for blocks, in combo order. */
    private static final Stage[] STAGES = {Stage.TERRAIN, Stage.FLUIDS, Stage.BLOCK_RULES};

    /** The graphics context the Lab draws through. */
    private final GraphicsContext graphics;

    /** Where bookmarks, exports and the corpus go. */
    private final Path dataFolder;

    /** The data being looked at. */
    private final LabSession session;

    /** Draws slices. */
    private final LabRenderer renderer;

    /** The node tree panel. */
    private final NodeTree nodeTree;

    /** The 3D preview. */
    private final LabPreview preview;

    /** Saved views. */
    private final Bookmarks bookmarks;

    /** Where the view looks. */
    private final LabView view = new LabView();

    /** Loads data, one load at a time. */
    private final ExecutorService loader;

    /** Works out readouts, traces and node trees. */
    private final ExecutorService inspector;

    /** Checks data folders for changes. */
    private final ScheduledExecutorService watcher;

    /** Whether a load is queued or running, so checks don't pile up. */
    private final AtomicBoolean loading = new AtomicBoolean();

    /** The data folders, separated by semicolons. */
    private final IkString foldersText;

    /** The world type. */
    private final IkString worldTypeText;

    /** The seed. */
    private final IkString seedText;

    /** The target. */
    private final IkString targetText = new IkString("", 256);

    /** The noise channel, empty for the first. */
    private final IkString channelText = new IkString("", 64);

    /** The center, as x,y,z. */
    private final IkString centerText = new IkString("", 128);

    /** The new bookmark's name. */
    private final IkString bookmarkName = new IkString("", 64);

    /** The colormap, an index into {@link #COLORMAPS}. */
    private final IkInt colormap = new IkInt(0);

    /** The stage for blocks, an index into {@link #STAGES}. */
    private final IkInt stage = new IkInt(STAGES.length - 1);

    /** The diverging range, or 0 to fit the values. */
    private final float[] range = {0};

    /** The latest loaded data. Render thread. */
    private LabSession.State state;

    /** The frame being shown. Render thread. */
    private LabRenderer.Frame frame;

    /** What was last asked of the renderer, to notice changes. Render thread. */
    private String drawnKey = "";

    /** The view's size, in pixels, as last drawn. Render thread. */
    private int viewWidth;

    /** The view's height, in pixels, as last drawn. Render thread. */
    private int viewHeight;

    /** The block under the cursor, to only work out the readout when it changes. Render thread. */
    private String hoverKey = "";

    /** The readout for the cursor. Render thread. */
    private List<String> readout = List.of();

    /** The pinned point, or null. Render thread. */
    private double[] pinned;

    /** The trace at the pinned point. Render thread. */
    private WorldgenDebug.Trace trace;

    /** The tree root and view the node tree was built for, to rebuild it when they change. */
    private String treeKey = "";

    /**
     * The target the node tree is rooted at. Choosing a target sets it; viewing a node from the
     * tree doesn't, so the tree stays put. Render thread.
     */
    private String treeRoot = "";

    /** Earlier targets with their tree roots, newest first, for Back. Render thread. */
    private final Deque<Visit> history = new ArrayDeque<>();

    /** A message about the last export. Render thread. */
    private String exportMessage = "";

    /** The Lab's surface, once built. Render thread. */
    private Surface surface;

    /**
     * Set up the Lab; call {@link #open} to show it.
     *
     * @param graphics The graphics context to draw through.
     * @param settings What to load first.
     * @param dataFolder Where bookmarks, exports and the corpus go.
     */
    public WorldgenLab(
            @NonNull GraphicsContext graphics,
            LabSession.@NonNull Settings settings,
            @NonNull Path dataFolder) {
        this.graphics = graphics;
        this.dataFolder = dataFolder;
        foldersText =
                new IkString(
                        settings.dataFolders().stream()
                                .map(Path::toString)
                                .collect(Collectors.joining(";")),
                        1024);
        worldTypeText = new IkString(settings.worldType(), 256);
        seedText = new IkString(Long.toString(settings.seed()), 32);
        session = new LabSession(loaded -> post(() -> onLoaded(loaded)));
        renderer = new LabRenderer(graphics, drawn -> post(() -> onFrame(drawn)));
        nodeTree = new NodeTree(renderer, this::post, this::viewNode);
        preview = new LabPreview(graphics);
        bookmarks = new Bookmarks(dataFolder.resolve("lab").resolve("bookmarks.kvt"));
        loader = Executors.newSingleThreadExecutor(daemon("Worldgen Lab load"));
        inspector = Executors.newSingleThreadExecutor(daemon("Worldgen Lab inspect"));
        watcher = Executors.newSingleThreadScheduledExecutor(daemon("Worldgen Lab watch"));
        watcher.scheduleWithFixedDelay(
                this::checkForChanges, WATCH_MILLIS, WATCH_MILLIS, TimeUnit.MILLISECONDS);
        load(settings);
    }

    /**
     * A thread factory for one daemon thread.
     *
     * @param name The thread's name.
     * @return The factory.
     */
    private static ThreadFactory daemon(@NonNull String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Run something on the render thread, at the start of the next frame.
     *
     * @param change What to run.
     */
    private void post(@NonNull Runnable change) {
        if (!graphics.isClosed()) {
            // Dropped if the plugin unloads first; graphics releases what the context owns
            graphics.ui()
                    .post(
                            () -> {
                                if (!graphics.isClosed()) {
                                    change.run();
                                }
                            });
        }
    }

    /**
     * Load data in the background.
     *
     * @param settings What to load.
     */
    private void load(LabSession.@NonNull Settings settings) {
        loading.set(true);
        loader.execute(
                () -> {
                    try {
                        session.load(settings);
                    } catch (RuntimeException e) {
                        log.warn("The Worldgen Lab could not load {}", settings, e);
                    } finally {
                        loading.set(false);
                    }
                });
    }

    /** Reload if any data file changed. On the watcher thread. */
    private void checkForChanges() {
        if (session.getState() == null || !loading.compareAndSet(false, true)) {
            return;
        }
        loader.execute(
                () -> {
                    try {
                        session.reloadIfChanged();
                    } catch (RuntimeException e) {
                        log.warn("The Worldgen Lab could not reload", e);
                    } finally {
                        loading.set(false);
                    }
                });
    }

    /**
     * Take in new data. Render thread.
     *
     * @param loaded The load's result.
     */
    private void onLoaded(LabSession.@NonNull State loaded) {
        final boolean first = state == null || state.debug() == null;
        state = loaded;
        if (loaded.debug() != null && (first || targetText.get().isEmpty())) {
            targetText.set(loaded.debug().terrainTarget());
            treeRoot = targetText.get();
        }
        if (first) {
            view.setScale(1);
            updateCenterText();
        }
        // Everything is redrawn from the new data
        drawnKey = "";
        hoverKey = "";
        treeKey = "";
        repin();
        preview.onDataChanged(loaded.debug());
    }

    /**
     * Take in a drawn frame. Render thread.
     *
     * @param drawn The frame.
     */
    private void onFrame(LabRenderer.@NonNull Frame drawn) {
        if (frame != null) {
            renderer.release(frame.texture());
        }
        frame = drawn;
    }

    /**
     * A place to go back to.
     *
     * @param target The main view's target.
     * @param root The node tree's root.
     */
    private record Visit(@NonNull String target, @NonNull String root) {}

    /**
     * Show a node from the tree in the main view, keeping the tree as it is.
     *
     * @param target The node's target.
     */
    private void viewNode(@NonNull String target) {
        remember();
        targetText.set(target);
    }

    /**
     * Show a target and root the node tree at it.
     *
     * @param target The target.
     */
    private void chooseTarget(@NonNull String target) {
        remember();
        targetText.set(target);
        treeRoot = target;
    }

    /** Remember the current target and tree root, for Back. */
    private void remember() {
        final Visit now = new Visit(targetText.get().trim(), treeRoot);
        if (now.target().isEmpty() || now.equals(history.peekFirst())) {
            return;
        }
        history.addFirst(now);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    /** Go back to the last target and tree root. */
    private void back() {
        final Visit last = history.pollFirst();
        if (last != null) {
            targetText.set(last.target());
            treeRoot = last.root();
        }
    }

    /**
     * Build the Lab's surface.
     *
     * @return The surface, ready to show.
     */
    public Surface build() {
        Column content =
                new Column("content")
                        .padding(Insets.all(4))
                        .gap(4)
                        .align(Align.STRETCH)
                        .add(
                                new Immediate("controls", frame -> drawControls())
                                        .height(Sizing.fixed(CONTROLS_HEIGHT)),
                                new Row("body")
                                        .height(Sizing.grow())
                                        .gap(4)
                                        .align(Align.STRETCH)
                                        .add(
                                                new Immediate("view", frame -> drawView())
                                                        .border(true),
                                                new Immediate("side", frame -> drawSide())
                                                        .width(Sizing.fixed(SIDE_WIDTH))
                                                        .border(true)));
        surface =
                graphics.ui()
                        .surface(SURFACE_ID)
                        .anchors(Anchors.center())
                        .width(Sizing.percent(0.95f))
                        .height(Sizing.percent(0.92f))
                        .movable()
                        .content(content);
        return surface;
    }

    /**
     * Whether the Lab is showing.
     *
     * @return True if its surface is built and visible.
     */
    public boolean isVisible() {
        return surface != null && surface.isVisible();
    }

    /** Show the Lab. */
    public void open() {
        graphics.ui().show(build());
    }

    /** Draw the controls: data, seed, target, plane, colormap, slice. */
    private void drawControls() {
        label("Data");
        IkGui.setNextItemWidth(WIDE_FIELD);
        IkGui.inputText("##folders", foldersText);
        IkGui.setItemTooltip("Data folders in load order, separated by semicolons.");
        IkGui.sameLine();
        label("World");
        IkGui.setNextItemWidth(MEDIUM_FIELD);
        IkGui.inputText("##world", worldTypeText);
        IkGui.sameLine();
        label("Seed");
        IkGui.setNextItemWidth(NARROW_FIELD);
        IkGui.inputText("##seed", seedText);
        IkGui.sameLine();
        if (IkGui.button("Random")) {
            seedText.set(Long.toString(new SplittableRandom().nextLong()));
            reload();
        }
        IkGui.sameLine();
        if (IkGui.button("Load")) {
            reload();
        }
        IkGui.sameLine();
        if (IkGui.button("Close")) {
            graphics.ui().setVisible(SURFACE_ID, false);
        }
        IkGui.sameLine();
        IkGui.text(status());

        label("Target");
        IkGui.setNextItemWidth(WIDE_FIELD);
        if (IkGui.inputText("##target", targetText)) {
            // A typed target is a choice of its own, so the tree follows it
            treeRoot = targetText.get().trim();
        }
        IkGui.setItemTooltip(
                "A noise, density or parameter ID, id#path for a node in a file, biome or blocks.");
        IkGui.sameLine();
        IkGui.beginDisabled(history.isEmpty());
        if (IkGui.button("Back")) {
            back();
        }
        IkGui.setItemTooltip("Go back to the previous target and node tree.");
        IkGui.endDisabled();
        IkGui.sameLine();
        if (IkGui.button("Terrain") && state != null && state.debug() != null) {
            chooseTarget(state.debug().terrainTarget());
        }
        IkGui.sameLine();
        if (IkGui.button("Biome")) {
            chooseTarget(SliceImages.BIOME_TARGET);
        }
        IkGui.sameLine();
        if (IkGui.button("Blocks")) {
            chooseTarget(SliceImages.BLOCKS_TARGET);
        }
        IkGui.sameLine();
        for (WorldgenDebug.Plane plane : WorldgenDebug.Plane.values()) {
            if (IkGui.radioButton(plane.name(), view.getPlane() == plane)) {
                view.setPlane(plane);
            }
            IkGui.sameLine();
        }
        label("Colors");
        IkGui.setNextItemWidth(NARROW_FIELD);
        IkGui.combo("##colors", colormap, COLORMAPS);
        IkGui.sameLine();
        label("Range");
        IkGui.setNextItemWidth(NARROW_FIELD);
        IkGui.dragFloat("##range", range);
        IkGui.setItemTooltip("The value drawn fully saturated; 0 fits the view.");
        IkGui.sameLine();
        label("Stage");
        IkGui.setNextItemWidth(NARROW_FIELD);
        IkGui.combo(
                "##stage", stage, Arrays.stream(STAGES).map(Stage::name).toArray(String[]::new));
        IkGui.sameLine();
        label("Center");
        IkGui.setNextItemWidth(MEDIUM_FIELD);
        if (IkGui.inputText("##center", centerText)) {
            parseCenter();
        }
        IkGui.sameLine();
        if (IkGui.button("-" + SLICE_STEP)) {
            view.setSlice(view.getSlice() - SLICE_STEP);
            updateCenterText();
        }
        IkGui.sameLine();
        if (IkGui.button("+" + SLICE_STEP)) {
            view.setSlice(view.getSlice() + SLICE_STEP);
            updateCenterText();
        }
    }

    /**
     * Put a label before the next field, on the same line.
     *
     * @param text The label.
     */
    private static void label(@NonNull String text) {
        IkGui.alignTextToFramePadding();
        IkGui.text(text);
        IkGui.sameLine();
    }

    /**
     * A short summary of the loaded data.
     *
     * @return The summary.
     */
    private String status() {
        if (state == null) {
            return "Loading...";
        }
        final long errors =
                state.diagnostics().stream()
                        .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
                        .count();
        String text =
                String.format(
                        "%d errors, %d warnings, data %016x",
                        errors, state.diagnostics().size() - errors, state.dataHash());
        if (state.stale()) {
            text += "  STALE: showing the last good data";
        } else if (state.debug() == null) {
            text += "  Nothing to show";
        }
        if (frame != null) {
            text += String.format("  drawn in %d ms", frame.millis());
        }
        return text;
    }

    /** Load the settings typed in the controls. */
    private void reload() {
        try {
            final List<Path> folders =
                    Arrays.stream(foldersText.get().split(";"))
                            .map(String::trim)
                            .filter(text -> !text.isEmpty())
                            .map(Path::of)
                            .toList();
            load(
                    new LabSession.Settings(
                            folders,
                            worldTypeText.get().trim(),
                            Long.parseLong(seedText.get().trim())));
        } catch (NumberFormatException e) {
            exportMessage = "The seed must be a whole number";
        }
    }

    /** Read the center typed in the controls. */
    private void parseCenter() {
        final String[] parts = centerText.get().split(",");
        if (parts.length != AXES) {
            return;
        }
        try {
            view.setCenter(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            // Keep typing
        }
    }

    /** Show the view's center in the controls. */
    private void updateCenterText() {
        final double[] c = view.getCenter();
        centerText.set(String.format("%.0f,%.0f,%.0f", c[0], c[1], c[2]));
    }

    /**
     * The request the view currently needs.
     *
     * @return The request, or null if there is nothing to draw.
     */
    private SliceImages.Request currentRequest() {
        if (viewWidth <= 0 || viewHeight <= 0 || targetText.get().isBlank()) {
            return null;
        }
        return new SliceImages.Request(
                targetText.get().trim(),
                channelText.get().isBlank() ? null : channelText.get().trim(),
                view.getPlane(),
                view.origin(viewWidth, viewHeight),
                viewWidth,
                viewHeight,
                view.getScale(),
                STAGES[stage.get()]);
    }

    /** Draw the slice view, and handle panning, zooming, hovering and pinning. */
    private void drawView() {
        final Vector2f available = IkGui.getContentRegionAvailable();
        viewWidth = Math.max(1, (int) available.x);
        viewHeight = Math.max(1, (int) available.y);
        final Vector2f corner = IkGui.getCursorScreenPos();
        requestDrawIfChanged();

        final DrawList draw = IkGui.getWindowDrawList();
        if (frame != null) {
            drawFrame(draw, corner);
        }
        drawOverlays(draw, corner);

        IkGui.invisibleButton("lab-view", viewWidth, viewHeight);
        if (IkGui.isItemActive() && IkGui.isMouseDragging(MouseButton.LEFT)) {
            final Vector2f drag = IkGui.getMouseDragDelta(MouseButton.LEFT);
            view.pan(drag.x, drag.y);
            IkGui.resetMouseDragDelta(MouseButton.LEFT);
            updateCenterText();
        }
        if (IkGui.isItemHovered()) {
            final Vector2f mouse = IkGui.getMousePos();
            final float px = mouse.x - corner.x;
            final float py = mouse.y - corner.y;
            final float wheel = IkGui.getIO().mouseWheel;
            if (wheel != 0) {
                if (IkGui.getIO().keyShift) {
                    // Shift scrubs the slice through the third axis
                    view.setSlice(
                            view.getSlice() + Math.signum(wheel) * Math.max(1, view.getScale()));
                } else {
                    view.zoomAt(wheel, px, py, viewWidth, viewHeight);
                }
                updateCenterText();
            }
            final double[] world = view.worldAt(px, py, viewWidth, viewHeight);
            requestReadout(world);
            if (IkGui.isMouseClicked(MouseButton.RIGHT)) {
                pinned = world;
                repin();
            }
        }
    }

    /** Ask the renderer for the view, if anything about it changed. */
    private void requestDrawIfChanged() {
        final SliceImages.Request request = currentRequest();
        if (request == null || state == null || state.debug() == null) {
            return;
        }
        final String key =
                String.join(
                        "|",
                        request.target(),
                        String.valueOf(request.channel()),
                        request.plane().name(),
                        Arrays.toString(request.origin()),
                        request.width() + "x" + request.height(),
                        Double.toString(request.scale()),
                        request.stage().name(),
                        COLORMAPS[colormap.get()],
                        Float.toString(range[0]),
                        Long.toString(state.version()));
        if (key.equals(drawnKey)) {
            return;
        }
        drawnKey = key;
        try {
            renderer.draw(
                    state.debug(),
                    request,
                    SliceImages.Colormap.of(COLORMAPS[colormap.get()]),
                    range[0]);
        } catch (IllegalArgumentException e) {
            exportMessage = e.getMessage();
        }
        rebuildTreeIfChanged(request);
    }

    /**
     * Draw the current frame where its world rectangle falls in the view, so it stays put while a
     * newer frame is drawn.
     *
     * @param draw The view's draw list.
     * @param corner The view's top-left corner on screen.
     */
    private void drawFrame(@NonNull DrawList draw, @NonNull Vector2f corner) {
        final SliceImages.Request drawn = frame.request();
        if (drawn.plane() != view.getPlane()) {
            return;
        }
        final double[] origin = view.origin(viewWidth, viewHeight);
        final int across = view.acrossAxis();
        final int down = view.downAxis();
        final double sign = view.getPlane() == WorldgenDebug.Plane.XZ ? 1 : -1;
        final float minX = (float) ((drawn.origin()[across] - origin[across]) / view.getScale());
        final float minY = (float) (sign * (drawn.origin()[down] - origin[down]) / view.getScale());
        final float width = (float) (drawn.width() * drawn.scale() / view.getScale());
        final float height = (float) (drawn.height() * drawn.scale() / view.getScale());
        draw.addImage(
                frame.texture(),
                corner.x + minX,
                corner.y + minY,
                corner.x + minX + width,
                corner.y + minY + height);
    }

    /**
     * Draw the chunk grid, the biome cell grid, the world border and the pinned point.
     *
     * @param draw The view's draw list.
     * @param corner The view's top-left corner on screen.
     */
    private void drawOverlays(@NonNull DrawList draw, @NonNull Vector2f corner) {
        drawGrid(draw, corner, World.CHUNK_SIZE, GRID_COLOR);
        drawGrid(draw, corner, Chunk.BIOME_CELL_SIZE, CELL_COLOR);
        if (state != null && state.debug() != null) {
            final Box border = state.debug().getGenerator().getWorldType().border();
            final double[] low = {border.minX(), border.minY(), border.minZ()};
            final double[] high = {border.maxX() + 1, border.maxY() + 1, border.maxZ() + 1};
            final float[] a = toScreen(low);
            final float[] b = toScreen(high);
            draw.addRect(
                    corner.x + Math.min(a[0], b[0]),
                    corner.y + Math.min(a[1], b[1]),
                    corner.x + Math.max(a[0], b[0]),
                    corner.y + Math.max(a[1], b[1]),
                    BORDER_COLOR);
        }
        if (pinned != null) {
            final float[] p = toScreen(pinned);
            draw.addRectFilled(
                    corner.x + p[0] - PIN_SIZE / 2,
                    corner.y + p[1] - PIN_SIZE / 2,
                    corner.x + p[0] + PIN_SIZE / 2,
                    corner.y + p[1] + PIN_SIZE / 2,
                    PIN_COLOR);
        }
    }

    /**
     * Where a world position falls in the view, in pixels from its corner.
     *
     * @param world The position, as x, y, z.
     * @return The pixel, as x, y.
     */
    private float[] toScreen(double @NonNull [] world) {
        final double[] origin = view.origin(viewWidth, viewHeight);
        final double sign = view.getPlane() == WorldgenDebug.Plane.XZ ? 1 : -1;
        final int across = view.acrossAxis();
        final int down = view.downAxis();
        return new float[] {
            (float) ((world[across] - origin[across]) / view.getScale()),
            (float) (sign * (world[down] - origin[down]) / view.getScale())
        };
    }

    /**
     * Draw grid lines every so many blocks, if they would be far enough apart to see.
     *
     * @param draw The view's draw list.
     * @param corner The view's top-left corner on screen.
     * @param spacing The spacing, in blocks.
     * @param color The line color.
     */
    private void drawGrid(
            @NonNull DrawList draw, @NonNull Vector2f corner, int spacing, int color) {
        if (spacing / view.getScale() < GRID_MIN_PIXELS) {
            return;
        }
        final double[] origin = view.origin(viewWidth, viewHeight);
        final double[] end = view.worldAt(viewWidth, viewHeight, viewWidth, viewHeight);
        for (int axisIndex = 0; axisIndex < 2; ++axisIndex) {
            final int axis = axisIndex == 0 ? view.acrossAxis() : view.downAxis();
            final double low = Math.min(origin[axis], end[axis]);
            final double high = Math.max(origin[axis], end[axis]);
            for (double line = Math.ceil(low / spacing) * spacing; line <= high; line += spacing) {
                double[] at = origin.clone();
                at[axis] = line;
                final float[] p = toScreen(at);
                if (axisIndex == 0) {
                    draw.addLine(
                            corner.x + p[0],
                            corner.y,
                            corner.x + p[0],
                            corner.y + viewHeight,
                            color);
                } else {
                    draw.addLine(
                            corner.x,
                            corner.y + p[1],
                            corner.x + viewWidth,
                            corner.y + p[1],
                            color);
                }
            }
        }
    }

    /**
     * Work out the readout for the block under the cursor, if it moved to another block.
     *
     * @param world The cursor's world position.
     */
    private void requestReadout(double @NonNull [] world) {
        if (state == null || state.debug() == null) {
            return;
        }
        final long x = (long) StrictMath.floor(world[0]);
        final long y = (long) StrictMath.floor(world[1]);
        final long z = (long) StrictMath.floor(world[2]);
        final String key = x + "," + y + "," + z + "|" + targetText.get() + "|" + state.version();
        if (key.equals(hoverKey)) {
            return;
        }
        hoverKey = key;
        final WorldgenDebug debug = state.debug();
        final String target = targetText.get().trim();
        inspector.execute(
                () -> {
                    final List<String> lines = readout(debug, target, world, x, y, z);
                    post(
                            () -> {
                                if (hoverKey.equals(key)) {
                                    readout = lines;
                                }
                            });
                });
    }

    /**
     * Everything about one point, as lines of text. On the inspector thread.
     *
     * @param debug The world generation.
     * @param target The view's target.
     * @param world The exact point.
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The lines.
     */
    private static List<String> readout(
            @NonNull WorldgenDebug debug,
            @NonNull String target,
            double @NonNull [] world,
            long x,
            long y,
            long z) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("Block %d, %d, %d", x, y, z));
        try {
            if (!SliceImages.BIOME_TARGET.equals(target)
                    && !SliceImages.BLOCKS_TARGET.equals(target)) {
                final DensityNode node = debug.resolve(target, null);
                lines.add(
                        String.format(
                                "%s = %.5f",
                                target, node.value(world[0], world[1], world[2], null)));
            }
            final WorldgenDebug.BiomeReport biome = debug.biomeAt(world[0], world[1], world[2]);
            biome.parameters()
                    .forEach((id, value) -> lines.add(String.format("  %s = %.4f", id, value)));
            final List<BiomeSelector.Candidate> ranked = biome.ranked();
            lines.add("Biome: " + biome.chosen());
            if (ranked.size() > 1) {
                lines.add(
                        String.format(
                                "  runner-up %s (%s, distance %.4f)",
                                ranked.get(1).biome().id(),
                                ranked.get(1).contains() ? "fits" : "outside",
                                ranked.get(1).distance()));
            }
            final var block = SliceImages.blockAt(debug, world, Stage.BLOCK_RULES, new HashMap<>());
            lines.add("Block: " + block.getName());
            final BlockRules.Match match = debug.getGenerator().explain(x, y, z);
            lines.add(match == null ? "  (no block rule)" : "  " + match);
        } catch (RuntimeException e) {
            lines.add("Could not read: " + e.getMessage());
        }
        return lines;
    }

    /** Work out the trace at the pinned point again, after it or the data changed. */
    private void repin() {
        if (pinned == null || state == null || state.debug() == null) {
            trace = null;
            return;
        }
        final WorldgenDebug debug = state.debug();
        final String target = targetText.get().trim();
        final double[] point = pinned.clone();
        if (SliceImages.BIOME_TARGET.equals(target) || SliceImages.BLOCKS_TARGET.equals(target)) {
            trace = null;
            return;
        }
        inspector.execute(
                () -> {
                    try {
                        final WorldgenDebug.Trace traced =
                                debug.trace(target, point[0], point[1], point[2]);
                        post(() -> trace = traced);
                    } catch (RuntimeException e) {
                        post(() -> trace = null);
                    }
                });
    }

    /**
     * Rebuild the node tree when the target or view changes.
     *
     * @param request The view being drawn.
     */
    private void rebuildTreeIfChanged(SliceImages.@NonNull Request request) {
        final String root = treeRoot.isBlank() ? request.target() : treeRoot.trim();
        // The root and the view, not the viewed target, so viewing a node keeps the tree
        final String key =
                String.join(
                        "|",
                        root,
                        request.plane().name(),
                        Arrays.toString(request.origin()),
                        request.width() + "x" + request.height(),
                        Double.toString(request.scale()),
                        Long.toString(state.version()));
        if (key.equals(treeKey)) {
            return;
        }
        treeKey = key;
        if (SliceImages.BIOME_TARGET.equals(root) || SliceImages.BLOCKS_TARGET.equals(root)) {
            nodeTree.show(null, null, request);
            return;
        }
        final WorldgenDebug debug = state.debug();
        final double[] a = request.origin();
        final double[] b = view.worldAt(viewWidth, viewHeight, viewWidth, viewHeight);
        final Box box =
                new Box(
                        Math.min(a[0], b[0]),
                        Math.min(a[1], b[1]),
                        Math.min(a[2], b[2]),
                        Math.max(a[0], b[0]),
                        Math.max(a[1], b[1]),
                        Math.max(a[2], b[2]));
        final int thumbHeight =
                Math.max(1, NodeTree.THUMBNAIL_SIZE * request.height() / request.width());
        final SliceImages.Request thumbnails =
                new SliceImages.Request(
                        request.target(),
                        request.channel(),
                        request.plane(),
                        request.origin(),
                        NodeTree.THUMBNAIL_SIZE,
                        thumbHeight,
                        request.scale() * request.width() / NodeTree.THUMBNAIL_SIZE,
                        request.stage());
        inspector.execute(
                () -> {
                    try {
                        final WorldgenDebug.Bounds tree = debug.bounds(root, box);
                        post(
                                () -> {
                                    if (treeKey.equals(key)) {
                                        nodeTree.show(debug, tree, thumbnails);
                                    }
                                });
                    } catch (RuntimeException e) {
                        post(() -> nodeTree.show(null, null, thumbnails));
                    }
                });
    }

    /** Draw the side panel. */
    private void drawSide() {
        if (IkGui.collapsingHeader("Legend", TreeNodeFlags.DEFAULT_OPEN)) {
            drawLegend();
        }
        if (IkGui.collapsingHeader("Readout", TreeNodeFlags.DEFAULT_OPEN)) {
            readout.forEach(IkGui::textWrapped);
        }
        if (IkGui.collapsingHeader("Trace (right click to pin)")) {
            if (trace != null) {
                drawTrace(trace, 0);
            } else {
                IkGui.textWrapped(
                        "Right click the view to pin a point and trace every node there.");
            }
        }
        if (IkGui.collapsingHeader("Node tree", TreeNodeFlags.DEFAULT_OPEN)) {
            nodeTree.draw(targetText.get().trim());
        }
        if (IkGui.collapsingHeader("Problems")) {
            drawProblems();
        }
        if (IkGui.collapsingHeader("Bookmarks")) {
            drawBookmarks();
        }
        if (IkGui.collapsingHeader("Export")) {
            drawExport();
        }
        if (IkGui.collapsingHeader("3D preview")) {
            preview.draw(state == null ? null : state.debug(), view, STAGES[stage.get()]);
        }
    }

    /** Draw the colors in the view: each ID's swatch, or the value range. */
    private void drawLegend() {
        if (frame == null) {
            return;
        }
        if (frame.image().range() != null) {
            IkGui.text(
                    String.format(
                            "Values from %.4f to %.4f",
                            frame.image().range().min(), frame.image().range().max()));
            return;
        }
        final DrawList draw = IkGui.getWindowDrawList();
        for (Map.Entry<String, Integer> entry : frame.image().legend().entrySet()) {
            final Vector2f at = IkGui.getCursorScreenPos();
            final int rgb = entry.getValue();
            draw.addRectFilled(at.x, at.y, at.x + SWATCH_SIZE, at.y + SWATCH_SIZE, opaque(rgb));
            IkGui.dummy(SWATCH_SIZE + SWATCH_GAP, SWATCH_SIZE);
            IkGui.sameLine();
            IkGui.text(entry.getKey());
        }
    }

    /**
     * An opaque IkGui color from a packed 0xRRGGBB color.
     *
     * @param rgb The packed color.
     * @return The IkGui color.
     */
    private static int opaque(int rgb) {
        final int red = (rgb >> RED_SHIFT) & CHANNEL_MASK;
        final int green = (rgb >> GREEN_SHIFT) & CHANNEL_MASK;
        final int blue = rgb & CHANNEL_MASK;
        return Color.rgba(red, green, blue, CHANNEL_MASK);
    }

    /**
     * Draw a trace tree.
     *
     * @param node The node's trace.
     * @param index Its index among its siblings.
     */
    private void drawTrace(WorldgenDebug.@NonNull Trace node, int index) {
        final String value =
                Double.isNaN(node.value()) ? "(lattice)" : String.format("%.5f", node.value());
        final String label =
                String.format(
                        "%s = %s###trace%d:%s",
                        node.type(), value, index, String.valueOf(node.origin()));
        if (IkGui.treeNode(label)) {
            if (node.origin() != null) {
                IkGui.textWrapped(node.origin());
            }
            for (int i = 0; i < node.children().size(); ++i) {
                drawTrace(node.children().get(i), i);
            }
            IkGui.treePop();
        }
    }

    /** Draw the data's problems, worst first. */
    private void drawProblems() {
        if (state == null || state.diagnostics().isEmpty()) {
            IkGui.text("No problems.");
            return;
        }
        for (Diagnostic diagnostic : state.diagnostics()) {
            if (diagnostic.severity() == Diagnostic.Severity.ERROR) {
                IkGui.textColored(1, 0.4f, 0.4f, 1, diagnostic.toString());
            } else {
                IkGui.textColored(1, 0.85f, 0.3f, 1, diagnostic.toString());
            }
        }
    }

    /** Draw the bookmarks: add the current view, go to one, or delete one. */
    private void drawBookmarks() {
        IkGui.setNextItemWidth(MEDIUM_FIELD);
        IkGui.inputText("##bookmark-name", bookmarkName);
        IkGui.sameLine();
        if (IkGui.button("Add") && !bookmarkName.get().isBlank()) {
            bookmarks.add(
                    new Bookmarks.Bookmark(
                            bookmarkName.get().trim(),
                            targetText.get().trim(),
                            view.getPlane(),
                            view.getCenter(),
                            view.getScale()));
            bookmarkName.set("");
        }
        final List<Bookmarks.Bookmark> list = bookmarks.list();
        for (int i = 0; i < list.size(); ++i) {
            final Bookmarks.Bookmark bookmark = list.get(i);
            IkGui.pushID(i);
            if (IkGui.button("Go")) {
                chooseTarget(bookmark.target());
                view.setPlane(bookmark.plane());
                view.setCenter(bookmark.center()[0], bookmark.center()[1], bookmark.center()[2]);
                view.setScale(bookmark.scale());
                updateCenterText();
            }
            IkGui.sameLine();
            if (IkGui.button("X")) {
                bookmarks.remove(i);
                IkGui.popID();
                return;
            }
            IkGui.sameLine();
            IkGui.text(bookmark.name() + "  " + bookmark.target());
            IkGui.popID();
        }
    }

    /** Draw the exports: a PNG of the view, the visible chunks as text, the section corpus. */
    private void drawExport() {
        if (IkGui.button("Save PNG") && frame != null) {
            exportMessage = savePng();
        }
        IkGui.sameLine();
        if (IkGui.button("Copy chunk list")) {
            final String list =
                    view.visibleChunks(viewWidth, viewHeight).stream()
                            .map(pos -> pos.x() + "," + pos.y() + "," + pos.z())
                            .collect(Collectors.joining(";"));
            IkGui.setClipboardText(list);
            exportMessage = "Copied " + list.split(";").length + " chunk positions";
        }
        IkGui.sameLine();
        if (IkGui.button("Export corpus")) {
            exportMessage = preview.exportCorpus(dataFolder);
        }
        IkGui.setItemTooltip("Write the 3D preview's chunks to a section corpus file.");
        if (!exportMessage.isEmpty()) {
            IkGui.textWrapped(exportMessage);
        }
    }

    /**
     * Save the current frame as a PNG.
     *
     * @return What happened.
     */
    private String savePng() {
        final SliceImages.Image image = frame.image();
        BufferedImage png =
                new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_RGB);
        png.setRGB(0, 0, image.width(), image.height(), image.rgb(), 0, image.width());
        final String name =
                frame.request().target().replaceAll("[^a-zA-Z0-9_.-]", "_")
                        + "-"
                        + System.currentTimeMillis()
                        + ".png";
        final Path file = dataFolder.resolve("lab").resolve("exports").resolve(name);
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(png, "png", file.toFile());
            return "Saved " + file.toAbsolutePath();
        } catch (IOException e) {
            return "Could not save: " + e.getMessage();
        }
    }

    /** The chunk positions of the view, for tests. */
    List<ChunkPos> visibleChunks() {
        return view.visibleChunks(viewWidth, viewHeight);
    }

    /**
     * Stop every thread now, then remove the surface and release textures on the render thread. If
     * the plugin's graphics context closes first, it releases them itself.
     */
    @Override
    public void close() {
        watcher.shutdownNow();
        loader.shutdownNow();
        inspector.shutdownNow();
        renderer.close();
        preview.stop();
        post(
                () -> {
                    graphics.ui().remove(SURFACE_ID);
                    if (frame != null) {
                        renderer.release(frame.texture());
                        frame = null;
                    }
                    nodeTree.clear();
                    preview.close();
                });
    }
}
