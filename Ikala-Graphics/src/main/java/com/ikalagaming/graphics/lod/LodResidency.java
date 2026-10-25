package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * <p>Units are numbered by index, and an index may be free, its unit null, so a caller can keep its
 * units' indices across rebuilds and only work out again the ones that changed.
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

    /** Values in a sphere: x, y, z, radius. */
    private static final int SPHERE_VALUES = AXES + 1;

    /** Buckets of need an octave for the budget's cutoff, as a power of two. */
    private static final int BUCKET_BITS = 5;

    /** Buckets of need an octave for the budget's cutoff. */
    static final int BUCKETS_PER_OCTAVE = 1 << BUCKET_BITS;

    /** Buckets of need for the budget's cutoff: 64 octaves over {@link #PREFETCH}. */
    static final int BUCKETS = 64 * BUCKETS_PER_OCTAVE;

    /** How far a double's bits are shifted to keep its exponent and its top mantissa bits. */
    private static final int BUCKET_SHIFT = 52 - BUCKET_BITS;

    /** {@link #PREFETCH}'s bits so shifted, the first bucket. */
    private static final long PREFETCH_KEY = Double.doubleToRawLongBits(PREFETCH) >>> BUCKET_SHIFT;

    /** Not unwanted yet. */
    private static final long WANTED = -1;

    /** The parents of a free index. */
    private static final int[] NO_PARENTS = {};

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

    /** The units, null at free indices. */
    private final List<Unit> units;

    /**
     * Each unit's bytes, error and sphere side by side, 0 at free indices: every update reads them
     * all, and tens of thousands of units' records are scattered over the heap.
     */
    private final long[] bytes;

    /** Each unit's error, see {@link #bytes}. */
    private final double[] errors;

    /** Each unit's sphere, x, y, z, radius each, see {@link #bytes}. */
    private final double[] spheres;

    /** How many units there are, leaving out free indices. */
    private final int present;

    /** How many bytes all the units take. */
    private final long total;

    /** Bytes of units in each bucket of need, while the budget's cutoff is worked out. */
    private final long[] bucketBytes = new long[BUCKETS];

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
     * How much each unit is needed at the last update: how many times the error threshold its group
     * projects to from the nearest observer, infinity for roots.
     */
    private final double[] need;

    /**
     * The share of the threshold a unit's group had to project over at the last update to be
     * wanted: {@link #PREFETCH}, or more where the budget allowed only the most needed.
     */
    private double cutoff = PREFETCH;

    /**
     * Set up for a set of units.
     *
     * @param units The units, null at free indices.
     * @param loaded Whether each one's meshes are already on the GPU. Those whose parents are all
     *     shown are shown at once, parents first.
     */
    public LodResidency(@NonNull List<Unit> units, boolean @NonNull [] loaded) {
        this.units = Collections.unmodifiableList(new ArrayList<>(units));
        final int n = units.size();
        this.loaded = loaded.clone();
        bytes = new long[n];
        errors = new double[n];
        spheres = new double[n * SPHERE_VALUES];
        int count = 0;
        long sum = 0;
        for (int i = 0; i < n; ++i) {
            final Unit unit = units.get(i);
            this.loaded[i] &= unit != null;
            if (unit != null) {
                bytes[i] = unit.bytes();
                errors[i] = unit.error();
                System.arraycopy(unit.bound(), 0, spheres, i * SPHERE_VALUES, SPHERE_VALUES);
                ++count;
                sum += unit.bytes();
            }
        }
        present = count;
        total = sum;
        this.shown = new boolean[n];
        wanted = new boolean[n];
        unwantedSince = new long[n];
        Arrays.fill(unwantedSince, WANTED);
        distance = new double[n];
        need = new double[n];
        final int[] childCount = new int[n];
        for (Unit unit : units) {
            if (unit == null) {
                continue;
            }
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
            for (int parent : parentsOf(i)) {
                children[parent][childCount[parent]++] = i;
            }
        }
        order = parentsFirst();
        for (int unit : order) {
            boolean parentsShown = true;
            for (int parent : parentsOf(unit)) {
                parentsShown &= shown[parent];
            }
            shown[unit] = this.loaded[unit] && parentsShown;
        }
    }

    /**
     * A unit's parents.
     *
     * @param unit The unit's index.
     * @return Its parents, none at a free index.
     */
    private int[] parentsOf(int unit) {
        final Unit found = units.get(unit);
        return found == null ? NO_PARENTS : found.parents();
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
            waiting[i] = parentsOf(i).length;
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
        update(new double[][] {eye}, pixelsPerUnit, thresholdPixels, now, streaming);
    }

    /**
     * Work out which units are wanted from where several observers are, as when instances of a
     * model each see it from their own place: a unit is wanted when any of them wants it, and roots
     * always are.
     *
     * @param eyes The observers, from the world's origin, in its units.
     * @param pixelsPerUnit Pixels per world unit at distance 1.
     * @param thresholdPixels The error threshold.
     * @param now The time, from {@link System#nanoTime()}.
     * @param streaming Whether to stream; if not, every unit is wanted.
     */
    public void update(
            double @NonNull [][] eyes,
            double pixelsPerUnit,
            double thresholdPixels,
            long now,
            boolean streaming) {
        update(eyes, pixelsPerUnit, thresholdPixels, now, streaming, Long.MAX_VALUE);
    }

    /**
     * Work out which units are wanted from where several observers are, as {@link
     * #update(double[][], double, double, long, boolean)} does, within a byte budget: if every unit
     * wanted would take more, only the most needed are, those whose groups project the most over
     * the threshold, and no unit whose parents aren't. The GPU draws the coarser parents of the
     * rest, as if the threshold were higher there.
     *
     * @param eyes The observers, from the world's origin, in its units.
     * @param pixelsPerUnit Pixels per world unit at distance 1.
     * @param thresholdPixels The error threshold.
     * @param now The time, from {@link System#nanoTime()}.
     * @param streaming Whether to stream; if not, every unit is wanted.
     * @param budgetBytes The most bytes the wanted units may take.
     */
    public void update(
            double @NonNull [][] eyes,
            double pixelsPerUnit,
            double thresholdPixels,
            long now,
            boolean streaming,
            long budgetBytes) {
        long wantedBytes = 0;
        final double[] bound = new double[SPHERE_VALUES];
        for (int i = 0; i < units.size(); ++i) {
            if (units.get(i) == null) {
                wanted[i] = false;
                continue;
            }
            System.arraycopy(spheres, i * SPHERE_VALUES, bound, 0, SPHERE_VALUES);
            final double error = errors[i];
            double nearest = eyes.length == 0 ? 0 : Double.MAX_VALUE;
            double most = !streaming || Double.isInfinite(error) ? Double.POSITIVE_INFINITY : 0;
            for (double[] eye : eyes) {
                double sum = 0;
                for (int axis = 0; axis < AXES; ++axis) {
                    final double d = bound[axis] - eye[axis];
                    sum += d * d;
                }
                nearest = Math.min(nearest, Math.max(0, Math.sqrt(sum) - bound[AXES]));
                most =
                        Math.max(
                                most,
                                LodCut.projected(error, bound, eye, pixelsPerUnit)
                                        / thresholdPixels);
            }
            distance[i] = nearest;
            need[i] = most;
            if (most > PREFETCH) {
                wantedBytes += bytes[i];
            }
        }
        // Over the budget, the most needed first until it is spent
        cutoff = PREFETCH;
        if (wantedBytes > budgetBytes) {
            cutoff = cutoffFor(budgetBytes);
        }
        for (int unit : order) {
            boolean any =
                    units.get(unit) != null
                            && (need[unit] > cutoff || Double.isInfinite(need[unit]));
            for (int parent : parentsOf(unit)) {
                any &= wanted[parent];
            }
            wanted[unit] = any;
            if (any) {
                unwantedSince[unit] = WANTED;
            } else if (unwantedSince[unit] == WANTED) {
                unwantedSince[unit] = now;
            }
        }
    }

    /**
     * The need a unit must be over to be wanted within a budget: taking the units the most needed
     * first, with the roots before all, the need of the first one past the budget. The units are
     * put in buckets by need in one pass, and only the bucket the budget runs out in is sorted:
     * tens of thousands of units are planned several times a second.
     *
     * @param budgetBytes The most bytes the wanted units may take.
     * @return The cutoff, or {@link #PREFETCH} if no finite need runs past the budget.
     */
    private double cutoffFor(long budgetBytes) {
        Arrays.fill(bucketBytes, 0);
        long spent = 0;
        double highest = 0;
        for (int i = 0; i < need.length; ++i) {
            if (units.get(i) == null || !(need[i] > PREFETCH)) {
                continue;
            }
            if (Double.isInfinite(need[i])) {
                spent += bytes[i];
            } else {
                bucketBytes[bucketOf(need[i])] += bytes[i];
                highest = Math.max(highest, need[i]);
            }
        }
        if (spent > budgetBytes) {
            // The roots alone take it all
            return highest > 0 ? highest : PREFETCH;
        }
        for (int bucket = BUCKETS - 1; bucket >= 0; --bucket) {
            if (spent + bucketBytes[bucket] <= budgetBytes) {
                spent += bucketBytes[bucket];
                continue;
            }
            // The budget runs out in this bucket: its units, the most needed first
            final List<Integer> inBucket = new ArrayList<>();
            for (int i = 0; i < need.length; ++i) {
                if (units.get(i) != null
                        && need[i] > PREFETCH
                        && !Double.isInfinite(need[i])
                        && bucketOf(need[i]) == bucket) {
                    inBucket.add(i);
                }
            }
            inBucket.sort(Comparator.comparingDouble((Integer i) -> need[i]).reversed());
            for (int i : inBucket) {
                spent += bytes[i];
                if (spent > budgetBytes) {
                    return need[i];
                }
            }
        }
        return PREFETCH;
    }

    /**
     * Which bucket a need over {@link #PREFETCH} goes in: {@link #BUCKETS_PER_OCTAVE} an octave up
     * from it, from the bits of the double, which grow with it, the highest taking all above.
     *
     * @param need The need.
     * @return The bucket.
     */
    static int bucketOf(double need) {
        final long key = Double.doubleToRawLongBits(need) >>> BUCKET_SHIFT;
        return (int) Math.min(BUCKETS - 1, Math.max(0, key - PREFETCH_KEY));
    }

    /**
     * How many times the error threshold the budget made the cut at the last update, 1 where
     * everything wanted fit: a group whose error projects under the cutoff has no children on the
     * GPU, so it is drawn whatever the threshold, while one under the threshold is drawn anyway.
     *
     * @return The factor.
     */
    public double budgetScale() {
        return Math.max(1, cutoff);
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
        for (int parent : parentsOf(unit)) {
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
            for (int parent : parentsOf(unit)) {
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
     * How many unit indices there are, free ones included.
     *
     * @return The count.
     */
    public int size() {
        return units.size();
    }

    /**
     * How many units there are, not counting free indices.
     *
     * @return The count.
     */
    public int count() {
        return present;
    }

    /**
     * How many bytes the loaded units take.
     *
     * @return The bytes.
     */
    public long loadedBytes() {
        long sum = 0;
        for (int i = 0; i < bytes.length; ++i) {
            sum += loaded[i] ? bytes[i] : 0;
        }
        return sum;
    }

    /**
     * Whether any unit is wanted and every wanted one is shown: the whole cut the observers want
     * can be drawn, with no cluster standing in for children that aren't there yet.
     *
     * @return True if so.
     */
    public boolean wantedAllShown() {
        boolean any = false;
        for (int i = 0; i < units.size(); ++i) {
            if (wanted[i]) {
                any = true;
                if (!shown[i]) {
                    return false;
                }
            }
        }
        return any;
    }

    /**
     * Whether any unit is wanted.
     *
     * @return True if one is.
     */
    public boolean anyWanted() {
        for (boolean w : wanted) {
            if (w) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many bytes the wanted units take.
     *
     * @return The bytes.
     */
    public long wantedBytes() {
        long sum = 0;
        for (int i = 0; i < bytes.length; ++i) {
            sum += wanted[i] ? bytes[i] : 0;
        }
        return sum;
    }

    /**
     * How many bytes all the units take.
     *
     * @return The bytes.
     */
    public long totalBytes() {
        return total;
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
