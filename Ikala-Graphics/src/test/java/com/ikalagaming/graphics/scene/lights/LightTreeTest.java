package com.ikalagaming.graphics.scene.lights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** How the light tree is built and how its cut spends a shadow map budget. */
class LightTreeTest {

    private static final double[] EYE = {0, 2, 0};
    private static final double SHARE = 2;

    /** A room of torches close together, and lone lamps spread out. */
    private static List<LightTree.Light> scene() {
        final List<LightTree.Light> lights = new ArrayList<>();
        int slot = 0;
        // Four torches within a meter of each other, 10 m away
        for (int i = 0; i < 4; ++i) {
            lights.add(new LightTree.Light(slot++, 10 + i * 0.3, 2, 0.2 * i, 1, 8));
        }
        // Lamps 20 m apart, out to 120 m
        for (int i = 1; i <= 6; ++i) {
            lights.add(new LightTree.Light(slot++, -20.0 * i, 3, 5, 2, 10));
        }
        return lights;
    }

    @Test
    void everyLightIsInExactlyOneLeafAndNodesHoldTheirChildren() {
        final LightTree tree = new LightTree(scene());
        int leaves = 0;
        for (LightTree.Node node : tree.nodes()) {
            if (node.isLeaf()) {
                leaves += 1;
                assertEquals(1, node.lights().length);
            } else {
                final int children =
                        tree.nodes().get(node.left()).lights().length
                                + tree.nodes().get(node.right()).lights().length;
                assertEquals(node.lights().length, children);
            }
        }
        assertEquals(tree.lights().size(), leaves);
        assertEquals(tree.lights().size(), tree.nodes().get(0).lights().length);
    }

    @Test
    void representativesAreTheBrightestAndMapsReachEveryLight() {
        final LightTree tree = new LightTree(scene());
        for (LightTree.Node node : tree.nodes()) {
            final LightTree.Light center = tree.lights().get(node.representative());
            for (int i : node.lights()) {
                final LightTree.Light light = tree.lights().get(i);
                assertTrue(light.intensity() <= center.intensity());
                final double distance =
                        Math.sqrt(
                                Math.pow(light.x() - center.x(), 2)
                                        + Math.pow(light.y() - center.y(), 2)
                                        + Math.pow(light.z() - center.z(), 2));
                assertTrue(distance + light.range() <= node.range() + 1e-9);
            }
        }
    }

    @Test
    void aTightGroupSharesAMapWhenTheBudgetIsShort() {
        final LightTree tree = new LightTree(scene());
        final List<LightTree.Node> cut = tree.cut(EYE, 2, SHARE, Set.of());
        assertEquals(2, cut.size());
        // The torches are the nearest and brightest group, sharing one map
        assertEquals(4, cut.get(0).lights().length);
        for (LightTree.Node node : cut) {
            assertTrue(node.isLeaf() || node.extent() <= SHARE);
        }
    }

    @Test
    void spareBudgetGivesSharedLightsTheirOwnMaps() {
        final LightTree tree = new LightTree(scene());
        final List<LightTree.Node> cut = tree.cut(EYE, 20, SHARE, Set.of());
        assertEquals(tree.lights().size(), cut.size());
        for (LightTree.Node node : cut) {
            assertTrue(node.isLeaf());
        }
    }

    @Test
    void theCutNeverOverlapsOrExceedsTheBudget() {
        final Random random = new Random(3);
        final List<LightTree.Light> lights = new ArrayList<>();
        for (int i = 0; i < 300; ++i) {
            lights.add(
                    new LightTree.Light(
                            i,
                            random.nextDouble() * 200 - 100,
                            random.nextDouble() * 10,
                            random.nextDouble() * 200 - 100,
                            0.5f + random.nextFloat(),
                            4 + random.nextFloat() * 10));
        }
        final LightTree tree = new LightTree(lights);
        for (int budget : new int[] {1, 4, 8, 16}) {
            final List<LightTree.Node> cut = tree.cut(EYE, budget, SHARE, Set.of());
            assertTrue(cut.size() <= budget);
            final Set<Integer> seen = new HashSet<>();
            for (LightTree.Node node : cut) {
                assertTrue(node.isLeaf() || node.extent() <= SHARE);
                for (int light : node.lights()) {
                    assertTrue(seen.add(light), "Light " + light + " in two maps");
                }
            }
            // Most important first
            for (int i = 1; i < cut.size(); ++i) {
                assertTrue(
                        LightTree.importance(cut.get(i - 1), EYE) * LightTree.HYSTERESIS
                                >= LightTree.importance(cut.get(i), EYE));
            }
        }
    }

    @Test
    void keptMapsWinCloseCalls() {
        final List<LightTree.Light> lights =
                List.of(
                        new LightTree.Light(0, 10, 2, 0, 1, 8),
                        new LightTree.Light(1, -10.5, 2, 0, 1, 8));
        final LightTree tree = new LightTree(lights);
        assertEquals(0, slotOf(tree, tree.cut(EYE, 1, SHARE, Set.of()).get(0)));
        // The farther one keeps its map while it is close enough
        assertEquals(1, slotOf(tree, tree.cut(EYE, 1, SHARE, Set.of(1)).get(0)));
    }

    private static int slotOf(LightTree tree, LightTree.Node node) {
        return tree.lights().get(node.representative()).slot();
    }
}
