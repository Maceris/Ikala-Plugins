package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.DebugShapeHandle;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.InstanceHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.scene.Observer;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugLine;
import com.ikalagaming.graphics.scene.debug.DebugShape;
import com.ikalagaming.graphics.scene.debug.DebugSphere;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Draws a DAG's cut in the scene, picked on the CPU from the scene's observer by {@link LodCut}.
 * The drawn clusters are merged into a few ordinary meshes, one per material or per debug color, so
 * a cut of thousands of clusters is a handful of draws. The meshes are rebuilt on a worker when the
 * observer moves far enough or the settings change, and the old ones keep drawing until the new
 * ones are uploaded.
 *
 * <p>Owned by a plugin's graphics context: its meshes and instances are released with the plugin.
 * Render thread only, except where noted.
 */
@Slf4j
public final class LodView implements AutoCloseable {

    /** How triangles are colored. */
    public enum Mode {
        /** By their own materials. */
        MATERIALS,
        /** A color per cluster. */
        CLUSTERS,
        /** A color per group that made the cluster, so siblings share it. */
        GROUPS,
        /** A color per level. */
        LEVELS
    }

    /**
     * What to show.
     *
     * @param thresholdPixels The most error, in pixels, a drawn cluster may have.
     * @param mode How triangles are colored.
     * @param fixedLevel Draw every cluster of this level instead of a cut, or -1 for the cut.
     * @param showBounds Whether to draw each drawn cluster's bounding sphere.
     * @param borderLevel Draw the edges locked while building this level's groups, or -1 for none.
     */
    public record Settings(
            double thresholdPixels,
            @NonNull Mode mode,
            int fixedLevel,
            boolean showBounds,
            int borderLevel) {}

    /** How many debug colors there are. */
    private static final int PALETTE = 16;

    /** The golden ratio's fractional part, for spreading hues apart. */
    private static final double GOLDEN = 0.618_033_988_749_895;

    /** How saturated the debug colors are. */
    private static final float DEBUG_SATURATION = 0.65f;

    /** How bright the debug colors are. */
    private static final float DEBUG_BRIGHTNESS = 0.95f;

    /** One color channel's largest value. */
    private static final float CHANNEL_MAX = 255f;

    /** How rough the debug materials are. */
    private static final float DEBUG_ROUGHNESS = 0.8f;

    /** How far the observer must move, as a share of the DAG's radius, before the cut is redone. */
    private static final double MOVE_SHARE = 0.01;

    /** The least the observer must move before the cut is redone, in world units. */
    private static final double MIN_MOVE = 0.25;

    /** The most debug shapes drawn, so a huge DAG doesn't flood the debug pass. */
    private static final int MAX_SHAPES = 20_000;

    /** The bounding sphere color. */
    private static final int BOUNDS_COLOR = Color.rgba(0, 200, 255, 255);

    /** The locked border color. */
    private static final int BORDER_COLOR = Color.rgba(255, 60, 30, 255);

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /** Floats per vertex in a mesh. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** Where the normal is in a vertex. */
    private static final int NORMAL = 3;

    /** Where the tangent is in a vertex. */
    private static final int TANGENT = 6;

    /** Where the bitangent is in a vertex. */
    private static final int BITANGENT = 9;

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /**
     * Meshes made on a worker, waiting for the render thread.
     *
     * @param generation Which build they are from.
     * @param keys Each mesh's material index or debug color.
     * @param meshes The meshes.
     * @param shapes Debug shapes to draw.
     * @param clusters How many clusters were drawn.
     * @param triangles How many triangles were drawn.
     * @param drawn Whether each cluster was drawn.
     * @param millis How long it took.
     */
    private record Built(
            long generation,
            int[] keys,
            List<MeshData> meshes,
            List<DebugShape> shapes,
            int clusters,
            int triangles,
            boolean[] drawn,
            double millis) {}

    /**
     * What is in the scene for one build.
     *
     * @param modelId The model's ID.
     * @param model The model.
     * @param instance Its instance.
     */
    private record Shown(String modelId, Model model, InstanceHandle instance) {}

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** The name, for model IDs. */
    private final String name;

    /**
     * The DAG.
     *
     * @return The DAG.
     */
    @Getter private final LodDag dag;

    /** Builds meshes. */
    private final ExecutorService worker;

    /** Counts builds, so a stale one is dropped. */
    private final AtomicLong generation = new AtomicLong();

    /** The debug color materials. */
    private final Material[] palette = new Material[PALETTE];

    /** How far the observer must move before the cut is redone. */
    private final double moveStep;

    /** The settings last built with. */
    private Settings builtSettings;

    /** Where the observer was when last built. */
    private final Vector3d builtAt = new Vector3d(Double.NaN, Double.NaN, Double.NaN);

    /** Whether a build is running. */
    private boolean building;

    /** What is drawn now. */
    private Shown current;

    /** What was drawn before, kept until the current meshes are uploaded. */
    private Shown previous;

    /** The debug shapes drawn now. */
    private final List<DebugShapeHandle> shapes = new ArrayList<>();

    /** Whether each cluster is drawn now, for the crack check. */
    private boolean[] drawn = new boolean[0];

    /**
     * How many clusters are drawn.
     *
     * @return The count.
     */
    @Getter private int drawnClusters;

    /**
     * How many triangles are drawn.
     *
     * @return The count.
     */
    @Getter private int drawnTriangles;

    /**
     * How long the last mesh build took.
     *
     * @return The time, in milliseconds.
     */
    @Getter private double buildMillis;

    /** Whether it was closed. */
    private boolean closed;

    /** A count for model IDs, so the old and new models never share one. */
    private int builds;

    /**
     * Set up a view of a DAG. Nothing is drawn until {@link #update}.
     *
     * @param graphics The plugin's graphics context.
     * @param name A name for its models.
     * @param dag The DAG.
     */
    public LodView(@NonNull GraphicsContext graphics, @NonNull String name, @NonNull LodDag dag) {
        this.graphics = graphics;
        this.name = name;
        this.dag = dag;
        worker = Executors.newSingleThreadExecutor(DagBuilder.daemonThreads("LOD view " + name));
        double radius = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (dag.consumer(cluster.id()) < 0) {
                radius = Math.max(radius, cluster.bound()[AXES]);
            }
        }
        moveStep = Math.max(MIN_MOVE, radius * MOVE_SHARE);
        final MaterialCache cache = GraphicsManager.getScene().getMaterialCache();
        for (int i = 0; i < PALETTE; ++i) {
            final java.awt.Color rgb =
                    java.awt.Color.getHSBColor(
                            (float) ((i * GOLDEN) % 1.0), DEBUG_SATURATION, DEBUG_BRIGHTNESS);
            Material material = new Material();
            material.getBaseColor()
                    .set(
                            rgb.getRed() / CHANNEL_MAX,
                            rgb.getGreen() / CHANNEL_MAX,
                            rgb.getBlue() / CHANNEL_MAX,
                            1);
            material.setRoughness(DEBUG_ROUGHNESS);
            cache.addMaterial(material);
            palette[i] = material;
        }
        cache.setDirty(true);
    }

    /**
     * Redo the cut if the observer moved far enough or the settings changed, and swap in meshes
     * that have finished. Call once a frame.
     *
     * @param settings What to show.
     */
    public void update(@NonNull Settings settings) {
        if (closed) {
            return;
        }
        retirePrevious();
        if (building) {
            return;
        }
        final Scene scene = GraphicsManager.getScene();
        final Observer observer = scene.getObserver();
        final Vector3d at = new Vector3d(observer.getPosition());
        if (settings.equals(builtSettings) && at.distance(builtAt) < moveStep) {
            return;
        }
        builtSettings = settings;
        builtAt.set(at);
        building = true;
        final long mine = generation.incrementAndGet();
        final LodCut.View view = new LodCut.View(at.x, at.y, at.z, pixelsPerUnit(observer));
        worker.execute(
                () -> {
                    final Built built;
                    try {
                        built = build(mine, view, settings);
                    } catch (RuntimeException e) {
                        log.warn("The LOD view {} could not build", name, e);
                        post(() -> building = false);
                        return;
                    }
                    post(() -> show(built));
                });
    }

    /**
     * Pixels per world unit at distance 1, from the observer's projection and the window height.
     *
     * @param observer The observer.
     * @return The scale.
     */
    private static double pixelsPerUnit(@NonNull Observer observer) {
        // m11 is 1 / tan(fov / 2), negative with Vulkan's y flip
        final double focal = Math.abs(observer.getProjectionMatrix().m11());
        return focal * GraphicsManager.getWindow().getHeight() / 2;
    }

    /**
     * Pick the clusters and merge them into meshes. On the worker.
     *
     * @param mine This build's generation.
     * @param view Where the cut is seen from.
     * @param settings What to show.
     * @return The meshes.
     */
    private Built build(long mine, LodCut.@NonNull View view, @NonNull Settings settings) {
        final long start = System.nanoTime();
        final boolean[] chosen;
        if (settings.fixedLevel() >= 0) {
            final int level = Math.min(settings.fixedLevel(), dag.getLevels() - 1);
            chosen = new boolean[dag.clusters().size()];
            for (LodDag.Cluster cluster : dag.clusters()) {
                chosen[cluster.id()] = cluster.level() == level;
            }
        } else {
            chosen = LodCut.select(dag, view, settings.thresholdPixels());
        }
        // Gather triangles per key
        Map<Integer, List<float[]>> buckets = new TreeMap<>();
        int clusters = 0;
        int triangles = 0;
        for (int i = 0; i < chosen.length; ++i) {
            if (!chosen[i]) {
                continue;
            }
            final LodDag.Cluster cluster = dag.clusters().get(i);
            ++clusters;
            triangles += cluster.triangleCount();
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                final int key = keyOf(cluster, t, settings.mode());
                float[] corners = new float[TRIANGLE_VALUES];
                System.arraycopy(
                        cluster.corners(), t * TRIANGLE_VALUES, corners, 0, TRIANGLE_VALUES);
                buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(corners);
            }
        }
        final int[] keys = new int[buckets.size()];
        List<MeshData> meshes = new ArrayList<>();
        int k = 0;
        for (Map.Entry<Integer, List<float[]>> bucket : buckets.entrySet()) {
            keys[k++] = bucket.getKey();
            meshes.add(mesh(bucket.getValue()));
        }
        final List<DebugShape> overlay = overlay(chosen, settings);
        return new Built(
                mine,
                keys,
                meshes,
                overlay,
                clusters,
                triangles,
                chosen,
                (System.nanoTime() - start) / NANOS_PER_MILLI);
    }

    /**
     * Which mesh a triangle goes in.
     *
     * @param cluster Its cluster.
     * @param triangle The triangle.
     * @param mode How triangles are colored.
     * @return Its material index, or its debug color.
     */
    private static int keyOf(LodDag.@NonNull Cluster cluster, int triangle, @NonNull Mode mode) {
        return switch (mode) {
            case MATERIALS -> cluster.materials()[triangle];
            case CLUSTERS -> Math.floorMod(cluster.id(), PALETTE);
            case GROUPS ->
                    Math.floorMod(
                            cluster.producer() < 0 ? cluster.id() : cluster.producer(), PALETTE);
            case LEVELS -> Math.floorMod(cluster.level(), PALETTE);
        };
    }

    /**
     * Make a mesh from triangles, each with its own flat normal.
     *
     * @param triangles The triangles' corners, relative to the DAG's origin.
     * @return The mesh.
     */
    private static MeshData mesh(@NonNull List<float[]> triangles) {
        final int vertexCount = triangles.size() * AXES;
        final float[] data = new float[vertexCount * STRIDE];
        final int[] indices = new int[vertexCount];
        final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] n = new double[AXES];
        for (int t = 0; t < triangles.size(); ++t) {
            final float[] corners = triangles.get(t);
            for (int axis = 0; axis < AXES; ++axis) {
                a[axis] = corners[axis];
                b[axis] = corners[AXES + axis];
                c[axis] = corners[2 * AXES + axis];
            }
            Geometry.normal(a, b, c, n);
            final double length = Math.sqrt(Geometry.dot(n, n));
            final float nx = length == 0 ? 0 : (float) (n[0] / length);
            final float ny = length == 0 ? 1 : (float) (n[1] / length);
            final float nz = length == 0 ? 0 : (float) (n[2] / length);
            // Any tangent across the face will do; there are no texture coordinates to follow
            final double tLength =
                    Math.sqrt(
                            (b[0] - a[0]) * (b[0] - a[0])
                                    + (b[1] - a[1]) * (b[1] - a[1])
                                    + (b[2] - a[2]) * (b[2] - a[2]));
            final float tx = tLength == 0 ? 1 : (float) ((b[0] - a[0]) / tLength);
            final float ty = tLength == 0 ? 0 : (float) ((b[1] - a[1]) / tLength);
            final float tz = tLength == 0 ? 0 : (float) ((b[2] - a[2]) / tLength);
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = t * AXES + corner;
                final int at = v * STRIDE;
                for (int axis = 0; axis < AXES; ++axis) {
                    final float p = corners[corner * AXES + axis];
                    data[at + axis] = p;
                    min[axis] = Math.min(min[axis], p);
                    max[axis] = Math.max(max[axis], p);
                }
                data[at + NORMAL] = nx;
                data[at + NORMAL + 1] = ny;
                data[at + NORMAL + 2] = nz;
                data[at + TANGENT] = tx;
                data[at + TANGENT + 1] = ty;
                data[at + TANGENT + 2] = tz;
                data[at + BITANGENT] = ny * tz - nz * ty;
                data[at + BITANGENT + 1] = nz * tx - nx * tz;
                data[at + BITANGENT + 2] = nx * ty - ny * tx;
                indices[v] = v;
            }
        }
        return new MeshData(
                new Vector3f(min[0], min[1], min[2]),
                new Vector3f(max[0], max[1], max[2]),
                vertexCount,
                data,
                indices,
                0,
                new byte[0]);
    }

    /**
     * The debug shapes to draw: drawn clusters' spheres and a level's locked edges.
     *
     * @param chosen Whether each cluster is drawn.
     * @param settings What to show.
     * @return The shapes, in world space.
     */
    private List<DebugShape> overlay(boolean @NonNull [] chosen, @NonNull Settings settings) {
        List<DebugShape> out = new ArrayList<>();
        final double[] origin = dag.getOrigin();
        if (settings.showBounds()) {
            for (int i = 0; i < chosen.length && out.size() < MAX_SHAPES; ++i) {
                if (chosen[i]) {
                    final double[] s = dag.clusters().get(i).tight();
                    out.add(
                            new DebugSphere(
                                    new Vector3d(
                                            origin[0] + s[0], origin[1] + s[1], origin[2] + s[2]),
                                    s[AXES],
                                    BOUNDS_COLOR));
                }
            }
        }
        if (settings.borderLevel() >= 0) {
            for (Grouping.Edge edge : lockedEdges(settings.borderLevel())) {
                if (out.size() >= MAX_SHAPES) {
                    break;
                }
                out.add(
                        new DebugLine(
                                world(edge.a(), origin),
                                world(edge.b(), origin),
                                BORDER_COLOR,
                                true));
            }
        }
        return out;
    }

    /**
     * The edges locked while building a level's groups: edges both of whose corners more than one
     * of the level's groups used.
     *
     * @param level The level.
     * @return The edges.
     */
    List<Grouping.Edge> lockedEdges(int level) {
        Map<Grouping.Corner, Integer> owners = new HashMap<>();
        List<Integer> children = new ArrayList<>();
        for (LodDag.Group group : dag.groups()) {
            if (group.level() != level) {
                continue;
            }
            for (int child : group.children()) {
                children.add(child);
                final float[] corners = dag.clusters().get(child).corners();
                for (int at = 0; at < corners.length; at += AXES) {
                    owners.merge(
                            Grouping.Corner.of(corners, at),
                            group.id(),
                            (x, y) -> x.equals(y) ? x : -1);
                }
            }
        }
        List<Grouping.Edge> out = new ArrayList<>();
        for (Grouping.Edge edge : Grouping.edgeUses(dag, children).keySet()) {
            if (owners.getOrDefault(edge.a(), 0) < 0 && owners.getOrDefault(edge.b(), 0) < 0) {
                out.add(edge);
            }
        }
        return out;
    }

    /**
     * A corner in world space.
     *
     * @param corner The corner, relative to the DAG's origin.
     * @param origin The DAG's origin.
     * @return The world position.
     */
    private static Vector3d world(Grouping.@NonNull Corner corner, double @NonNull [] origin) {
        return new Vector3d(
                origin[0] + Float.intBitsToFloat(corner.x()),
                origin[1] + Float.intBitsToFloat(corner.y()),
                origin[2] + Float.intBitsToFloat(corner.z()));
    }

    /**
     * Put built meshes in the scene. Render thread.
     *
     * @param built The meshes.
     */
    private void show(@NonNull Built built) {
        building = false;
        if (closed || built.generation() != generation.get()) {
            return;
        }
        final Scene scene = GraphicsManager.getScene();
        final MaterialCache cache = scene.getMaterialCache();
        final Model model = new Model(name + "#lod" + builds++);
        for (int i = 0; i < built.meshes().size(); ++i) {
            final MeshData mesh = built.meshes().get(i);
            final int key = built.keys()[i];
            mesh.setMaterial(
                    builtSettings.mode() == Mode.MATERIALS ? material(cache, key) : palette[key]);
            graphics.meshes().register(mesh);
            model.getMeshDataList().add(mesh);
        }
        scene.addModel(model);
        final double[] origin = dag.getOrigin();
        final InstanceHandle instance =
                graphics.instances()
                        .place(
                                model,
                                new Vector3d(origin[0], origin[1], origin[2]),
                                new Quaternionf(),
                                1);
        // The old model goes once the new meshes are uploaded; one still pending is dropped now
        if (previous != null) {
            scene.removeModel(previous.modelId());
        }
        previous = current;
        current = new Shown(model.getId(), model, instance);
        for (DebugShapeHandle handle : shapes) {
            graphics.debug().remove(handle);
        }
        shapes.clear();
        for (DebugShape shape : built.shapes()) {
            shapes.add(graphics.debug().add(shape));
        }
        drawn = built.drawn();
        drawnClusters = built.clusters();
        drawnTriangles = built.triangles();
        buildMillis = built.millis();
    }

    /**
     * A material by index, or the default if the index is out of range.
     *
     * @param cache The material cache.
     * @param index The index.
     * @return The material.
     */
    private static Material material(@NonNull MaterialCache cache, int index) {
        return index >= 0 && index < cache.getMaterialCount()
                ? cache.getMaterial(index)
                : MaterialCache.DEFAULT_MATERIAL;
    }

    /** Remove the previous model once every current mesh is uploaded. */
    private void retirePrevious() {
        if (previous == null || current == null) {
            return;
        }
        for (MeshData mesh : current.model().getMeshDataList()) {
            if (!graphics.meshes().isResident(mesh.getMesh())) {
                return;
            }
        }
        GraphicsManager.getScene().removeModel(previous.modelId());
        previous = null;
    }

    /**
     * Whether each cluster is drawn now.
     *
     * @return A copy of the cut.
     */
    public boolean[] drawn() {
        return drawn.clone();
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

    /**
     * Stop building and take everything out of the scene. Render thread. If the plugin's context
     * already closed, its meshes, instances and shapes went with it; the models and debug
     * materials, which the scene holds for every plugin, are still removed.
     */
    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
        final Scene scene = GraphicsManager.getScene();
        if (scene == null) {
            return;
        }
        if (previous != null) {
            scene.removeModel(previous.modelId());
        }
        if (current != null) {
            scene.removeModel(current.modelId());
        }
        previous = null;
        current = null;
        if (!graphics.isClosed()) {
            shapes.forEach(graphics.debug()::remove);
        }
        shapes.clear();
        for (Material material : palette) {
            scene.getMaterialCache().removeMaterial(material);
        }
        scene.getMaterialCache().setDirty(true);
    }
}
