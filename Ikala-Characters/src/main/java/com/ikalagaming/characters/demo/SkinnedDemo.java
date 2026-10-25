package com.ikalagaming.characters.demo;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Proportions;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.SkinnedInstanceHandle;
import com.ikalagaming.graphics.SkinnedMeshHandle;
import com.ikalagaming.graphics.SkinnedPart;
import com.ikalagaming.graphics.Skins;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.scene.Camera;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A grid of bending skinned tubes in front of the camera, to check skinning, culling, shadows and
 * region hiding without any character art: a four-bone rig, posed by a clip through the pose
 * runtime, its palette sent every frame from a timer, as a game thread would.
 *
 * <p>The tubes are three segments, each in its own mask region. Every third tube hides its middle
 * segment.
 *
 * @author Ches Burks
 */
@Slf4j
public final class SkinnedDemo {
    /** Tubes along each side of the grid. */
    private static final int GRID = 3;

    /** Meters between tubes. */
    private static final double SPACING = 1.5;

    /** How far in front of the camera the grid starts, in meters. */
    private static final double DISTANCE = 4;

    /** How long one segment of a tube is, in meters. */
    private static final float SEGMENT = 0.5f;

    /** The tube's radius, in meters. */
    private static final float RADIUS = 0.12f;

    /** Vertices around each ring of a tube. */
    private static final int RING = 16;

    /** Rings along each segment. */
    private static final int RINGS_PER_SEGMENT = 6;

    /** The rig's bones, from the root up. */
    private static final String[] BONES = {"root", "lower", "middle", "upper"};

    /** Updates per second. */
    private static final int RATE = 60;

    /** Guards starting and stopping. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The plugin whose graphics context owns the demo. */
    private final String plugin;

    /** Runs the updates, or null while stopped. */
    private ScheduledExecutorService timer;

    /** The placed tubes. */
    private final List<SkinnedInstanceHandle> instances = new ArrayList<>();

    /** The registered meshes. */
    private final List<SkinnedMeshHandle> meshes = new ArrayList<>();

    /**
     * Set up a demo, stopped.
     *
     * @param plugin The plugin whose graphics context owns what it places.
     */
    public SkinnedDemo(@NonNull String plugin) {
        this.plugin = plugin;
    }

    /**
     * Whether the demo is running.
     *
     * @return True if it is.
     */
    public boolean isRunning() {
        lock.lock();
        try {
            return timer != null;
        } finally {
            lock.unlock();
        }
    }

    /** Place the tubes in front of the camera and start posing them. */
    public void start() {
        lock.lock();
        try {
            if (timer != null) {
                return;
            }
            GraphicsContext graphics = GraphicsManager.forPlugin(plugin);
            Skins skins = graphics.skins();
            Skeleton skeleton = skeleton();
            Clip bend = bendClip(skeleton);

            Material material = new Material();
            material.getBaseColor().set(new Vector4f(0.75f, 0.45f, 0.3f, 1));
            material.setRoughness(0.6f);
            GraphicsManager.getScene().getMaterialCache().addMaterial(material);

            SkinnedMeshHandle tube = registerTube(skins);
            meshes.add(tube);

            Camera camera = GraphicsManager.getScene().getCamera();
            Vector3f forward = camera.getInvViewMatrix().transformDirection(new Vector3f(0, 0, -1));
            forward.y = 0;
            forward.normalize();
            Vector3f right = new Vector3f(-forward.z, 0, forward.x);
            Vector3d origin = new Vector3d(camera.getPosition()).add(0, -1.2, 0);

            List<Posed> posed = new ArrayList<>();
            for (int row = 0; row < GRID; ++row) {
                for (int column = 0; column < GRID; ++column) {
                    double across = (column - (GRID - 1) / 2.0) * SPACING;
                    double along = DISTANCE + row * SPACING;
                    Vector3d position =
                            new Vector3d(origin)
                                    .add(
                                            forward.x * along + right.x * across,
                                            0,
                                            forward.z * along + right.z * across);
                    SkinnedInstanceHandle handle =
                            skins.place(skeleton.size(), position, new Quaternionf(), 1);
                    skins.setParts(handle, List.of(new SkinnedPart(tube, material)));
                    final int index = row * GRID + column;
                    if (index % 3 == 2) {
                        skins.setHiddenRegions(handle, 1L << 1);
                    }
                    instances.add(handle);
                    posed.add(new Posed(handle, new Pose(skeleton), index * 0.37f));
                }
            }

            PoseSolver solver = new PoseSolver(skeleton);
            Proportions proportions = Proportions.neutral(skeleton);
            float[] palette = new float[skeleton.size() * Skeleton.MATRIX_FLOATS];
            Vector3f min = new Vector3f(-1, -0.1f, -1);
            Vector3f max = new Vector3f(1, 3 * SEGMENT + 0.3f, 1);
            long started = System.nanoTime();
            timer =
                    Executors.newSingleThreadScheduledExecutor(
                            runnable -> {
                                Thread thread = new Thread(runnable, "Skinned demo");
                                thread.setDaemon(true);
                                return thread;
                            });
            timer.scheduleAtFixedRate(
                    () -> {
                        try {
                            float seconds = (System.nanoTime() - started) / 1e9f;
                            for (Posed each : posed) {
                                bend.sample(seconds + each.phase(), each.pose());
                                solver.solve(each.pose(), proportions);
                                solver.writePalette(proportions, palette, 0);
                                skins.setPose(each.handle(), palette, 0, min, max);
                            }
                        } catch (RuntimeException e) {
                            // An exception would cancel the timer silently
                            log.warn("The skinned demo stopped updating", e);
                            throw e;
                        }
                    },
                    0,
                    1000 / RATE,
                    TimeUnit.MILLISECONDS);
            log.info("Placed {} skinned demo tubes", instances.size());
        } finally {
            lock.unlock();
        }
    }

    /** Stop posing and remove everything the demo placed. */
    public void stop() {
        lock.lock();
        try {
            if (timer == null) {
                return;
            }
            timer.shutdownNow();
            timer = null;
            if (!GraphicsManager.forPlugin(plugin).isClosed()) {
                Skins skins = GraphicsManager.forPlugin(plugin).skins();
                instances.forEach(skins::remove);
                meshes.forEach(skins::releaseMesh);
            }
            instances.clear();
            meshes.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * One tube and its pose.
     *
     * @param handle The instance.
     * @param pose Its pose.
     * @param phase How far into the clip it starts, in seconds.
     */
    private record Posed(SkinnedInstanceHandle handle, Pose pose, float phase) {}

    /**
     * The demo rig: a root on the ground and three bones stacked up from it.
     *
     * @return The skeleton.
     */
    private static Skeleton skeleton() {
        List<Rig.Bone> bones = new ArrayList<>();
        Map<String, Skeleton.Rest> rest = new HashMap<>();
        for (int i = 0; i < BONES.length; ++i) {
            bones.add(new Rig.Bone(BONES[i], i == 0 ? null : BONES[i - 1], Rig.BoneKind.DEFORM));
            rest.put(
                    BONES[i],
                    new Skeleton.Rest(new Vector3f(0, i <= 1 ? 0 : SEGMENT, 0), new Quaternionf()));
        }
        return new Skeleton(new Rig(Id.parse("ikala:rig/demo_tube"), bones), rest);
    }

    /**
     * A clip bending each segment back and forth, the upper ones more.
     *
     * @param skeleton The skeleton.
     * @return The clip.
     */
    private static Clip bendClip(Skeleton skeleton) {
        final int keys = 9;
        final float duration = 2;
        List<Clip.RotationTrack> tracks = new ArrayList<>();
        for (int bone = 1; bone < BONES.length; ++bone) {
            float[] times = new float[keys];
            float[] rotations = new float[keys * 4];
            for (int k = 0; k < keys; ++k) {
                times[k] = duration * k / (keys - 1);
                float angle = (float) Math.sin(2 * Math.PI * k / (keys - 1)) * 0.35f * bone;
                Quaternionf rotation = new Quaternionf().rotateZ(angle);
                rotations[k * 4] = rotation.x;
                rotations[k * 4 + 1] = rotation.y;
                rotations[k * 4 + 2] = rotation.z;
                rotations[k * 4 + 3] = rotation.w;
            }
            tracks.add(new Clip.RotationTrack(BONES[bone], times, rotations));
        }
        return new Clip(skeleton, "bend", duration, true, tracks, List.of());
    }

    /**
     * Register the tube: three open segments, each weighted to its bone and blended into the next
     * at the joint, in its own mask region.
     *
     * @param skins The skins API.
     * @return The mesh.
     */
    private static SkinnedMeshHandle registerTube(Skins skins) {
        final int segments = BONES.length - 1;
        final int rings = segments * RINGS_PER_SEGMENT + 1;
        // Each segment has its own vertices, so its triangles are all in one region
        final int vertices = segments * (RINGS_PER_SEGMENT + 1) * RING;
        float[] data = new float[vertices * 14];
        int[] joints = new int[vertices * 4];
        float[] weights = new float[vertices * 4];
        int[] regions = new int[vertices];
        List<Integer> indices = new ArrayList<>();
        int vertex = 0;
        for (int segment = 0; segment < segments; ++segment) {
            final int first = vertex;
            for (int r = 0; r <= RINGS_PER_SEGMENT; ++r) {
                final int ring = segment * RINGS_PER_SEGMENT + r;
                final float height = ring * SEGMENT / RINGS_PER_SEGMENT;
                final float along = height / SEGMENT;
                // Blend into the next bone near the joint above
                final int bone = Math.min(segments, 1 + (int) along);
                final float into = along - (int) along;
                final float blend = Math.clamp((into - 0.7f) / 0.3f, 0, 1);
                for (int k = 0; k < RING; ++k) {
                    double angle = 2 * Math.PI * k / RING;
                    float nx = (float) Math.cos(angle);
                    float nz = (float) Math.sin(angle);
                    int base = vertex * 14;
                    data[base] = nx * RADIUS;
                    data[base + 1] = height;
                    data[base + 2] = nz * RADIUS;
                    // Normal, tangent around the tube, bitangent up it, texture coordinates
                    data[base + 3] = nx;
                    data[base + 5] = nz;
                    data[base + 6] = -nz;
                    data[base + 8] = nx;
                    data[base + 10] = 1;
                    data[base + 12] = (float) k / RING;
                    data[base + 13] = (float) ring / rings;
                    joints[vertex * 4] = bone;
                    joints[vertex * 4 + 1] = Math.min(segments, bone + 1);
                    weights[vertex * 4] = 1 - blend;
                    weights[vertex * 4 + 1] = blend;
                    regions[vertex] = segment;
                    ++vertex;
                }
            }
            for (int r = 0; r < RINGS_PER_SEGMENT; ++r) {
                for (int k = 0; k < RING; ++k) {
                    int a = first + r * RING + k;
                    int b = first + r * RING + (k + 1) % RING;
                    int c = a + RING;
                    int d = b + RING;
                    // Counter-clockwise seen from outside
                    indices.addAll(List.of(a, c, b, b, c, d));
                }
            }
        }
        int[] indexArray = indices.stream().mapToInt(Integer::intValue).toArray();
        return skins.registerMesh(
                data,
                joints,
                weights,
                regions,
                indexArray,
                new Vector3f(-RADIUS, 0, -RADIUS),
                new Vector3f(RADIUS, segments * SEGMENT, RADIUS));
    }
}
