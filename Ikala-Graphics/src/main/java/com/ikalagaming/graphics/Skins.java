package com.ikalagaming.graphics;

import com.ikalagaming.graphics.vulkan.SkinnedRegistry;
import com.ikalagaming.graphics.vulkan.SkinnedRenderer;
import com.ikalagaming.graphics.vulkan.VulkanInstance;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

import java.util.List;

/**
 * Skinned meshes and the posed instances that draw them, for one plugin, like characters and
 * animals. Graphics only skins and draws them: the owner works out each instance's pose and sends
 * its palette of bone matrices, as often as it changes, usually every frame.
 *
 * <p>A skinned instance draws a list of {@linkplain SkinnedPart parts} with one palette, so a body
 * can be built from swappable meshes that share a skeleton. Its vertices can also be tagged with
 * mask regions, 0 to 63, which the instance can hide, so clothing can hide the skin under it.
 *
 * <p>Everything registered or placed here is owned by the plugin's {@link GraphicsContext}, and
 * removed when the plugin unloads. Everything is safe from any thread, and changes show from the
 * next frame.
 *
 * <pre>
 * SkinnedMeshHandle body = graphics.skins().registerMesh(vertices, joints, weights, null,
 *         indices, min, max);
 * SkinnedInstanceHandle hero = graphics.skins().place(boneCount, position, rotation, 1);
 * graphics.skins().setParts(hero, List.of(new SkinnedPart(body, skin)));
 * // Each frame:
 * graphics.skins().setPose(hero, palette, 0, boundsMin, boundsMax);
 * </pre>
 *
 * @see GraphicsContext#skins()
 */
@Slf4j
public final class Skins {

    /** The context these belong to. */
    private final GraphicsContext context;

    /**
     * Create the skins API for a context.
     *
     * @param context The owning context.
     */
    Skins(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Register a skinned mesh. The data is checked and copied before this returns, and the mesh is
     * drawn once uploaded.
     *
     * @param vertices The vertices in the scene's vertex format, 14 floats each: position, normal,
     *     tangent, bitangent and texture coordinates, at rest.
     * @param joints Four bone indices per vertex, into an instance's palette.
     * @param weights Four weights per vertex, scaled to add up to 1.
     * @param regions One mask region per vertex, 0 to 63, or 255 for never hidden; null for none.
     *     All three vertices of a triangle must share a region.
     * @param indices The triangle indices, three per triangle.
     * @param aabbMin The minimum corner of the mesh's bounds at rest.
     * @param aabbMax The maximum corner of the mesh's bounds at rest.
     * @return The handle, which becomes resident once uploaded.
     * @throws IllegalArgumentException If the data is malformed.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public SkinnedMeshHandle registerMesh(
            float @NonNull [] vertices,
            int @NonNull [] joints,
            float @NonNull [] weights,
            int[] regions,
            int @NonNull [] indices,
            @NonNull Vector3fc aabbMin,
            @NonNull Vector3fc aabbMax) {
        context.checkOpen();
        VulkanInstance renderer = renderer();
        return renderer.getState()
                .skins
                .register(
                        renderer.getState(),
                        context.getOwnerKey(),
                        vertices,
                        joints,
                        weights,
                        regions,
                        indices,
                        aabbMin,
                        aabbMax);
    }

    /**
     * Release a skinned mesh this plugin owns. Instances still listing it stop drawing it. Nothing
     * happens if the handle is null or stale; another plugin's mesh is ignored with a warning.
     *
     * @param mesh The mesh.
     */
    public void releaseMesh(SkinnedMeshHandle mesh) {
        SkinnedRenderer skins = skinsOrNull();
        if (mesh == null || skins == null) {
            return;
        }
        String owner = skins.getArena().getRegistry().ownerOf(mesh.mesh());
        if (owner == null) {
            return;
        }
        if (!owner.equals(context.getOwnerKey())) {
            log.warn(
                    "{} tried to release a skinned mesh owned by {}, ignoring it",
                    context.getOwnerKey(),
                    owner);
            return;
        }
        skins.getArena().release(mesh.mesh());
    }

    /**
     * Whether a skinned mesh has been uploaded, so it is drawn.
     *
     * @param mesh The mesh.
     * @return False if the handle is null, stale or still uploading.
     */
    public boolean isResident(SkinnedMeshHandle mesh) {
        SkinnedRenderer skins = skinsOrNull();
        return mesh != null
                && skins != null
                && skins.getArena().getRegistry().isResident(mesh.mesh());
    }

    /**
     * Place a skinned instance. It draws nothing until it has {@linkplain #setParts parts} and a
     * {@linkplain #setPose pose}.
     *
     * @param boneCount How many bones its palette holds, 1 to {@value SkinnedRegistry#MAX_BONES}.
     * @param position Its world position.
     * @param rotation Its rotation.
     * @param scale Its uniform scale.
     * @return The handle.
     * @throws IllegalArgumentException If the bone count is out of range.
     * @throws IllegalStateException If the plugin was unloaded, or the renderer isn't running.
     */
    public SkinnedInstanceHandle place(
            int boneCount,
            @NonNull Vector3dc position,
            @NonNull Quaternionfc rotation,
            float scale) {
        context.checkOpen();
        return renderer()
                .getState()
                .skins
                .getRegistry()
                .place(context.getOwnerKey(), boneCount, position, rotation, scale);
    }

    /**
     * Change the meshes an instance draws, replacing its old list.
     *
     * @param instance The instance.
     * @param parts The meshes and their materials.
     * @throws IllegalArgumentException If a mesh needs more bones than the instance has.
     */
    public void setParts(SkinnedInstanceHandle instance, @NonNull List<SkinnedPart> parts) {
        SkinnedRegistry registry = registryOrNull();
        if (registry != null && !registry.setParts(owner(), instance, parts)) {
            ignored(registry, instance, "change the parts of");
        }
    }

    /**
     * Pose an instance: its skinning matrices, each a bone's current model space transform times
     * the inverse of its rest transform, and the bounds the pose puts it in, for culling.
     *
     * @param instance The instance.
     * @param palette The matrices, column major, 16 floats per bone. They are copied.
     * @param offset Where the instance's first matrix starts.
     * @param boundsMin The minimum corner of the posed bounds, in model space.
     * @param boundsMax The maximum corner of the posed bounds, in model space.
     * @throws IllegalArgumentException If the palette is too short or the bounds aren't finite.
     */
    public void setPose(
            SkinnedInstanceHandle instance,
            float @NonNull [] palette,
            int offset,
            @NonNull Vector3fc boundsMin,
            @NonNull Vector3fc boundsMax) {
        SkinnedRegistry registry = registryOrNull();
        if (registry != null
                && !registry.setPose(owner(), instance, palette, offset, boundsMin, boundsMax)) {
            ignored(registry, instance, "pose");
        }
    }

    /**
     * Change some of an instance's transform.
     *
     * @param instance The instance.
     * @param position The new world position, or null to keep it.
     * @param rotation The new rotation, or null to keep it.
     * @param scale The new uniform scale, or NaN to keep it.
     */
    public void transform(
            SkinnedInstanceHandle instance,
            Vector3dc position,
            Quaternionfc rotation,
            float scale) {
        SkinnedRegistry registry = registryOrNull();
        if (registry != null && !registry.transform(owner(), instance, position, rotation, scale)) {
            ignored(registry, instance, "move");
        }
    }

    /**
     * Change which mask regions of an instance's meshes are hidden.
     *
     * @param instance The instance.
     * @param regions One bit per region, set to hide it.
     */
    public void setHiddenRegions(SkinnedInstanceHandle instance, long regions) {
        SkinnedRegistry registry = registryOrNull();
        if (registry != null && !registry.setHiddenRegions(owner(), instance, regions)) {
            ignored(registry, instance, "hide regions of");
        }
    }

    /**
     * Change whether an instance casts shadows. They do by default.
     *
     * @param instance The instance.
     * @param casts Whether it casts shadows.
     */
    public void setCastsShadows(SkinnedInstanceHandle instance, boolean casts) {
        SkinnedRegistry registry = registryOrNull();
        if (registry != null && !registry.setCastsShadows(owner(), instance, casts)) {
            ignored(registry, instance, "change the shadows of");
        }
    }

    /**
     * Remove an instance this plugin placed. Nothing happens if the handle is null or stale.
     *
     * @param instance The instance.
     */
    public void remove(SkinnedInstanceHandle instance) {
        SkinnedRegistry registry = registryOrNull();
        if (registry != null && !registry.remove(owner(), instance)) {
            ignored(registry, instance, "remove");
        }
    }

    /**
     * Whether a handle still refers to a placed instance.
     *
     * @param instance The handle.
     * @return False if it is null or stale.
     */
    public boolean isValid(SkinnedInstanceHandle instance) {
        SkinnedRegistry registry = registryOrNull();
        return registry != null && registry.isValid(instance);
    }

    /**
     * The key this plugin's instances are owned under.
     *
     * @return The owner key.
     */
    private String owner() {
        return context.getOwnerKey();
    }

    /**
     * Warn when a change to another plugin's instance was ignored. Stale handles are ignored
     * quietly.
     *
     * @param registry The registry.
     * @param instance The handle.
     * @param action What was attempted, for the message.
     */
    private void ignored(
            @NonNull SkinnedRegistry registry, SkinnedInstanceHandle instance, String action) {
        String owner = registry.ownerOf(instance);
        if (owner != null && !owner.equals(owner())) {
            log.warn(
                    "{} tried to {} a skinned instance owned by {}, ignoring it",
                    owner(),
                    action,
                    owner);
        }
    }

    /**
     * The skinned renderer, if the renderer is running.
     *
     * @return The skinned renderer, or null.
     */
    private static SkinnedRenderer skinsOrNull() {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        return renderer == null ? null : renderer.getState().skins;
    }

    /**
     * The instance registry, if the renderer is running.
     *
     * @return The registry, or null.
     */
    private static SkinnedRegistry registryOrNull() {
        SkinnedRenderer skins = skinsOrNull();
        return skins == null ? null : skins.getRegistry();
    }

    /**
     * Fetch the running renderer.
     *
     * @return The renderer.
     * @throws IllegalStateException If the renderer isn't running.
     */
    private static VulkanInstance renderer() {
        VulkanInstance renderer = GraphicsManager.getRenderInstance();
        if (renderer == null || renderer.getState().skins == null) {
            throw new IllegalStateException("The renderer is not running");
        }
        return renderer;
    }
}
