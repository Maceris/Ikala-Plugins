package com.ikalagaming.graphics.ui.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.graphics.ui.graph.Port.Side;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/** Routing, path strokes and highlights, without IkGui. */
class GraphGeometryTest {

    private static void assertPoint(float x, float y, Vector2f point) {
        assertEquals(x, point.x, 1e-3, "x of " + point);
        assertEquals(y, point.y, 1e-3, "y of " + point);
    }

    private static Router.Link link(
            Object from, RectFloat fromRect, Object to, RectFloat toRect, Route route) {
        Side[] sides = Router.resolveSides(fromRect, Side.AUTO, toRect, Side.AUTO);
        return new Router.Link(
                from,
                sides[0],
                Router.anchor(fromRect, sides[0], 0),
                to,
                sides[1],
                Router.anchor(toRect, sides[1], 0),
                route);
    }

    @Test
    void autoSidesFaceEachOther() {
        RectFloat top = new RectFloat(0, 0, 20, 20);
        RectFloat below = new RectFloat(0, 100, 20, 120);
        RectFloat right = new RectFloat(100, 0, 120, 20);
        Side[] down = Router.resolveSides(top, Side.AUTO, below, Side.AUTO);
        assertEquals(Side.BOTTOM, down[0]);
        assertEquals(Side.TOP, down[1]);
        Side[] across = Router.resolveSides(right, Side.AUTO, top, Side.AUTO);
        assertEquals(Side.LEFT, across[0]);
        assertEquals(Side.RIGHT, across[1]);
        // A side that is asked for is kept
        assertEquals(Side.LEFT, Router.resolveSides(top, Side.LEFT, below, Side.AUTO)[0]);
    }

    @Test
    void anchorsSitOnTheSideWithAnOffset() {
        RectFloat rect = new RectFloat(0, 0, 40, 20);
        assertPoint(20, 0, Router.anchor(rect, Side.TOP, 0));
        assertPoint(30, 20, Router.anchor(rect, Side.BOTTOM, 0.25f));
        assertPoint(0, 5, Router.anchor(rect, Side.LEFT, -0.25f));
        assertPoint(20, 10, Router.anchor(rect, Side.CENTER, 0));
    }

    @Test
    void orthogonalRoutesTurnAtTheMidline() {
        Router.Link down =
                link(
                        "a",
                        new RectFloat(0, 0, 20, 20),
                        "b",
                        new RectFloat(60, 100, 80, 120),
                        Route.ORTHOGONAL);
        List<Vector2f> points = Router.route(down, null, 30).points();
        assertEquals(4, points.size());
        assertPoint(10, 20, points.get(0));
        assertPoint(10, 60, points.get(1));
        assertPoint(70, 60, points.get(2));
        assertPoint(70, 100, points.get(3));

        // Lined up, so it goes straight
        Router.Link straight =
                link(
                        "a",
                        new RectFloat(0, 0, 20, 20),
                        "b",
                        new RectFloat(0, 100, 20, 120),
                        Route.ORTHOGONAL);
        assertEquals(2, Router.route(straight, null, 30).points().size());
    }

    @Test
    void orthogonalRoutesBetweenAxesTurnOnce() {
        RectFloat from = new RectFloat(0, 0, 20, 20);
        RectFloat to = new RectFloat(100, 100, 120, 120);
        Router.Link link =
                new Router.Link(
                        "a",
                        Side.BOTTOM,
                        Router.anchor(from, Side.BOTTOM, 0),
                        "b",
                        Side.LEFT,
                        Router.anchor(to, Side.LEFT, 0),
                        Route.ORTHOGONAL);
        List<Vector2f> points = Router.route(link, null, 30).points();
        assertEquals(3, points.size());
        assertPoint(10, 110, points.get(1));
    }

    @Test
    void curvesLeaveAlongTheirSides() {
        Router.Link link =
                link(
                        "a",
                        new RectFloat(0, 0, 20, 20),
                        "b",
                        new RectFloat(0, 200, 20, 220),
                        Route.CURVE);
        Router.Path path = Router.route(link, null, 30);
        assertTrue(path.curve());
        List<Vector2f> points = path.points();
        // Down out of the bottom of the first, and up into the top of the second
        assertEquals(10, points.get(1).x, 1e-3);
        assertTrue(points.get(1).y > points.get(0).y);
        assertEquals(10, points.get(2).x, 1e-3);
        assertTrue(points.get(2).y < points.get(3).y);
    }

    @Test
    void linksLeavingTheSameSideShareATrunk() {
        RectFloat parent = new RectFloat(40, 0, 60, 20);
        RectFloat near = new RectFloat(0, 60, 20, 80);
        RectFloat far = new RectFloat(80, 100, 100, 120);
        List<Router.Link> links =
                List.of(
                        link("p", parent, "near", near, Route.ORTHOGONAL),
                        link("p", parent, "far", far, Route.ORTHOGONAL));
        List<Float> midlines = Router.midlines(links);
        // Halfway between the parent's bottom and the nearest child's top
        assertEquals(40, midlines.get(0), 1e-3);
        assertEquals(40, midlines.get(1), 1e-3);
    }

    @Test
    void linksEnteringTheSameSideMergeIntoOne() {
        RectFloat child = new RectFloat(40, 100, 60, 120);
        RectFloat left = new RectFloat(0, 0, 20, 20);
        RectFloat right = new RectFloat(80, 40, 100, 60);
        List<Router.Link> links =
                List.of(
                        link("left", left, "child", child, Route.ORTHOGONAL),
                        link("right", right, "child", child, Route.ORTHOGONAL));
        List<Float> midlines = Router.midlines(links);
        // Halfway between the child's top and the nearest parent's bottom
        assertEquals(80, midlines.get(0), 1e-3);
        assertEquals(80, midlines.get(1), 1e-3);
    }

    @Test
    void onlyOrthogonalLinksShareTrunks() {
        RectFloat parent = new RectFloat(40, 0, 60, 20);
        List<Router.Link> links =
                List.of(
                        link("p", parent, "a", new RectFloat(0, 60, 20, 80), Route.STRAIGHT),
                        link("p", parent, "b", new RectFloat(80, 60, 100, 80), Route.STRAIGHT));
        assertNull(Router.midlines(links).get(0));
    }

    @Test
    void roundAndChamferedCorners() {
        List<Vector2f> corner =
                List.of(new Vector2f(0, 0), new Vector2f(10, 0), new Vector2f(10, 10));
        List<Vector2f> chamfered =
                PathStroke.cornered(
                        corner, new PathStroke.Corner(PathStroke.CornerKind.CHAMFER, 3));
        assertEquals(4, chamfered.size());
        assertPoint(7, 0, chamfered.get(1));
        assertPoint(10, 3, chamfered.get(2));

        List<Vector2f> rounded =
                PathStroke.cornered(corner, new PathStroke.Corner(PathStroke.CornerKind.ROUND, 3));
        assertTrue(rounded.size() > 4);
        assertPoint(7, 0, rounded.get(1));
        assertPoint(10, 3, rounded.get(rounded.size() - 2));

        // A corner can't use more than half of a side
        List<Vector2f> clamped =
                PathStroke.cornered(
                        corner, new PathStroke.Corner(PathStroke.CornerKind.CHAMFER, 50));
        assertPoint(5, 0, clamped.get(1));
        assertEquals(corner, PathStroke.cornered(corner, PathStroke.Corner.SHARP));
    }

    @Test
    void dashesContinueAroundCorners() {
        List<Vector2f> corner =
                List.of(new Vector2f(0, 0), new Vector2f(10, 0), new Vector2f(10, 10));
        List<PathStroke.Segment> segments = PathStroke.segments(corner, new float[] {4, 4});
        float[][] expected = {{0, 0, 4, 0}, {8, 0, 10, 0}, {10, 0, 10, 2}, {10, 6, 10, 10}};
        assertEquals(expected.length, segments.size(), segments.toString());
        for (int i = 0; i < expected.length; ++i) {
            PathStroke.Segment segment = segments.get(i);
            assertPoint(expected[i][0], expected[i][1], new Vector2f(segment.x1(), segment.y1()));
            assertPoint(expected[i][2], expected[i][3], new Vector2f(segment.x2(), segment.y2()));
        }
        assertEquals(2, PathStroke.segments(corner, null).size());
    }

    @Test
    void parallelStrokesAreOffsetSideways() {
        List<Vector2f> line = List.of(new Vector2f(0, 0), new Vector2f(10, 0));
        List<Vector2f> left = PathStroke.offset(line, 2);
        assertPoint(0, -2, left.get(0));
        assertPoint(10, -2, left.get(1));

        // Around a corner, the stroke stays the same distance from both sides
        List<Vector2f> corner =
                List.of(new Vector2f(0, 0), new Vector2f(10, 0), new Vector2f(10, 10));
        // Positive offsets go left of the direction, which is the outside of this corner
        List<Vector2f> outside = PathStroke.offset(corner, 2);
        assertPoint(12, -2, outside.get(1));
    }

    @Test
    void stylesParseCornersAndArrows() {
        assertEquals(
                new PathStroke.Corner(PathStroke.CornerKind.ROUND, 12),
                PathStroke.Corner.parse("round 6", 2));
        assertEquals(PathStroke.Corner.SHARP, PathStroke.Corner.parse("wavy", 2));
        assertEquals(PathStroke.Arrow.END, PathStroke.Arrow.parse("end"));
        assertEquals(PathStroke.Arrow.NONE, PathStroke.Arrow.parse(null));
        assertEquals(Route.CURVE, Route.parse("Curve"));
        assertNull(Route.parse("zigzag"));
        assertEquals(new Port(Side.TOP, 0.25f), Port.parse("top 0.25"));
        assertThrows(IllegalArgumentException.class, () -> Port.parse("middle"));
    }

    @Test
    void highlightsFollowLinks() {
        List<Highlight.Link> links =
                List.of(
                        new Highlight.Link("a", "b"),
                        new Highlight.Link("b", "c"),
                        new Highlight.Link("d", "b"),
                        new Highlight.Link("c", "e"));
        assertEquals(Set.of(0, 1, 2), Highlight.EDGES.related(links, "b"));
        assertEquals(Set.of(0, 1, 2), Highlight.ANCESTORS.related(links, "c"));
        assertEquals(Set.of(1, 3), Highlight.DESCENDANTS.related(links, "b"));
        assertTrue(Highlight.NONE.related(links, "b").isEmpty());
        assertTrue(Highlight.EDGES.related(links, null).isEmpty());
    }

    @Test
    void highlightsStopAtCycles() {
        List<Highlight.Link> loop =
                List.of(new Highlight.Link("a", "b"), new Highlight.Link("b", "a"));
        assertEquals(Set.of(0, 1), Highlight.DESCENDANTS.related(loop, "a"));
        assertEquals(Set.of(0, 1), Highlight.ANCESTORS.related(loop, "a"));
    }

    @Test
    void graphNodeOutlinesStayInTheirBounds() {
        RectFloat bounds = new RectFloat(10, 10, 58, 58);
        for (GraphNode.Shape shape : GraphNode.Shape.values()) {
            Vector2f[] points = GraphNode.outline(shape, bounds);
            if (points == null) {
                continue;
            }
            for (Vector2f point : points) {
                assertTrue(
                        bounds.containsWithPadding(point, new Vector2f(1e-3f)),
                        shape + " point " + point + " is outside");
            }
        }
        assertEquals(8, GraphNode.outline(GraphNode.Shape.OCTAGON, bounds).length);
        assertEquals(6, GraphNode.outline(GraphNode.Shape.HEXAGON, bounds).length);
        assertNull(GraphNode.outline(GraphNode.Shape.ROUNDED, bounds));
        assertEquals(GraphNode.Shape.OCTAGON, GraphNode.Shape.parse("octagon"));
    }
}
