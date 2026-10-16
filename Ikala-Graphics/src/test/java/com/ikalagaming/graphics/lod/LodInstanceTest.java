package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.vulkan.LodCullMath;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Tests for the cut instances of a model pick. */
class LodInstanceTest {

    /** Screen pixels per unit at distance 1: 1080 pixels high, 60 degrees. */
    private static final double PIXELS = 1080 / (2 * Math.tan(Math.toRadians(30)));

    /** How many random views to compare. */
    private static final int VIEWS = 100;

    /** The share of choices rounding may turn the other way. */
    private static final double ROUNDING_SHARE = 1e-3;

    /** A textured sphere's DAG. */
    private static LodDag dag;

    /** Build the DAG once. */
    @BeforeAll
    static void build() {
        dag = DagBuilder.build(List.of(AttributesTest.sphere()), AttributesTest.OPTIONS);
    }

    /**
     * A random view around the sphere, from just outside it to far away.
     *
     * @param random The random numbers.
     * @return The view.
     */
    private static LodCut.View randomView(Random random) {
        final Vector3d direction =
                new Vector3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                        .normalize();
        final double distance = 9 + random.nextDouble() * 400;
        return new LodCut.View(
                direction.x * distance, direction.y * distance, direction.z * distance, PIXELS);
    }

    /** An instance left where the model is, unturned and unscaled, picks the model's own cut. */
    @Test
    void testIdentityInstanceIsTheModel() {
        final Random random = new Random(3);
        for (int v = 0; v < VIEWS; ++v) {
            final LodCut.View view = randomView(random);
            final double threshold = 0.5 + random.nextDouble() * 8;
            assertArrayEquals(
                    LodCullMath.select(dag, view, threshold),
                    LodCullMath.selectInstance(
                            dag, view, threshold, new double[3], new Quaterniond(), 1));
        }
    }

    /**
     * An instance moved, turned and scaled picks the cut the model would from where the observer is
     * relative to it, but for clusters rounding takes the other way.
     */
    @Test
    void testMovedInstanceSeesTheSameCut() {
        final Random random = new Random(5);
        int compared = 0;
        int differ = 0;
        final double[] origin = dag.getOrigin();
        for (int v = 0; v < VIEWS; ++v) {
            final LodCut.View view = randomView(random);
            final double threshold = 0.5 + random.nextDouble() * 8;
            final double[] position = {
                random.nextDouble() * 1000 - 500,
                random.nextDouble() * 100,
                random.nextDouble() * 1000 - 500
            };
            final Quaterniond rotation =
                    new Quaterniond()
                            .rotateXYZ(
                                    random.nextDouble() * 6,
                                    random.nextDouble() * 6,
                                    random.nextDouble() * 6);
            final double scale = 0.01 + random.nextDouble() * 4;
            // The view as the instance's frame carries it into the world
            final double[] moved =
                    LodCullMath.instanceSphere(
                            new double[] {
                                view.x() - origin[0], view.y() - origin[1], view.z() - origin[2], 0
                            },
                            position,
                            rotation,
                            scale,
                            origin);
            final boolean[] own = LodCullMath.select(dag, view, threshold);
            final boolean[] instance =
                    LodCullMath.selectInstance(
                            dag,
                            new LodCut.View(moved[0], moved[1], moved[2], PIXELS),
                            threshold,
                            position,
                            rotation,
                            scale);
            for (int i = 0; i < own.length; ++i) {
                ++compared;
                differ += own[i] == instance[i] ? 0 : 1;
            }
        }
        assertTrue(
                differ <= compared * ROUNDING_SHARE,
                differ + " of " + compared + " choices differ");
    }

    /** An instance's eye and spheres undo each other: the eye's place is the sphere's center. */
    @Test
    void testEyeAndSpheresAgree() {
        final double[] origin = {1, 2, 3};
        final double[] position = {10, -4, 7};
        final Quaterniond rotation = new Quaterniond().rotateXYZ(0.3, 1.1, -0.7);
        final double scale = 0.25;
        final double[] local = {4, -2, 5, 2};
        final double[] world = LodCullMath.instanceSphere(local, position, rotation, scale, origin);
        assertEquals(0.5, world[3], 1e-12);
        final double[] back =
                LodCullMath.modelEye(
                        new double[] {world[0], world[1], world[2]},
                        position,
                        rotation,
                        scale,
                        origin);
        for (int axis = 0; axis < 3; ++axis) {
            assertEquals(local[axis], back[axis], 1e-9);
        }
    }

    /** A draw's instance keeps the instance's slot and the cluster apart. */
    @Test
    void testDrawIdsSplit() {
        final int id = LodCullMath.drawId(LodCullMath.MAX_INSTANCE_SLOTS - 1, 1234);
        assertTrue((id & LodCullMath.INSTANCED) != 0);
        assertEquals(
                LodCullMath.MAX_INSTANCE_SLOTS - 1,
                (id & ~LodCullMath.INSTANCED) >>> LodCullMath.INSTANCE_SHIFT);
        assertEquals(1234, id & (LodCullMath.MAX_MODEL_CLUSTERS - 1));
    }
}
