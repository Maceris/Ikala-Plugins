package com.ikalagaming.graphics.benchmark;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.LightHandle;
import com.ikalagaming.graphics.LodHandle;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.SectionHandle;
import com.ikalagaming.graphics.Sections;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.lod.LodStore;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

/**
 * What a {@link GateBenchmark} run puts in the renderer for its world: the block meshes, the
 * sections composed from them, the torches, the textured test meshes, and the live level of detail
 * world drawn from the sections. Sections and torches are made on the builder and the stream's
 * worker as well as the render thread; once the world is released, whatever a worker makes after is
 * removed again at once.
 */
@Slf4j
final class GateWorld {

    /** The color of the torches scattered over the world: a warm white. */
    private static final Vector3f TORCH_COLOR = new Vector3f(1, 0.85f, 0.6f);

    /** How bright each torch is, which with the default range reaches 10 m. */
    private static final float TORCH_INTENSITY = 1;

    /** How high above the ground each torch is, in meters. */
    private static final double TORCH_HEIGHT = 1.5;

    /** How many tree cells out from the origin to look for a hut for the hut torch. */
    private static final int HUT_SEARCH_CELLS = 8;

    /** Whether the torches cast shadows, from the startup property. */
    private static final boolean LIGHT_SHADOWS =
            Boolean.getBoolean(GateBenchmark.PROPERTY + ".lightShadows");

    /**
     * Whether runs place textured test meshes on the terrain (see {@link TestMeshes}), from the
     * startup property.
     */
    private static final boolean TEXTURED_MESHES =
            Boolean.getBoolean(GateBenchmark.PROPERTY + ".textured");

    /** How far apart the textured test meshes stand, in blocks. */
    private static final int TEST_MESH_SPACING = 24;

    /**
     * The block meshes' data, made once and kept, so their materials are added to the cache only
     * once. Removing a material renumbers the ones after it, which would break indices already on
     * the GPU, so they stay. Render thread only.
     */
    @Nullable private static MeshData[] blockMeshes;

    /**
     * A section's placements as composed: the terrain's, then any textured test meshes.
     *
     * @param meshes Each placement's mesh.
     * @param packedPositions Each placement's cell.
     * @param faceMasks The faces each placement's neighbors hide.
     */
    private record Composed(
            MeshHandle @NonNull [] meshes,
            int @NonNull [] packedPositions,
            byte @NonNull [] faceMasks) {
        /**
         * How many placements there are.
         *
         * @return The count.
         */
        int count() {
            return meshes.length;
        }
    }

    /** The context everything is made under. */
    private final GraphicsContext graphics;

    /** The run's options. */
    private final GateOptions options;

    /** The view distance, in sections. */
    private final int radius;

    /** How many torches to scatter over the world. */
    private final int torches;

    /** The block meshes, by block id. */
    private final MeshHandle[] meshes = new MeshHandle[SyntheticTerrain.BLOCK_TYPES];

    /** The textured test mesh, or null if this run has none. */
    @Nullable private MeshHandle testMesh;

    /**
     * Each section's cells with a textured test mesh, packed as {@link SectionBaker#pack} packs
     * them; empty without test meshes. Set before any section is composed, then only read.
     */
    private volatile Map<LodStore.SectionPos, IntArrayList> testCells = Map.of();

    /** Guards {@link #sections}, {@link #sectionAt} and {@link #lights}, which workers add to. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The sections composed. */
    private final Set<SectionHandle> sections = new LinkedHashSet<>();

    /** Each composed section by position, to rebake after a dig. */
    private final Map<LodStore.SectionPos, SectionHandle> sectionAt = new HashMap<>();

    /** The torches. */
    private final List<LightHandle> lights = new ArrayList<>();

    /** The live level of detail world, while there is one. Render thread. */
    @Nullable private LodHandle lodWorld;

    /**
     * Whether the world has been released. Set by the render thread, and read by workers that make
     * sections or lights, which release what they make once it is set.
     */
    private volatile boolean released;

    /**
     * Set up a run's world.
     *
     * @param graphics The context everything is made under.
     * @param options The run's options.
     * @param radius The view distance, in sections.
     * @param torches How many torches to scatter over the world.
     */
    GateWorld(
            @NonNull GraphicsContext graphics,
            @NonNull GateOptions options,
            int radius,
            int torches) {
        this.graphics = graphics;
        this.options = options;
        this.radius = radius;
        this.torches = torches;
    }

    /**
     * Register the block meshes, and the textured test mesh if runs place them, and make the live
     * level of detail world if the run draws with one. Render thread.
     *
     * @param scene The scene, whose material cache takes the materials.
     * @param lod Whether the world's opaque blocks are drawn by live level of detail.
     * @return The block sources, by block id, for laying out the world.
     */
    BakeSource[] register(@NonNull Scene scene, boolean lod) {
        if (blockMeshes == null) {
            final BlockMeshes.Textured blocks = BlockMeshes.textured(graphics.textures());
            blockMeshes = blocks.meshes();
            blocks.materials().forEach(scene.getMaterialCache()::addMaterial);
        }
        final BakeSource[] sources = new BakeSource[blockMeshes.length];
        for (int block = 1; block < blockMeshes.length; ++block) {
            meshes[block] =
                    graphics.meshes()
                            .registerBakeable(
                                    blockMeshes[block], BlockMeshes.BOX_MIN, BlockMeshes.BOX_MAX);
            sources[block] = graphics.meshes().faceInfo(meshes[block]);
        }
        if (TEXTURED_MESHES && testMesh == null) {
            testMesh = registerTestMesh(scene);
        }
        scene.getMaterialCache().setDirty(true);
        if (lod) {
            lodWorld = graphics.lods().fromSections();
            final String pixels = options.get("lodPx");
            if (pixels != null) {
                graphics.lods().setThreshold(lodWorld, Double.parseDouble(pixels));
            }
        }
        return sources;
    }

    /**
     * Compose every section laid out with something in it, then those with only a textured test
     * mesh. On the builder.
     *
     * @param terrain The world.
     * @param layout The sections laid out.
     * @param building Whether the run is still building, asked before each section.
     * @return False if the run stopped building before every section was composed.
     */
    boolean composeAll(
            @NonNull SyntheticTerrain terrain,
            GateScene.@NonNull Layout layout,
            @NonNull BooleanSupplier building) {
        if (testMesh != null) {
            testCells = testCells(terrain);
        }
        final Set<LodStore.SectionPos> composed = new HashSet<>();
        for (GateScene.Section section : layout.sections()) {
            if (!building.getAsBoolean()) {
                return false;
            }
            final LodStore.SectionPos position =
                    new LodStore.SectionPos(section.x(), section.y(), section.z());
            compose(position, section.placements());
            composed.add(position);
        }
        // Test meshes standing in sections with nothing else
        for (LodStore.SectionPos position : testCells.keySet()) {
            if (!composed.contains(position)) {
                compose(
                        position,
                        new SyntheticTerrain.Placements(0, new byte[0], new int[0], new byte[0]));
            }
        }
        return true;
    }

    /**
     * Compose a section for the first time, with any textured test meshes in it, unless it has
     * nothing at all. On any thread.
     *
     * @param position The section.
     * @param placements The terrain's placements in it.
     * @return Its handle, or null if it has nothing or the world was released.
     */
    @Nullable SectionHandle compose(
            LodStore.@NonNull SectionPos position,
            SyntheticTerrain.@NonNull Placements placements) {
        final Composed placed = withTestMeshes(position, placements);
        if (placed.count() == 0) {
            return null;
        }
        final SectionHandle handle = request(position, placed).submit();
        final boolean late;
        lock.lock();
        try {
            // Released meanwhile: the release took what was kept, so this one goes here
            late = released;
            if (!late) {
                sections.add(handle);
                sectionAt.put(position, handle);
            }
        } finally {
            lock.unlock();
        }
        if (late) {
            graphics.sections().remove(handle);
            return null;
        }
        return handle;
    }

    /**
     * Bake a section again from the edited world, or compose it if it had nothing before. Render
     * thread.
     *
     * @param world The world.
     * @param position The section.
     * @return Its handle, or null if it has nothing and never had.
     */
    @Nullable SectionHandle rebake(@NonNull SyntheticTerrain world, LodStore.@NonNull SectionPos position) {
        final Composed placed =
                withTestMeshes(
                        position, world.placements(position.x(), position.y(), position.z()));
        final int count = placed.count();
        final var request = request(position, placed);
        lock.lock();
        try {
            if (released) {
                // Nothing made now would be released
                return null;
            }
            final SectionHandle handle = sectionAt.get(position);
            if (handle != null) {
                graphics.sections().rebake(handle, request);
                return handle;
            }
            if (count == 0) {
                return null;
            }
            final SectionHandle added = request.submit();
            sections.add(added);
            sectionAt.put(position, added);
            return added;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a section is composed.
     *
     * @param position The section.
     * @return True if it is.
     */
    boolean isComposed(LodStore.@NonNull SectionPos position) {
        lock.lock();
        try {
            return sectionAt.containsKey(position);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove the section composed at a position, if there is one. On the stream's worker.
     *
     * @param position The section.
     * @return True if there was one.
     */
    boolean remove(LodStore.@NonNull SectionPos position) {
        final SectionHandle handle;
        lock.lock();
        try {
            handle = sectionAt.remove(position);
            if (handle != null) {
                sections.remove(handle);
            }
        } finally {
            lock.unlock();
        }
        if (handle != null) {
            graphics.sections().remove(handle);
        }
        return handle != null;
    }

    /**
     * How many sections are composed.
     *
     * @return The count.
     */
    int sectionCount() {
        lock.lock();
        try {
            return sections.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * A section's request to compose or bake again.
     *
     * @param position The section.
     * @param placed Its placements.
     * @return The request.
     */
    private Sections.Request request(
            LodStore.@NonNull SectionPos position, @NonNull Composed placed) {
        final int size = SyntheticTerrain.SECTION;
        final int count = placed.count();
        return graphics.sections()
                .compose(
                        new Vector3d(position.x() * size, position.y() * size, position.z() * size))
                .placements(
                        placed.meshes(),
                        placed.packedPositions(),
                        new byte[count],
                        placed.faceMasks(),
                        new short[count]);
    }

    /**
     * A section's placements: the terrain's, then the textured test meshes standing in it.
     *
     * @param position The section.
     * @param placements The terrain's placements in it.
     * @return Everything placed in it.
     */
    private Composed withTestMeshes(
            LodStore.@NonNull SectionPos position,
            SyntheticTerrain.@NonNull Placements placements) {
        final IntArrayList extra = testCells.get(position);
        final int terrain = placements.count();
        final int count = terrain + (extra == null ? 0 : extra.size());
        final MeshHandle[] placed = new MeshHandle[count];
        final int[] packed = Arrays.copyOf(placements.packedPositions(), count);
        final byte[] faceMasks = Arrays.copyOf(placements.faceMasks(), count);
        for (int i = 0; i < terrain; ++i) {
            placed[i] = meshes[placements.blocks()[i]];
        }
        for (int i = terrain; i < count; ++i) {
            placed[i] = testMesh;
            packed[i] = extra.getInt(i - terrain);
        }
        return new Composed(placed, packed, faceMasks);
    }

    /**
     * Load the textured test mesh's texture and material, and register the mesh for baking.
     *
     * @param scene The scene, whose material cache takes the material.
     * @return The mesh.
     */
    private MeshHandle registerTestMesh(@NonNull Scene scene) {
        final byte[] pixels = TestMeshes.checkerPixels();
        final ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
        final TextureHandle texture;
        try {
            buffer.put(pixels).flip();
            texture =
                    graphics.textures()
                            .load(
                                    buffer,
                                    Format.R8G8B8A8_SRGB,
                                    TestMeshes.TEXTURE_SIZE,
                                    TestMeshes.TEXTURE_SIZE);
        } finally {
            MemoryUtil.memFree(buffer);
        }
        final Material material = TestMeshes.material();
        material.setTexture(texture);
        scene.getMaterialCache().addMaterial(material);
        return graphics.meshes()
                .registerBakeable(
                        TestMeshes.torus(material), BlockMeshes.BOX_MIN, BlockMeshes.BOX_MAX);
    }

    /**
     * Where the textured test meshes stand: on top of whatever is highest in their columns, tree
     * tops included, so they show above the canopy, on a grid {@link #TEST_MESH_SPACING} blocks
     * apart, within the run's radius.
     *
     * @param terrain The terrain.
     * @return Each section's cells with a test mesh.
     */
    private Map<LodStore.SectionPos, IntArrayList> testCells(@NonNull SyntheticTerrain terrain) {
        final Map<LodStore.SectionPos, IntArrayList> cells = new HashMap<>();
        final int size = SyntheticTerrain.SECTION;
        final int reach = radius * size;
        int count = 0;
        for (int x = -reach; x <= reach; x += TEST_MESH_SPACING) {
            for (int z = -reach; z <= reach; z += TEST_MESH_SPACING) {
                final int top = terrain.top(x, z);
                if ((long) x * x + (long) z * z > (long) reach * reach || top < terrain.minY()) {
                    continue;
                }
                final int y = top + 1;
                final LodStore.SectionPos position =
                        new LodStore.SectionPos(
                                Math.floorDiv(x, size),
                                Math.floorDiv(y, size),
                                Math.floorDiv(z, size));
                cells.computeIfAbsent(position, key -> new IntArrayList())
                        .add(
                                SectionBaker.pack(
                                        Math.floorMod(x, size),
                                        Math.floorMod(y, size),
                                        Math.floorMod(z, size)));
                ++count;
            }
        }
        log.info("Gate benchmark placed {} textured test meshes", count);
        return cells;
    }

    /**
     * Scatter torches over the ground within the view distance, the same ones every run, after the
     * hut torch if the {@code .hutTorch} option asks for one. On the builder.
     *
     * @param terrain The world.
     * @param building Whether the run is still building, asked before each torch.
     */
    void placeLights(@NonNull SyntheticTerrain terrain, @NonNull BooleanSupplier building) {
        final String hutTorch = options.get("hutTorch");
        if (hutTorch != null && !"false".equalsIgnoreCase(hutTorch)) {
            placeHutTorch(terrain, hutTorch);
        }
        final SplittableRandom random = new SplittableRandom(GateBenchmark.SEED);
        final double reach = (double) radius * SyntheticTerrain.SECTION;
        for (int i = 0; i < torches && building.getAsBoolean(); ++i) {
            // Evenly over the disc, so the far edge isn't sparser than the middle
            final double distance = reach * Math.sqrt(random.nextDouble());
            final double angle = 2 * Math.PI * random.nextDouble();
            final int x = (int) Math.floor(distance * Math.cos(angle));
            final int z = (int) Math.floor(distance * Math.sin(angle));
            int y = terrain.maxY();
            while (y > terrain.minY() && terrain.block(x, y, z) == SyntheticTerrain.AIR) {
                --y;
            }
            final LightHandle light =
                    graphics.lights()
                            .point(
                                    new Vector3d(x + 0.5, y + 1 + TORCH_HEIGHT, z + 0.5),
                                    TORCH_COLOR,
                                    TORCH_INTENSITY);
            if (LIGHT_SHADOWS) {
                graphics.lights().setCastsShadows(light, true);
            }
            if (i == 0) {
                log.info(
                        "Gate benchmark first torch at ({}, {}, {})",
                        x + 0.5,
                        y + 1 + TORCH_HEIGHT,
                        z + 0.5);
            }
            keepLight(light);
        }
    }

    /**
     * Put a torch in the middle of the clear hut nearest a column, to see whether light gets
     * through its walls. On the builder.
     *
     * @param terrain The world.
     * @param where {@code true} for the origin, or the column as {@code x,z}.
     */
    private void placeHutTorch(@NonNull SyntheticTerrain terrain, @NonNull String where) {
        int x = 0;
        int z = 0;
        if (!Boolean.parseBoolean(where)) {
            final String[] parts = where.split(",");
            if (parts.length != 2) {
                log.warn("The benchmark hut torch must be true or x,z, not {}", where);
                return;
            }
            x = Integer.parseInt(parts[0].trim());
            z = Integer.parseInt(parts[1].trim());
        }
        final int[] hut = terrain.hutNear(x, z, HUT_SEARCH_CELLS);
        if (hut == null) {
            log.warn("Gate benchmark found no hut for the hut torch");
            return;
        }
        final Vector3d at = new Vector3d(hut[0] + 0.5, hut[1] + TORCH_HEIGHT, hut[2] + 0.5);
        final LightHandle light = graphics.lights().point(at, TORCH_COLOR, TORCH_INTENSITY);
        log.info("Gate benchmark hut torch at ({}, {}, {})", at.x, at.y, at.z);
        keepLight(light);
    }

    /**
     * Keep a torch to remove with the world, or remove it now if the world was released while it
     * was being placed. On a worker.
     *
     * @param light The torch.
     */
    private void keepLight(@NonNull LightHandle light) {
        final boolean late;
        lock.lock();
        try {
            late = released;
            if (!late) {
                lights.add(light);
            }
        } finally {
            lock.unlock();
        }
        if (late) {
            graphics.lights().remove(light);
        }
    }

    /**
     * How many torches the run placed.
     *
     * @return The count.
     */
    int torchCount() {
        lock.lock();
        try {
            return lights.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * The live level of detail world.
     *
     * @return The world, or null if the run draws without one or it was released.
     */
    @Nullable LodHandle lodWorld() {
        return lodWorld;
    }

    /**
     * Whether the world has been released.
     *
     * @return True once {@link #markReleased} was called.
     */
    boolean isReleased() {
        return released;
    }

    /**
     * Mark the world released, so workers remove whatever they make from now on themselves. The
     * first thing a release does, before stopping anything that makes sections.
     */
    void markReleased() {
        released = true;
    }

    /** Remove every section, torch and mesh made, and the level of detail world. Render thread. */
    void release() {
        lock.lock();
        try {
            for (SectionHandle section : sections) {
                graphics.sections().remove(section);
            }
            sections.clear();
            sectionAt.clear();
            for (LightHandle light : lights) {
                graphics.lights().remove(light);
            }
            lights.clear();
        } finally {
            lock.unlock();
        }
        for (MeshHandle mesh : meshes) {
            graphics.meshes().release(mesh);
        }
        if (lodWorld != null) {
            graphics.lods().remove(lodWorld);
            lodWorld = null;
        }
    }
}
