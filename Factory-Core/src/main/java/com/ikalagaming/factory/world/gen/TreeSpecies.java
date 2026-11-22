package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.Block;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * A kind of tree: its blocks, a shape type from code and the ranges each tree draws its own values
 * from, so no two trees of a species match. Growing a tree is a pure function of its seed.
 *
 * <p>The {@link Shape#BROADLEAF} shape grows by space colonization: points are scattered through a
 * crown envelope, and branches grow a step at a time toward the points near them, until the points
 * are used up. Leaves gather around the branches' tips.
 *
 * @param id The data ID.
 * @param shape How it grows.
 * @param trunk The block of its trunk and branches.
 * @param leaves The block of its leaves.
 * @param height How tall it grows, in blocks: min and max.
 * @param crownStart How far up the trunk its crown starts, as a share of its height: min and max.
 * @param crownRadius How wide its crown spreads from the trunk, in blocks: min and max.
 * @param leafRadius How thick the leaves are around each branch tip, in blocks: min and max.
 */
public record TreeSpecies(
        @NonNull String id,
        @NonNull Shape shape,
        @NonNull Block trunk,
        @NonNull Block leaves,
        double @NonNull [] height,
        double @NonNull [] crownStart,
        double @NonNull [] crownRadius,
        double @NonNull [] leafRadius) {

    /** Shape types, from code. */
    public enum Shape {
        /** A spreading crown grown by space colonization, like an oak. */
        BROADLEAF
    }

    /**
     * What a tree is made of, relative to the open position at its foot.
     *
     * @param xs Each block's x offset.
     * @param ys Each block's y offset, 0 at the foot.
     * @param zs Each block's z offset.
     * @param blocks Each block.
     */
    public record Grown(int[] xs, int[] ys, int[] zs, Block[] blocks) {
        /**
         * How many blocks.
         *
         * @return The count.
         */
        public int size() {
            return blocks.length;
        }
    }

    /** How many crown points per cubic block of crown, before the limits. */
    private static final double POINTS_PER_BLOCK = 0.5;

    /** The fewest crown points. */
    private static final int MIN_POINTS = 40;

    /** The most crown points. */
    private static final int MAX_POINTS = 400;

    /** How far a branch grows each step, in blocks. */
    private static final double STEP = 1;

    /** Points this close to a branch are used up. */
    private static final double KILL_DISTANCE = 1.6;

    /** How much branches lean up as they grow. */
    private static final double UPWARD = 0.3;

    /** The most growth steps. */
    private static final int MAX_ITERATIONS = 200;

    /** Branches carrying at least this many tips are trunk-thick: a plus shape. */
    private static final int THICK_TIPS = 40;

    /** The share of leaves left out inside the leaf ball, for gaps. */
    private static final double LEAF_GAPS = 0.12;

    /** The share left out at the leaf ball's surface, for a ragged edge. */
    private static final double LEAF_EDGE_GAPS = 0.45;

    /** How thick the leaf ball's ragged surface is, in blocks. */
    private static final double LEAF_EDGE = 0.7;

    /** Bits to shift for the y part of a packed position. */
    private static final int Y_SHIFT = 16;

    /** Bits to shift for the x part of a packed position. */
    private static final int X_SHIFT = 8;

    /** Added to offsets so they pack as positive numbers. */
    private static final int OFFSET = 128;

    /** Added to y offsets so they pack as positive numbers. */
    private static final int Y_OFFSET = 1024;

    /** The mask of one packed part. */
    private static final int PART = 0xff;

    /** Mixed into leaf gap rolls. */
    private static final long LEAF_SALT = 0x2545_F491_4F6C_DD1DL;

    /** How far up to grow past the trunk if no crown point reaches it. */
    private static final int MAX_TRUNK_EXTENSION = 64;

    /**
     * Check the ranges.
     *
     * @throws IllegalArgumentException If a range isn't two numbers with the first no more than the
     *     second, or is out of bounds.
     */
    public TreeSpecies {
        height = check("height", height, 1, OFFSET * 4.0);
        crownStart = check("crown_start", crownStart, 0, 1);
        crownRadius = check("crown_radius", crownRadius, 0.5, OFFSET / 2.0);
        leafRadius = check("leaf_radius", leafRadius, 0, OFFSET / 4.0);
    }

    /**
     * Check one range.
     *
     * @param name Its name, for the message.
     * @param range The range.
     * @param low The lowest allowed value.
     * @param high The highest allowed value.
     * @return A copy.
     */
    private static double[] check(String name, double[] range, double low, double high) {
        if (range.length != 2 || range[0] > range[1] || range[0] < low || range[1] > high) {
            throw new IllegalArgumentException(
                    name + " must be two numbers from " + low + " to " + high + ", lowest first");
        }
        return range.clone();
    }

    /**
     * How far from the trunk any tree of the species can reach, in blocks.
     *
     * @return The distance.
     */
    public int maxRadius() {
        return (int) Math.ceil(crownRadius[1] + leafRadius[1]) + 2;
    }

    /**
     * How high above its foot any tree of the species can reach, in blocks.
     *
     * @return The height.
     */
    public int maxHeight() {
        return (int) Math.ceil(height[1] + leafRadius[1]) + 2;
    }

    /**
     * Grow one tree.
     *
     * @param seed The tree's own seed.
     * @return Its blocks, within {@link #maxRadius()} of the trunk and {@link #maxHeight()} above
     *     its foot.
     */
    public Grown grow(long seed) {
        final SplittableRandom random = new SplittableRandom(seed);
        final double treeHeight = draw(random, height);
        final double crownBottom = treeHeight * draw(random, crownStart);
        final double radius = draw(random, crownRadius);
        final double leafBall = draw(random, leafRadius);
        final double crownHalf = Math.max(1, (treeHeight - crownBottom) / 2);
        final double crownMiddle = crownBottom + crownHalf;

        final int pointCount =
                (int)
                        Math.clamp(
                                4.0
                                        / 3.0
                                        * Math.PI
                                        * radius
                                        * radius
                                        * crownHalf
                                        * POINTS_PER_BLOCK,
                                MIN_POINTS,
                                MAX_POINTS);
        final List<double[]> points = new ArrayList<>();
        while (points.size() < pointCount) {
            final double x = random.nextDouble() * 2 - 1;
            final double y = random.nextDouble() * 2 - 1;
            final double z = random.nextDouble() * 2 - 1;
            if (x * x + y * y + z * z <= 1) {
                points.add(new double[] {x * radius, crownMiddle + y * crownHalf, z * radius});
            }
        }

        final List<double[]> nodes = new ArrayList<>();
        final List<Integer> parents = new ArrayList<>();
        nodes.add(new double[] {0, 0, 0});
        parents.add(-1);
        for (int y = 1; y <= (int) crownBottom; ++y) {
            nodes.add(new double[] {0, y, 0});
            parents.add(nodes.size() - 2);
        }
        colonize(points, nodes, parents, Math.max(radius, crownHalf) + 2, treeHeight);
        return blocks(seed, nodes, parents, leafBall, crownBottom);
    }

    /**
     * Grow branches toward the crown points until they are used up or nothing grows.
     *
     * @param points The crown points; used ones are removed.
     * @param nodes The branch nodes, the trunk first; grown ones are added.
     * @param parents Each node's parent, -1 for the root.
     * @param influence How far a point pulls a node from.
     * @param treeHeight The tree's height, the most the trunk extends to reach the crown.
     */
    private static void colonize(
            List<double[]> points,
            List<double[]> nodes,
            List<Integer> parents,
            double influence,
            double treeHeight) {
        int extended = 0;
        for (int iteration = 0; iteration < MAX_ITERATIONS && !points.isEmpty(); ++iteration) {
            final Map<Integer, double[]> pulls = new HashMap<>();
            for (int p = points.size() - 1; p >= 0; --p) {
                final double[] point = points.get(p);
                int nearest = -1;
                double best = influence * influence;
                boolean used = false;
                for (int n = 0; n < nodes.size(); ++n) {
                    final double d = distanceSquared(point, nodes.get(n));
                    if (d < KILL_DISTANCE * KILL_DISTANCE) {
                        used = true;
                        break;
                    }
                    if (d < best) {
                        best = d;
                        nearest = n;
                    }
                }
                if (used) {
                    points.remove(p);
                    continue;
                }
                if (nearest >= 0) {
                    final double[] node = nodes.get(nearest);
                    final double length = Math.sqrt(best);
                    final double[] pull = pulls.computeIfAbsent(nearest, k -> new double[3]);
                    for (int axis = 0; axis < 3; ++axis) {
                        pull[axis] += (point[axis] - node[axis]) / length;
                    }
                }
            }
            if (pulls.isEmpty()) {
                // Nothing reaches the crown yet: grow the trunk's tip straight up
                final int tip = nodes.size() - 1;
                if (extended >= MAX_TRUNK_EXTENSION || nodes.get(tip)[1] >= treeHeight) {
                    return;
                }
                final double[] top = nodes.get(tip);
                nodes.add(new double[] {top[0], top[1] + STEP, top[2]});
                parents.add(tip);
                ++extended;
                continue;
            }
            // Grow in node order, so the result doesn't depend on the map's order
            final List<Integer> growing = new ArrayList<>(pulls.keySet());
            growing.sort(null);
            for (int n : growing) {
                final double[] pull = pulls.get(n);
                pull[1] += UPWARD;
                final double length =
                        Math.sqrt(pull[0] * pull[0] + pull[1] * pull[1] + pull[2] * pull[2]);
                if (length == 0) {
                    continue;
                }
                final double[] from = nodes.get(n);
                nodes.add(
                        new double[] {
                            from[0] + pull[0] / length * STEP,
                            from[1] + pull[1] / length * STEP,
                            from[2] + pull[2] / length * STEP
                        });
                parents.add(n);
            }
        }
    }

    /**
     * Turn branches into logs and leaves.
     *
     * @param seed The tree's seed, for gaps in the leaves.
     * @param nodes The branch nodes.
     * @param parents Each node's parent.
     * @param leafBall How thick the leaves are around tips.
     * @param crownBottom Where the crown starts, above which every few nodes carry leaves too.
     * @return The blocks.
     */
    private Grown blocks(
            long seed,
            List<double[]> nodes,
            List<Integer> parents,
            double leafBall,
            double crownBottom) {
        final int count = nodes.size();
        final int[] children = new int[count];
        final int[] tips = new int[count];
        for (int n = 1; n < count; ++n) {
            ++children[parents.get(n)];
        }
        // Children always come after their parents, so walking backwards sums each subtree
        for (int n = count - 1; n >= 0; --n) {
            if (children[n] == 0) {
                ++tips[n];
            }
            if (parents.get(n) >= 0) {
                tips[parents.get(n)] += tips[n];
            }
        }
        final Map<Integer, Block> out = new HashMap<>();
        final int reach = maxRadius();
        final int top = maxHeight();
        for (int n = 0; n < count; ++n) {
            if (children[n] == 0 || (nodes.get(n)[1] > crownBottom && n % 3 == 0)) {
                leaves(seed, nodes.get(n), leafBall, out, reach, top);
            }
        }
        for (int n = 1; n < count; ++n) {
            logs(nodes.get(parents.get(n)), nodes.get(n), tips[n] >= THICK_TIPS, out, reach, top);
        }
        if (count == 1) {
            put(out, 0, 0, 0, trunk, reach, top);
        }
        final List<Integer> keys = new ArrayList<>(out.keySet());
        keys.sort(null);
        final int[] xs = new int[keys.size()];
        final int[] ys = new int[keys.size()];
        final int[] zs = new int[keys.size()];
        final Block[] blocks = new Block[keys.size()];
        for (int i = 0; i < keys.size(); ++i) {
            final int key = keys.get(i);
            ys[i] = (key >>> Y_SHIFT) - Y_OFFSET;
            xs[i] = ((key >>> X_SHIFT) & PART) - OFFSET;
            zs[i] = (key & PART) - OFFSET;
            blocks[i] = out.get(key);
        }
        return new Grown(xs, ys, zs, blocks);
    }

    /**
     * Fill a ball of leaves, with gaps, where there is nothing yet.
     *
     * @param seed The tree's seed.
     * @param center The ball's center.
     * @param ball Its radius.
     * @param out The blocks so far.
     * @param reach How far from the trunk blocks may go.
     * @param top How high blocks may go.
     */
    private void leaves(
            long seed, double[] center, double ball, Map<Integer, Block> out, int reach, int top) {
        final int r = (int) Math.ceil(ball);
        final int cx = (int) Math.floor(center[0] + 0.5);
        final int cy = (int) Math.floor(center[1] + 0.5);
        final int cz = (int) Math.floor(center[2] + 0.5);
        for (int dy = -r; dy <= r; ++dy) {
            for (int dz = -r; dz <= r; ++dz) {
                for (int dx = -r; dx <= r; ++dx) {
                    final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > ball) {
                        continue;
                    }
                    final int x = cx + dx;
                    final int y = cy + dy;
                    final int z = cz + dz;
                    final double roll =
                            (WorldgenHash.combine(
                                                    seed ^ LEAF_SALT,
                                                    WorldgenHash.combine(
                                                            x, WorldgenHash.combine(y, z)))
                                            >>> 11)
                                    * 0x1.0p-53;
                    final double gaps = distance > ball - LEAF_EDGE ? LEAF_EDGE_GAPS : LEAF_GAPS;
                    if (roll < gaps) {
                        continue;
                    }
                    if (inside(x, y, z, reach, top) && !out.containsKey(pack(x, y, z))) {
                        out.put(pack(x, y, z), leaves);
                    }
                }
            }
        }
    }

    /**
     * Lay logs along a branch, replacing leaves.
     *
     * @param from The branch's start.
     * @param to Its end.
     * @param thick Whether it is trunk-thick.
     * @param out The blocks so far.
     * @param reach How far from the trunk blocks may go.
     * @param top How high blocks may go.
     */
    private void logs(
            double[] from,
            double[] to,
            boolean thick,
            Map<Integer, Block> out,
            int reach,
            int top) {
        final int samples = 4;
        for (int s = 0; s <= samples; ++s) {
            final double t = (double) s / samples;
            final int x = (int) Math.floor(from[0] + (to[0] - from[0]) * t + 0.5);
            final int y = (int) Math.floor(from[1] + (to[1] - from[1]) * t + 0.5);
            final int z = (int) Math.floor(from[2] + (to[2] - from[2]) * t + 0.5);
            put(out, x, y, z, trunk, reach, top);
            if (thick) {
                put(out, x + 1, y, z, trunk, reach, top);
                put(out, x - 1, y, z, trunk, reach, top);
                put(out, x, y, z + 1, trunk, reach, top);
                put(out, x, y, z - 1, trunk, reach, top);
            }
        }
    }

    /**
     * Put a block, if it is within the tree's bounds.
     *
     * @param out The blocks.
     * @param x The x offset.
     * @param y The y offset.
     * @param z The z offset.
     * @param block The block.
     * @param reach How far from the trunk blocks may go.
     * @param top How high blocks may go.
     */
    private static void put(
            Map<Integer, Block> out, int x, int y, int z, Block block, int reach, int top) {
        if (inside(x, y, z, reach, top)) {
            out.put(pack(x, y, z), block);
        }
    }

    /**
     * Whether an offset is within a tree's bounds.
     *
     * @param x The x offset.
     * @param y The y offset.
     * @param z The z offset.
     * @param reach How far from the trunk.
     * @param top How high.
     * @return True if inside.
     */
    private static boolean inside(int x, int y, int z, int reach, int top) {
        return Math.abs(x) <= reach && Math.abs(z) <= reach && y >= 0 && y <= top;
    }

    /**
     * Pack an offset into one number.
     *
     * @param x The x offset.
     * @param y The y offset.
     * @param z The z offset.
     * @return The packed offset.
     */
    private static int pack(int x, int y, int z) {
        return ((y + Y_OFFSET) << Y_SHIFT) | ((x + OFFSET) << X_SHIFT) | (z + OFFSET);
    }

    /**
     * Draw a value from a range.
     *
     * @param random The tree's random numbers.
     * @param range The range.
     * @return The value.
     */
    private static double draw(SplittableRandom random, double[] range) {
        return range[0] + (range[1] - range[0]) * random.nextDouble();
    }

    /**
     * The squared distance between two points.
     *
     * @param a One point.
     * @param b The other.
     * @return The squared distance.
     */
    private static double distanceSquared(double[] a, double[] b) {
        final double x = a[0] - b[0];
        final double y = a[1] - b[1];
        final double z = a[2] - b[2];
        return x * x + y * y + z * z;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TreeSpecies that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "TreeSpecies[" + id + "]";
    }
}
