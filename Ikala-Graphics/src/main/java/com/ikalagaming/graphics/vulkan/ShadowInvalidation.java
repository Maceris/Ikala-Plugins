package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.Sections;

import lombok.NonNull;
import org.joml.Vector3dc;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Boxes in the world where something that doesn't move changed, such as a section baked again or
 * level of detail swapping cells, so cached shadow maps can draw those parts again. Added from any
 * thread; taken by the render thread once a frame.
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

    /** The boxes waiting, each min x, y, z then max x, y, z. */
    private final ConcurrentLinkedQueue<double[]> boxes = new ConcurrentLinkedQueue<>();

    /**
     * Note a changed box.
     *
     * @param min Its minimum corner, in the world.
     * @param max Its maximum corner, in the world.
     */
    public void add(double @NonNull [] min, double @NonNull [] max) {
        boxes.add(new double[] {min[0], min[1], min[2], max[0], max[1], max[2]});
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
                    origin.z() + size
                });
    }

    /**
     * Note a changed sphere.
     *
     * @param origin What the sphere's center is measured from, in the world.
     * @param sphere x, y, z and radius.
     */
    public void addSphere(double @NonNull [] origin, double @NonNull [] sphere) {
        final double radius = sphere[AXES];
        boxes.add(
                new double[] {
                    origin[0] + sphere[0] - radius,
                    origin[1] + sphere[1] - radius,
                    origin[2] + sphere[2] - radius,
                    origin[0] + sphere[0] + radius,
                    origin[1] + sphere[1] + radius,
                    origin[2] + sphere[2] + radius
                });
    }

    /**
     * Take every box waiting. Render thread.
     *
     * @param visitor What to do with each.
     */
    public void drain(@NonNull Visitor visitor) {
        final double[] min = new double[AXES];
        final double[] max = new double[AXES];
        double[] box;
        while ((box = boxes.poll()) != null) {
            System.arraycopy(box, 0, min, 0, AXES);
            System.arraycopy(box, AXES, max, 0, AXES);
            visitor.changed(min, max);
        }
    }
}
