package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.world.gen.debug.Colormaps;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which bakeable mesh each block is drawn with in the 3D preview.
 *
 * <p>Block definitions don't name a mesh yet. When they do, with a mesh exported from the asset
 * editor, {@link #meshFor} loads that mesh instead, and nothing else in the preview changes. Until
 * then, and for any block whose mesh is missing, a block is a unit cube in its slice view color, so
 * 2D and 3D agree. Fluids are translucent.
 *
 * <p>Render thread only, except {@link #removeMaterials}.
 */
final class BlockMeshes implements AutoCloseable {

    /** The unit box's minimum corner. */
    private static final Vector3f BOX_MIN = new Vector3f(0, 0, 0);

    /** The unit box's maximum corner. */
    private static final Vector3f BOX_MAX = new Vector3f(1, 1, 1);

    /** How much of what is behind a fluid it hides. */
    private static final float FLUID_OPACITY = 0.6f;

    /** How rough placeholder blocks are. */
    private static final float ROUGHNESS = 0.85f;

    /** How rough fluids are: smooth, for a highlight. */
    private static final float FLUID_ROUGHNESS = 0.1f;

    /** Bits to shift for the red channel of a packed color. */
    private static final int RED_SHIFT = 16;

    /** Bits to shift for the green channel of a packed color. */
    private static final int GREEN_SHIFT = 8;

    /** The mask of one channel. */
    private static final int CHANNEL_MASK = 0xff;

    /** One channel's largest value, as a float. */
    private static final float CHANNEL_MAX = 255f;

    /**
     * A block's mesh, and what the baker knows about it.
     *
     * @param mesh The registered mesh.
     * @param source Its faces, for working out which neighbors hide them.
     */
    record Entry(@NonNull MeshHandle mesh, @NonNull BakeSource source) {}

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** Meshes by block name. */
    private final Map<String, Entry> entries = new HashMap<>();

    /** Materials added to the scene, to remove again. */
    private final List<Material> materials = new CopyOnWriteArrayList<>();

    /**
     * Set up the lookup.
     *
     * @param graphics The plugin's graphics context.
     */
    BlockMeshes(@NonNull GraphicsContext graphics) {
        this.graphics = graphics;
    }

    /**
     * Make sure every block has a mesh.
     *
     * @param blocks Block names.
     * @param fluids The names of blocks that are fluids.
     * @return A snapshot of the meshes for those blocks, by name.
     */
    Map<String, Entry> meshesFor(@NonNull Collection<String> blocks, @NonNull Set<String> fluids) {
        Map<String, Entry> snapshot = new HashMap<>();
        boolean added = false;
        for (String block : blocks) {
            Entry entry = entries.get(block);
            if (entry == null) {
                entry = meshFor(block, fluids.contains(block));
                entries.put(block, entry);
                added = true;
            }
            snapshot.put(block, entry);
        }
        if (added) {
            GraphicsManager.getScene().getMaterialCache().setDirty(true);
        }
        return snapshot;
    }

    /**
     * Register a block's mesh. This is the seam where a definition's own mesh will be loaded.
     *
     * @param block The block name.
     * @param fluid Whether it is a fluid.
     * @return Its entry.
     */
    private Entry meshFor(@NonNull String block, boolean fluid) {
        final MeshData mesh =
                com.ikalagaming.graphics.benchmark.BlockMeshes.box(
                        new float[] {BOX_MIN.x, BOX_MIN.y, BOX_MIN.z},
                        new float[] {BOX_MAX.x, BOX_MAX.y, BOX_MAX.z},
                        placeholderMaterial(block, fluid));
        final Scene scene = GraphicsManager.getScene();
        scene.getMaterialCache().addMaterial(mesh.getMaterial());
        materials.add(mesh.getMaterial());
        final MeshHandle handle = graphics.meshes().registerBakeable(mesh, BOX_MIN, BOX_MAX);
        return new Entry(handle, graphics.meshes().faceInfo(handle));
    }

    /**
     * A placeholder block's material, in its slice view color.
     *
     * @param block The block name.
     * @param fluid Whether it is a fluid, drawn see-through.
     * @return The material.
     */
    private static Material placeholderMaterial(@NonNull String block, boolean fluid) {
        final int rgb = Colormaps.categorical(block);
        Material material = new Material();
        material.getBaseColor()
                .set(
                        ((rgb >> RED_SHIFT) & CHANNEL_MASK) / CHANNEL_MAX,
                        ((rgb >> GREEN_SHIFT) & CHANNEL_MASK) / CHANNEL_MAX,
                        (rgb & CHANNEL_MASK) / CHANNEL_MAX,
                        fluid ? FLUID_OPACITY : 1);
        material.setRoughness(fluid ? FLUID_ROUGHNESS : ROUGHNESS);
        material.setTransparency(
                fluid ? Material.Transparency.TRANSLUCENT : Material.Transparency.OPAQUE);
        return material;
    }

    /**
     * Remove every material from the scene. Safe from any thread, since the material cache locks.
     */
    void removeMaterials() {
        final Scene scene = GraphicsManager.getScene();
        if (scene != null) {
            materials.forEach(scene.getMaterialCache()::removeMaterial);
            scene.getMaterialCache().setDirty(true);
        }
        materials.clear();
    }

    /** Release every mesh and remove every material. Render thread. */
    @Override
    public void close() {
        if (!graphics.isClosed()) {
            entries.values().forEach(entry -> graphics.meshes().release(entry.mesh()));
        }
        entries.clear();
        removeMaterials();
    }
}
