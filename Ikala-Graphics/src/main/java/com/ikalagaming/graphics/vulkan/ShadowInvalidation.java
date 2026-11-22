package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.Sections;

import lombok.NonNull;
import org.joml.Vector3dc;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Boxes in the world where something that doesn't move changed, such as a section baked again or
 * level of detail swapping cells, so cached shadow maps can draw those parts again. Each says what
 * changed, see {@link Change}, so maps that don't draw it can skip it. Added from any thread; taken
 * by the render thread once a frame.
 *
 * <p>Boxes are packed into a pair of arrays that swap at each drain, so noting one makes no
 * garbage: streaming notes thousands a frame.
 */
public final class ShadowInvalidation {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** How a changed box is taken. */
    @FunctionalInterface
    public interface Visitor {
        /**
         * Take a box.
         *
         * @param min Its minimum corner, in the world.
         * @param max Its maximum corner, in the world.
         */
        void changed(double @NonNull [] min, double @NonNull [] max);
    }

    /** What changed in a box. */
    public enum Change {
        /**
         * Sections' meshes or bricks, or anything else drawn from the sections themselves, which
         * every map draws.
         */
        SURFACE,
        /**
         * Level of detail clusters or the instances it draws, which maps built only from the
         * sections, like the occupancy volume, don't draw.
         */
        LEVEL_OF_DETAIL,
        /**
         * Only a surface's level of detail, swapped for another within its error, as when streaming
         * shows or hides a unit; maps that may keep either can skip it too.
         */
        SWAP
    }

    /** How a changed box is taken when draining, with what changed. */
    @FunctionalInterface
    public interface Taker {
        /**
         * Take a box.
         *
         * @param min Its minimum corner, in the world.
         * @param max Its maximum corner, in the world.
         * @param change What changed there.
         */
        void changed(double @NonNull [] min, double @NonNull [] max, @NonNull Change change);
    }

    /** What changed, by ordinal. */
    private static final Change[] CHANGES = Change.values();

    /** Where a box says what changed, by {@link Change} ordinal. */
    private static final int CHANGE = 2 * AXES;

    /** Values per box: min x, y, z, max x, y, z, then what changed by ordinal. */
    private static final int BOX = CHANGE + 1;

    /** Boxes there is room for at first. */
    private static final int INITIAL_BOXES = 256;

    /**
     * Boxes kept room for between frames: a burst past this, like a whole world loading, gets its
     * room back once drained.
     */
    private static final int KEPT_BOXES = 16_384;

    /** Guards {@link #pending} and {@link #pendingCount}. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The boxes waiting, {@link #BOX} values each. */
    private double[] pending = new double[INITIAL_BOXES * BOX];

    /** How many values of {@link #pending} are used. */
    private int pendingCount;

    /** The boxes being drained, swapped with {@link #pending}. Render thread. */
    private double[] draining = new double[INITIAL_BOXES * BOX];

    /** The minimum corner handed to the taker. Render thread. */
    private final double[] drainMin = new double[AXES];

    /** The maximum corner handed to the taker. Render thread. */
    private final double[] drainMax = new double[AXES];

    /**
     * Note a changed box.
     *
     * @param min Its minimum corner, in the world.
     * @param max Its maximum corner, in the world.
     */
    public void add(double @NonNull [] min, double @NonNull [] max) {
        put(min[0], min[1], min[2], max[0], max[1], max[2], Change.SURFACE);
    }

    /**
     * Note a changed section.
     *
     * @param origin The section's corner, in the world.
     */
    public void addSection(@NonNull Vector3dc origin) {
        final double size = Sections.SECTION_SIZE;
        put(
                origin.x(),
                origin.y(),
                origin.z(),
                origin.x() + size,
                origin.y() + size,
                origin.z() + size,
                Change.SURFACE);
    }

    /**
     * Note a sphere where level of detail clusters, or instances it draws, changed.
     *
     * @param origin What the sphere's center is measured from, in the world.
     * @param sphere x, y, z and radius.
     */
    public void addLevelOfDetail(double @NonNull [] origin, double @NonNull [] sphere) {
        addSphere(origin, sphere, Change.LEVEL_OF_DETAIL);
    }

    /**
     * Note a sphere where only a surface's level of detail changed, to another within its error.
     *
     * @param origin What the sphere's center is measured from, in the world.
     * @param sphere x, y, z and radius.
     */
    public void addSwap(double @NonNull [] origin, double @NonNull [] sphere) {
        addSphere(origin, sphere, Change.SWAP);
    }

    /**
     * Note a box where only a surface's level of detail changed, to another within its error, as
     * {@link #addSwap} does for a sphere: for many clusters close together at once, as one box
     * around them all.
     *
     * @param min The box's minimum corner, in the world.
     * @param max The box's maximum corner, in the world.
     */
    public void addSwapBox(double @NonNull [] min, double @NonNull [] max) {
        put(min[0], min[1], min[2], max[0], max[1], max[2], Change.SWAP);
    }

    /**
     * Note a changed sphere.
     *
     * @param origin What the sphere's center is measured from, in the world.
     * @param sphere x, y, z and radius.
     * @param change What changed there.
     */
    private void addSphere(
            double @NonNull [] origin, double @NonNull [] sphere, @NonNull Change change) {
        final double radius = sphere[AXES];
        put(
                origin[0] + sphere[0] - radius,
                origin[1] + sphere[1] - radius,
                origin[2] + sphere[2] - radius,
                origin[0] + sphere[0] + radius,
                origin[1] + sphere[1] + radius,
                origin[2] + sphere[2] + radius,
                change);
    }

    /**
     * Add a box to those waiting.
     *
     * @param minX Its minimum x.
     * @param minY Its minimum y.
     * @param minZ Its minimum z.
     * @param maxX Its maximum x.
     * @param maxY Its maximum y.
     * @param maxZ Its maximum z.
     * @param change What changed there.
     */
    private void put(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            @NonNull Change change) {
        lock.lock();
        try {
            if (pendingCount + BOX > pending.length) {
                pending = Arrays.copyOf(pending, pending.length * 2);
            }
            final double[] into = pending;
            int at = pendingCount;
            into[at++] = minX;
            into[at++] = minY;
            into[at++] = minZ;
            into[at++] = maxX;
            into[at++] = maxY;
            into[at++] = maxZ;
            into[at++] = change.ordinal();
            pendingCount = at;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Take every box waiting. Render thread.
     *
     * @param taker What to do with each.
     */
    public void drain(@NonNull Taker taker) {
        final double[] boxes;
        final int count;
        lock.lock();
        try {
            boxes = pending;
            count = pendingCount;
            // Boxes added while these are taken go to the other array
            pending = draining;
            pendingCount = 0;
        } finally {
            lock.unlock();
        }
        draining = boxes;
        final double[] min = drainMin;
        final double[] max = drainMax;
        for (int at = 0; at < count; at += BOX) {
            System.arraycopy(boxes, at, min, 0, AXES);
            System.arraycopy(boxes, at + AXES, max, 0, AXES);
            taker.changed(min, max, CHANGES[(int) boxes[at + CHANGE]]);
        }
        // A burst's room isn't kept
        if (boxes.length > KEPT_BOXES * BOX) {
            draining = new double[INITIAL_BOXES * BOX];
        }
    }
}
