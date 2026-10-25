package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.Sections;

import lombok.NonNull;
import org.joml.Vector3dc;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Boxes in the world where something that doesn't move changed, such as a section baked again or
 * level of detail swapping cells, so cached shadow maps can draw those parts again. Some are only
 * swaps of one level of detail of a surface for another, which maps that may keep either can skip.
 * Added from any thread; taken by the render thread once a frame.
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

    /** How a changed box is taken when draining, with whether it is a swap. */
    @FunctionalInterface
    public interface Taker {
        /**
         * Take a box.
         *
         * @param min Its minimum corner, in the world.
         * @param max Its maximum corner, in the world.
         * @param swap Whether only a surface's level of detail changed there, to another within its
         *     error, as when streaming shows or hides a unit.
         */
        void changed(double @NonNull [] min, double @NonNull [] max, boolean swap);
    }

    /** Where a box says whether it is a swap: 1 if so. */
    private static final int SWAP = 2 * AXES;

    /** The boxes waiting, each min x, y, z, max x, y, z, then whether it is a swap. */
    private final ConcurrentLinkedQueue<double[]> boxes = new ConcurrentLinkedQueue<>();

    /**
     * Note a changed box.
     *
     * @param min Its minimum corner, in the world.
     * @param max Its maximum corner, in the world.
     */
    public void add(double @NonNull [] min, double @NonNull [] max) {
        boxes.add(new double[] {min[0], min[1], min[2], max[0], max[1], max[2], 0});
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
                    0
                });
    }

    /**
     * Note a changed sphere.
     *
     * @param origin What the sphere's center is measured from, in the world.
     * @param sphere x, y, z and radius.
     */
    public void addSphere(double @NonNull [] origin, double @NonNull [] sphere) {
        addSphere(origin, sphere, false);
    }

    /**
     * Note a sphere where only a surface's level of detail changed, to another within its error.
     *
     * @param origin What the sphere's center is measured from, in the world.
     * @param sphere x, y, z and radius.
     */
    public void addSwap(double @NonNull [] origin, double @NonNull [] sphere) {
        addSphere(origin, sphere, true);
    }

    /**
     * Note a changed sphere.
     *
     * @param origin What the sphere's center is measured from, in the world.
     * @param sphere x, y, z and radius.
     * @param swap Whether only a surface's level of detail changed there.
     */
    private void addSphere(double @NonNull [] origin, double @NonNull [] sphere, boolean swap) {
        final double radius = sphere[AXES];
        boxes.add(
                new double[] {
                    origin[0] + sphere[0] - radius,
                    origin[1] + sphere[1] - radius,
                    origin[2] + sphere[2] - radius,
                    origin[0] + sphere[0] + radius,
                    origin[1] + sphere[1] + radius,
                    origin[2] + sphere[2] + radius,
                    swap ? 1 : 0
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
            taker.changed(min, max, box[SWAP] != 0);
        }
    }
}
