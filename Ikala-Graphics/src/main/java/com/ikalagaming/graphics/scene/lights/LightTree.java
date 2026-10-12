package com.ikalagaming.graphics.scene.lights;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A binary tree over shadow-casting lights, for spending a shadow map budget the way lightcuts
 * (Walter et al. 2005) spend shading work: the most important parts of the tree get maps, and a
 * tight group of lights may share one map, drawn from its brightest light, instead of each needing
 * its own. Lights the budget doesn't reach cast no shadows.
 *
 * <p>Built fresh each frame, splitting at the middle of the longest axis of each group's box, with
 * one light at each leaf. Pure Java, so it is tested without a GPU.
 */
public final class LightTree {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /**
     * How much more important a group counts if its representative already has a map, so small
     * changes from frame to frame don't swap maps and throw their caches away.
     */
    public static final double HYSTERESIS = 1.25;

    /** The nearest a group is treated as being, in meters, so importance stays finite. */
    private static final double MIN_DISTANCE = 1;

    /**
     * One shadow-casting light.
     *
     * @param slot The light's slot in the registry.
     * @param x Its world x.
     * @param y Its world y.
     * @param z Its world z.
     * @param intensity How bright it is.
     * @param range How far it reaches, in meters.
     */
    public record Light(int slot, double x, double y, double z, float intensity, float range) {}

    /**
     * A part of the tree: some lights, with what a shared map for them would need.
     *
     * @param lights The lights, by index into {@link #lights()}.
     * @param min The minimum corner of the box around their positions.
     * @param max The maximum corner.
     * @param intensity Their total intensity.
     * @param representative The brightest of them, by index into {@link #lights()}: where a shared
     *     map is drawn from, a real light's position, so it stands in open air.
     * @param range How far a shared map must reach from the representative to cover where every
     *     light reaches.
     * @param left The first child, or -1 for a leaf.
     * @param right The second child, or -1 for a leaf.
     */
    public record Node(
            int @NonNull [] lights,
            double @NonNull [] min,
            double @NonNull [] max,
            double intensity,
            int representative,
            double range,
            int left,
            int right) {

        /**
         * How far apart its lights are: the longest side of the box around them.
         *
         * @return The extent, in meters.
         */
        public double extent() {
            return Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
        }

        /**
         * Whether it holds one light.
         *
         * @return True for a leaf.
         */
        public boolean isLeaf() {
            return left < 0;
        }
    }

    /** The lights, as given. */
    private final List<Light> lights;

    /** The nodes, the root first. */
    private final List<Node> nodes = new ArrayList<>();

    /**
     * Build a tree over some lights.
     *
     * @param lights The lights.
     */
    public LightTree(@NonNull List<Light> lights) {
        this.lights = List.copyOf(lights);
        if (!lights.isEmpty()) {
            final int[] all = new int[lights.size()];
            for (int i = 0; i < all.length; ++i) {
                all[i] = i;
            }
            build(all);
        }
    }

    /**
     * The lights.
     *
     * @return The lights, as given.
     */
    public List<Light> lights() {
        return lights;
    }

    /**
     * The nodes, the root first.
     *
     * @return The nodes.
     */
    public List<Node> nodes() {
        return nodes;
    }

    /**
     * Build the node for some lights and everything below it.
     *
     * @param members The lights, by index.
     * @return The node's index.
     */
    private int build(int @NonNull [] members) {
        final double[] min = {
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
        };
        final double[] max = {
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
        };
        double intensity = 0;
        int brightest = members[0];
        for (int i : members) {
            final Light light = lights.get(i);
            final double[] at = {light.x(), light.y(), light.z()};
            for (int axis = 0; axis < AXES; ++axis) {
                min[axis] = Math.min(min[axis], at[axis]);
                max[axis] = Math.max(max[axis], at[axis]);
            }
            intensity += light.intensity();
            final Light best = lights.get(brightest);
            if (light.intensity() > best.intensity()
                    || (light.intensity() == best.intensity() && light.slot() < best.slot())) {
                brightest = i;
            }
        }
        final Light center = lights.get(brightest);
        double range = 0;
        for (int i : members) {
            final Light light = lights.get(i);
            final double dx = light.x() - center.x();
            final double dy = light.y() - center.y();
            final double dz = light.z() - center.z();
            range = Math.max(range, Math.sqrt(dx * dx + dy * dy + dz * dz) + light.range());
        }
        final int index = nodes.size();
        nodes.add(null);
        int left = -1;
        int right = -1;
        if (members.length > 1) {
            // Split along the longest axis
            int axis = 0;
            for (int a = 1; a < AXES; ++a) {
                if (max[a] - min[a] > max[axis] - min[axis]) {
                    axis = a;
                }
            }
            final int along = axis;
            final Integer[] sorted = new Integer[members.length];
            for (int i = 0; i < members.length; ++i) {
                sorted[i] = members[i];
            }
            Arrays.sort(
                    sorted,
                    Comparator.<Integer>comparingDouble(i -> coordinate(lights.get(i), along))
                            .thenComparingInt(i -> lights.get(i).slot()));
            // At the middle of the box, so a tight group stays together, or at the median if
            // every light is on one side
            final double middle = (min[along] + max[along]) / 2;
            int half = 0;
            while (half < sorted.length && coordinate(lights.get(sorted[half]), along) < middle) {
                ++half;
            }
            if (half == 0 || half == sorted.length) {
                half = members.length / 2;
            }
            final int[] low = new int[half];
            final int[] high = new int[members.length - half];
            for (int i = 0; i < sorted.length; ++i) {
                if (i < half) {
                    low[i] = sorted[i];
                } else {
                    high[i - half] = sorted[i];
                }
            }
            left = build(low);
            right = build(high);
        }
        nodes.set(index, new Node(members, min, max, intensity, brightest, range, left, right));
        return index;
    }

    /**
     * One coordinate of a light's position.
     *
     * @param light The light.
     * @param axis 0 for x, 1 for y, 2 for z.
     * @return The coordinate.
     */
    private static double coordinate(@NonNull Light light, int axis) {
        return switch (axis) {
            case 0 -> light.x();
            case 1 -> light.y();
            default -> light.z();
        };
    }

    /**
     * How much a node's lights would show if they cast no shadows, from where it is seen: their
     * total intensity over the square of the distance to the nearest point of their box.
     *
     * @param node The node.
     * @param eye The eye, in the world.
     * @return The importance.
     */
    static double importance(@NonNull Node node, double @NonNull [] eye) {
        double squared = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            final double d =
                    Math.max(
                            0,
                            Math.max(node.min()[axis] - eye[axis], eye[axis] - node.max()[axis]));
            squared += d * d;
        }
        final double distance = Math.max(MIN_DISTANCE, Math.sqrt(squared));
        return node.intensity() / (distance * distance);
    }

    /**
     * Pick which parts of the tree get shadow maps. Parts too spread out to share a map are split,
     * most important first; parts tight enough become maps until the budget runs out, and while
     * there is budget left the most important shared map is split so its lights get maps of their
     * own.
     *
     * @param eye Where the scene is seen from, in the world.
     * @param budget The most maps.
     * @param shareExtent The farthest apart lights may be and share a map, in meters.
     * @param kept The slots of lights that are representatives of maps already, which count {@link
     *     #HYSTERESIS} times as important.
     * @return The nodes that get maps, most important first.
     */
    public List<Node> cut(
            double @NonNull [] eye, int budget, double shareExtent, @NonNull Set<Integer> kept) {
        if (nodes.isEmpty() || budget <= 0) {
            return List.of();
        }
        final Comparator<Integer> byImportance =
                Comparator.comparingDouble((Integer i) -> -score(i, eye, kept))
                        .thenComparingInt(i -> i);
        final PriorityQueue<Integer> open = new PriorityQueue<>(byImportance);
        open.add(0);
        final List<Integer> chosen = new ArrayList<>();
        while (!open.isEmpty() && chosen.size() < budget) {
            final int index = open.poll();
            final Node node = nodes.get(index);
            if (node.isLeaf() || node.extent() <= shareExtent) {
                chosen.add(index);
            } else {
                open.add(node.left());
                open.add(node.right());
            }
        }
        // With budget to spare, split shared maps, most important first
        final PriorityQueue<Integer> shared = new PriorityQueue<>(byImportance);
        for (int index : chosen) {
            if (!nodes.get(index).isLeaf()) {
                shared.add(index);
            }
        }
        while (!shared.isEmpty() && chosen.size() < budget) {
            final int index = shared.poll();
            final Node node = nodes.get(index);
            chosen.remove(Integer.valueOf(index));
            for (int child : new int[] {node.left(), node.right()}) {
                chosen.add(child);
                if (!nodes.get(child).isLeaf()) {
                    shared.add(child);
                }
            }
        }
        chosen.sort(byImportance);
        final List<Node> out = new ArrayList<>(chosen.size());
        for (int index : chosen) {
            out.add(nodes.get(index));
        }
        return out;
    }

    /**
     * A node's importance with the bonus for keeping a map.
     *
     * @param index The node.
     * @param eye The eye, in the world.
     * @param kept The slots of lights that are representatives of maps already.
     * @return The score.
     */
    private double score(int index, double @NonNull [] eye, @NonNull Set<Integer> kept) {
        final Node node = nodes.get(index);
        final double importance = importance(node, eye);
        return kept.contains(lights.get(node.representative()).slot())
                ? importance * HYSTERESIS
                : importance;
    }
}
