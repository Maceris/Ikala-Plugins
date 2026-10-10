package com.ikalagaming.graphics;

import com.ikalagaming.graphics.bake.BakeSource;
import com.ikalagaming.graphics.bake.CubeRotations;
import com.ikalagaming.graphics.bake.Face;
import com.ikalagaming.graphics.bake.Faces;
import com.ikalagaming.graphics.bake.SectionBaker;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.vulkan.SectionManager;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nullable;

/**
 * Bakes sections for one plugin: many placements of meshes registered for baking, merged into a few
 * compact meshes with the faces their neighbors hide left out. This is how terrain is drawn, since
 * placing every block as its own instance doesn't scale. Graphics knows nothing about the grid: the
 * plugin that does works out which faces each placement's neighbors hide, with {@link Faces}, and
 * hands everything over in flat arrays.
 *
 * <pre>{@code
 * SectionHandle section = context.sections()
 *         .compose(origin)
 *         .placements(meshes, packedPositions, rotations, faceMasks, userData)
 *         .submit();
 * }</pre>
 *
 * <p>Baking happens on worker threads. A section isn't drawn until its first bake is uploaded, and
 * a rebaked section keeps drawing its old bake until the new one is ready, so edits never leave a
 * hole. Everything here is safe from any thread. Sections are removed when the plugin unloads.
 *
 * @see GraphicsContext#sections()
 */
public final class Sections {

    /**
     * The size of a section along each axis, in meters, which is also blocks at one meter a block.
     * The view distance, light ranges and shadow reach are counted in sections, so changing this
     * scales them too. Baked vertex positions must still reach past a section, see {@link
     * com.ikalagaming.graphics.bake.BakedVertex#STEPS_PER_UNIT}.
     */
    public static final int SECTION_SIZE = 16;

    /** The context these sections belong to. */
    private final GraphicsContext context;

    /**
     * Create the sections API for a context.
     *
     * @param context The owning context.
     */
    Sections(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /** A section to bake, filled in with {@link #placements} before it is submitted. */
    public final class Request {
        /** Where the section's cell offsets start from, in the world. */
        private final Vector3d origin;

        /** The resolved bake, once placements are given. */
        private SectionManager.Request request;

        /**
         * Start a request.
         *
         * @param origin Where the section's cell offsets start from.
         */
        private Request(@NonNull Vector3dc origin) {
            this.origin = new Vector3d(origin);
        }

        /**
         * Say what the section holds, one entry per placement in every array. The arrays are copied
         * before this returns.
         *
         * @param meshes The mesh each placement puts down, each registered with {@link
         *     Meshes#registerBakeable}.
         * @param packedPositions The cell each fills, from {@link SectionBaker#pack(int, int,
         *     int)}: whole boxes from the origin along each axis.
         * @param rotations Each placement's rotation about its box's center, one of the {@value
         *     CubeRotations#COUNT} {@link CubeRotations}.
         * @param faceMasks Which faces of each placement its neighbors hide, after rotation, one
         *     bit per {@link Face#bit()}, as {@link Faces#hiddenFaces} works out.
         * @param userData What each placement writes into its vertices for shaders, like a tint,
         *     not interpreted by graphics.
         * @return This request.
         * @throws IllegalArgumentException If the arrays differ in length, or a mesh wasn't
         *     registered for baking or was released.
         */
        public Request placements(
                @NonNull MeshHandle @NonNull [] meshes,
                int @NonNull [] packedPositions,
                byte @NonNull [] rotations,
                byte @NonNull [] faceMasks,
                short @NonNull [] userData) {
            final int count = meshes.length;
            if (packedPositions.length != count
                    || rotations.length != count
                    || faceMasks.length != count
                    || userData.length != count) {
                throw new IllegalArgumentException(
                        "Every placement array must be as long as the meshes");
            }
            final VulkanInstance renderer = renderer();
            final MaterialCache materials = GraphicsManager.getScene().getMaterialCache();
            final BakeSource[] sources = new BakeSource[count];
            final int[] materialIndices = new int[count];
            for (int i = 0; i < count; ++i) {
                sources[i] = renderer.getState().bakeSources.get(meshes[i]);
                if (sources[i] == null) {
                    throw new IllegalArgumentException(
                            "Placement "
                                    + i
                                    + "'s mesh wasn't registered for baking, or was released");
                }
                materialIndices[i] = materials.getMaterialIndex(sources[i].getMaterial());
            }
            request =
                    new SectionManager.Request(
                            origin,
                            sources,
                            materialIndices,
                            packedPositions.clone(),
                            rotations.clone(),
                            faceMasks.clone(),
                            userData.clone());
            return this;
        }

        /**
         * Bake a new section from this request.
         *
         * @return The section's handle. It is drawn once its bake is uploaded.
         * @throws IllegalStateException If no placements were given, the plugin was unloaded, or
         *     the renderer isn't running.
         */
        public SectionHandle submit() {
            context.checkOpen();
            return new SectionHandle(manager().submit(context.getOwnerKey(), resolved()));
        }

        /**
         * The resolved bake.
         *
         * @return The bake.
         * @throws IllegalStateException If no placements were given.
         */
        private SectionManager.Request resolved() {
            if (request == null) {
                throw new IllegalStateException("A section needs its placements before baking");
            }
            return request;
        }
    }

    /**
     * Start a section. Placements are at whole numbers of boxes from the origin, and their meshes
     * must stay within {@value com.ikalagaming.graphics.bake.BakedVertex#MAX_DISTANCE} units of it.
     *
     * @param origin Where the section's cell offsets start from, in the world.
     * @return A request to fill in with {@link Request#placements} and submit.
     */
    public Request compose(@NonNull Vector3dc origin) {
        context.checkOpen();
        return new Request(origin);
    }

    /**
     * Bake a section again, as after an edit. It keeps drawing its current bake until the new one
     * is uploaded. Nothing happens if the handle is stale or another plugin's.
     *
     * @param section The section.
     * @param request What it holds now, with its placements given. Its origin replaces the old one.
     * @throws IllegalStateException If no placements were given, the plugin was unloaded, or the
     *     renderer isn't running.
     */
    public void rebake(@Nullable SectionHandle section, @NonNull Request request) {
        context.checkOpen();
        if (section != null) {
            manager().rebake(section.id(), context.getOwnerKey(), request.resolved());
        }
    }

    /**
     * Remove a section. Nothing happens if the handle is null, stale, or another plugin's.
     *
     * @param section The section.
     */
    public void remove(@Nullable SectionHandle section) {
        SectionManager manager = managerOrNull();
        if (section != null && manager != null) {
            manager.remove(section.id(), context.getOwnerKey());
        }
    }

    /**
     * Whether a section is drawn.
     *
     * @param section The section.
     * @return True once its first bake is uploaded, until it is removed.
     */
    public boolean isResident(@Nullable SectionHandle section) {
        SectionManager manager = managerOrNull();
        return section != null && manager != null && manager.isResident(section.id());
    }

    /**
     * Whether a handle still refers to a section.
     *
     * @param section The section.
     * @return False if null or removed.
     */
    public boolean isValid(@Nullable SectionHandle section) {
        SectionManager manager = managerOrNull();
        return section != null && manager != null && manager.isValid(section.id());
    }

    /**
     * The renderer's section manager, if the renderer is running.
     *
     * @return The manager, or null.
     */
    @Nullable private static SectionManager managerOrNull() {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        return renderer == null ? null : renderer.getState().sections;
    }

    /**
     * The renderer's section manager.
     *
     * @return The manager.
     * @throws IllegalStateException If the renderer isn't running.
     */
    private static SectionManager manager() {
        return renderer().getState().sections;
    }

    /**
     * Fetch the running renderer.
     *
     * @return The renderer.
     * @throws IllegalStateException If the renderer isn't running.
     */
    private static VulkanInstance renderer() {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().sections == null) {
            throw new IllegalStateException("The renderer is not running");
        }
        return renderer;
    }
}
