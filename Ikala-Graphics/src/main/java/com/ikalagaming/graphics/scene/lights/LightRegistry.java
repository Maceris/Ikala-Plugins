package com.ikalagaming.graphics.scene.lights;

import com.ikalagaming.graphics.LightHandle;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * Every point and spot light in a scene, each owned by the plugin that placed it.
 *
 * <p>Lights are written into a fresh buffer each frame, so a removed light's slot can be reused
 * right away. Plugins change lights from their own threads while the render thread reads them, so
 * every method holds {@link #lock}.
 */
public class LightRegistry {

    /**
     * Receives each live light, as {@link #visit(LightVisitor)} walks them.
     *
     * <p>Called with the registry locked, so it must not call back into the registry. The vectors
     * are only valid during the call.
     */
    @FunctionalInterface
    public interface LightVisitor {
        /**
         * Receive a light.
         *
         * @param light The light's settings.
         */
        void light(@NonNull View light);
    }

    /** A read-only look at a light's settings. */
    public interface View {
        /**
         * What kind of light it is.
         *
         * @return The type.
         */
        LightType type();

        /**
         * Where the light is.
         *
         * @return The world position.
         */
        Vector3dc position();

        /**
         * Which way a spotlight points. Unused for point lights.
         *
         * @return The normalized direction.
         */
        Vector3fc direction();

        /**
         * The light's color.
         *
         * @return The color, with r, g and b in x, y and z.
         */
        Vector3fc color();

        /**
         * How bright the light is.
         *
         * @return The intensity.
         */
        float intensity();

        /**
         * How far the light reaches.
         *
         * @return The range, in meters.
         */
        float range();

        /**
         * The cosine of the angle from a spotlight's axis where it starts to fade.
         *
         * @return The cosine of the inner angle.
         */
        float cosInner();

        /**
         * The cosine of the angle from a spotlight's axis where it ends.
         *
         * @return The cosine of the outer angle.
         */
        float cosOuter();
    }

    /** One light's settings. */
    private static final class Entry implements View {
        private final Vector3d position = new Vector3d();
        private final Vector3f direction = new Vector3f(0, -1, 0);
        private final Vector3f color = new Vector3f();
        private LightType type;
        private float intensity;

        /** The range set by the owner, or NaN to follow the intensity. */
        private float explicitRange;

        private float cosInner;
        private float cosOuter;

        @Override
        public LightType type() {
            return type;
        }

        @Override
        public Vector3dc position() {
            return position;
        }

        @Override
        public Vector3fc direction() {
            return direction;
        }

        @Override
        public Vector3fc color() {
            return color;
        }

        @Override
        public float intensity() {
            return intensity;
        }

        @Override
        public float range() {
            return Float.isNaN(explicitRange)
                    ? LightRange.defaultRange(intensity)
                    : LightRange.clamp(explicitRange);
        }

        @Override
        public float cosInner() {
            return cosInner;
        }

        @Override
        public float cosOuter() {
            return cosOuter;
        }
    }

    /** The most lights a scene can hold. */
    public static final int MAX_LIGHTS = 1 << 16;

    /** Guards everything below. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The light in each slot, null for a free slot. */
    private final List<Entry> entries = new ArrayList<>();

    /** The owner of each slot's light. */
    private final List<String> owners = new ArrayList<>();

    /** How many times each slot has been freed. */
    private final List<Integer> generations = new ArrayList<>();

    /** Free slots, reused last freed first. */
    private final List<Integer> freeSlots = new ArrayList<>();

    /** How many lights are live. */
    private int liveCount;

    /**
     * Add a point light.
     *
     * @param owner The key of the plugin that owns it.
     * @param position The world position.
     * @param color The color.
     * @param intensity The intensity.
     * @param range How far it reaches in meters, or NaN to follow the intensity.
     * @return The handle.
     * @throws IllegalStateException If the registry is full.
     */
    public LightHandle addPoint(
            @NonNull String owner,
            @NonNull Vector3dc position,
            @NonNull Vector3fc color,
            float intensity,
            float range) {
        lock.lock();
        try {
            final int slot = takeSlot();
            Entry entry = entries.get(slot);
            entry.type = LightType.POINT;
            entry.position.set(position);
            entry.color.set(color);
            entry.intensity = intensity;
            entry.explicitRange = range;
            entry.cosInner = 1;
            entry.cosOuter = -1;
            owners.set(slot, owner);
            return new LightHandle(slot, generations.get(slot));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Add a spotlight.
     *
     * @param owner The key of the plugin that owns it.
     * @param position The world position.
     * @param direction Which way it points.
     * @param color The color.
     * @param intensity The intensity.
     * @param range How far it reaches in meters, or NaN to follow the intensity.
     * @param innerAngle The angle from the axis where it starts to fade, in degrees.
     * @param outerAngle The angle from the axis where it ends, in degrees.
     * @return The handle.
     * @throws IllegalArgumentException If the direction is zero or the angles are out of range.
     * @throws IllegalStateException If the registry is full.
     */
    public LightHandle addSpot(
            @NonNull String owner,
            @NonNull Vector3dc position,
            @NonNull Vector3fc direction,
            @NonNull Vector3fc color,
            float intensity,
            float range,
            float innerAngle,
            float outerAngle) {
        checkDirection(direction);
        checkCone(innerAngle, outerAngle);
        lock.lock();
        try {
            final int slot = takeSlot();
            Entry entry = entries.get(slot);
            entry.type = LightType.SPOT;
            entry.position.set(position);
            entry.direction.set(direction).normalize();
            entry.color.set(color);
            entry.intensity = intensity;
            entry.explicitRange = range;
            setConeLocked(entry, innerAngle, outerAngle);
            owners.set(slot, owner);
            return new LightHandle(slot, generations.get(slot));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Move a light.
     *
     * @param owner The key of the plugin asking, which must own it.
     * @param handle The light.
     * @param position The new world position.
     * @return False if the handle was stale or owned by another plugin.
     */
    public boolean move(
            @NonNull String owner, @Nullable LightHandle handle, @NonNull Vector3dc position) {
        lock.lock();
        try {
            Entry entry = ownedLocked(owner, handle);
            if (entry == null) {
                return false;
            }
            entry.position.set(position);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Point a spotlight another way. Does nothing to a point light's look.
     *
     * @param owner The key of the plugin asking, which must own it.
     * @param handle The light.
     * @param direction Which way it points.
     * @return False if the handle was stale or owned by another plugin.
     * @throws IllegalArgumentException If the direction is zero.
     */
    public boolean setDirection(
            @NonNull String owner, @Nullable LightHandle handle, @NonNull Vector3fc direction) {
        checkDirection(direction);
        lock.lock();
        try {
            Entry entry = ownedLocked(owner, handle);
            if (entry == null) {
                return false;
            }
            entry.direction.set(direction).normalize();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change a light's color.
     *
     * @param owner The key of the plugin asking, which must own it.
     * @param handle The light.
     * @param color The new color.
     * @return False if the handle was stale or owned by another plugin.
     */
    public boolean setColor(
            @NonNull String owner, @Nullable LightHandle handle, @NonNull Vector3fc color) {
        lock.lock();
        try {
            Entry entry = ownedLocked(owner, handle);
            if (entry == null) {
                return false;
            }
            entry.color.set(color);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change how bright a light is. A light without a range of its own reaches further or less far
     * to match.
     *
     * @param owner The key of the plugin asking, which must own it.
     * @param handle The light.
     * @param intensity The new intensity.
     * @return False if the handle was stale or owned by another plugin.
     */
    public boolean setIntensity(
            @NonNull String owner, @Nullable LightHandle handle, float intensity) {
        lock.lock();
        try {
            Entry entry = ownedLocked(owner, handle);
            if (entry == null) {
                return false;
            }
            entry.intensity = intensity;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change how far a light reaches.
     *
     * @param owner The key of the plugin asking, which must own it.
     * @param handle The light.
     * @param range The range in meters, or NaN to follow the intensity again.
     * @return False if the handle was stale or owned by another plugin.
     */
    public boolean setRange(@NonNull String owner, @Nullable LightHandle handle, float range) {
        lock.lock();
        try {
            Entry entry = ownedLocked(owner, handle);
            if (entry == null) {
                return false;
            }
            entry.explicitRange = range;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Change a spotlight's cone. Does nothing to a point light's look.
     *
     * @param owner The key of the plugin asking, which must own it.
     * @param handle The light.
     * @param innerAngle The angle from the axis where it starts to fade, in degrees.
     * @param outerAngle The angle from the axis where it ends, in degrees.
     * @return False if the handle was stale or owned by another plugin.
     * @throws IllegalArgumentException If the angles are out of range.
     */
    public boolean setCone(
            @NonNull String owner,
            @Nullable LightHandle handle,
            float innerAngle,
            float outerAngle) {
        checkCone(innerAngle, outerAngle);
        lock.lock();
        try {
            Entry entry = ownedLocked(owner, handle);
            if (entry == null || entry.type != LightType.SPOT) {
                return entry != null;
            }
            setConeLocked(entry, innerAngle, outerAngle);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Read a light's position.
     *
     * @param handle The light.
     * @param dest Where to store the world position.
     * @return False if the handle is null or stale, leaving dest unchanged.
     */
    public boolean getPosition(@Nullable LightHandle handle, @NonNull Vector3d dest) {
        lock.lock();
        try {
            Entry entry = validLocked(handle);
            if (entry == null) {
                return false;
            }
            dest.set(entry.position);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How far a light reaches, after its default and the limits are applied.
     *
     * @param handle The light.
     * @return The range in meters, or NaN if the handle is null or stale.
     */
    public float getRange(@Nullable LightHandle handle) {
        lock.lock();
        try {
            Entry entry = validLocked(handle);
            return entry == null ? Float.NaN : entry.range();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether a handle still refers to a light.
     *
     * @param handle The handle.
     * @return False if the handle is null or stale.
     */
    public boolean isValid(@Nullable LightHandle handle) {
        lock.lock();
        try {
            return validLocked(handle) != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The plugin that owns a light.
     *
     * @param handle The light.
     * @return The owner's key, or null if the handle is null or stale.
     */
    public String ownerOf(@Nullable LightHandle handle) {
        lock.lock();
        try {
            return validLocked(handle) == null ? null : owners.get(handle.slot());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove a light. Its handle, and any copies of it, go stale.
     *
     * @param owner The key of the plugin asking, which must own it.
     * @param handle The light.
     * @return False if the handle was stale or owned by another plugin.
     */
    public boolean remove(@NonNull String owner, @Nullable LightHandle handle) {
        lock.lock();
        try {
            if (ownedLocked(owner, handle) == null) {
                return false;
            }
            freeLocked(handle.slot());
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove every light a plugin owns.
     *
     * @param owner The plugin's key.
     * @return How many lights were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            int removed = 0;
            for (int slot = 0; slot < entries.size(); ++slot) {
                if (entries.get(slot) != null && owner.equals(owners.get(slot))) {
                    freeLocked(slot);
                    removed += 1;
                }
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Hand every live light to a visitor, in slot order.
     *
     * @param visitor Receives each light.
     * @return How many lights were visited.
     */
    public int visit(@NonNull LightVisitor visitor) {
        lock.lock();
        try {
            int visited = 0;
            for (Entry entry : entries) {
                if (entry != null && entry.type != null) {
                    visitor.light(entry);
                    visited += 1;
                }
            }
            return visited;
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many lights are in the scene.
     *
     * @return The number of live lights.
     */
    public int getLightCount() {
        lock.lock();
        try {
            return liveCount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take a free slot, adding one if there is none. The lock must be held.
     *
     * @return The slot, holding a cleared entry.
     * @throws IllegalStateException If the registry is full.
     */
    private int takeSlot() {
        final int slot;
        if (freeSlots.isEmpty()) {
            if (entries.size() >= MAX_LIGHTS) {
                throw new IllegalStateException(
                        "A scene can hold at most " + MAX_LIGHTS + " lights");
            }
            slot = entries.size();
            entries.add(new Entry());
            owners.add(null);
            generations.add(0);
        } else {
            slot = freeSlots.removeLast();
            entries.set(slot, new Entry());
        }
        liveCount += 1;
        return slot;
    }

    /**
     * Free a slot holding a light. The lock must be held.
     *
     * @param slot The slot.
     */
    private void freeLocked(int slot) {
        entries.set(slot, null);
        owners.set(slot, null);
        generations.set(slot, generations.get(slot) + 1);
        freeSlots.add(slot);
        liveCount -= 1;
    }

    /**
     * The entry a handle refers to. The lock must be held.
     *
     * @param handle The handle.
     * @return The entry, or null if the handle is null or stale.
     */
    private Entry validLocked(@Nullable LightHandle handle) {
        if (handle == null) {
            return null;
        }
        final int slot = handle.slot();
        if (slot < 0 || slot >= entries.size() || generations.get(slot) != handle.generation()) {
            return null;
        }
        return entries.get(slot);
    }

    /**
     * The entry a handle refers to, if a plugin owns it. The lock must be held.
     *
     * @param owner The plugin's key.
     * @param handle The handle.
     * @return The entry, or null if the handle is stale or owned by another plugin.
     */
    private Entry ownedLocked(@NonNull String owner, @Nullable LightHandle handle) {
        Entry entry = validLocked(handle);
        return entry != null && owner.equals(owners.get(handle.slot())) ? entry : null;
    }

    /**
     * Store a spotlight's cone. The lock must be held.
     *
     * @param entry The light.
     * @param innerAngle The angle from the axis where it starts to fade, in degrees.
     * @param outerAngle The angle from the axis where it ends, in degrees.
     */
    private static void setConeLocked(@NonNull Entry entry, float innerAngle, float outerAngle) {
        entry.cosInner = (float) Math.cos(Math.toRadians(innerAngle));
        entry.cosOuter = (float) Math.cos(Math.toRadians(outerAngle));
    }

    /**
     * Check a direction can be normalized.
     *
     * @param direction The direction.
     * @throws IllegalArgumentException If it is zero or not finite.
     */
    private static void checkDirection(@NonNull Vector3fc direction) {
        final float lengthSquared = direction.lengthSquared();
        if (lengthSquared <= 0 || !Float.isFinite(lengthSquared)) {
            throw new IllegalArgumentException("A spotlight needs a direction, got " + direction);
        }
    }

    /**
     * Check a spotlight's cone.
     *
     * @param innerAngle The angle from the axis where it starts to fade, in degrees.
     * @param outerAngle The angle from the axis where it ends, in degrees.
     * @throws IllegalArgumentException If the outer angle isn't between 0 and 180 degrees, or the
     *     inner angle isn't between 0 and the outer angle.
     */
    private static void checkCone(float innerAngle, float outerAngle) {
        final float halfTurn = 180;
        if (!(outerAngle > 0 && outerAngle < halfTurn)) {
            throw new IllegalArgumentException(
                    "A spotlight's outer angle must be between 0 and 180 degrees, got "
                            + outerAngle);
        }
        if (!(innerAngle >= 0 && innerAngle <= outerAngle)) {
            throw new IllegalArgumentException(
                    "A spotlight's inner angle must be between 0 and its outer angle "
                            + outerAngle
                            + ", got "
                            + innerAngle);
        }
    }
}
