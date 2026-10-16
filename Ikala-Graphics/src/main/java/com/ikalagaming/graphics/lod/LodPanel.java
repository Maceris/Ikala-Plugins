package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.LodBakes;
import com.ikalagaming.graphics.vulkan.LodRenderer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * An IkGui panel for building a DAG and looking at it, which any plugin can draw, for example in a
 * retained UI {@code Immediate} area. It builds on a background thread, then shows the cut through
 * a {@link LodView}, with controls for the error threshold, coloring, a fixed level, bounds and
 * locked border overlays, freezing the observer to look at the cut from outside, and a crack check,
 * plus the build's stats and its {@link Fidelity} to the input.
 *
 * <p>Render thread only, except the build. Close it with the plugin.
 */
@Slf4j
public final class LodPanel implements AutoCloseable {

    /** The smallest error threshold offered, in pixels. */
    private static final float MIN_THRESHOLD = 0.25f;

    /** The largest error threshold offered, in pixels. */
    private static final float MAX_THRESHOLD = 32;

    /** The threshold to start with, in pixels: Nanite's, where changes can't be seen. */
    private static final float DEFAULT_THRESHOLD = 1;

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** The coloring choices, in combo order. */
    private static final String[] MODES = {
        "Materials", "Clusters", "Groups", "Levels", "Mismatch", "Visibility", "Baked"
    };

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Bytes in a mebibyte. */
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** What is being built. */
    private final String name;

    /** The error threshold. */
    private final float[] threshold = {DEFAULT_THRESHOLD};

    /** The coloring, an index into {@link #MODES}. */
    private final IkInt mode = new IkInt(0);

    /** A level to draw whole, or -1 for the cut. */
    private final int[] fixedLevel = {-1};

    /** A level whose locked edges to draw, or -1 for none. */
    private final int[] borderLevel = {-1};

    /** Whether to draw bounding spheres. */
    private final IkBoolean showBounds = new IkBoolean(false);

    /** Whether the observer is frozen. */
    private final IkBoolean freeze = new IkBoolean(false);

    /** The view, once built. Render thread. */
    private LodView view;

    /** The levels table, once built. */
    private volatile LodTables.Table levels;

    /** The fidelity tables, once measured. */
    private volatile List<LodTables.Table> fidelityTables;

    /** Why the build failed, or null. */
    private volatile String failure;

    /** When the build started. */
    private final long started = System.nanoTime();

    /** How long the build took, once done. */
    private volatile double buildMillis;

    /** The last crack check's result, or empty. */
    private String crackResult = "";

    /** Whether it was closed. */
    private volatile boolean closed;

    /**
     * Start building a DAG in the background, naming materials by index.
     *
     * @param graphics The plugin's graphics context.
     * @param name What is being built, for the panel and the view's models.
     * @param inputs The meshes, fetched on the build thread.
     */
    public LodPanel(
            @NonNull GraphicsContext graphics,
            @NonNull String name,
            @NonNull Supplier<List<LodMesh>> inputs) {
        this(graphics, name, inputs, material -> "material " + material);
    }

    /**
     * Start building a DAG in the background.
     *
     * @param graphics The plugin's graphics context.
     * @param name What is being built, for the panel and the view's models.
     * @param inputs The meshes, fetched on the build thread.
     * @param materialName A name for each material index, for the fidelity report.
     */
    public LodPanel(
            @NonNull GraphicsContext graphics,
            @NonNull String name,
            @NonNull Supplier<List<LodMesh>> inputs,
            @NonNull IntFunction<String> materialName) {
        this(graphics, name, inputs, materialName, false);
    }

    /**
     * Start building a DAG in the background, as one DAG or in region cells.
     *
     * @param graphics The plugin's graphics context.
     * @param name What is being built, for the panel and the view's models.
     * @param inputs The meshes, fetched on the build thread. For cells, each must be one section,
     *     with its section's corner as its origin.
     * @param materialName A name for each material index, for the fidelity report.
     * @param cells Whether to build in region cells (see {@link LodStore}), which keeps no lineage
     *     to measure fidelity with.
     */
    public LodPanel(
            @NonNull GraphicsContext graphics,
            @NonNull String name,
            @NonNull Supplier<List<LodMesh>> inputs,
            @NonNull IntFunction<String> materialName,
            boolean cells) {
        this(graphics, name, inputs, materialName, cells, DagBuilder.Options.defaults());
    }

    /**
     * Start building one DAG in the background, with options of its own.
     *
     * @param graphics The plugin's graphics context.
     * @param name What is being built, for the panel and the view's models.
     * @param inputs The meshes, fetched on the build thread.
     * @param materialName A name for each material index, for the fidelity report.
     * @param options How to build. Fidelity is measured only if they keep the lineage.
     */
    public LodPanel(
            @NonNull GraphicsContext graphics,
            @NonNull String name,
            @NonNull Supplier<List<LodMesh>> inputs,
            @NonNull IntFunction<String> materialName,
            DagBuilder.@NonNull Options options) {
        this(graphics, name, inputs, materialName, false, options);
    }

    /**
     * Start building a DAG in the background, as one DAG or in region cells.
     *
     * @param graphics The plugin's graphics context.
     * @param name What is being built, for the panel and the view's models.
     * @param inputs The meshes, fetched on the build thread.
     * @param materialName A name for each material index, for the fidelity report.
     * @param cells Whether to build in region cells.
     * @param options How to build.
     */
    private LodPanel(
            @NonNull GraphicsContext graphics,
            @NonNull String name,
            @NonNull Supplier<List<LodMesh>> inputs,
            @NonNull IntFunction<String> materialName,
            boolean cells,
            DagBuilder.@NonNull Options options) {
        this.graphics = graphics;
        this.name = name;
        Thread builder =
                new Thread(
                        () -> {
                            try {
                                final List<LodMesh> meshes = inputs.get();
                                final LodDag dag =
                                        cells ? inCells(meshes) : DagBuilder.build(meshes, options);
                                buildMillis = (System.nanoTime() - started) / NANOS_PER_MILLI;
                                levels = LodTables.levels(dag);
                                log.info(
                                        "Built LOD for {} in {} ms\n{}",
                                        name,
                                        Math.round(buildMillis),
                                        LodStats.report(name, dag, List.of()));
                                post(
                                        () -> {
                                            if (!closed) {
                                                view = new LodView(graphics, name, dag);
                                            }
                                        });
                                if (cells || !options.keepLineage()) {
                                    fidelityTables =
                                            List.of(
                                                    new LodTables.Table(
                                                            cells
                                                                    ? "Fidelity isn't measured in"
                                                                            + " region cells: they"
                                                                            + " keep no lineage."
                                                                    : "Fidelity isn't measured:"
                                                                            + " the lineage wasn't"
                                                                            + " kept.",
                                                            new String[] {""},
                                                            List.of()));
                                    return;
                                }
                                final long measuring = System.nanoTime();
                                final Fidelity fidelity = Fidelity.measure(dag);
                                fidelityTables = LodTables.fidelity(fidelity, materialName);
                                log.info(
                                        "Measured LOD fidelity for {} in {} ms\n{}",
                                        name,
                                        Math.round(
                                                (System.nanoTime() - measuring) / NANOS_PER_MILLI),
                                        fidelity.report(materialName));
                                post(
                                        () -> {
                                            if (view != null) {
                                                view.setFidelity(fidelity);
                                            }
                                        });
                            } catch (RuntimeException e) {
                                log.warn("Could not build LOD for {}", name, e);
                                failure = String.valueOf(e.getMessage());
                            }
                        },
                        "LOD panel " + name);
        builder.setDaemon(true);
        builder.start();
    }

    /**
     * Build sections in region cells, and flatten the cells into one DAG.
     *
     * @param sections The section meshes, each with its section's corner as its origin.
     * @return The flattened DAG.
     */
    private static LodDag inCells(@NonNull List<LodMesh> sections) {
        final LodStore store =
                new LodStore(
                        new double[AXES],
                        Sections.SECTION_SIZE,
                        LodStore.DEFAULT_CELL_SECTIONS,
                        LodStore.DEFAULT_STOP_AT,
                        DagBuilder.Options.defaults(),
                        LodCache.fromSettings());
        for (LodMesh mesh : sections) {
            final double[] origin = mesh.getOrigin();
            store.put(
                    new LodStore.SectionPos(
                            (int) Math.floor(origin[0] / Sections.SECTION_SIZE),
                            (int) Math.floor(origin[1] / Sections.SECTION_SIZE),
                            (int) Math.floor(origin[2] / Sections.SECTION_SIZE)),
                    mesh);
        }
        store.rebuild();
        return store.snapshot();
    }

    /**
     * Whether the build is done and its cut is being shown.
     *
     * @return True once built. Render thread.
     */
    public boolean isBuilt() {
        return view != null;
    }

    /** Draw the panel and keep the view up to date. Call once a frame, inside an IkGui window. */
    public void draw() {
        if (failure != null) {
            IkGui.textWrapped("Could not build " + name + ": " + failure);
            return;
        }
        if (view == null) {
            IkGui.text(
                    String.format(
                            "Building %s... %.0f s",
                            name, (System.nanoTime() - started) / NANOS_PER_SECOND));
            return;
        }
        view.update(
                new LodView.Settings(
                        threshold[0],
                        LodView.Mode.values()[mode.get()],
                        fixedLevel[0],
                        showBounds.get(),
                        borderLevel[0]));
        final int topLevel = view.getDag().getLevels() - 1;
        IkGui.sliderFloat("Error (pixels)", threshold, MIN_THRESHOLD, MAX_THRESHOLD);
        IkGui.combo("Colors", mode, MODES);
        if (LodView.Mode.values()[mode.get()] == LodView.Mode.MISMATCH) {
            IkGui.textWrapped(
                    fidelityTables == null
                            ? "Measuring fidelity..."
                            : "Gray: the input under the triangle has its material. Redder: more"
                                    + " of it doesn't. Magenta: its material isn't even the most"
                                    + " common under it.");
        }
        if (LodView.Mode.values()[mode.get()] == LodView.Mode.BAKED) {
            IkGui.textWrapped(
                    "Gray: its own material. Blue: blended to its materials' average. Orange: baked"
                            + " into its cluster's tile.");
        }
        IkGui.sliderInt("Draw level", fixedLevel, -1, topLevel, fixedLevel[0] < 0 ? "cut" : "%d");
        IkGui.sliderInt(
                "Locked edges of",
                borderLevel,
                -1,
                topLevel,
                borderLevel[0] < 0 ? "none" : "level %d");
        IkGui.checkbox("Cluster bounds", showBounds);
        if (IkGui.checkbox("Freeze observer", freeze)) {
            final Scene scene = GraphicsManager.getScene();
            if (freeze.get()) {
                scene.freezeObserver();
            } else {
                scene.unfreezeObserver();
            }
        }
        if (IkGui.button("Check cracks")) {
            final CrackCheck.Result result = new CrackCheck(view.getDag()).check(view.drawn());
            crackResult =
                    result.isClean()
                            ? "No cracks in " + result.drawnClusters() + " clusters."
                            : result.openEdges().size()
                                    + " cracks and "
                                    + result.overdrawnEdges().size()
                                    + " edges drawn twice in "
                                    + result.drawnClusters()
                                    + " clusters.";
        }
        if (!crackResult.isEmpty()) {
            IkGui.sameLine();
            IkGui.text(crackResult);
        }
        IkGui.text(
                String.format(
                        "Drawing %,d triangles in %,d clusters (cut in %.1f ms). Built in %.0f ms.",
                        view.getDrawnTriangles(),
                        view.getDrawnClusters(),
                        view.getBuildMillis(),
                        buildMillis));
        if (view.isTexturedShown()) {
            final LodRenderer.Stats gpu = graphics.lods().stats();
            IkGui.text(
                    String.format(
                            "GPU cut: %,d triangles in %,d clusters; %,d outside the view, %,d"
                                    + " hidden, %,d drawn early, %,d late.",
                            gpu.selectedTriangles(),
                            gpu.selectedClusters(),
                            gpu.outside(),
                            gpu.occluded(),
                            gpu.early(),
                            gpu.late()));
            final LodRenderer.LiveStats residency = view.residency();
            if (residency != null) {
                IkGui.text(
                        String.format(
                                "On the GPU: %,d of %,d groups, %.1f of %.1f MiB (%.1f MiB wanted).",
                                residency.shownUnits(),
                                residency.units(),
                                residency.loadedBytes() / BYTES_PER_MIB,
                                residency.totalBytes() / BYTES_PER_MIB,
                                residency.wantedBytes() / BYTES_PER_MIB));
            }
            final long allBaked = bakedBytes(view.getDag());
            if (allBaked > 0) {
                final LodBakes.Stats bakes = graphics.lods().bakeStats();
                IkGui.text(
                        String.format(
                                "Baked tiles: %,d loaded, %.1f of %.1f MiB, in %d pages of %.0f"
                                        + " MiB.",
                                bakes.tiles(),
                                bakes.texels() * LodBakes.BYTES_PER_TEXEL / BYTES_PER_MIB,
                                allBaked / BYTES_PER_MIB,
                                bakes.pages(),
                                bakes.pages() * LodBakes.PAGE_BYTES / BYTES_PER_MIB));
            }
            // Below the totals, which a long table would push out of sight
            if (gpu.levelTriangles().length > 0) {
                LodTables.draw(LodTables.cutLevels(gpu));
            }
        }
        if (IkGui.collapsingHeader("Levels##lod", TreeNodeFlags.DEFAULT_OPEN)) {
            LodTables.draw(levels);
        }
        final List<LodTables.Table> fidelity = fidelityTables;
        if (fidelity == null) {
            IkGui.text("Measuring fidelity...");
            return;
        }
        if (IkGui.collapsingHeader("Fidelity##lod", TreeNodeFlags.DEFAULT_OPEN)) {
            LodTables.draw(fidelity.get(0));
        }
        if (IkGui.collapsingHeader("Material shares##lod")) {
            fidelity.subList(1, fidelity.size()).forEach(LodTables::draw);
        }
    }

    /**
     * The bytes every baked cluster's tile of a DAG would take on the GPU.
     *
     * @param dag The DAG.
     * @return The bytes.
     */
    private static long bakedBytes(@NonNull LodDag dag) {
        long texels = 0;
        for (LodDag.Group group : dag.groups()) {
            texels += group.bakedTexels();
        }
        return texels * LodBakes.BYTES_PER_TEXEL;
    }

    /**
     * Run something on the render thread, unless the plugin unloads first.
     *
     * @param change What to run.
     */
    private void post(@NonNull Runnable change) {
        if (!graphics.isClosed()) {
            graphics.ui()
                    .post(
                            () -> {
                                if (!graphics.isClosed()) {
                                    change.run();
                                }
                            });
        }
    }

    /** Remove the view and unfreeze the observer if this froze it. Render thread. */
    @Override
    public void close() {
        closed = true;
        if (view != null) {
            view.close();
            view = null;
        }
        if (freeze.get()) {
            GraphicsManager.getScene().unfreezeObserver();
        }
    }
}
