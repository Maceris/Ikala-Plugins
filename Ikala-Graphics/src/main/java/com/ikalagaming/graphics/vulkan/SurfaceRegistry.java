package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.bake.SurfaceColumns;

import lombok.NonNull;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The soft surface layers of the sections baked, by world column, for the deformation field's
 * column table, see {@link DeformationField}: which surfaces stand in each column, how high, how
 * deep, how fast they fill back in and whether stamps may press them. Sections feed it as they are
 * submitted, baked again and removed. Safe from any thread.
 */
public final class SurfaceRegistry {

    /** A column's flag in the table: a surface is there. */
    public static final int PRESENT = 1;

    /** A column's flag in the table: stamps may press it. */
    public static final int PRESSABLE = 2;

    /** The flag in a layer's flags that lets stamps press a column. */
    private static final int LAYER_PRESSABLE = 1;

    /** Receives a column's surfaces, see {@link #fill}. */
    @FunctionalInterface
    public interface ColumnSink {
        /**
         * One surface in a column.
         *
         * @param x The world column along x.
         * @param z The world column along z.
         * @param slot Which of the column's surfaces, highest first, up to {@link
         *     DeformMath#SLOTS}.
         * @param top Its top, in meters.
         * @param bottom Its bottom, in meters.
         * @param refill How fast it fills back in, in meters a second.
         * @param flags {@link #PRESENT}, and {@link #PRESSABLE} if stamps may press it.
         */
        void surface(int x, int z, int slot, float top, float bottom, float refill, int flags);
    }

    /**
     * A section's layer.
     *
     * @param owner Its owner's key.
     * @param x Its origin's world column along x.
     * @param y Its origin's height, in meters.
     * @param z Its origin's world column along z.
     * @param columns The layer.
     */
    private record Entry(
            @NonNull String owner, int x, double y, int z, @NonNull SurfaceColumns columns) {}

    /** Guards everything here. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Each section's layer, by section id. */
    private final Map<Long, Entry> byId = new HashMap<>();

    /** The layers standing over each run of 16 columns along x and z, by {@link #chunk}. */
    private final Map<Long, List<Entry>> byChunk = new HashMap<>();

    /** How fast each material fills back in, by material index, in meters a second. */
    private final Map<Integer, Float> refillRates = new HashMap<>();

    /** Counts every change, so the table is built again only when something changed. */
    private long version;

    /**
     * Set or replace a section's layer, or forget it with null.
     *
     * @param id The section.
     * @param owner Its owner's key.
     * @param origin Its origin, in the world.
     * @param columns Its layer, or null for none.
     */
    public void put(
            long id, @NonNull String owner, @NonNull Vector3dc origin, SurfaceColumns columns) {
        lock.lock();
        try {
            removeLocked(id);
            if (columns != null) {
                final Entry entry =
                        new Entry(
                                owner,
                                (int) Math.floor(origin.x()),
                                origin.y(),
                                (int) Math.floor(origin.z()),
                                columns);
                byId.put(id, entry);
                for (long key : chunks(entry)) {
                    byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
                }
            }
            ++version;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forget a section's layer.
     *
     * @param id The section.
     */
    public void remove(long id) {
        lock.lock();
        try {
            if (removeLocked(id)) {
                ++version;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forget every layer a plugin owns.
     *
     * @param owner The plugin's key.
     */
    public void removeOwner(@NonNull String owner) {
        lock.lock();
        try {
            final List<Long> ids = new ArrayList<>();
            byId.forEach(
                    (id, entry) -> {
                        if (entry.owner().equals(owner)) {
                            ids.add(id);
                        }
                    });
            for (long id : ids) {
                removeLocked(id);
            }
            if (!ids.isEmpty()) {
                ++version;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set how fast a material fills back in once pressed.
     *
     * @param material The material's index.
     * @param metersPerSecond The rate.
     */
    public void setRefillRate(int material, float metersPerSecond) {
        lock.lock();
        try {
            refillRates.put(material, Math.max(0, metersPerSecond));
            ++version;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Which version the layers are at, which every change counts up.
     *
     * @return The version.
     */
    public long version() {
        lock.lock();
        try {
            return version;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Hand over the surfaces of a rectangle of world columns, highest first in each, up to {@link
     * DeformMath#SLOTS} a column.
     *
     * @param x0 The first column along x.
     * @param z0 The first column along z.
     * @param width How many columns along x.
     * @param depth How many columns along z.
     * @param sink What takes them.
     */
    public void fill(int x0, int z0, int width, int depth, @NonNull ColumnSink sink) {
        final float[] tops = new float[DeformMath.SLOTS];
        final float[] bottoms = new float[DeformMath.SLOTS];
        final float[] rates = new float[DeformMath.SLOTS];
        final int[] flags = new int[DeformMath.SLOTS];
        lock.lock();
        try {
            for (int z = z0; z < z0 + depth; ++z) {
                for (int x = x0; x < x0 + width; ++x) {
                    final List<Entry> entries = byChunk.get(chunkKey(x >> 4, z >> 4));
                    if (entries == null) {
                        continue;
                    }
                    int count = 0;
                    for (Entry entry : entries) {
                        final int localX = x - entry.x();
                        final int localZ = z - entry.z();
                        if (localX < 0
                                || localZ < 0
                                || localX >= SurfaceColumns.SECTION
                                || localZ >= SurfaceColumns.SECTION) {
                            continue;
                        }
                        final SurfaceColumns columns = entry.columns();
                        final int column = SurfaceColumns.index(localX, localZ);
                        final short top = columns.tops()[column];
                        if (top == SurfaceColumns.NONE) {
                            continue;
                        }
                        final float steps = columns.stepsPerMeter();
                        final float topMeters = (float) (entry.y() + top / steps);
                        final float bottomMeters =
                                (float) (entry.y() + (top - columns.depths()[column]) / steps);
                        float rate =
                                columns.refillRates() == null
                                        ? Float.NaN
                                        : columns.refillRates()[column];
                        if (Float.isNaN(rate)) {
                            rate = refillRates.getOrDefault(columns.materials()[column], 0f);
                        }
                        final boolean pressable =
                                columns.flags() == null
                                        || (columns.flags()[column] & LAYER_PRESSABLE) != 0;
                        count =
                                insert(
                                        tops,
                                        bottoms,
                                        rates,
                                        flags,
                                        count,
                                        topMeters,
                                        bottomMeters,
                                        rate,
                                        PRESENT | (pressable ? PRESSABLE : 0));
                    }
                    for (int slot = 0; slot < count; ++slot) {
                        sink.surface(
                                x, z, slot, tops[slot], bottoms[slot], rates[slot], flags[slot]);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Put a surface into a column's list, highest first, dropping the lowest past {@link
     * DeformMath#SLOTS}.
     *
     * @param tops The tops so far.
     * @param bottoms The bottoms so far.
     * @param rates The refill rates so far.
     * @param flags The flags so far.
     * @param count How many so far.
     * @param top This one's top.
     * @param bottom Its bottom.
     * @param rate Its refill rate.
     * @param flag Its flags.
     * @return How many now.
     */
    private static int insert(
            float[] tops,
            float[] bottoms,
            float[] rates,
            int[] flags,
            int count,
            float top,
            float bottom,
            float rate,
            int flag) {
        int at = 0;
        while (at < count && tops[at] >= top) {
            ++at;
        }
        if (at >= DeformMath.SLOTS) {
            return count;
        }
        final int moved = Math.min(count, DeformMath.SLOTS - 1) - at;
        if (moved > 0) {
            System.arraycopy(tops, at, tops, at + 1, moved);
            System.arraycopy(bottoms, at, bottoms, at + 1, moved);
            System.arraycopy(rates, at, rates, at + 1, moved);
            System.arraycopy(flags, at, flags, at + 1, moved);
        }
        tops[at] = top;
        bottoms[at] = bottom;
        rates[at] = rate;
        flags[at] = flag;
        return Math.min(count + 1, DeformMath.SLOTS);
    }

    /**
     * Forget a section's layer, under the lock.
     *
     * @param id The section.
     * @return Whether it had one.
     */
    private boolean removeLocked(long id) {
        final Entry old = byId.remove(id);
        if (old == null) {
            return false;
        }
        for (long key : chunks(old)) {
            final List<Entry> entries = byChunk.get(key);
            if (entries != null) {
                entries.remove(old);
                if (entries.isEmpty()) {
                    byChunk.remove(key);
                }
            }
        }
        return true;
    }

    /**
     * The runs of 16 columns a layer covers: one, or up to four if its origin isn't on a multiple
     * of 16.
     *
     * @param entry The layer.
     * @return Their keys.
     */
    private static long[] chunks(@NonNull Entry entry) {
        final int x0 = entry.x() >> 4;
        final int z0 = entry.z() >> 4;
        final int x1 = (entry.x() + SurfaceColumns.SECTION - 1) >> 4;
        final int z1 = (entry.z() + SurfaceColumns.SECTION - 1) >> 4;
        final long[] keys = new long[(x1 - x0 + 1) * (z1 - z0 + 1)];
        int i = 0;
        for (int z = z0; z <= z1; ++z) {
            for (int x = x0; x <= x1; ++x) {
                keys[i++] = chunkKey(x, z);
            }
        }
        return keys;
    }

    /**
     * A run of 16 columns' key.
     *
     * @param x The run along x.
     * @param z The run along z.
     * @return The key.
     */
    private static long chunkKey(int x, int z) {
        return ((long) x << Integer.SIZE) ^ (z & 0xFFFF_FFFFL);
    }
}
