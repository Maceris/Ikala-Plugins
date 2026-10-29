package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.Sections;

import lombok.NonNull;
import org.joml.Vector3dc;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Boxes in the world where something that doesn't move changed, such as a section baked again or
 * level of detail swapping cells, so cached shadow maps can draw those parts again. Each says what
 * changed, see {@link Change}, so maps that don't draw it can skip it. Added from any thread; taken
 * by the render thread once a frame.
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

    /** The boxes waiting, each min x, y, z, max x, y, z, then what changed by ordinal. */
    private final ConcurrentLinkedQueue<double[]> boxes = new ConcurrentLinkedQueue<>();

    /**
     * Note a changed box.
     *
     * @param min Its minimum corner, in the world.
     * @param max Its maximum corner, in the world.
     */
    public void add(double @NonNull [] min, double @NonNull [] max) {
        boxes.add(
                new double[] {
                    min[0], min[1], min[2], max[0], max[1], max[2], Change.SURFACE.ordinal()
                });
    }

    /**
     * Note a changed section.
     *
     * @param origin The section's corner, in the world.
     */
    public void addSection(@NonNull Vector3dc origin) {
        final double size = Sections.SECTION_SIZE;
        boxes.add(
                new double[] {
                    origin.x(),
                    origin.y(),
                    origin.z(),
                    origin.x() + size,
                    origin.y() + size,
                    origin.z() + size,
                    Change.SURFACE.ordinal()
                });
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
     * Note a changed sphere.
     *
     * @param origin What the sphere's center is measured from, in the world.
     * @param sphere x, y, z and radius.
     * @param change What changed there.
     */
    private void addSphere(
            double @NonNull [] origin, double @NonNull [] sphere, @NonNull Change change) {
        final double radius = sphere[AXES];
        boxes.add(
                new double[] {
                    origin[0] + sphere[0] - radius,
                    origin[1] + sphere[1] - radius,
                    origin[2] + sphere[2] - radius,
                    origin[0] + sphere[0] + radius,
                    origin[1] + sphere[1] + radius,
                    origin[2] + sphere[2] + radius,
                    change.ordinal()
                });
    }

    /**
     * Take every box waiting. Render thread.
     *
     * @param taker What to do with each.
     */
    public void drain(@NonNull Taker taker) {
        final double[] min = new double[AXES];
        final double[] max = new double[AXES];
        double[] box;
        while ((box = boxes.poll()) != null) {
            System.arraycopy(box, 0, min, 0, AXES);
            System.arraycopy(box, AXES, max, 0, AXES);
            taker.changed(min, max, CHANGES[(int) box[CHANGE]]);
        }
    }
}
