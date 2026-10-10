package com.ikalagaming.graphics.ui.graph;

import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.graph.Port.Side;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Works out the path of links between nodes, from the nodes' rectangles. Pure geometry, in pixels,
 * so it can be tested on its own.
 */
public final class Router {

    /**
     * A routed link.
     *
     * @param points The polyline, or for a curve, the start, two control points and the end.
     * @param curve Whether the points are a cubic bezier curve.
     */
    public record Path(@NonNull List<Vector2f> points, boolean curve) {}

    /**
     * One link to route, with its sides already resolved.
     *
     * @param from A key for the node the link leaves.
     * @param fromSide The side it leaves.
     * @param fromPoint Where it leaves.
     * @param to A key for the node the link enters.
     * @param toSide The side it enters.
     * @param toPoint Where it enters.
     * @param route How it is routed.
     */
    public record Link(
            @NonNull Object from,
            @NonNull Side fromSide,
            @NonNull Vector2f fromPoint,
            @NonNull Object to,
            @NonNull Side toSide,
            @NonNull Vector2f toPoint,
            @NonNull Route route) {}

    /**
     * Resolve the sides of a link: {@code auto} becomes the sides facing each other, picking the
     * axis the nodes are furthest apart along.
     *
     * @param from The node the link leaves.
     * @param fromSide The side asked for.
     * @param to The node the link enters.
     * @param toSide The side asked for.
     * @return The from side and the to side, never {@code auto}.
     */
    public static Side[] resolveSides(
            @NonNull RectFloat from,
            @NonNull Side fromSide,
            @NonNull RectFloat to,
            @NonNull Side toSide) {
        float dx = to.getCenterX() - from.getCenterX();
        float dy = to.getCenterY() - from.getCenterY();
        boolean vertical = Math.abs(dy) >= Math.abs(dx);
        Side facingFrom;
        Side facingTo;
        if (vertical) {
            facingFrom = dy >= 0 ? Side.BOTTOM : Side.TOP;
            facingTo = dy >= 0 ? Side.TOP : Side.BOTTOM;
        } else {
            facingFrom = dx >= 0 ? Side.RIGHT : Side.LEFT;
            facingTo = dx >= 0 ? Side.LEFT : Side.RIGHT;
        }
        return new Side[] {
            fromSide == Side.AUTO ? facingFrom : fromSide, toSide == Side.AUTO ? facingTo : toSide
        };
    }

    /**
     * Where a port is on a node.
     *
     * @param rect The node.
     * @param side The side, not {@code auto}.
     * @param offset How far along the side from its middle, as a fraction of its length.
     * @return The point.
     */
    public static Vector2f anchor(@NonNull RectFloat rect, @NonNull Side side, float offset) {
        float x = rect.getCenterX() + offset * rect.getWidth();
        float y = rect.getCenterY() + offset * rect.getHeight();
        return switch (side) {
            case TOP -> new Vector2f(x, rect.getTop());
            case BOTTOM -> new Vector2f(x, rect.getBottom());
            case LEFT -> new Vector2f(rect.getLeft(), y);
            case RIGHT -> new Vector2f(rect.getRight(), y);
            case CENTER, AUTO -> new Vector2f(rect.getCenterX(), rect.getCenterY());
        };
    }

    /**
     * The direction a link leaves a side, or for the center, toward the other end.
     *
     * @param side The side.
     * @param from Where the link is.
     * @param toward The other end of the link.
     * @return A unit vector.
     */
    static Vector2f direction(@NonNull Side side, Vector2f from, Vector2f toward) {
        return switch (side) {
            case TOP -> new Vector2f(0, -1);
            case BOTTOM -> new Vector2f(0, 1);
            case LEFT -> new Vector2f(-1, 0);
            case RIGHT -> new Vector2f(1, 0);
            case CENTER, AUTO -> {
                Vector2f d = new Vector2f(toward).sub(from);
                yield d.lengthSquared() < 1e-8f ? new Vector2f(1, 0) : d.normalize();
            }
        };
    }

    /**
     * Find the shared midlines for orthogonal links, so links that leave the same side of a node
     * branch off one trunk, and links that enter the same side of a node merge into one. A trunk
     * sits halfway between the shared side and the nearest node at the other ends.
     *
     * @param links The links.
     * @return For each link, the coordinate its middle runs along (y for links between top and
     *     bottom sides, x for left and right), or null if it has none.
     */
    public static List<Float> midlines(@NonNull List<Link> links) {
        Map<List<Object>, List<Integer>> leaving = new HashMap<>();
        Map<List<Object>, List<Integer>> entering = new HashMap<>();
        for (int i = 0; i < links.size(); ++i) {
            Link link = links.get(i);
            if (!sharesTrunk(link)) {
                continue;
            }
            leaving.computeIfAbsent(List.of(link.from(), link.fromSide()), k -> new ArrayList<>())
                    .add(i);
            entering.computeIfAbsent(List.of(link.to(), link.toSide()), k -> new ArrayList<>())
                    .add(i);
        }

        List<Float> result = new ArrayList<>(links.size());
        for (int i = 0; i < links.size(); ++i) {
            result.add(null);
        }
        // Links leaving together share a trunk near their source
        for (List<Integer> group : leaving.values()) {
            float trunk = trunk(links, group, true);
            for (int i : group) {
                result.set(i, trunk);
            }
        }
        // Links that merge into a node, and don't branch from a shared trunk, merge near it
        for (List<Integer> group : entering.values()) {
            if (group.size() < 2) {
                continue;
            }
            List<Integer> single = new ArrayList<>();
            for (int i : group) {
                Link link = links.get(i);
                if (leaving.get(List.of(link.from(), link.fromSide())).size() == 1) {
                    single.add(i);
                }
            }
            if (single.size() < 2) {
                continue;
            }
            float trunk = trunk(links, single, false);
            for (int i : single) {
                result.set(i, trunk);
            }
        }
        return result;
    }

    /**
     * Whether a link has a middle part that runs across, so it can share a trunk: orthogonal, and
     * leaving and entering along the same axis.
     *
     * @param link The link.
     * @return True if it can share a trunk.
     */
    private static boolean sharesTrunk(Link link) {
        return link.route() == Route.ORTHOGONAL
                && ((link.fromSide().vertical() && link.toSide().vertical())
                        || (link.fromSide().horizontal() && link.toSide().horizontal()));
    }

    /**
     * The trunk for a group of links sharing a side: halfway between the shared side and the
     * nearest of the other ends.
     *
     * @param links Every link.
     * @param group The links sharing the side.
     * @param fromShared Whether they share the side they leave, rather than the one they enter.
     * @return The trunk's coordinate.
     */
    private static float trunk(List<Link> links, List<Integer> group, boolean fromShared) {
        Link first = links.get(group.getFirst());
        Side side = fromShared ? first.fromSide() : first.toSide();
        Vector2f shared = fromShared ? first.fromPoint() : first.toPoint();
        boolean vertical = side.vertical();
        float base = vertical ? shared.y : shared.x;
        float nearest = Float.NaN;
        for (int i : group) {
            Link link = links.get(i);
            Vector2f other = fromShared ? link.toPoint() : link.fromPoint();
            float value = vertical ? other.y : other.x;
            if (Float.isNaN(nearest) || Math.abs(value - base) < Math.abs(nearest - base)) {
                nearest = value;
            }
        }
        return (base + nearest) / 2;
    }

    /**
     * Route a link.
     *
     * @param link The link, with its sides resolved.
     * @param midline The coordinate a shared trunk runs along, or null to go halfway.
     * @param curveReach How far a curve's control points reach out from its ends, in pixels.
     * @return The path.
     */
    public static Path route(@NonNull Link link, Float midline, float curveReach) {
        Vector2f a = link.fromPoint();
        Vector2f b = link.toPoint();
        return switch (link.route()) {
            case STRAIGHT -> new Path(List.of(new Vector2f(a), new Vector2f(b)), false);
            case CURVE -> {
                Vector2f outA = direction(link.fromSide(), a, b);
                Vector2f outB = direction(link.toSide(), b, a);
                float reach = Math.max(curveReach, a.distance(b) * 0.4f);
                yield new Path(
                        List.of(
                                new Vector2f(a),
                                new Vector2f(outA).mul(reach).add(a),
                                new Vector2f(outB).mul(reach).add(b),
                                new Vector2f(b)),
                        true);
            }
            case ORTHOGONAL -> new Path(orthogonal(link, midline), false);
        };
    }

    /**
     * Route a link with horizontal and vertical lines only.
     *
     * @param link The link.
     * @param midline The coordinate the middle runs along, or null to go halfway.
     * @return The polyline.
     */
    private static List<Vector2f> orthogonal(Link link, Float midline) {
        Vector2f a = link.fromPoint();
        Vector2f b = link.toPoint();
        Side from = sideFor(link.fromSide(), a, b);
        Side to = sideFor(link.toSide(), b, a);
        List<Vector2f> points = new ArrayList<>();
        points.add(new Vector2f(a));
        if (from.vertical() && to.vertical()) {
            if (Math.abs(a.x - b.x) >= 0.5f || midline != null) {
                float y = Objects.requireNonNullElse(midline, (a.y + b.y) / 2);
                points.add(new Vector2f(a.x, y));
                points.add(new Vector2f(b.x, y));
            }
        } else if (from.horizontal() && to.horizontal()) {
            if (Math.abs(a.y - b.y) >= 0.5f || midline != null) {
                float x = Objects.requireNonNullElse(midline, (a.x + b.x) / 2);
                points.add(new Vector2f(x, a.y));
                points.add(new Vector2f(x, b.y));
            }
        } else if (from.vertical()) {
            // Down or up, then across into the side
            points.add(new Vector2f(a.x, b.y));
        } else {
            // Across, then down or up into the side
            points.add(new Vector2f(b.x, a.y));
        }
        points.add(new Vector2f(b));
        return removeRepeats(points);
    }

    /**
     * The side an orthogonal route treats an end as leaving from. The center leaves along the axis
     * the other end is furthest along.
     *
     * @param side The side.
     * @param at Where the end is.
     * @param other The other end.
     * @return The side, never {@code center} or {@code auto}.
     */
    private static Side sideFor(Side side, Vector2f at, Vector2f other) {
        if (side != Side.CENTER && side != Side.AUTO) {
            return side;
        }
        float dx = other.x - at.x;
        float dy = other.y - at.y;
        if (Math.abs(dy) >= Math.abs(dx)) {
            return dy >= 0 ? Side.BOTTOM : Side.TOP;
        }
        return dx >= 0 ? Side.RIGHT : Side.LEFT;
    }

    /**
     * Drop points that repeat the one before, which happen when a trunk passes through an end.
     *
     * @param points The polyline.
     * @return The polyline without repeated points.
     */
    private static List<Vector2f> removeRepeats(List<Vector2f> points) {
        List<Vector2f> result = new ArrayList<>(points.size());
        for (Vector2f point : points) {
            if (result.isEmpty() || result.getLast().distanceSquared(point) > 1e-6f) {
                result.add(point);
            }
        }
        if (result.size() == 1) {
            result.add(new Vector2f(result.getFirst()));
        }
        return result;
    }

    /** Static helpers only. */
    private Router() {}
}
