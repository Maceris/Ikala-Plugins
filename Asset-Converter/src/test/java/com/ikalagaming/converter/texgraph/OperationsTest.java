package com.ikalagaming.converter.texgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.ui.graph.model.Graph;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Each library node against values worked out by hand. Inputs are made with Expression nodes, so a
 * test says what goes in as a formula of the pixel's position.
 */
class OperationsTest {
    private static final int SIZE = 16;

    private static final float EPSILON = 1e-5f;

    private final TextureLibrary library = TextureLibraries.standard();

    private final Evaluator.Settings settings = new Evaluator.Settings(SIZE, SIZE, 11, null);

    /** A node to run, with its parameters and scalar inputs as formulas. */
    private final class Run {
        private final Graph graph = library.newGraph();

        private final String node;

        Run(String type) {
            node = graph.add(type, 400, 0).getId();
        }

        Run set(String param, String value) {
            graph.setParam(node, param, value);
            return this;
        }

        /** Link a scalar made by a formula into an input. */
        Run in(String pin, String formula) {
            final String expression = graph.add(Expression.TYPE, 0, 0).getId();
            graph.setParam(expression, Expression.FORMULA, formula);
            graph.link(expression, "result", node, pin);
            return this;
        }

        /** Link a color made of four formulas into an input. */
        Run color(String pin, String r, String g, String b, String a) {
            final String combine = graph.add("channels/combine", 200, 0).getId();
            final String[] formulas = {r, g, b, a};
            final String[] names = {"r", "g", "b", "a"};
            for (int c = 0; c < 4; ++c) {
                final String expression = graph.add(Expression.TYPE, 0, 0).getId();
                graph.setParam(expression, Expression.FORMULA, formulas[c]);
                graph.link(expression, "result", combine, names[c]);
            }
            graph.link(combine, "color", node, pin);
            return this;
        }

        /** Link coordinates made of two formulas into an input. */
        Run coordinates(String pin, String u, String v) {
            final String append = graph.add("channels/append", 200, 0).getId();
            in2(append, "a", u);
            in2(append, "b", v);
            graph.link(append, "coordinates", node, pin);
            return this;
        }

        private void in2(String target, String pin, String formula) {
            final String expression = graph.add(Expression.TYPE, 0, 0).getId();
            graph.setParam(expression, Expression.FORMULA, formula);
            graph.link(expression, "result", target, pin);
        }

        Plane out(String pin) {
            final Evaluator evaluator = new Evaluator(library);
            final Plane plane = evaluator.output(graph, settings, node, pin);
            assertNotNull(plane, () -> "No output: " + evaluator.getErrors());
            return plane;
        }
    }

    /** A plane's value, wrapping. */
    private static float at(Plane plane, int x, int y, int c) {
        return plane.get(Math.floorMod(x, SIZE), Math.floorMod(y, SIZE), c);
    }

    // ---- tonal

    @Test
    void remapMovesARangeAndClampsWhenAsked() {
        Plane out =
                new Run("ops/remap")
                        .in("value", "x")
                        .set("from low", "0.25")
                        .set("from high", "0.75")
                        .set("to low", "10")
                        .set("to high", "20")
                        .out("result");
        // x at column 8 is 8.5 / 16
        assertEquals(10 + 10 * (8.5f / 16 - 0.25f) / 0.5f, out.get(8, 0, 0), 1e-4f);
        assertEquals(10 + 10 * (0.5f / 16 - 0.25f) / 0.5f, out.get(0, 0, 0), 1e-4f, "Unclamped");
        Plane clamped =
                new Run("ops/remap")
                        .in("value", "x")
                        .set("from low", "0.25")
                        .set("clamp", "true")
                        .set("from high", "0.75")
                        .out("result");
        assertEquals(0, clamped.get(0, 0, 0));
        assertEquals(1, clamped.get(15, 0, 0));
    }

    @Test
    void thresholdIsHardOrSmooth() {
        Plane hard = new Run("ops/threshold").in("value", "x").set("level", "0.5").out("result");
        assertEquals(0, hard.get(7, 0, 0));
        assertEquals(1, hard.get(8, 0, 0));
        Plane soft =
                new Run("ops/threshold")
                        .in("value", "0.5")
                        .set("level", "0.5")
                        .set("softness", "0.2")
                        .out("result");
        assertEquals(0.5f, soft.get(3, 3, 0), EPSILON, "Halfway at the level");
    }

    @Test
    void edgeDetectFindsStepsAcrossTheWrap() {
        Plane flat = new Run("ops/edge_detect").in("value", "0.3").out("result");
        assertEquals(0, flat.get(4, 4, 0), EPSILON);
        Plane step = new Run("ops/edge_detect").in("value", "px < 8 ? 0 : 1").out("result");
        assertEquals(1, step.get(8, 3, 0), EPSILON, "Sobel of a unit step is 4, over 4");
        assertEquals(1, step.get(0, 3, 0), EPSILON, "The step at the wrap");
        assertEquals(0, step.get(4, 3, 0), EPSILON);
    }

    @Test
    void distanceCountsPixelsToTheNearestGap() {
        Plane out =
                new Run("ops/distance")
                        .in("mask", "px == 0 ? 0 : 1")
                        .set("reach", "8")
                        .out("result");
        assertEquals(0, out.get(0, 5, 0));
        assertEquals(3 / 8f, out.get(3, 5, 0), EPSILON);
        assertEquals(2 / 8f, out.get(14, 5, 0), EPSILON, "Across the wrap");
        assertEquals(1, out.get(8, 5, 0), EPSILON, "Past the reach");
    }

    @Test
    void curvesBendThroughTheirPoints() {
        Plane out = new Run("ops/curves").in("value", "x").set("points", "0:1 1:0").out("result");
        assertEquals(1 - 8.5f / 16, out.get(8, 0, 0), 1e-4f, "Two points are a straight line");
    }

    // ---- moving pixels

    @Test
    void offsetShiftsWholePixelsAndWraps() {
        Plane out =
                new Run("ops/offset")
                        .in("value", "px + 16 * py")
                        .set("x", "3")
                        .set("y", "-2")
                        .out("result");
        assertEquals(0 + 16 * 2, out.get(3, 0, 0), EPSILON);
        assertEquals(13 + 16 * 2, out.get(0, 0, 0), EPSILON, "From across the wrap");
    }

    @Test
    void transformRotatesAQuarterTurnExactly() {
        Plane same = new Run("ops/transform").in("value", "px + 16 * py").out("result");
        assertEquals(5 + 16 * 9, same.get(5, 9, 0), EPSILON, "Nothing set changes nothing");
        Plane turned =
                new Run("ops/transform")
                        .in("value", "px + 16 * py")
                        .set("rotate", "90")
                        .out("result");
        // A quarter turn about the middle: the pixel at (x, y) comes from (y, 15 - x)
        final Set<Float> seen = new HashSet<>();
        for (int y = 0; y < SIZE; ++y) {
            for (int x = 0; x < SIZE; ++x) {
                seen.add(turned.get(x, y, 0));
            }
        }
        assertEquals(SIZE * SIZE, seen.size(), "Every pixel lands once");
        assertEquals(same.get(3, 15 - 6, 0), turned.get(6, 3, 0), EPSILON);
        Plane doubled = new Run("ops/transform").in("value", "px").set("scale", "2").out("result");
        assertEquals(doubled.get(8, 0, 0), doubled.get(9, 0, 0), EPSILON, "Pixels doubled");
    }

    @Test
    void warpMovesByItsInputsAroundTheMiddle() {
        Plane still = new Run("ops/warp").in("value", "px").in("x", "0.5").out("result");
        assertEquals(4, still.get(4, 0, 0), EPSILON, "0.5 is no move");
        Plane moved =
                new Run("ops/warp")
                        .in("value", "px")
                        .in("x", "1")
                        .set("intensity", "2")
                        .out("result");
        assertEquals(6, moved.get(4, 0, 0), EPSILON, "1 moves the lookup by the intensity");
    }

    @Test
    void blurKeepsFlatAndAverages() {
        Plane flat = new Run("ops/blur").in("value", "0.4").set("radius", "2").out("result");
        assertEquals(0.4f, flat.get(5, 5, 0), EPSILON);
        Plane stripes = new Run("ops/blur").in("value", "px % 2").set("radius", "1").out("result");
        // A box of three over alternating 0 and 1, three times over
        assertTrue(Math.abs(stripes.get(4, 0, 0) - 0.5f) < 0.1f);
    }

    @Test
    void slopeBlurOnAFlatSlopeChangesNothing() {
        Plane out =
                new Run("ops/slope_blur")
                        .in("value", "px + 16 * py")
                        .in("slope", "0.5")
                        .out("result");
        assertEquals(7 + 16 * 2, out.get(7, 2, 0), EPSILON);
    }

    @Test
    void colorVersionsMoveEveryChannel() {
        Plane out =
                new Run("ops/offset_color")
                        .color("color", "px / 16", "py / 16", "0.5", "1")
                        .set("x", "1")
                        .out("result");
        assertEquals(0 / 16f, out.get(1, 3, 0), EPSILON);
        assertEquals(3 / 16f, out.get(1, 3, 1), EPSILON);
        assertEquals(0.5f, out.get(1, 3, 2), EPSILON);
    }

    // ---- coordinates and channels

    @Test
    void coordinateNodesMoveTheTilesCoordinates() {
        Plane offset = new Run("coords/offset").set("x", "0.25").out("coordinates");
        assertEquals(0.5f / 16 + 0.25f, offset.get(0, 0, 0), EPSILON);
        Plane rotated = new Run("coords/rotate").set("degrees", "180").out("coordinates");
        assertEquals(1 - 0.5f / 16, rotated.get(0, 0, 0), EPSILON);
        assertEquals(1 - 0.5f / 16, rotated.get(0, 0, 1), EPSILON);
        Plane scaled = new Run("coords/scale").set("x", "2").out("coordinates");
        assertEquals((0.5f / 16 - 0.5f) * 2 + 0.5f, scaled.get(0, 0, 0), EPSILON);
        Plane polar = new Run("coords/polar").out("coordinates");
        final float right = polar.get(15, 7, 0);
        assertEquals(0, Math.min(right, 1 - right), 0.02f, "To the right is angle 0, either side");
        assertTrue(polar.get(0, 0, 1) > polar.get(7, 7, 1), "Further from the middle");
    }

    @Test
    void cellsGiveAnIdAndAPlaceInEachCell() {
        final Run run = new Run("coords/cells").set("across", "4").set("down", "2");
        Plane id = run.out("id");
        Plane local = run.out("local");
        assertEquals(id.get(0, 0, 0), id.get(3, 7, 0), "One cell");
        assertNotEquals(id.get(0, 0, 0), id.get(4, 0, 0), "The next cell");
        assertEquals(0.5f / 4, local.get(0, 0, 0), EPSILON);
        assertEquals(3.5f / 4, local.get(3, 0, 0), EPSILON);
        assertEquals(7.5f / 8, local.get(0, 7, 1), EPSILON);
    }

    @Test
    void channelNodesRearrange() {
        Plane swizzled =
                new Run("channels/swizzle")
                        .color("color", "0.1", "0.2", "0.3", "0.4")
                        .set("order", "bgr1")
                        .out("color");
        assertEquals(0.3f, swizzled.get(0, 0, 0), EPSILON);
        assertEquals(0.2f, swizzled.get(0, 0, 1), EPSILON);
        assertEquals(0.1f, swizzled.get(0, 0, 2), EPSILON);
        assertEquals(1, swizzled.get(0, 0, 3), EPSILON);
        Plane masked =
                new Run("channels/component_mask")
                        .color("color", "0.1", "0.2", "0.3", "0.4")
                        .set("g", "false")
                        .set("a", "false")
                        .out("color");
        assertEquals(0.1f, masked.get(0, 0, 0), EPSILON);
        assertEquals(0, masked.get(0, 0, 1), EPSILON);
        assertEquals(1, masked.get(0, 0, 3), EPSILON, "A dropped alpha is opaque");
        final Run append = new Run("channels/append").in("a", "1").in("b", "2").in("c", "3");
        assertEquals(2, append.out("coordinates").get(0, 0, 1), EPSILON);
        assertEquals(3, append.out("vector").get(0, 0, 2), EPSILON);
    }

    // ---- color

    @Test
    void gradientMapBlendsStopsBySrgbValue() {
        Plane out =
                new Run("color/gradient_map")
                        .in("value", "px / 15")
                        .set("stops", "0:#000000 1:#ffffff")
                        .out("color");
        assertEquals(0, out.get(0, 0, 0), EPSILON);
        assertEquals(1, out.get(15, 0, 0), EPSILON);
        // Halfway is sRGB 0.5, which is about 0.214 linear, not 0.5
        Plane half =
                new Run("color/gradient_map")
                        .in("value", "0.5")
                        .set("stops", "0:#000000 1:#ffffff")
                        .out("color");
        assertEquals((float) Srgb.toLinear(0.5), half.get(0, 0, 0), 1e-4f);
    }

    @Test
    void hsvTurnsHueAndDrainsSaturation() {
        Plane same =
                new Run("color/hsv")
                        .color("color", "0.6", "0.2", "0.1", "1")
                        .set("hue", "360")
                        .out("color");
        assertEquals(0.6f, same.get(0, 0, 0), 1e-4f, "A whole turn is no turn");
        assertEquals(0.2f, same.get(0, 0, 1), 1e-4f);
        Plane gray =
                new Run("color/hsv")
                        .color("color", "0.6", "0.2", "0.1", "1")
                        .set("saturation", "0")
                        .out("color");
        assertEquals(gray.get(0, 0, 0), gray.get(0, 0, 1), 1e-4f);
        assertEquals(gray.get(0, 0, 0), gray.get(0, 0, 2), 1e-4f);
    }

    @Test
    void orderedDitherStaysBetweenNeighboringColors() {
        // Close grays, as a stone's palette is: dither must mix neighbors, not far apart tones
        final float[] palette = ColorNodes.palette("#56565a #6b6b6f #808084 #96969a");
        final Plane mid =
                Plane.filled(SIZE, SIZE, toLinear(0x75), toLinear(0x75), toLinear(0x79), 1);
        final Plane index = ColorNodes.quantize(mid, palette, "ordered", 1);
        final Set<Integer> used = new HashSet<>();
        for (int y = 0; y < SIZE; ++y) {
            for (int x = 0; x < SIZE; ++x) {
                used.add(Math.round(index.get(x, y, 0)));
            }
        }
        assertEquals(Set.of(1, 2), used, "Only the two colors either side of it");
        final float[][] encoded = new float[2][3];
        encoded[1][0] = 0.3f;
        encoded[1][1] = 0.3f;
        encoded[1][2] = 0.3f;
        assertEquals(0.3f, ColorNodes.ditherStep(encoded), 1e-5f, "The step is the spacing");
    }

    /**
     * An sRGB byte as linear.
     *
     * @param value The byte.
     * @return The linear value.
     */
    private static float toLinear(int value) {
        return Srgb.decode(value);
    }

    // ---- noise and patterns

    @Test
    void whiteNoiseIsAValuePerPixel() {
        final Plane white = Noise.make(Noise.Kind.WHITE, SIZE, SIZE, 1, 1, 0.5f, 4);
        final Set<Float> values = new HashSet<>();
        for (int y = 0; y < SIZE; ++y) {
            for (int x = 0; x < SIZE; ++x) {
                values.add(white.get(x, y, 0));
            }
        }
        assertTrue(values.size() > SIZE * SIZE * 0.95, "Nearly all pixels differ");
        assertEquals(white, Noise.make(Noise.Kind.WHITE, SIZE, SIZE, 1, 1, 0.5f, 4), "Repeatable");
        final Plane blocks = Noise.make(Noise.Kind.WHITE, SIZE, SIZE, 2, 1, 0.5f, 4);
        assertEquals(blocks.get(0, 0, 0), blocks.get(1, 1, 0), "Size 2 is 2x2 blocks");
    }

    @Test
    void ridgedNoiseStaysInRange() {
        final Plane ridged = Noise.make(Noise.Kind.RIDGED, SIZE, SIZE, 4, 3, 0.5f, 2);
        for (int y = 0; y < SIZE; ++y) {
            for (int x = 0; x < SIZE; ++x) {
                final float value = ridged.get(x, y, 0);
                assertTrue(value >= 0 && value <= 1, "In range at " + x + ", " + y);
            }
        }
    }

    @Test
    void stripesCheckerAndGradientsLayOutAsAsked() {
        Plane stripes =
                new Run("pattern/stripes")
                        .set("period", "4")
                        .set("width", "1")
                        .set("direction", "vertical")
                        .out("mask");
        assertEquals(1, stripes.get(0, 3, 0));
        assertEquals(0, stripes.get(1, 3, 0));
        assertEquals(1, stripes.get(4, 3, 0));
        Plane checker = new Run("pattern/checker").set("size", "4").out("mask");
        assertEquals(1, checker.get(0, 0, 0));
        assertEquals(0, checker.get(4, 0, 0));
        assertEquals(1, checker.get(4, 4, 0));
        Plane across = new Run("pattern/gradient").set("kind", "across").out("value");
        assertEquals(0.5f / 16, across.get(0, 9, 0), EPSILON);
        Plane radial = new Run("pattern/gradient").set("kind", "radial").out("value");
        assertTrue(radial.get(0, 0, 0) > radial.get(7, 7, 0));
    }

    @Test
    void piecesTilesSeamsAndRivetsHaveTheirGaps() {
        Plane tiles = new Run("pattern/tiles").set("size", "8").set("gap", "1").out("mask");
        assertEquals(0, tiles.get(7, 2, 0));
        assertEquals(0, tiles.get(2, 15, 0));
        assertEquals(1, tiles.get(2, 2, 0));
        Plane seams = new Run("pattern/panel_seams").set("size", "8").out("mask");
        assertEquals(0, seams.get(8, 3, 0));
        assertEquals(1, seams.get(3, 3, 0));
        Plane rivets =
                new Run("pattern/rivets")
                        .set("spacing", "8")
                        .set("radius", "1")
                        .set("inset", "2")
                        .out("mask");
        int count = 0;
        for (int y = 0; y < SIZE; ++y) {
            for (int x = 0; x < SIZE; ++x) {
                count += Math.round(rivets.get(x, y, 0));
            }
        }
        assertEquals(4 * 5, count, "Four rivets of five pixels each");
        Plane planks = new Run("pattern/planks").out("mask");
        assertEquals(0, planks.get(3, 5, 0), "The gap beside a plank");
    }

    @Test
    void scratchesAndGrainAreRepeatable() {
        Plane scratches = new Run("pattern/scratches").set("count", "3").out("mask");
        int lit = 0;
        for (int y = 0; y < SIZE; ++y) {
            for (int x = 0; x < SIZE; ++x) {
                lit += Math.round(scratches.get(x, y, 0));
            }
        }
        assertTrue(lit > 3 && lit <= 3 * 13, "Three short lines, " + lit + " pixels");
        assertEquals(scratches, new Run("pattern/scratches").set("count", "3").out("mask"));
        Plane grain =
                new Run("pattern/wood_grain").set("wobble", "0").set("lines", "2").out("value");
        assertEquals(grain.get(0, 0, 0), grain.get(0, 9, 0), EPSILON, "No wobble, straight lines");
        assertEquals(grain.get(0, 0, 0), grain.get(8, 0, 0), EPSILON, "Two lines across");
    }

    @Test
    void expressionReadsItsInputsAndPosition() {
        Plane out =
                new Run(Expression.TYPE)
                        .in("a", "2")
                        .set(Expression.FORMULA, "a * px + py + w")
                        .out("result");
        assertEquals(2 * 3 + 4 + 16, out.get(3, 4, 0), EPSILON);
        final Map<String, String> unused = new LinkedHashMap<>();
        assertTrue(unused.isEmpty());
    }
}
