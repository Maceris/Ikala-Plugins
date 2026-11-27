package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.texgraph.Baker;
import com.ikalagaming.converter.texgraph.Evaluator;
import com.ikalagaming.converter.texgraph.OutputNodes;
import com.ikalagaming.converter.texgraph.Plane;
import com.ikalagaming.converter.texgraph.PlaneViews;
import com.ikalagaming.converter.texgraph.Srgb;
import com.ikalagaming.converter.texgraph.SubgraphNodes;
import com.ikalagaming.converter.texgraph.TextureGraph;
import com.ikalagaming.converter.texgraph.TextureLibrary;
import com.ikalagaming.converter.texgraph.TexturePins;
import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.TextureFilter;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.TextureWrap;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.ui.UiFrame;
import com.ikalagaming.graphics.ui.graph.GraphEditor;
import com.ikalagaming.graphics.ui.graph.model.Graph;
import com.ikalagaming.graphics.ui.graph.model.NodeTypes;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * What the graph panel shows about a texture graph's nodes beyond the graph itself: a swatch on
 * every node, from one atlas; the node shown in the Viewer, live, and an Output's maps on the 3D
 * view's shape; a grid of the shown node under nine seeds; Measure; each pin's range on hover; and
 * what each node cost. Reads what validation already evaluated, so a swatch costs no extra
 * evaluation. Render thread only.
 */
public class GraphPreviews {
    /** The seed grid's cells across and down. */
    private static final int SEED_GRID = 3;

    /** A seed grid cell's size on screen, in pixels. */
    private static final float SEED_CELL = 72;

    /** The outline of the seed in use. */
    private static final int CHOSEN = Color.rgba(1.0f, 0.72f, 0.2f, 1);

    /** The maps an Output can show in the Viewer. */
    enum ShownMap {
        /** Base color. */
        COLOR("Base color"),
        /** Height. */
        HEIGHT("Height"),
        /** The normal map. */
        NORMAL("Normal"),
        /** Occlusion, roughness and metallic. */
        ORM("Occlusion, roughness, metallic"),
        /** Emitted light. */
        EMISSIVE("Emissive"),
        /** The material mask. */
        MASK("Mask");

        /** The name in the menu. */
        final String label;

        ShownMap(String label) {
            this.label = label;
        }
    }

    /** Where the graph panel shows things. */
    public interface Host {
        /**
         * Show a viewer in the Viewer panel, in front.
         *
         * @param viewer The viewer.
         */
        void showViewer(@NonNull Viewer viewer);

        /**
         * Whether a viewer is the one the Viewer panel shows.
         *
         * @param viewer The viewer.
         * @return True if shown.
         */
        boolean isShown(@NonNull Viewer viewer);

        /**
         * Put maps on the 3D view's preview shape, showing a shape first if no scene is shown.
         *
         * @param color Base color, sRGB, or null.
         * @param normal The normal map, or null.
         * @param orm Occlusion, roughness and metallic, or null.
         */
        void wearMaps(
                @Nullable TextureHandle color,
                @Nullable TextureHandle normal,
                @Nullable TextureHandle orm);

        /** Bring the graph panel to the front, as when the graph panel opens another file. */
        default void showGraph() {}
    }

    /** Uploads swatches and maps. */
    private final Textures textures;

    /** Where captures of the shown node are written. */
    private final Executor background;

    /** Shows viewers and puts maps on the shape. */
    private final Host host;

    /** The node types. */
    private final TextureLibrary library;

    /** The texture graph. */
    private final TextureGraph texture;

    /** Evaluates the graph; what it cached is what swatches show. */
    private final Evaluator evaluator;

    /** Where the graph's relative file names start. */
    private final Supplier<Path> folder;

    /** Every node's swatch. */
    private final SwatchAtlas atlas;

    /** The plane each node's swatch was made from, to know when to make it again. */
    private final Map<String, Plane> swatched = new HashMap<>();

    /** Each plane's range text, made once. */
    private final Map<Plane, String> ranges = new IdentityHashMap<>();

    /**
     * The node shown in the Viewer, or null. -- GETTER -- The shown node.
     *
     * @return Its ID, or null.
     */
    @Getter private @Nullable String shown;

    /** Which map an Output shows. */
    private ShownMap shownMap = ShownMap.COLOR;

    /** The viewer showing the shown node, or null. */
    private @Nullable Swatch viewer;

    /** What the viewer was last given, to know when to give it new pixels. */
    private @Nullable Object viewerSource;

    /** Whether the Output's maps are kept on the 3D view's shape. */
    private boolean onShape;

    /** Whether the shape wears the maps' textures now. */
    private boolean worn;

    /** The maps on the shape: base color, normal, occlusion-roughness-metallic. */
    private final TextureHandle[] shapeMaps = new TextureHandle[3];

    /** The maps last made for the shown Output, or null. */
    private @Nullable Baker.Maps maps;

    /** What the maps were made from: how many nodes had run, and the settings. */
    private long mapsStamp = Long.MIN_VALUE;

    /** Whether the seed grid is shown. */
    private boolean seedGrid;

    /** The seed the grid starts from. */
    private long seedBase;

    /** An evaluator for each seed grid cell, keeping its own cache. */
    private final Evaluator[] seedEvaluators = new Evaluator[SEED_GRID * SEED_GRID];

    /** What the seed grid was made from. */
    private long seedStamp = Long.MIN_VALUE;

    /** The last Measure's result, or empty. */
    private String measured = "";

    /** Picks new seeds for the grid. */
    private final Random random = new Random();

    /**
     * Set up the previews of a graph.
     *
     * @param textures Uploads swatches and maps.
     * @param background Where captures are written.
     * @param host Shows viewers and puts maps on the shape.
     * @param library The node types.
     * @param texture The texture graph.
     * @param evaluator Its evaluator.
     * @param folder Where its relative file names start, or null while it isn't saved.
     */
    public GraphPreviews(
            @NonNull Textures textures,
            @NonNull Executor background,
            @NonNull Host host,
            @NonNull TextureLibrary library,
            @NonNull TextureGraph texture,
            @NonNull Evaluator evaluator,
            @NonNull Supplier<Path> folder) {
        this.textures = textures;
        this.background = background;
        this.host = host;
        this.library = library;
        this.texture = texture;
        this.evaluator = evaluator;
        this.folder = folder;
        atlas = new SwatchAtlas(textures);
        seedBase = texture.getSeed();
    }

    /**
     * A node's preview for the editor: its swatch, and what it cost under it.
     *
     * @param id The node.
     * @return The preview, or null for nodes without one.
     */
    public @Nullable GraphEditor.NodePreview preview(@NonNull String id) {
        final SwatchAtlas.Cell cell = atlas.cell(id);
        final TextureHandle handle = atlas.getTexture();
        if (cell == null || handle == null) {
            return null;
        }
        final Evaluator.Cost cost = evaluator.getCosts().get(id);
        return new GraphEditor.NodePreview(
                handle,
                SwatchAtlas.VIEW,
                cell.minU(),
                cell.minV(),
                cell.maxU(),
                cell.maxV(),
                cell.aspect(),
                cost == null ? null : costText(cost.nanos(), cost.bytes()),
                id.equals(shown));
    }

    /**
     * A cost as text.
     *
     * @param nanos The time, in nanoseconds.
     * @param bytes The memory, in bytes.
     * @return Like "0.12 ms, 4 KiB".
     */
    static String costText(long nanos, long bytes) {
        return String.format(Locale.ROOT, "%.2f ms, %s", nanos / 1e6, size(bytes));
    }

    /**
     * Bytes as text.
     *
     * @param bytes The bytes.
     * @return In KiB or MiB.
     */
    private static String size(long bytes) {
        if (bytes >= 1L << 20) {
            return String.format(Locale.ROOT, "%.1f MiB", bytes / (double) (1L << 20));
        }
        return Math.max(1, Math.round(bytes / 1024.0)) + " KiB";
    }

    /**
     * What a node shows: its first output, or for an Output what flows into its first linked color,
     * else its first linked input.
     *
     * @param graph The graph.
     * @param id The node.
     * @return The plane and its pin type, or null if there is nothing to show.
     */
    private @Nullable Shown look(Graph graph, String id) {
        final NodeTypes.NodeType type = graph.typeOf(id);
        if (type == null || NodeTypes.isEditorType(type.id())) {
            return null;
        }
        if (type.id().startsWith(SubgraphNodes.OUTPUT_PREFIX)) {
            final Plane plane = evaluator.input(graph, settings(), id, SubgraphNodes.OUTPUT_IN);
            final String pinType = type.id().substring(SubgraphNodes.OUTPUT_PREFIX.length());
            return plane == null ? null : new Shown(plane, pinType);
        }
        if (OutputNodes.isOutput(type.id())) {
            NodeTypes.Pin chosen = null;
            for (NodeTypes.Pin pin : type.inputs()) {
                if (Evaluator.source(graph, id, pin.name()) == null) {
                    continue;
                }
                if (chosen == null
                        || TexturePins.COLOR.equals(pin.type())
                                && !TexturePins.COLOR.equals(chosen.type())) {
                    chosen = pin;
                }
            }
            if (chosen == null) {
                return null;
            }
            final Plane plane = evaluator.input(graph, settings(), id, chosen.name());
            return plane == null ? null : new Shown(plane, chosen.type());
        }
        if (type.outputs().isEmpty()) {
            return null;
        }
        final Map<String, Plane> outputs = evaluator.cached(id);
        final NodeTypes.Pin pin = type.outputs().getFirst();
        final Plane plane = outputs == null ? null : outputs.get(pin.name());
        return plane == null ? null : new Shown(plane, pin.type());
    }

    /**
     * A plane to show and its pin type.
     *
     * @param plane The plane.
     * @param type The pin type.
     */
    private record Shown(Plane plane, String type) {}

    /**
     * The evaluator settings now.
     *
     * @return The settings.
     */
    private Evaluator.Settings settings() {
        return texture.settings(folder.get());
    }

    /**
     * Bring the swatches, the shown node and the seed grid up to date with what was last evaluated.
     * Call each frame, after validation.
     */
    public void update() {
        final Graph graph = texture.getGraph();
        for (String key : atlas.keys()) {
            if (!key.startsWith(SEED_KEY)
                    && !key.startsWith(OTHER_KEY)
                    && graph.node(key) == null) {
                atlas.remove(key);
                swatched.remove(key);
            }
        }
        for (Graph.Node node : graph.nodes()) {
            final Shown look = look(graph, node.getId());
            if (look == null) {
                if (swatched.remove(node.getId()) != null) {
                    atlas.remove(node.getId());
                }
                continue;
            }
            if (swatched.get(node.getId()) != look.plane()) {
                atlas.put(
                        node.getId(),
                        PlaneViews.rgba(look.plane(), look.type(), true),
                        look.plane().getWidth(),
                        look.plane().getHeight());
                swatched.put(node.getId(), look.plane());
            }
        }
        if (ranges.size() > 4 * Math.max(16, swatched.size())) {
            ranges.clear();
        }
        if (shown != null && graph.node(shown) == null) {
            shown = null;
        }
        updateShown(graph);
        updateSeedGrid(graph);
    }

    /**
     * Show a node in the Viewer, or stop showing it if it is the one shown.
     *
     * @param id The node.
     */
    public void toggleShown(@NonNull String id) {
        if (id.equals(shown) && viewer != null && host.isShown(viewer)) {
            shown = null;
            return;
        }
        show(id);
    }

    /**
     * Show a node in the Viewer, updating as the graph changes.
     *
     * @param id The node, or null to show none.
     */
    public void show(@Nullable String id) {
        shown = id;
        viewerSource = null;
        mapsStamp = Long.MIN_VALUE;
        seedStamp = Long.MIN_VALUE;
        if (id != null) {
            updateShown(texture.getGraph());
            if (viewer != null) {
                host.showViewer(viewer);
            }
        }
    }

    /**
     * Give the shown node's pixels to the viewer, and the Output's maps to the shape, when they
     * changed.
     *
     * @param graph The graph.
     */
    private void updateShown(Graph graph) {
        if (shown == null) {
            return;
        }
        final boolean output = OutputNodes.isOutput(graph.node(shown).getType());
        if (output) {
            final long stamp = stamp();
            if (stamp != mapsStamp) {
                final List<String> problems = new ArrayList<>();
                maps = Baker.maps(texture, evaluator, folder.get(), dataFolder(), problems);
                mapsStamp = stamp;
                viewerSource = null;
                if (onShape) {
                    putOnShape();
                }
            }
            final BufferedImage image = maps == null ? null : image(maps, shownMap);
            if (image != null && viewerSource != image) {
                give(
                        shown + " " + shownMap.label.toLowerCase(Locale.ROOT),
                        pixels(image, shownMap),
                        image.getWidth(),
                        image.getHeight());
                viewerSource = image;
            }
            return;
        }
        final Shown look = look(graph, shown);
        if (look != null && viewerSource != look.plane()) {
            give(
                    shown,
                    PlaneViews.rgba(look.plane(), look.type(), false),
                    look.plane().getWidth(),
                    look.plane().getHeight());
            viewerSource = look.plane();
            final Plane indices = look.plane();
            if (viewer != null) {
                viewer.getViewer()
                        .setPixelNote(
                                TexturePins.PALETTE.equals(look.type())
                                                && indices.getPalette() != null
                                        ? (x, y) ->
                                                x < indices.getWidth() && y < indices.getHeight()
                                                        ? "Palette index "
                                                                + Math.round(indices.get(x, y, 0))
                                                                + " of "
                                                                + indices.paletteSize()
                                                        : null
                                        : null);
            }
        }
    }

    /**
     * Show pixels in the viewer, making it if it was closed.
     *
     * @param name What they are.
     * @param rgba The pixels.
     * @param width Pixels across.
     * @param height Pixels down.
     */
    private void give(String name, float[] rgba, int width, int height) {
        if (viewer != null && host.isShown(viewer)) {
            viewer.update(name, rgba, width, height);
            return;
        }
        viewer = new Swatch(textures, background, name, rgba, width, height);
        host.showViewer(viewer);
    }

    /**
     * A number that changes when what the graph evaluates to may have: how many nodes have run, and
     * the size and seed.
     *
     * @return The stamp.
     */
    private long stamp() {
        return evaluator.getRuns() * 31
                + ((long) texture.getWidth() << 40)
                + ((long) texture.getHeight() << 20)
                + texture.getSeed() * 17;
    }

    /**
     * The content folder the graph's model is under.
     *
     * @return The folder, or null when unknown.
     */
    private @Nullable Path dataFolder() {
        final Path base = folder.get();
        return base == null || texture.getData() == null
                ? null
                : base.resolve(texture.getData()).normalize();
    }

    /**
     * One of the maps.
     *
     * @param maps The maps.
     * @param which Which.
     * @return The image, or null if not made.
     */
    private static @Nullable BufferedImage image(Baker.Maps maps, ShownMap which) {
        return switch (which) {
            case COLOR -> maps.color();
            case HEIGHT -> maps.height();
            case NORMAL -> maps.normal();
            case ORM -> maps.orm();
            case EMISSIVE -> maps.emissive();
            case MASK -> maps.mask();
        };
    }

    /**
     * A map's pixels as linear RGBA, decoding the sRGB ones.
     *
     * @param image The map.
     * @param which Which map it is.
     * @return The pixels.
     */
    private static float[] pixels(BufferedImage image, ShownMap which) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final float[] out = new float[width * height * 4];
        final boolean srgb =
                which == ShownMap.COLOR || which == ShownMap.EMISSIVE || which == ShownMap.MASK;
        final boolean gray16 = image.getType() == BufferedImage.TYPE_USHORT_GRAY;
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final int at = (y * width + x) * 4;
                if (gray16) {
                    final float value = image.getRaster().getSample(x, y, 0) / 65_535f;
                    out[at] = value;
                    out[at + 1] = value;
                    out[at + 2] = value;
                    out[at + 3] = 1;
                    continue;
                }
                final int argb = image.getRGB(x, y);
                for (int c = 0; c < 3; ++c) {
                    final int value = argb >> (16 - 8 * c) & 0xFF;
                    out[at + c] = srgb ? Srgb.decode(value) : value / 255f;
                }
                out[at + 3] = (argb >>> 24) / 255f;
            }
        }
        return out;
    }

    /** Put the shown Output's first face's maps on the 3D view's shape. */
    private void putOnShape() {
        final Baker.Maps made = maps;
        if (made == null) {
            return;
        }
        final int left = made.tiles().getFirst() * made.tileWidth();
        final TextureHandle[] before = shapeMaps.clone();
        shapeMaps[0] = upload(shapeMaps[0], made.color(), left, made.tileWidth(), true);
        shapeMaps[1] = upload(shapeMaps[1], made.normal(), left, made.tileWidth(), false);
        shapeMaps[2] = upload(shapeMaps[2], made.orm(), left, made.tileWidth(), false);
        if (!java.util.Arrays.equals(before, shapeMaps) || !worn) {
            // New pixels in the same textures show by themselves
            host.wearMaps(shapeMaps[0], shapeMaps[1], shapeMaps[2]);
            worn = true;
        }
    }

    /**
     * Upload one tile of a map, into the texture it had if the size still fits.
     *
     * @param old The texture it had, or null.
     * @param image The map, or null for none.
     * @param left The tile's left edge.
     * @param width The tile's width.
     * @param srgb Whether it holds sRGB colors.
     * @return The texture, or null if there is no map.
     */
    private @Nullable TextureHandle upload(
            @Nullable TextureHandle old,
            @Nullable BufferedImage image,
            int left,
            int width,
            boolean srgb) {
        if (image == null) {
            textures.release(old);
            return null;
        }
        final int height = image.getHeight();
        final ByteBuffer pixels =
                ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final int argb = image.getRGB(left + x, y);
                pixels.put((byte) (argb >> 16))
                        .put((byte) (argb >> 8))
                        .put((byte) argb)
                        .put((byte) (argb >>> 24));
            }
        }
        pixels.flip();
        final Format format = srgb ? Format.R8G8B8A8_SRGB : Format.R8G8B8A8_UNORM;
        if (old != null
                && textures.isValid(old)
                && old.width() == width
                && old.height() == height
                && old.format() == format) {
            textures.update(old, pixels, 0, 0, width, height);
            return old;
        }
        textures.release(old);
        return textures.load(
                pixels, format, width, height, TextureWrap.REPEAT, TextureFilter.PIXELS);
    }

    /** What the seed grid's atlas keys start with. */
    private static final String SEED_KEY = "seed:";

    /** What the atlas keys of swatches the caller keeps start with, like instances'. */
    private static final String OTHER_KEY = "other:";

    /**
     * Keep a swatch of the caller's own in the atlas, like an instance's, until removed.
     *
     * @param key Its name.
     * @param plane What it shows.
     * @param type The plane's pin type.
     */
    public void putSwatch(@NonNull String key, @NonNull Plane plane, @NonNull String type) {
        atlas.put(
                OTHER_KEY + key,
                PlaneViews.rgba(plane, type, false),
                plane.getWidth(),
                plane.getHeight());
    }

    /**
     * Take out a swatch of the caller's own.
     *
     * @param key Its name.
     */
    public void removeSwatch(@NonNull String key) {
        atlas.remove(OTHER_KEY + key);
    }

    /**
     * Draw a swatch of the caller's own as a button, empty until it is made.
     *
     * @param key Its name.
     * @param size Its side, in pixels.
     * @return True if it was clicked.
     */
    public boolean drawSwatch(@NonNull String key, float size) {
        final Vector2f at = IkGui.getCursorScreenPos();
        final boolean clicked = IkGui.invisibleButton("swatch " + key, size, size);
        final TextureHandle handle = atlas.getTexture();
        final SwatchAtlas.Cell placed = atlas.cell(OTHER_KEY + key);
        final DrawList drawList = IkGui.getWindowDrawList();
        if (handle != null && placed != null) {
            drawList.addImageView(
                    handle,
                    at.x,
                    at.y,
                    at.x + size,
                    at.y + size,
                    placed.minU(),
                    placed.minV(),
                    placed.maxU(),
                    placed.maxV(),
                    SwatchAtlas.VIEW);
        }
        drawList.addRect(at.x, at.y, at.x + size, at.y + size, CHOSEN, 0, 0, 1);
        return clicked;
    }

    /**
     * Make the seed grid's swatches again when the graph changed.
     *
     * @param graph The graph.
     */
    private void updateSeedGrid(Graph graph) {
        if (!seedGrid || shown == null) {
            return;
        }
        final long stamp = stamp() * 7 + seedBase;
        if (stamp == seedStamp) {
            return;
        }
        seedStamp = stamp;
        final NodeTypes.NodeType type = graph.typeOf(shown);
        if (type == null) {
            return;
        }
        for (int i = 0; i < seedEvaluators.length; ++i) {
            if (seedEvaluators[i] == null) {
                seedEvaluators[i] = new Evaluator(library);
            }
            final Evaluator.Settings settings = settings().withSeed(seedOf(i));
            Plane plane = null;
            String pinType = TexturePins.COLOR;
            if (OutputNodes.isOutput(type.id())) {
                for (NodeTypes.Pin pin : type.inputs()) {
                    if (Evaluator.source(graph, shown, pin.name()) != null) {
                        plane = seedEvaluators[i].input(graph, settings, shown, pin.name());
                        pinType = pin.type();
                        if (TexturePins.COLOR.equals(pin.type())) {
                            break;
                        }
                    }
                }
            } else if (!type.outputs().isEmpty()) {
                plane =
                        seedEvaluators[i].output(
                                graph, settings, shown, type.outputs().getFirst().name());
                pinType = type.outputs().getFirst().type();
            }
            if (plane == null) {
                atlas.remove(SEED_KEY + i);
            } else {
                atlas.put(
                        SEED_KEY + i,
                        PlaneViews.rgba(plane, pinType, false),
                        plane.getWidth(),
                        plane.getHeight());
            }
        }
    }

    /**
     * A seed grid cell's seed.
     *
     * @param cell The cell, 0 to 8.
     * @return The seed.
     */
    public long seedOf(int cell) {
        return seedBase + cell;
    }

    /**
     * Show or hide the seed grid.
     *
     * @param on Whether to show it.
     */
    public void setSeedGrid(boolean on) {
        seedGrid = on;
        seedStamp = Long.MIN_VALUE;
        if (!on) {
            for (int i = 0; i < seedEvaluators.length; ++i) {
                atlas.remove(SEED_KEY + i);
                seedEvaluators[i] = null;
            }
        }
    }

    /** Offer nine other seeds in the grid. */
    public void otherSeeds() {
        seedBase = random.nextLong(1, Long.MAX_VALUE / 2);
        seedStamp = Long.MIN_VALUE;
    }

    /**
     * Choose which map a shown Output shows.
     *
     * @param name The map's name, like "normal", in any case.
     * @return False if there is no such map.
     */
    public boolean showMap(@NonNull String name) {
        for (ShownMap map : ShownMap.values()) {
            if (map.name().equalsIgnoreCase(name)) {
                shownMap = map;
                viewerSource = null;
                return true;
            }
        }
        return false;
    }

    /**
     * Keep the shown Output's maps on the 3D view's shape, or stop.
     *
     * @param on Whether to.
     */
    public void setOnShape(boolean on) {
        onShape = on;
        worn = false;
        if (on) {
            putOnShape();
        }
    }

    /**
     * What the previews show, for checks: how many swatches, the shown node and what the Viewer has
     * of it, and each node's cost.
     *
     * @return The description.
     */
    public String describe() {
        final StringBuilder out = new StringBuilder();
        out.append(swatched.size()).append(" swatches, shown ").append(shown);
        if (viewer != null && host.isShown(viewer)) {
            out.append(" in the Viewer");
        }
        if (seedGrid) {
            out.append(", seed grid ").append(seedBase).append(" up");
        }
        out.append(", costs ");
        out.append(evaluator.getCosts().keySet().stream().sorted().toList());
        return out.toString();
    }

    /**
     * Measure a node: its average, as linear albedo, per channel and overall.
     *
     * @param id The node.
     * @return The result, or null if the node shows nothing.
     */
    public @Nullable String measure(@NonNull String id) {
        final Shown look = look(texture.getGraph(), id);
        if (look == null) {
            measured = id + " has nothing to measure yet";
            return null;
        }
        final float[] average = PlaneViews.average(look.plane());
        measured =
                String.format(
                        Locale.ROOT,
                        "%s: linear r %.3f, g %.3f, b %.3f; albedo %.3f",
                        id,
                        average[0],
                        average[1],
                        average[2],
                        PlaneViews.luminance(average));
        return measured;
    }

    /**
     * A pin's tooltip: its type and the range of what flows through it.
     *
     * @param node The node.
     * @param pin The pin.
     * @param input Whether it is an input.
     * @return The text, or null when nothing was evaluated there.
     */
    public @Nullable String describePin(@NonNull String node, @NonNull String pin, boolean input) {
        final Graph graph = texture.getGraph();
        final NodeTypes.Pin found = input ? graph.inputPin(node, pin) : graph.outputPin(node, pin);
        if (found == null) {
            return null;
        }
        final Plane plane;
        if (input) {
            plane =
                    Evaluator.source(graph, node, pin) == null
                            ? null
                            : evaluator.input(graph, settings(), node, pin);
        } else {
            final Map<String, Plane> outputs = evaluator.cached(node);
            plane = outputs == null ? null : outputs.get(pin);
        }
        if (plane == null) {
            return pin + " (" + found.type() + ")";
        }
        return pin + " (" + found.type() + "): " + ranges.computeIfAbsent(plane, PlaneViews::range);
    }

    /**
     * Draw the shown node's and the graph's sections of the properties panel: what is shown, the
     * map and shape controls for an Output, the seed grid, Measure's result and the graph's cost.
     *
     * @param frame The current frame.
     */
    public void drawProperties(@NonNull UiFrame frame) {
        final Graph graph = texture.getGraph();
        IkGui.separator();
        long nanos = 0;
        long bytes = 0;
        for (Map.Entry<String, Evaluator.Cost> cost : evaluator.getCosts().entrySet()) {
            if (graph.node(cost.getKey()) != null) {
                nanos += cost.getValue().nanos();
                bytes += cost.getValue().bytes();
            }
        }
        IkGui.textDisabled("Last runs of every node: " + costText(nanos, bytes));
        if (!measured.isEmpty()) {
            IkGui.textWrapped("Measured " + measured);
        }
        if (shown == null) {
            IkGui.textDisabled("Press D over a node, or use its menu, to show it in the Viewer");
            return;
        }
        IkGui.textUnformatted("Shown in the Viewer: " + shown);
        IkGui.sameLine();
        if (IkGui.smallButton("Stop showing")) {
            frame.fire(() -> show(null));
            return;
        }
        if (OutputNodes.isOutput(graph.node(shown).getType())) {
            IkGui.setNextItemWidth(220);
            if (IkGui.beginCombo("Map", shownMap.label)) {
                for (ShownMap map : ShownMap.values()) {
                    if (IkGui.selectable(map.label, map == shownMap)) {
                        frame.fire(
                                () -> {
                                    shownMap = map;
                                    viewerSource = null;
                                });
                    }
                }
                IkGui.endCombo();
            }
            if (IkGui.checkbox("On the 3D view's shape", onShape)) {
                frame.fire(
                        () -> {
                            onShape = !onShape;
                            worn = false;
                            if (onShape) {
                                putOnShape();
                            }
                        });
            }
        }
        if (IkGui.checkbox("Seed grid", seedGrid)) {
            frame.fire(() -> setSeedGrid(!seedGrid));
        }
        if (seedGrid) {
            IkGui.sameLine();
            if (IkGui.smallButton("Other seeds")) {
                frame.fire(this::otherSeeds);
            }
            drawSeedGrid(frame);
        }
    }

    /**
     * Draw the seed grid: each cell the shown node under one seed, the graph's seed outlined; a
     * click uses that seed.
     *
     * @param frame The current frame.
     */
    private void drawSeedGrid(UiFrame frame) {
        final TextureHandle handle = atlas.getTexture();
        for (int i = 0; i < SEED_GRID * SEED_GRID; ++i) {
            if (i % SEED_GRID != 0) {
                IkGui.sameLine();
            }
            final int cell = i;
            final Vector2f at = IkGui.getCursorScreenPos();
            if (IkGui.invisibleButton("seed" + i, SEED_CELL, SEED_CELL)) {
                frame.fire(() -> pickSeed(seedOf(cell)));
            }
            if (IkGui.isItemHovered()) {
                IkGui.setTooltip("Seed " + seedOf(i));
            }
            final DrawList drawList = IkGui.getWindowDrawList();
            final SwatchAtlas.Cell placed = atlas.cell(SEED_KEY + i);
            if (handle != null && placed != null) {
                drawList.addImageView(
                        handle,
                        at.x,
                        at.y,
                        at.x + SEED_CELL,
                        at.y + SEED_CELL,
                        placed.minU(),
                        placed.minV(),
                        placed.maxU(),
                        placed.maxV(),
                        SwatchAtlas.VIEW);
            }
            if (seedOf(i) == texture.getSeed()) {
                drawList.addRect(at.x, at.y, at.x + SEED_CELL, at.y + SEED_CELL, CHOSEN, 0, 0, 2);
            }
        }
    }

    /** Called with a seed picked from the grid. */
    private java.util.function.LongConsumer seedPicked = seed -> {};

    /**
     * Set what picking a seed in the grid does.
     *
     * @param picked Gets the seed.
     */
    public void onSeedPicked(@NonNull java.util.function.LongConsumer picked) {
        seedPicked = picked;
    }

    /**
     * Use a seed from the grid.
     *
     * @param seed The seed.
     */
    public void pickSeed(long seed) {
        seedPicked.accept(seed);
    }

    /** Forget everything shown, as when another graph opens. */
    public void clear() {
        for (String key : atlas.keys()) {
            atlas.remove(key);
        }
        swatched.clear();
        ranges.clear();
        shown = null;
        viewerSource = null;
        maps = null;
        measured = "";
        setSeedGrid(false);
        seedBase = texture.getSeed();
    }

    /** Release the textures. */
    public void close() {
        atlas.close();
        for (int i = 0; i < shapeMaps.length; ++i) {
            textures.release(shapeMaps[i]);
            shapeMaps[i] = null;
        }
        // The Viewer panel closes the swatch it shows, and closed the others when it moved on
        viewer = null;
    }
}
