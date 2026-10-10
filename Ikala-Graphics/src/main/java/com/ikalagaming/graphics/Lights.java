package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.LightRange;
import com.ikalagaming.graphics.scene.lights.LightRegistry;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

import javax.annotation.Nullable;

/**
 * Places point lights and spotlights in the scene for one plugin. Every light placed here is owned
 * by the plugin's {@link GraphicsContext}, and is removed automatically when the plugin unloads if
 * it wasn't removed before then. A plugin can only change or remove its own lights; anything else
 * is ignored with a warning. The sun and the ambient light are scene-wide, set through {@link
 * com.ikalagaming.graphics.scene.lights.SceneLights}.
 *
 * <p>A light reaches a limited distance, its range. Without one given, the range follows the
 * intensity, so a light of intensity 1 reaches 10 m; see {@link LightRange}.
 *
 * <p>Everything here is safe from any thread, and changes show from the next frame.
 *
 * <pre>
 * LightHandle torch = graphics.lights().point(position, warmWhite, 1);
 * graphics.lights().move(torch, 1, 2, 3);
 * graphics.lights().remove(torch);
 * </pre>
 *
 * @see GraphicsContext#lights()
 */
@Slf4j
public final class Lights {

    /** The context these lights belong to. */
    private final GraphicsContext context;

    /**
     * Create the lights API for a context.
     *
     * @param context The owning context.
     */
    Lights(@NonNull GraphicsContext context) {
        this.context = context;
    }

    /**
     * Place a point light that reaches as far as its intensity carries it.
     *
     * @param position The world position.
     * @param color The color.
     * @param intensity The intensity.
     * @return The handle.
     * @throws IllegalStateException If the plugin was unloaded, or graphics isn't running.
     */
    public LightHandle point(
            @NonNull Vector3dc position, @NonNull Vector3fc color, float intensity) {
        return point(position, color, intensity, Float.NaN);
    }

    /**
     * Place a point light with its own range.
     *
     * @param position The world position.
     * @param color The color.
     * @param intensity The intensity.
     * @param range How far it reaches in meters, or NaN to follow the intensity.
     * @return The handle.
     * @throws IllegalStateException If the plugin was unloaded, or graphics isn't running.
     */
    public LightHandle point(
            @NonNull Vector3dc position, @NonNull Vector3fc color, float intensity, float range) {
        context.checkOpen();
        return registry().addPoint(owner(), position, color, intensity, checkRange(range));
    }

    /**
     * Place a spotlight that fades from its axis out to the edge of its cone, and reaches as far as
     * its intensity carries it.
     *
     * @param position The world position.
     * @param direction Which way it points.
     * @param color The color.
     * @param intensity The intensity.
     * @param outerAngle The angle from the axis where the light ends, in degrees.
     * @return The handle.
     * @throws IllegalArgumentException If the direction is zero or the angle out of range.
     * @throws IllegalStateException If the plugin was unloaded, or graphics isn't running.
     */
    public LightHandle spot(
            @NonNull Vector3dc position,
            @NonNull Vector3fc direction,
            @NonNull Vector3fc color,
            float intensity,
            float outerAngle) {
        return spot(position, direction, color, intensity, 0, outerAngle, Float.NaN);
    }

    /**
     * Place a spotlight that is full strength out to its inner angle and fades out to its outer
     * angle.
     *
     * @param position The world position.
     * @param direction Which way it points.
     * @param color The color.
     * @param intensity The intensity.
     * @param innerAngle The angle from the axis where the light starts to fade, in degrees.
     * @param outerAngle The angle from the axis where the light ends, in degrees.
     * @param range How far it reaches in meters, or NaN to follow the intensity.
     * @return The handle.
     * @throws IllegalArgumentException If the direction is zero or the angles out of range.
     * @throws IllegalStateException If the plugin was unloaded, or graphics isn't running.
     */
    public LightHandle spot(
            @NonNull Vector3dc position,
            @NonNull Vector3fc direction,
            @NonNull Vector3fc color,
            float intensity,
            float innerAngle,
            float outerAngle,
            float range) {
        context.checkOpen();
        return registry()
                .addSpot(
                        owner(),
                        position,
                        direction,
                        color,
                        intensity,
                        checkRange(range),
                        innerAngle,
                        outerAngle);
    }

    /**
     * Move a light.
     *
     * @param light The light.
     * @param x The new world x position.
     * @param y The new world y position.
     * @param z The new world z position.
     */
    public void move(@Nullable LightHandle light, double x, double y, double z) {
        move(light, new Vector3d(x, y, z));
    }

    /**
     * Move a light.
     *
     * @param light The light.
     * @param position The new world position.
     */
    public void move(@Nullable LightHandle light, @NonNull Vector3dc position) {
        LightRegistry registry = registryOrNull();
        if (registry != null && !registry.move(owner(), light, position)) {
            ignored(registry, light, "move");
        }
    }

    /**
     * Point a spotlight another way.
     *
     * @param light The light.
     * @param direction Which way it points.
     * @throws IllegalArgumentException If the direction is zero.
     */
    public void setDirection(@Nullable LightHandle light, @NonNull Vector3fc direction) {
        LightRegistry registry = registryOrNull();
        if (registry != null && !registry.setDirection(owner(), light, direction)) {
            ignored(registry, light, "turn");
        }
    }

    /**
     * Change a light's color.
     *
     * @param light The light.
     * @param color The new color.
     */
    public void setColor(@Nullable LightHandle light, @NonNull Vector3fc color) {
        LightRegistry registry = registryOrNull();
        if (registry != null && !registry.setColor(owner(), light, color)) {
            ignored(registry, light, "recolor");
        }
    }

    /**
     * Change how bright a light is. A light without a range of its own reaches further or less far
     * to match.
     *
     * @param light The light.
     * @param intensity The new intensity.
     */
    public void setIntensity(@Nullable LightHandle light, float intensity) {
        LightRegistry registry = registryOrNull();
        if (registry != null && !registry.setIntensity(owner(), light, intensity)) {
            ignored(registry, light, "dim");
        }
    }

    /**
     * Change how far a light reaches.
     *
     * @param light The light.
     * @param range The range in meters, or NaN to follow the intensity again.
     */
    public void setRange(@Nullable LightHandle light, float range) {
        LightRegistry registry = registryOrNull();
        if (registry != null && !registry.setRange(owner(), light, checkRange(range))) {
            ignored(registry, light, "change the range of");
        }
    }

    /**
     * Change a spotlight's cone. Point lights ignore it.
     *
     * @param light The light.
     * @param innerAngle The angle from the axis where the light starts to fade, in degrees.
     * @param outerAngle The angle from the axis where the light ends, in degrees.
     * @throws IllegalArgumentException If the angles are out of range.
     */
    public void setCone(@Nullable LightHandle light, float innerAngle, float outerAngle) {
        LightRegistry registry = registryOrNull();
        if (registry != null && !registry.setCone(owner(), light, innerAngle, outerAngle)) {
            ignored(registry, light, "reshape");
        }
    }

    /**
     * Read a light's position.
     *
     * @param light The light.
     * @param dest Where to store the world position.
     * @return False if the handle is null or stale, leaving dest unchanged.
     */
    public boolean getPosition(@Nullable LightHandle light, @NonNull Vector3d dest) {
        LightRegistry registry = registryOrNull();
        return registry != null && registry.getPosition(light, dest);
    }

    /**
     * How far a light reaches, after its default and the limits are applied.
     *
     * @param light The light.
     * @return The range in meters, or NaN if the handle is null or stale.
     */
    public float getRange(@Nullable LightHandle light) {
        LightRegistry registry = registryOrNull();
        return registry == null ? Float.NaN : registry.getRange(light);
    }

    /**
     * Remove a light this plugin owns. The handle, and any copies of it, become stale. Nothing
     * happens if the handle is null or already stale.
     *
     * @param light The light.
     */
    public void remove(@Nullable LightHandle light) {
        LightRegistry registry = registryOrNull();
        if (registry != null && !registry.remove(owner(), light)) {
            ignored(registry, light, "remove");
        }
    }

    /**
     * Whether a handle still refers to a light.
     *
     * @param light The handle to check.
     * @return False if the handle is null or stale.
     */
    public boolean isValid(@Nullable LightHandle light) {
        LightRegistry registry = registryOrNull();
        return registry != null && registry.isValid(light);
    }

    /**
     * Warn about a range that will be clamped.
     *
     * @param range The range asked for, in meters, or NaN for the default.
     * @return The same range; the registry clamps it.
     */
    private float checkRange(float range) {
        if (!Float.isNaN(range) && LightRange.clamp(range) != range) {
            log.warn(
                    "{} asked for a light range of {} m, which is kept within {} to {} m",
                    owner(),
                    range,
                    LightRange.MIN_RANGE,
                    LightRange.MAX_RANGE);
        }
        return range;
    }

    /**
     * The key this plugin's lights are owned under.
     *
     * @return The owner key.
     */
    private String owner() {
        return context.getOwnerKey();
    }

    /**
     * Warn when a change to another plugin's light was ignored. Stale handles are ignored quietly,
     * since a handle can go stale while its owner is still using it.
     *
     * @param registry The registry.
     * @param light The handle.
     * @param action What was attempted, for the message.
     */
    private void ignored(
            @NonNull LightRegistry registry, @Nullable LightHandle light, @NonNull String action) {
        String owner = registry.ownerOf(light);
        if (owner != null && !owner.equals(owner())) {
            log.warn("{} tried to {} a light owned by {}, ignoring it", owner(), action, owner);
        }
    }

    /**
     * The scene's light registry, if graphics is running.
     *
     * @return The registry, or null.
     */
    private static LightRegistry registryOrNull() {
        Scene scene = GraphicsManager.getScene();
        return scene == null ? null : scene.getLightRegistry();
    }

    /**
     * The scene's light registry.
     *
     * @return The registry.
     * @throws IllegalStateException If graphics isn't running.
     */
    private static LightRegistry registry() {
        LightRegistry registry = registryOrNull();
        if (registry == null) {
            throw new IllegalStateException("Graphics is not running");
        }
        return registry;
    }
}
