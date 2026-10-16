package com.ikalagaming.converter.scene;

import com.ikalagaming.converter.scene.ImportedScene.ImportedMaterial;
import com.ikalagaming.converter.scene.ImportedScene.Node;
import com.ikalagaming.converter.scene.ImportedScene.TextureRef;
import com.ikalagaming.converter.scene.ImportedScene.TextureSlot;
import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.InstanceHandle;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.texture.DdsFile;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Puts an {@link ImportedScene} in the world: each mesh is registered once as a model, and each
 * node that draws it becomes an instance of that model. A node whose transform an instance can't
 * hold, with a scale that differs along its axes, a shear or a mirror, gets its meshes baked into
 * world space instead.
 *
 * <p>Materials go into the scene's material cache, and textures load in the background after the
 * meshes, a bounded amount at a time so a scene with gigabytes of textures doesn't stage them all
 * at once; surfaces show white until theirs arrive. Base color and emissive textures load as sRGB,
 * the rest as linear.
 *
 * <p>{@link #prepare} runs on any thread, {@link #place()} and {@link #remove()} on the render
 * thread, {@link #loadTextures} on a background thread.
 */
@Slf4j
public final class ScenePlacement {
    /** How far scales along a node's axes may differ and still count as uniform. */
    static final double UNIFORM_TOLERANCE = 1e-4;

    /** How far a node's axes may lean and still count as at right angles. */
    static final double SQUARE_TOLERANCE = 1e-4;

    /** The most texture data staged but not yet on the GPU before loading waits. */
    static final long PENDING_TEXTURE_BYTES = 256L * 1024 * 1024;

    /** How long to wait for uploads before checking again, in milliseconds. */
    private static final long UPLOAD_WAIT_MILLIS = 15;

    /** Floats per vertex in mesh data. */
    private static final int STRIDE = MeshData.VERTEX_SIZE_IN_FLOATS;

    /** Makes model IDs unique between placements. */
    private static final AtomicInteger PLACEMENTS = new AtomicInteger();

    /**
     * Whether the scene's normal maps point green down the texture, DirectX's way, which FBX files
     * usually have, rather than up, OpenGL's way, which glTF and most others have.
     *
     * @return True for DirectX normal maps.
     */
    @Getter private boolean normalGreenDown;

    /** The plugin's graphics context, or null for a placement only looked at, in tests. */
    private final @Nullable GraphicsContext graphics;

    /** The scene read from the file. */
    @Getter private final ImportedScene imported;

    /** The prefix of this placement's model IDs. */
    private final String idPrefix;

    /** One material per imported material. */
    private final List<Material> materials = new ArrayList<>();

    /** The models to add, each with where its instances go. */
    private final List<Placed> models = new ArrayList<>();

    /** Every instance, by the node it stands for, once placed. */
    private final Map<Node, List<InstanceHandle>> instances = new IdentityHashMap<>();

    /** Textures loaded, by file, so a file several materials use loads once. */
    private final Map<Path, TextureHandle> textures = new ConcurrentHashMap<>();

    /** Whether the scene was taken out of the world, after which textures still loading go. */
    private volatile boolean removed;

    /**
     * How many nodes had their meshes baked into world space.
     *
     * @return The count.
     */
    @Getter private int bakedNodes;

    /**
     * How many textures have loaded so far.
     *
     * @return The count.
     */
    @Getter private volatile int texturesLoaded;

    /**
     * How many textures there are to load.
     *
     * @return The count.
     */
    @Getter private int texturesToLoad;

    /**
     * Where a model's instances go.
     *
     * @param model The model.
     * @param transforms Each instance's node and transform: position, rotation and scale.
     */
    private record Placed(@NonNull Model model, @NonNull List<Transform> transforms) {}

    /**
     * An instance's transform.
     *
     * @param node The node it stands for.
     * @param position The position.
     * @param rotation The rotation.
     * @param scale The uniform scale.
     */
    record Transform(
            @NonNull Node node,
            @NonNull Vector3d position,
            @NonNull Quaternionf rotation,
            float scale) {}

    /**
     * Set up a placement.
     *
     * @param graphics The plugin's graphics context, or null if it will only be looked at.
     * @param imported The scene.
     */
    private ScenePlacement(@Nullable GraphicsContext graphics, @NonNull ImportedScene imported) {
        this.graphics = graphics;
        this.imported = imported;
        idPrefix = "workbench-scene-" + PLACEMENTS.getAndIncrement() + "-";
    }

    /**
     * Work out the models and instances for a scene, baking nodes that need it. Any thread.
     *
     * @param graphics The plugin's graphics context, or null for a placement only looked at, as
     *     tests do.
     * @param imported The scene.
     * @return The placement, ready to {@link #place()}.
     */
    public static ScenePlacement prepare(
            @Nullable GraphicsContext graphics, @NonNull ImportedScene imported) {
        ScenePlacement placement = new ScenePlacement(graphics, imported);
        placement.normalGreenDown = defaultGreenDown(imported.file());
        for (ImportedMaterial source : imported.materials()) {
            final Material material = material(source);
            material.setNormalGreenDown(placement.normalGreenDown);
            placement.materials.add(material);
        }
        final Map<Integer, Placed> byMesh = new HashMap<>();
        final Deque<Node> open = new ArrayDeque<>();
        open.push(imported.root());
        while (!open.isEmpty()) {
            final Node node = open.pop();
            node.children().forEach(open::push);
            if (node.meshes().length == 0) {
                continue;
            }
            final Transform transform = instanceTransform(node, node.world());
            if (transform == null) {
                placement.bake(node);
                continue;
            }
            for (int index : node.meshes()) {
                byMesh.computeIfAbsent(index, placement::shared).transforms().add(transform);
            }
        }
        return placement;
    }

    /**
     * Guess which way a file's normal maps point green: down, DirectX's way, for FBX files, which
     * mostly come from tools that bake that way, and up, OpenGL's way, for the rest.
     *
     * @param file The model file.
     * @return True for DirectX normal maps.
     */
    static boolean defaultGreenDown(@NonNull Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".fbx");
    }

    /**
     * Set which way every material's normal map points green, for when the guess was wrong. Render
     * thread only; the materials are written to the GPU every frame.
     *
     * @param greenDown True for DirectX normal maps, false for OpenGL ones.
     */
    public void setNormalGreenDown(boolean greenDown) {
        normalGreenDown = greenDown;
        for (Material material : materials) {
            material.setNormalGreenDown(greenDown);
        }
    }

    /**
     * Make the shared model for a mesh.
     *
     * @param index The mesh.
     * @return Its model, with no instances yet.
     */
    private Placed shared(int index) {
        final ImportedScene.Mesh mesh = imported.meshes().get(index);
        final Model model = new Model(idPrefix + "mesh-" + index);
        mesh.data().setMaterial(materials.get(mesh.material()));
        model.getMeshDataList().add(mesh.data());
        final Placed placed = new Placed(model, new ArrayList<>());
        models.add(placed);
        return placed;
    }

    /**
     * Bake a node's meshes into world space, relative to its position, as their own model.
     *
     * @param node The node.
     */
    private void bake(Node node) {
        ++bakedNodes;
        final Matrix4dc world = node.world();
        final Vector3d origin = world.getTranslation(new Vector3d());
        final Model model = new Model(idPrefix + "baked-" + bakedNodes);
        for (int index : node.meshes()) {
            final ImportedScene.Mesh mesh = imported.meshes().get(index);
            final MeshData baked = bakeMesh(mesh.data(), world, origin);
            baked.setMaterial(materials.get(mesh.material()));
            model.getMeshDataList().add(baked);
        }
        final List<Transform> transforms = new ArrayList<>();
        transforms.add(new Transform(node, origin, new Quaternionf(), 1));
        models.add(new Placed(model, transforms));
    }

    /**
     * Work out the instance transform for a node, if an instance can hold it: a position, a
     * rotation, and the same positive scale along every axis.
     *
     * @param node The node.
     * @param world Its world transform.
     * @return The transform, or null if it needs baking.
     */
    static @Nullable Transform instanceTransform(@NonNull Node node, @NonNull Matrix4dc world) {
        final Vector3d x = new Vector3d(world.m00(), world.m01(), world.m02());
        final Vector3d y = new Vector3d(world.m10(), world.m11(), world.m12());
        final Vector3d z = new Vector3d(world.m20(), world.m21(), world.m22());
        final double sx = x.length();
        final double sy = y.length();
        final double sz = z.length();
        final double smallest = Math.min(sx, Math.min(sy, sz));
        final double largest = Math.max(sx, Math.max(sy, sz));
        if (smallest <= 0 || largest / smallest - 1 > UNIFORM_TOLERANCE) {
            return null;
        }
        x.div(sx);
        y.div(sy);
        z.div(sz);
        if (Math.abs(x.dot(y)) > SQUARE_TOLERANCE
                || Math.abs(y.dot(z)) > SQUARE_TOLERANCE
                || Math.abs(z.dot(x)) > SQUARE_TOLERANCE
                || x.cross(y, new Vector3d()).dot(z) < 0) {
            // Sheared, or mirrored, which a rotation can't hold
            return null;
        }
        final Quaterniond rotation =
                new Matrix3d(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z)
                        .getNormalizedRotation(new Quaterniond());
        return new Transform(
                node,
                world.getTranslation(new Vector3d()),
                new Quaternionf(
                        (float) rotation.x,
                        (float) rotation.y,
                        (float) rotation.z,
                        (float) rotation.w),
                (float) ((sx + sy + sz) / 3));
    }

    /**
     * Copy a mesh with its vertices moved into world space, relative to an origin. Normals use the
     * inverse transpose, and a mirroring transform flips the triangles so they still face out.
     *
     * @param source The mesh.
     * @param world The transform.
     * @param origin Where positions are measured from.
     * @return The baked copy.
     */
    static MeshData bakeMesh(
            @NonNull MeshData source, @NonNull Matrix4dc world, @NonNull Vector3d origin) {
        final Matrix4d relative = new Matrix4d().translation(origin.negate(new Vector3d()));
        relative.mul(world);
        final Matrix3d linear = new Matrix3d();
        relative.get3x3(linear);
        final Matrix3d normals = new Matrix3d(linear).invert().transpose();
        final float[] in = source.getVertexData();
        final float[] out = new float[in.length];
        final Vector3d point = new Vector3d();
        final Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        final Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        final Vector3f moved = new Vector3f();
        for (int at = 0; at + STRIDE <= in.length; at += STRIDE) {
            System.arraycopy(in, at, out, at, STRIDE);
            relative.transformPosition(point.set(in[at], in[at + 1], in[at + 2]));
            moved.set((float) point.x, (float) point.y, (float) point.z);
            out[at] = moved.x;
            out[at + 1] = moved.y;
            out[at + 2] = moved.z;
            min.min(moved);
            max.max(moved);
            direction(normals, in, out, at + 3);
            direction(linear, in, out, at + 6);
            direction(linear, in, out, at + 9);
        }
        int[] indices = source.getIndices().clone();
        if (linear.determinant() < 0) {
            for (int i = 0; i + 2 < indices.length; i += 3) {
                final int swap = indices[i + 1];
                indices[i + 1] = indices[i + 2];
                indices[i + 2] = swap;
            }
        }
        if (in.length == 0) {
            min.zero();
            max.zero();
        }
        return new MeshData(min, max, source.getVertexCount(), out, indices, 0, new byte[0]);
    }

    /**
     * Transform one direction in vertex data and normalize it.
     *
     * @param matrix The transform.
     * @param in The source vertices.
     * @param out The baked vertices.
     * @param at Where the direction's x is.
     */
    private static void direction(Matrix3d matrix, float[] in, float[] out, int at) {
        final Vector3d v = matrix.transform(new Vector3d(in[at], in[at + 1], in[at + 2]));
        final double length = v.length();
        if (length > 0) {
            v.div(length);
        }
        out[at] = (float) v.x;
        out[at + 1] = (float) v.y;
        out[at + 2] = (float) v.z;
    }

    /**
     * Make a material from what the file says, before its textures arrive.
     *
     * @param source The imported material.
     * @return The material.
     */
    private static Material material(ImportedMaterial source) {
        Material material = new Material();
        material.getBaseColor().set(source.color());
        material.setRoughness(Float.isNaN(source.roughness()) ? 0.5f : source.roughness());
        material.setMetallic(Float.isNaN(source.metallic()) ? 0 : source.metallic());
        material.getEmissive().set(source.emissive());
        if (source.textures().containsKey(TextureSlot.EMISSIVE)
                && source.emissive().lengthSquared() == 0) {
            // A texture with no color to scale it by glows as the texture shows
            material.getEmissive().set(1);
        }
        if (source.color().w < 1) {
            material.setTransparency(Material.Transparency.TRANSLUCENT);
        }
        return material;
    }

    /**
     * How many models there are.
     *
     * @return The count.
     */
    public int modelCount() {
        return models.size();
    }

    /**
     * How many instances there are.
     *
     * @return The count.
     */
    public int instanceCount() {
        int total = 0;
        for (Placed placed : models) {
            total += placed.transforms().size();
        }
        return total;
    }

    /**
     * Put the scene in the world: materials into the cache, meshes registered and models added, and
     * an instance for every node. Render thread only.
     */
    public void place() {
        final Scene scene = GraphicsManager.getScene();
        final MaterialCache cache = scene.getMaterialCache();
        for (Material material : materials) {
            cache.addMaterial(material);
        }
        cache.setDirty(true);
        for (Placed placed : models) {
            for (MeshData mesh : placed.model().getMeshDataList()) {
                graphics.meshes().register(mesh);
            }
            scene.addModel(placed.model());
            for (Transform transform : placed.transforms()) {
                final InstanceHandle handle =
                        graphics.instances()
                                .place(
                                        placed.model(),
                                        transform.position(),
                                        transform.rotation(),
                                        transform.scale());
                instances.computeIfAbsent(transform.node(), node -> new ArrayList<>()).add(handle);
            }
        }
    }

    /**
     * Load every texture the materials name, a bounded amount at a time, and hand each to its
     * materials on the render thread as it is queued. On a background thread.
     *
     * @param post Runs a change on the render thread.
     * @param cancelled Whether to stop, checked between textures.
     * @param progress Told how far along loading is.
     */
    public void loadTextures(
            @NonNull Consumer<Runnable> post,
            @NonNull BooleanSupplier cancelled,
            @NonNull Consumer<String> progress) {
        record Use(int material, TextureSlot slot) {}
        final Map<Path, List<Use>> uses = new HashMap<>();
        final Map<Path, ColorSpace> spaces = new HashMap<>();
        final List<ImportedMaterial> sources = imported.materials();
        for (int m = 0; m < sources.size(); ++m) {
            for (Map.Entry<TextureSlot, TextureRef> entry : sources.get(m).textures().entrySet()) {
                final TextureSlot slot = entry.getKey();
                final Path file = entry.getValue().found();
                if (file == null || !applied(slot)) {
                    continue;
                }
                uses.computeIfAbsent(file, f -> new ArrayList<>()).add(new Use(m, slot));
                spaces.put(file, colorSpace(slot));
            }
        }
        texturesToLoad = uses.size();
        final List<TextureHandle> pending = new ArrayList<>();
        final Map<TextureHandle, Long> pendingBytes = new HashMap<>();
        for (Map.Entry<Path, List<Use>> entry : uses.entrySet()) {
            if (cancelled.getAsBoolean()) {
                return;
            }
            waitForUploads(pending, pendingBytes, cancelled);
            final Path file = entry.getKey();
            final TextureHandle texture;
            final long bytes;
            try {
                if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dds")) {
                    final DdsFile dds = DdsFile.read(file);
                    bytes = dds.getData().remaining();
                    texture = graphics.textures().load(dds, spaces.get(file));
                } else {
                    texture = graphics.textures().load(file.toString(), spaces.get(file));
                    bytes = (long) texture.width() * texture.height() * 4;
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Can't load texture {}", file, e);
                continue;
            }
            textures.put(file, texture);
            if (removed) {
                // Taken out while this one loaded, so nothing will release it otherwise
                textures.remove(file);
                graphics.textures().release(texture);
                return;
            }
            pending.add(texture);
            pendingBytes.put(texture, bytes);
            final List<Use> users = entry.getValue();
            post.accept(
                    () -> {
                        for (Use use : users) {
                            apply(materials.get(use.material()), use.slot(), texture);
                        }
                    });
            ++texturesLoaded;
            progress.accept("Loading textures, " + texturesLoaded + " of " + texturesToLoad);
        }
    }

    /**
     * Whether a slot's texture is used: base color, normal, packed specular (occlusion, roughness
     * and metalness, as metal-rough scenes like Bistro pack them) and emissive. Lone roughness,
     * metalness, occlusion and opacity maps aren't yet.
     *
     * @param slot The slot.
     * @return True if it is loaded.
     */
    static boolean applied(@NonNull TextureSlot slot) {
        return switch (slot) {
            case BASE_COLOR, NORMAL, SPECULAR, EMISSIVE -> true;
            default -> false;
        };
    }

    /**
     * Give a material a loaded texture.
     *
     * @param material The material.
     * @param slot What the texture is for.
     * @param texture The texture.
     */
    private static void apply(Material material, TextureSlot slot, TextureHandle texture) {
        switch (slot) {
            case NORMAL -> material.setNormalMap(texture);
                // Specular maps' red isn't reliably occlusion; Bistro's are 0 there
            case SPECULAR -> material.setOcclusionRoughnessMetal(texture);
            case EMISSIVE -> material.setEmissiveMap(texture);
            default -> {
                material.setTexture(texture);
                // The texture's color stands in for the material's
                material.getBaseColor().set(1, 1, 1, material.getBaseColor().w);
            }
        }
    }

    /**
     * The color space a slot's textures load in.
     *
     * @param slot The slot.
     * @return sRGB for colors, linear for data.
     */
    static ColorSpace colorSpace(@NonNull TextureSlot slot) {
        return slot == TextureSlot.BASE_COLOR || slot == TextureSlot.EMISSIVE
                ? ColorSpace.SRGB
                : ColorSpace.LINEAR;
    }

    /**
     * Wait while too much texture data is staged but not yet uploaded.
     *
     * @param pending Textures queued, in order.
     * @param bytes How much data each staged.
     * @param cancelled Whether to stop waiting.
     */
    private void waitForUploads(
            List<TextureHandle> pending,
            Map<TextureHandle, Long> bytes,
            BooleanSupplier cancelled) {
        while (!cancelled.getAsBoolean()) {
            pending.removeIf(
                    texture -> {
                        final boolean done =
                                graphics.textures().isResident(texture)
                                        || !graphics.textures().isValid(texture);
                        if (done) {
                            bytes.remove(texture);
                        }
                        return done;
                    });
            long staged = 0;
            for (long size : bytes.values()) {
                staged += size;
            }
            if (staged < PENDING_TEXTURE_BYTES) {
                return;
            }
            try {
                Thread.sleep(UPLOAD_WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * The instances standing for a node.
     *
     * @param node The node.
     * @return Its instances, empty if it draws nothing.
     */
    public List<InstanceHandle> instancesOf(@NonNull Node node) {
        return instances.getOrDefault(node, List.of());
    }

    /**
     * Whether a node's meshes were baked into world space instead of instanced.
     *
     * @param node The node.
     * @return True if baked.
     */
    public boolean isBaked(@NonNull Node node) {
        return node.meshes().length > 0 && instanceTransform(node, node.world()) == null;
    }

    /**
     * Take the scene out of the world: models with their meshes and instances, materials, and
     * textures. Render thread only.
     */
    public void remove() {
        removed = true;
        final Scene scene = GraphicsManager.getScene();
        for (Placed placed : models) {
            scene.removeModel(placed.model().getId());
        }
        instances.clear();
        final MaterialCache cache = scene.getMaterialCache();
        for (Material material : materials) {
            cache.removeMaterial(material);
        }
        cache.setDirty(true);
        for (TextureHandle texture : textures.values()) {
            graphics.textures().release(texture);
        }
        textures.clear();
    }
}
