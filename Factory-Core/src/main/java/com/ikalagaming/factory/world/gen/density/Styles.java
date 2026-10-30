package com.ikalagaming.factory.world.gen.density;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Terrain built from terrain styles: each style is a shape plus a weight saying how strongly it
 * applies where. Shape styles are blended by weight into the base surface, and overlay styles then
 * add solid to it or carve holes in it, in priority order.
 *
 * <p>Every step is a pure function of the point: a style whose weight is 0 at a point has no effect
 * there, so skipping styles that are absent over a whole chunk never changes a value, and
 * neighboring chunks always agree.
 *
 * <p>Where the shape weights add up to less than 1, the fallback shape fills the rest, so the base
 * is always a blend whose weights total at least 1 and there is never a division by zero. Its
 * bounds are the hull of the shapes present.
 *
 * <p>Overlays with their own sampling grid ({@link #fine()}) are applied after the rest, by
 * priority among themselves. The generator samples {@link #coarse()} on the world's grid, and then
 * applies the fine overlays at each block, so a thin shape stays sharp.
 */
public final class Styles implements DensityNode {

    /** What an overlay does to the terrain under it. */
    public enum Op {
        /** Adds solid, like floating islands. */
        UNION,
        /** Removes solid, like chasms. */
        CARVE
    }

    /**
     * One style, compiled.
     *
     * @param id The style's data ID.
     * @param weight How strongly it applies, clamped to 0 to 1 when read.
     * @param density Its terrain.
     * @param op What it does, for overlays; null for shape styles.
     */
    public record Style(
            @NonNull String id, @NonNull DensityNode weight, @NonNull DensityNode density, Op op) {

        /**
         * The weight at a point.
         *
         * @param x The x coordinate.
         * @param y The y coordinate.
         * @param z The z coordinate.
         * @param cache The chunk's cache, or null.
         * @return From 0 to 1.
         */
        double weightAt(double x, double y, double z, EvalCache cache) {
            return Math.clamp(weight.value(x, y, z, cache), 0, 1);
        }

        /**
         * The weight's range over a box.
         *
         * @param box The box.
         * @return The range, within 0 to 1.
         */
        public Interval weightBounds(@NonNull Box box) {
            return weight.bounds(box).clamp(0, 1);
        }
    }

    /** The shape styles, blended into the base surface. */
    private final List<Style> shapes;

    /** The shape that fills in where the shape weights add up to less than 1. */
    private final DensityNode fallback;

    /** The overlays on the world's grid, in the order they apply. */
    private final List<Style> coarseOverlays;

    /** The overlays with their own grid, in the order they apply, after the coarse ones. */
    private final List<Style> fineOverlays;

    /** How far out of range a faded-out overlay sits. */
    private final double fade;

    /** Which axes the value depends on. */
    private final int axes;

    /** The part sampled on the world's grid. */
    private final DensityNode coarse;

    /**
     * Combine styles.
     *
     * @param shapes The shape styles.
     * @param fallback The shape used where shape weights total less than 1.
     * @param coarseOverlays The overlays on the world's grid, in the order they apply.
     * @param fineOverlays The overlays with their own grid, in the order they apply.
     * @param fade How far out of range a faded-out overlay sits, above zero.
     */
    public Styles(
            @NonNull List<Style> shapes,
            @NonNull DensityNode fallback,
            @NonNull List<Style> coarseOverlays,
            @NonNull List<Style> fineOverlays,
            double fade) {
        this.shapes = List.copyOf(shapes);
        this.fallback = fallback;
        this.coarseOverlays = List.copyOf(coarseOverlays);
        this.fineOverlays = List.copyOf(fineOverlays);
        this.fade = fade;
        int mask = fallback.axes();
        for (Style style : allStyles()) {
            mask |= style.weight().axes() | style.density().axes();
        }
        axes = mask;
        coarse = new Coarse();
    }

    /**
     * Every style, shapes first, then overlays in the order they apply.
     *
     * @return The styles.
     */
    public List<Style> allStyles() {
        List<Style> all = new ArrayList<>(shapes);
        all.addAll(coarseOverlays);
        all.addAll(fineOverlays);
        return all;
    }

    /**
     * The overlays with their own sampling grid, which the generator applies per block.
     *
     * @return The overlays, in the order they apply.
     */
    public List<Style> fine() {
        return fineOverlays;
    }

    /**
     * Everything except the fine overlays, for sampling on the world's grid.
     *
     * @return The coarse part.
     */
    public DensityNode coarse() {
        return coarse;
    }

    /**
     * The fine overlays that can have an effect somewhere in a box.
     *
     * @param box The box.
     * @return Those whose weight isn't 0 everywhere in it, in the order they apply.
     */
    public List<Style> fineIn(@NonNull Box box) {
        return fineOverlays.stream().filter(s -> s.weightBounds(box).max() > 0).toList();
    }

    /**
     * Apply overlays to a value.
     *
     * @param terrain The value so far.
     * @param overlays The overlays, in the order they apply.
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @param cache The chunk's cache, or null.
     * @return The value after them.
     */
    public double applyOverlays(
            double terrain,
            @NonNull List<Style> overlays,
            double x,
            double y,
            double z,
            EvalCache cache) {
        double result = terrain;
        for (Style overlay : overlays) {
            final double w = overlay.weightAt(x, y, z, cache);
            if (w <= 0) {
                continue;
            }
            final double s = overlay.density().value(x, y, z, cache);
            if (overlay.op() == Op.UNION) {
                result = Math.max(result, -fade + w * (s + fade));
            } else {
                result = Math.min(result, fade + w * (s - fade));
            }
        }
        return result;
    }

    /**
     * The range after overlays.
     *
     * @param terrain The range so far.
     * @param overlays The overlays, in the order they apply.
     * @param box The box.
     * @return The range after them.
     */
    private Interval overlayBounds(
            @NonNull Interval terrain, @NonNull List<Style> overlays, @NonNull Box box) {
        Interval result = terrain;
        for (Style overlay : overlays) {
            final Interval w = overlay.weightBounds(box);
            if (w.max() <= 0) {
                continue;
            }
            final Interval s = overlay.density().bounds(box);
            Interval applied;
            if (overlay.op() == Op.UNION) {
                final Interval effect = Interval.of(-fade).add(w.mul(s.add(Interval.of(fade))));
                applied = result.max(effect);
            } else {
                final Interval effect = Interval.of(fade).add(w.mul(s.add(Interval.of(-fade))));
                applied = result.min(effect);
            }
            // Where the weight is 0 the value is unchanged
            result = w.min() > 0 ? applied : applied.union(result);
        }
        return result;
    }

    /**
     * The blended base surface at a point.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     * @param z The z coordinate.
     * @param cache The chunk's cache, or null.
     * @return The value.
     */
    private double base(double x, double y, double z, EvalCache cache) {
        double sum = 0;
        double total = 0;
        for (Style shape : shapes) {
            final double w = shape.weightAt(x, y, z, cache);
            if (w <= 0) {
                continue;
            }
            sum += w * shape.density().value(x, y, z, cache);
            total += w;
        }
        if (total < 1) {
            sum += (1 - total) * fallback.value(x, y, z, cache);
            total = 1;
        }
        return sum / total;
    }

    /**
     * The base surface's range over a box.
     *
     * @param box The box.
     * @return The hull of the shapes that can contribute there.
     */
    private Interval baseBounds(@NonNull Box box) {
        Interval result = null;
        double lowestTotal = 0;
        for (Style shape : shapes) {
            final Interval w = shape.weightBounds(box);
            if (w.max() <= 0) {
                continue;
            }
            final Interval s = shape.density().bounds(box);
            result = result == null ? s : result.union(s);
            lowestTotal += w.min();
        }
        if (lowestTotal < 1) {
            final Interval f = fallback.bounds(box);
            result = result == null ? f : result.union(f);
        }
        return result;
    }

    @Override
    public double value(double x, double y, double z, EvalCache cache) {
        return applyOverlays(coarse.value(x, y, z, cache), fineOverlays, x, y, z, cache);
    }

    @Override
    public Interval bounds(@NonNull Box box) {
        return overlayBounds(coarse.bounds(box), fineOverlays, box);
    }

    @Override
    public int axes() {
        return axes;
    }

    @Override
    public String type() {
        return "styles";
    }

    @Override
    public List<DensityNode> children() {
        List<DensityNode> children = new ArrayList<>();
        children.add(fallback);
        for (Style style : allStyles()) {
            children.add(style.weight());
            children.add(style.density());
        }
        return children;
    }

    /** The blended shapes and the overlays on the world's grid. */
    private final class Coarse implements DensityNode {
        @Override
        public double value(double x, double y, double z, EvalCache cache) {
            return applyOverlays(base(x, y, z, cache), coarseOverlays, x, y, z, cache);
        }

        @Override
        public Interval bounds(@NonNull Box box) {
            return overlayBounds(baseBounds(box), coarseOverlays, box);
        }

        @Override
        public int axes() {
            return axes;
        }

        @Override
        public String type() {
            return "styles_coarse";
        }

        @Override
        public List<DensityNode> children() {
            return Styles.this.children();
        }
    }
}
