package com.ikalagaming.characters.pose;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.JsonText;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.definition.BodyPlan;

import lombok.NonNull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests for {@link WingMotion}, on the spring tests' rig: a four bone chain, 0.2 m a bone, hanging
 * straight down from 0.15 m behind hips a meter up, used as a left and a right wing.
 *
 * @author Ches Burks
 */
class TestWingMotion {
    /** How close positions must be, in meters. */
    private static final float CLOSE = 1e-3f;

    /** Fails on any problem. */
    private static final Report FAIL =
            new Report() {
                @Override
                public void error(@NonNull String path, @NonNull String message) {
                    Assertions.fail(path + ": " + message);
                }

                @Override
                public void warning(@NonNull String path, @NonNull String message) {
                    Assertions.fail(path + ": " + message);
                }
            };

    /** The rig. */
    private final TestSpringChains.Setup rig = new TestSpringChains.Setup();

    /** The plan: the chain as both wings, folding its first bone 30 degrees about z. */
    private final BodyPlan plan =
            DataFormat.readDefinition(
                    JsonText.parse(
                            """
                            { "id": "test:plan", "rig": "test:rig", "variants": [ { "id": "a" } ],
                              "extensions": [
                                { "id": "wing.L", "root": "hips", "bones": ["c1", "c2", "c3", "c4"],
                                  "wing": { "fold": [[0, 0, 30]], "flap": [20], "flapRate": 2 } },
                                { "id": "wing.R", "root": "hips", "bones": ["c1", "c2", "c3", "c4"],
                                  "wing": { "fold": [[0, 0, 30]], "flap": [20], "flapRate": 2 } }
                              ] }
                            """,
                            error -> Assertions.fail(error.toString())),
                    BodyPlan.class,
                    FAIL);

    /**
     * Where the chain's tip joint ends up after the wings move on.
     *
     * @param wings The wings.
     * @param seconds How long they move on, in one update.
     * @return The last bone's head.
     */
    private Vector3f tip(WingMotion wings, float seconds) {
        PoseSolver solver = new PoseSolver(rig.skeleton);
        solver.solve(new Pose(rig.skeleton), Proportions.neutral(rig.skeleton));
        wings.update(seconds, solver);
        return solver.model(rig.skeleton.index("c4")).getTranslation(new Vector3f());
    }

    /**
     * Where a joint 0.6 m down the chain lands with the first bone turned some degrees about z.
     *
     * @param degrees The turn.
     * @return The joint.
     */
    private static Vector3f turned(double degrees) {
        double angle = Math.toRadians(degrees);
        return new Vector3f(
                (float) (0.6 * Math.sin(angle)), (float) (1 - 0.6 * Math.cos(angle)), -0.15f);
    }

    /**
     * Assert a point is where it should be.
     *
     * @param expected Where.
     * @param actual Where it is.
     */
    private static void assertNear(Vector3f expected, Vector3f actual) {
        Assertions.assertTrue(
                expected.distance(actual) < CLOSE, "Expected " + expected + " but was " + actual);
    }

    /** Spread wings are as at rest; folded ones turn by the fold, the right mirrored. */
    @Test
    void testSpreadAndFold() {
        WingMotion spread =
                new WingMotion(rig.skeleton, plan, List.of("wing.L"), WingMotion.State.SPREAD);
        assertNear(turned(0), tip(spread, 0));
        WingMotion left =
                new WingMotion(rig.skeleton, plan, List.of("wing.L"), WingMotion.State.FOLD);
        assertNear(turned(30), tip(left, 0));
        WingMotion right =
                new WingMotion(rig.skeleton, plan, List.of("wing.R"), WingMotion.State.FOLD);
        assertNear(turned(-30), tip(right, 0));
    }

    /** Flapping wings swing by the flap's angle, a quarter beat in at the top. */
    @Test
    void testFlap() {
        WingMotion flap =
                new WingMotion(rig.skeleton, plan, List.of("wing.L"), WingMotion.State.FLAP);
        // Two beats a second: a quarter beat is an eighth of a second
        assertNear(turned(20), tip(flap, 0.125f));
        assertNear(turned(0), tip(flap, 0.125f));
        assertNear(turned(-20), tip(flap, 0.125f));
    }

    /** A change of pose fades: halfway through it the wing is between the two. */
    @Test
    void testFade() {
        WingMotion wings =
                new WingMotion(rig.skeleton, plan, List.of("wing.L"), WingMotion.State.FOLD);
        wings.setState(WingMotion.State.SPREAD);
        Vector3f halfway = tip(wings, WingMotion.FADE / 2);
        assertNear(turned(15), halfway);
        assertNear(turned(0), tip(wings, WingMotion.FADE));
        Assertions.assertEquals(WingMotion.State.SPREAD, wings.getState());
    }

    /**
     * Wings that follow the motion fold when standing or walking, spread when running, flap for a
     * jump and glide for a fall; choosing a pose stops them following.
     */
    @Test
    void testFollowsMotion() {
        Assertions.assertEquals(WingMotion.State.FOLD, WingMotion.automatic(0, null));
        Assertions.assertEquals(WingMotion.State.FOLD, WingMotion.automatic(1.4f, "move"));
        Assertions.assertEquals(WingMotion.State.SPREAD, WingMotion.automatic(4.5f, "move"));
        Assertions.assertEquals(WingMotion.State.FLAP, WingMotion.automatic(0, "jump"));
        Assertions.assertEquals(WingMotion.State.GLIDE, WingMotion.automatic(2, "fall.long"));

        WingMotion wings =
                new WingMotion(rig.skeleton, plan, List.of("wing.L"), WingMotion.State.FOLD);
        wings.follow(0, "jump");
        Assertions.assertEquals(WingMotion.State.FOLD, wings.getState(), "Not following yet");
        wings.setAutomatic();
        wings.follow(0, "jump");
        Assertions.assertEquals(WingMotion.State.FLAP, wings.getState());
        wings.setState(WingMotion.State.SPREAD);
        Assertions.assertFalse(wings.isAutomatic());
        wings.follow(0, "jump");
        Assertions.assertEquals(WingMotion.State.SPREAD, wings.getState());
    }

    /**
     * A wing's offset moves its root, and the wing with it, in every pose, mirrored for the right;
     * a wing part's own poses replace the plan's.
     */
    @Test
    void testOffsetAndOwnPoses() {
        BodyPlan.WingPoses moved =
                new BodyPlan.WingPoses(
                        List.of(),
                        List.of(),
                        List.of(),
                        2,
                        0.1f,
                        4,
                        0.4f,
                        new com.ikalagaming.characters.data.Vec3(0.1f, 0.05f, 0));
        for (String side : List.of("wing.L", "wing.R")) {
            WingMotion wings =
                    new WingMotion(
                            rig.skeleton,
                            plan,
                            List.of(side),
                            WingMotion.State.SPREAD,
                            java.util.Map.of(side, moved));
            Vector3f expected = turned(0).add(side.endsWith(".R") ? -0.1f : 0.1f, 0.05f, 0);
            assertNear(expected, tip(wings, 0));
        }
        // The part's poses have no fold, so folding leaves the wing as it is
        WingMotion folded =
                new WingMotion(
                        rig.skeleton,
                        plan,
                        List.of("wing.L"),
                        WingMotion.State.FOLD,
                        java.util.Map.of("wing.L", moved));
        assertNear(turned(0).add(0.1f, 0.05f, 0), tip(folded, 0));
    }

    /** A wing's poses can be replaced as it moves, as an editor does while tuning them. */
    @Test
    void testSetPoses() {
        WingMotion wings =
                new WingMotion(rig.skeleton, plan, List.of("wing.L"), WingMotion.State.FOLD);
        assertNear(turned(30), tip(wings, 0));
        BodyPlan.WingPoses wider =
                new BodyPlan.WingPoses(
                        List.of(new com.ikalagaming.characters.data.Vec3(0, 0, 50)),
                        List.of(),
                        List.of(),
                        2,
                        0.1f,
                        4,
                        0.4f,
                        null);
        Assertions.assertTrue(wings.setPoses("wing.L", wider));
        Assertions.assertEquals(wider, wings.poses("wing.L"));
        assertNear(turned(50), tip(wings, 0));
        Assertions.assertFalse(wings.setPoses("wing.R", wider), "Not worn");
        Assertions.assertNull(wings.poses("wing.R"));
    }

    /** Chains without wing poses aren't wings. */
    @Test
    void testOnlyWings() {
        Assertions.assertTrue(
                new WingMotion(rig.skeleton, plan, List.of("tail"), WingMotion.State.FOLD)
                        .isEmpty());
        Assertions.assertEquals(WingMotion.State.FLAP, WingMotion.State.of("flap"));
        Assertions.assertEquals("glide", WingMotion.State.GLIDE.key());
    }
}
