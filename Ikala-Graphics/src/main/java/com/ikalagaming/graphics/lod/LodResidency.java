package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which streaming units of a level of detail world belong on the GPU. A unit is the set of
 * clusters one group consumes, its inputs, which were simplified together, as Nanite's cluster
 * groups; clusters nothing consumes form a root unit. Each unit's parents are the units holding the
 * clusters its group made.
 *
 * <p>A unit is wanted when its group's error, projected from its sphere, is over half the error
 * threshold: the cut at that threshold could need it soon, with a margin for a moving observer.
 * Saturated errors and spheres make the wanted units closed upward, and roots are always wanted.
 *
 * <p>Residency is kept closed upward as well, so the GPU never lacks a parent: a unit is shown, its
 * clusters drawable, only once it is loaded and every parent is shown, and it is evicted only once
 * no child is loaded. Unwanted units wait {@link #EVICT_DELAY_NANOS} before going, so looking back
 * and forth doesn't churn uploads.
 *
 * <p>Not thread safe: the render thread owns it.
 */
public final class LodResidency {

    /** How long a unit stays after it stops being wanted. */
    public static final long EVICT_DELAY_NANOS = 2_000_000_000L;

    /** The share of the error threshold a unit's group must project over to be wanted. */
    public static final double PREFETCH = 0.5;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Not unwanted yet. */
    private static final long WANTED = -1;

    /**
     * One unit.
     *
     * @param error The error of the group that consumes its clusters, infinity for roots.
     * @param bound That group's sphere, x, y, z, radius, from the world's origin.
     * @param bytes How many bytes its meshes take on the GPU.
     * @param parents The units holding the clusters its group made.
     */
    public record Unit(
            double error, double @NonNull [] bound, long bytes, int @NonNull [] parents) {}

    /** The units. */
    private final List<Unit> units;

    /** Each unit's children: the units whose parents include it. */
    private final int[][] children;

    /** The units, every parent before its children. */
    private final int[] order;

    /** Whether each unit's meshes are all on the GPU. */
    private final boolean[] loaded;

    /** Whether each unit's clusters may be drawn. */
    private final boolean[] shown;

    /** Whether each unit was wanted at the last update. */
    private final boolean[] wanted;

    /** When each unit stopped being wanted, or {@link #WANTED}. */
    private final long[] unwantedSince;

    /** How far each unit was at the last update, for ordering. */
    private final double[] distance;

    /**
     * Set up for a set of units.
     *
     * @param units The units.
     * @param loaded Whether each one's meshes are already on the GPU. Those whose parents are all
     *     shown are shown at once, parents first.
     */
    public LodResidency(@NonNull List<Unit> units, boolean @NonNull [] loaded) {
        this.units = List.copyOf(units);
        final int n = units.size();
        this.loaded = loaded.clone();
        this.shown = new boolean[n];
        wanted = new boolean[n];
        unwantedSince = new long[n];
        Arrays.fill(unwantedSince, WANTED);
        distance = new double[n];
        final int[] childCount = new int[n];
        for (Unit unit : units) {
            for (int parent : unit.parents()) {
                childCount[parent] += 1;
            }
        }
        children = new int[n][];
        for (int i = 0; i < n; ++i) {
            children[i] = new int[childCount[i]];
            childCount[i] = 0;
        }
        for (int i = 0; i < n; ++i) {
            for (int parent : units.get(i).parents()) {
                children[parent][childCount[parent]++] = i;
            }
        }
        order = parentsFirst();
        for (int unit : order) {
            boolean parentsShown = true;
            for (int parent : units.get(unit).parents()) {
                parentsShown &= shown[parent];
            }
            shown[unit] = this.loaded[unit] && parentsShown;
        }
    }

    /**
     * Order the units so every parent comes before its children.
     *
     * @return The order.
     */
    private int[] parentsFirst() {
        final int n = units.size();
        final int[] waiting = new int[n];
        final ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int i = 0; i < n; ++i) {
            waiting[i] = units.get(i).parents().length;
            if (waiting[i] == 0) {
                ready.add(i);
            }
        }
        final int[] out = new int[n];
        int count = 0;
        while (!ready.isEmpty()) {
            final int unit = ready.poll();
            out[count++] = unit;
            for (int child : children[unit]) {
                if (--waiting[child] == 0) {
                    ready.add(child);
                }
            }
        }
        if (count != n) {
            throw new IllegalArgumentException("The units' parents form a cycle");
        }
        return out;
    }

    /**
     * Work out which units are wanted from where the observer is.
     *
     * @param eye The observer, from the world's origin.
     * @param pixelsPerUnit Pixels per world unit at distance 1.
     * @param thresholdPixels The error threshold.
     * @param now The time, from {@link System#nanoTime()}.
     * @param streaming Whether to stream; if not, every unit is wanted.
     */
    public void update(
            double @NonNull [] eye,
            double pixelsPerUnit,
            double thresholdPixels,
            long now,
            boolean streaming) {
        for (int i = 0; i < units.size(); ++i) {
            final Unit unit = units.get(i);
            final double[] bound = unit.bound();
            double sum = 0;
            for (int axis = 0; axis < AXES; ++axis) {
                final double d = bound[axis] - eye[axis];
                sum += d * d;
            }
            distance[i] = Math.max(0, Math.sqrt(sum) - bound[AXES]);
            wanted[i] =
                    !streaming
                            || LodCut.projected(unit.error(), bound, eye, pixelsPerUnit)
                                    > thresholdPixels * PREFETCH;
            if (wanted[i]) {
                unwantedSince[i] = WANTED;
            } else if (unwantedSince[i] == WANTED) {
                unwantedSince[i] = now;
            }
        }
    }

    /**
     * The wanted units not yet loaded, parents' order first and then nearest first, so the most
     * needed go up first.
     *
     * @return The units to load.
     */
    public List<Integer> toLoad() {
        final int[] rank = new int[units.size()];
        for (int i = 0; i < order.length; ++i) {
            rank[order[i]] = depth(order[i], rank);
        }
        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < units.size(); ++i) {
            if (wanted[i] && !loaded[i]) {
                out.add(i);
            }
        }
        out.sort(
                Comparator.<Integer>comparingInt(i -> rank[i])
                        .thenComparingDouble(i -> distance[i]));
        return out;
    }

    /**
     * How deep a unit is: one more than its deepest parent. Parents are ranked first.
     *
     * @param unit The unit.
     * @param rank The ranks so far.
     * @return Its depth.
     */
    private int depth(int unit, int @NonNull [] rank) {
        int deepest = -1;
        for (int parent : units.get(unit).parents()) {
            deepest = Math.max(deepest, rank[parent]);
        }
        return deepest + 1;
    }

    /**
     * Note that a unit's meshes are all on the GPU.
     *
     * @param unit The unit.
     */
    public void setLoaded(int unit) {
        loaded[unit] = true;
    }

    /**
     * Show every loaded, wanted unit whose parents are all shown, parents first, so several levels
     * can come in together.
     *
     * @return The units shown now.
     */
    public List<Integer> toShow() {
        final List<Integer> out = new ArrayList<>();
        for (int unit : order) {
            if (shown[unit] || !loaded[unit] || !wanted[unit]) {
                continue;
            }
            boolean parentsShown = true;
            for (int parent : units.get(unit).parents()) {
                parentsShown &= shown[parent];
            }
            if (parentsShown) {
                shown[unit] = true;
                out.add(unit);
            }
        }
        return out;
    }

    /**
     * Evict the loaded units unwanted for longer than the delay, children first, never one with a
     * loaded child. Over the byte cap, unwanted units go without waiting, farthest first.
     *
     * @param now The time, from {@link System#nanoTime()}.
     * @param capBytes The most bytes to keep loaded.
     * @return The units evicted now, no longer loaded or shown.
     */
    public List<Integer> toEvict(long now, long capBytes) {
        final boolean over = loadedBytes() > capBytes;
        final List<Integer> out = new ArrayList<>();
        for (int k = order.length - 1; k >= 0; --k) {
            final int unit = order[k];
            if (!loaded[unit] || wanted[unit] || unwantedSince[unit] == WANTED) {
                continue;
            }
            if (!over && now - unwantedSince[unit] < EVICT_DELAY_NANOS) {
                continue;
            }
            boolean childLoaded = false;
            for (int child : children[unit]) {
                childLoaded |= loaded[child];
            }
            if (childLoaded) {
                continue;
            }
            loaded[unit] = false;
            shown[unit] = false;
            out.add(unit);
        }
        return out;
    }

    /**
     * Whether a unit is wanted.
     *
     * @param unit The unit.
     * @return True if it was at the last update.
     */
    public boolean isWanted(int unit) {
        return wanted[unit];
    }

    /**
     * Whether a unit is shown.
     *
     * @param unit The unit.
     * @return True if its clusters may be drawn.
     */
    public boolean isShown(int unit) {
        return shown[unit];
    }

    /**
     * Whether a unit is loaded.
     *
     * @param unit The unit.
     * @return True if its meshes are on the GPU.
     */
    public boolean isLoaded(int unit) {
        return loaded[unit];
    }

    /**
     * How many units there are.
     *
     * @return The count.
     */
    public int size() {
        return units.size();
    }

    /**
     * How many bytes the loaded units take.
     *
     * @return The bytes.
     */
    public long loadedBytes() {
        long sum = 0;
        for (int i = 0; i < units.size(); ++i) {
            sum += loaded[i] ? units.get(i).bytes() : 0;
        }
        return sum;
    }

    /**
     * How many bytes the wanted units take.
     *
     * @return The bytes.
     */
    public long wantedBytes() {
        long sum = 0;
        for (int i = 0; i < units.size(); ++i) {
            sum += wanted[i] ? units.get(i).bytes() : 0;
        }
        return sum;
    }

    /**
     * How many bytes all the units take.
     *
     * @return The bytes.
     */
    public long totalBytes() {
        long sum = 0;
        for (Unit unit : units) {
            sum += unit.bytes();
        }
        return sum;
    }

    /**
     * How many units are shown.
     *
     * @return The count.
     */
    public int shownCount() {
        int count = 0;
        for (boolean s : shown) {
            count += s ? 1 : 0;
        }
        return count;
    }
}
