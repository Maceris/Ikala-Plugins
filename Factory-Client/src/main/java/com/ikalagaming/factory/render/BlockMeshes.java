package com.ikalagaming.factory.render;

import com.ikalagaming.factory.render.looks.BlockLook;
import com.ikalagaming.factory.render.looks.BlockLooks;
import com.ikalagaming.factory.world.gen.debug.Colormaps;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which bakeable mesh each block is drawn with, in the game and the Worldgen Lab's 3D preview.
 *
 * <p>A block with a look file (see {@link BlockLooks}) is drawn as its look says: its model file's
 * mesh with its textures and surface values. Any other is a unit cube in its slice view color, so
 * 2D and 3D agree and new blocks are never invisible; fluids among those are translucent. Fluids,
 * and blocks whose model file is missing, are drawn as a cube made in code.
 *
 * <p>Render thread only, except {@link #removeMaterials}.
 */
public final class BlockMeshes implements AutoCloseable {

    /** The unit box's minimum corner. */
    private static final Vector3f BOX_MIN = new Vector3f(0, 0, 0);

    /** The unit box's maximum corner. */
    private static final Vector3f BOX_MAX = new Vector3f(1, 1, 1);

    /** How much of what is behind a fluid it hides. */
    private static final float FLUID_OPACITY = 0.6f;

    /** How rough placeholder blocks are. */
    private static final float ROUGHNESS = 0.85f;

    /** The gray of faces whose texture is missing. */
    private static final float MISSING_GRAY = 0.8f;

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
     * @param material Its material, which is in the scene's material cache.
     */
    public record Entry(
            @NonNull MeshHandle mesh, @NonNull BakeSource source, @NonNull Material material) {}

    /** The plugin's graphics context. */
    private final GraphicsContext graphics;

    /** Meshes by block name. */
    private final Map<String, Entry> entries = new HashMap<>();

    /** Materials added to the scene, to remove again. */
    private final List<Material> materials = new CopyOnWriteArrayList<>();

    /** Blocks' looks and their textures. */
    private final BlockLooks looks;

    /**
     * Set up the lookup.
     *
     * @param graphics The plugin's graphics context.
     */
    public BlockMeshes(@NonNull GraphicsContext graphics) {
        this(graphics, BlockLooks.defaultRoots());
    }

    /**
     * Set up the lookup, with looks from chosen folders.
     *
     * @param graphics The plugin's graphics context.
     * @param lookRoots The folders to find looks in, in order; see {@link BlockLooks}.
     */
    public BlockMeshes(@NonNull GraphicsContext graphics, @NonNull List<Path> lookRoots) {
        this.graphics = graphics;
        looks = new BlockLooks(graphics, lookRoots);
    }

    /**
     * Whether a block is a fluid by its look, drawn by the water system rather than as a block.
     *
     * @param block The block's name.
     * @return True if its look's model is {@value BlockLook#FLUID}.
     */
    public boolean isFluid(@NonNull String block) {
        return looks.look(block).map(BlockLook::isFluid).orElse(false);
    }

    /**
     * Make sure every block has a mesh.
     *
     * @param blocks Block names.
     * @param fluids The names of blocks that are fluids.
     * @return A snapshot of the meshes for those blocks, by name.
     */
    public Map<String, Entry> meshesFor(
            @NonNull Collection<String> blocks, @NonNull Set<String> fluids) {
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
        final Scene scene = GraphicsManager.getScene();
        final BlockLook look = looks.look(block).orElse(null);
        final Material material =
                look == null ? placeholderMaterial(block, fluid) : lookMaterial(look);
        final MeshData shape = look == null || look.isFluid() ? null : looks.model(look.model());
        final MeshData mesh;
        if (shape == null) {
            mesh =
                    com.ikalagaming.graphics.benchmark.BlockMeshes.box(
                            new float[] {BOX_MIN.x, BOX_MIN.y, BOX_MIN.z},
                            new float[] {BOX_MAX.x, BOX_MAX.y, BOX_MAX.z},
                            material);
        } else {
            // Blocks share a model's arrays, each with its own material
            mesh =
                    new MeshData(
                            shape.getAabbMin(),
                            shape.getAabbMax(),
                            shape.getVertexCount(),
                            shape.getVertexData(),
                            shape.getIndices(),
                            0,
                            new byte[0]);
            mesh.setMaterial(material);
        }
        for (Material each : faceMaterials(material)) {
            scene.getMaterialCache().addMaterial(each);
            materials.add(each);
        }
        final MeshHandle handle = graphics.meshes().registerBakeable(mesh, BOX_MIN, BOX_MAX);
        return new Entry(handle, graphics.meshes().faceInfo(handle), material);
    }

    /**
     * A material and its top and bottom variants, if it has them.
     *
     * @param material The material.
     * @return Each, once.
     */
    private static List<Material> faceMaterials(@NonNull Material material) {
        final List<Material> out = new ArrayList<>();
        out.add(material);
        if (material.getTop() != null) {
            out.add(material.getTop());
        }
        if (material.getBottom() != null) {
            out.add(material.getBottom());
        }
        return out;
    }

    /**
     * The material for a look: its sides', with top and bottom variants where their textures
     * differ.
     *
     * @param look The look.
     * @return The material.
     */
    private Material lookMaterial(@NonNull BlockLook look) {
        final Material side = surface(look, look.side());
        if (!Objects.equals(look.top(), look.side())) {
            side.setTop(surface(look, look.top()));
        }
        if (!Objects.equals(look.bottom(), look.side())) {
            side.setBottom(surface(look, look.bottom()));
        }
        return side;
    }

    /**
     * One face's material for a look.
     *
     * @param look The look, for its surface values.
     * @param texture The face's texture name, or null for none.
     * @return The material: white under its texture, or a plain light gray without one, or if the
     *     texture is missing.
     */
    private Material surface(@NonNull BlockLook look, String texture) {
        final Material material = new Material();
        final TextureHandle handle = texture == null ? null : looks.texture(texture);
        if (handle != null) {
            material.setTexture(handle);
            material.getBaseColor().set(Material.DEFAULT_COLOR);
        } else {
            material.getBaseColor().set(MISSING_GRAY, MISSING_GRAY, MISSING_GRAY, 1);
        }
        material.setRoughness(look.roughness());
        material.setMetallic(look.metallic());
        if (look.emission() > 0) {
            // Glows in its own color: the texture's, if it has one
            material.setEmissiveMap(handle);
            material.getEmissive().set(look.emission());
        }
        material.setTransparency(
                switch (look.transparency()) {
                    case OPAQUE -> Material.Transparency.OPAQUE;
                    case CUTOUT -> Material.Transparency.CUTOUT;
                    case TRANSLUCENT -> Material.Transparency.TRANSLUCENT;
                });
        return material;
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
    public void removeMaterials() {
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
        looks.close();
        entries.clear();
        removeMaterials();
    }
}
