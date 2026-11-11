package com.ikalagaming.characters.pose;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonText;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Rig;

import lombok.NonNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link SpringChains}, on a small rig with Y up and the character facing +Z: hips a
 * meter up, a four bone chain hanging from them behind, and a bar across behind the hips to collide
 * with.
 *
 * @author Ches Burks
 */
class TestSpringChains {
    /** How long each chain bone is, in meters. */
    private static final float BONE = 0.2f;

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

    /** The test skeleton, and a way to run chains on it. */
    static final class Setup {
        /** The test skeleton. */
        final Skeleton skeleton;

        /**
         * Build the test skeleton, its chain pointing along a direction at rest.
         *
         * @param chainTurn How the chain's first bone turns from the hips: its bones' y axes point
         *     along the chain.
         */
        Setup(Quaternionf chainTurn) {
            Rig rig =
                    new Rig(
                            Id.parse("test:rig"),
                            List.of(
                                    new Rig.Bone("root", null, Rig.BoneKind.DEFORM),
                                    new Rig.Bone("hips", "root", Rig.BoneKind.DEFORM),
                                    new Rig.Bone("c1", "hips", Rig.BoneKind.DEFORM),
                                    new Rig.Bone("c2", "c1", Rig.BoneKind.DEFORM),
                                    new Rig.Bone("c3", "c2", Rig.BoneKind.DEFORM),
                                    new Rig.Bone("c4", "c3", Rig.BoneKind.DEFORM),
                                    new Rig.Bone("barLeft", "root", Rig.BoneKind.DEFORM),
                                    new Rig.Bone("barRight", "root", Rig.BoneKind.DEFORM)));
            Map<String, Skeleton.Rest> rest = new HashMap<>();
            rest.put("root", rest(0, 0, 0, new Quaternionf()));
            rest.put("hips", rest(0, 1, 0, new Quaternionf()));
            rest.put("c1", rest(0, 0, -0.15f, chainTurn));
            for (String bone : List.of("c2", "c3", "c4")) {
                rest.put(bone, rest(0, BONE, 0, new Quaternionf()));
            }
            // A bar across behind the hips, 0.5 m below them
            rest.put("barLeft", rest(-0.3f, 0.5f, -0.15f, new Quaternionf()));
            rest.put("barRight", rest(0.3f, 0.5f, -0.15f, new Quaternionf()));
            skeleton = new Skeleton(rig, rest);
        }

        /** A test rig whose chain hangs straight down. */
        Setup() {
            this(new Quaternionf().rotateX((float) Math.PI));
        }

        /**
         * A rest transform.
         *
         * @param x The offset's x.
         * @param y The offset's y.
         * @param z The offset's z.
         * @param rotation The rotation.
         * @return The transform.
         */
        private static Skeleton.Rest rest(float x, float y, float z, Quaternionf rotation) {
            return new Skeleton.Rest(new Vector3f(x, y, z), rotation);
        }

        /**
         * A plan with the chain on a spring.
         *
         * @param spring The spring's JSON.
         * @return The plan.
         */
        private static BodyPlan plan(String spring) {
            String text =
                            """
                { "id": "test:plan", "rig": "test:rig", "variants": [ { "id": "a" } ],
                  "extensions": [ { "id": "tail", "root": "hips",
                    "bones": ["c1", "c2", "c3", "c4"], "spring": %s } ] }
                """
                            .formatted(spring);
            return DataFormat.readDefinition(
                    JsonText.parse(text, error -> Assertions.fail(error.toString())),
                    BodyPlan.class,
                    FAIL);
        }

        /**
         * Run the chains for a while in a pose, a frame at a time.
         *
         * @param springs The chains.
         * @param solver The solver.
         * @param pose The pose.
         * @param seconds How long.
         * @param frame How long each frame is.
         */
        private void run(
                SpringChains springs, PoseSolver solver, Pose pose, float seconds, float frame) {
            Proportions neutral = Proportions.neutral(skeleton);
            for (int f = Math.round(seconds / frame); f > 0; --f) {
                solver.solve(pose, neutral);
                springs.update(frame, solver, 0);
            }
        }
    }

    /**
     * A point of a chain.
     *
     * @param points The points, 3 floats each.
     * @param index Which.
     * @return It.
     */
    private static Vector3f point(float[] points, int index) {
        return new Vector3f(points[index * 3], points[index * 3 + 1], points[index * 3 + 2]);
    }

    /**
     * A chain sticking straight back falls, under gravity alone, to hang straight down below the
     * root, its bones keeping their lengths, and the bones are turned to match.
     */
    @Test
    void testHangsUnderGravity() {
        Setup rig = new Setup(new Quaternionf().rotateX(-(float) Math.PI / 2));
        SpringChains springs =
                new SpringChains(
                        rig.skeleton,
                        Setup.plan("{ \"stiffness\": 0, \"damping\": 4, \"gravity\": 1 }"),
                        List.of("tail"));
        PoseSolver solver = new PoseSolver(rig.skeleton);
        Pose pose = new Pose(rig.skeleton);
        rig.run(springs, solver, pose, 6, 1 / 60f);
        float[] points = springs.points(0);
        Vector3f head = new Vector3f(0, 1, -0.15f);
        for (int i = 0; i < 4; ++i) {
            Vector3f expected = new Vector3f(0, 1 - BONE * (i + 1), -0.15f);
            Assertions.assertTrue(
                    point(points, i).distance(expected) < 0.01f,
                    "Joint " + i + " at " + point(points, i) + ", not " + expected);
        }
        // The last bone points at its point
        Vector3f lastHead = solver.model(rig.skeleton.index("c4")).getTranslation(new Vector3f());
        Assertions.assertTrue(lastHead.distance(point(points, 2)) < 0.01f, lastHead.toString());
        Assertions.assertTrue(head.y > lastHead.y);
    }

    /** Without gravity, a chain knocked out of its pose springs back to where it is posed. */
    @Test
    void testSpringsBack() {
        Setup rig = new Setup();
        SpringChains springs =
                new SpringChains(
                        rig.skeleton,
                        Setup.plan("{ \"stiffness\": 40, \"damping\": 6, \"gravity\": 0 }"),
                        List.of("tail"));
        PoseSolver solver = new PoseSolver(rig.skeleton);
        Pose pose = new Pose(rig.skeleton);
        rig.run(springs, solver, pose, 0.1f, 1 / 60f);
        // Swing the hips round fast, then stop
        Pose turned = new Pose(rig.skeleton);
        int hips = rig.skeleton.index("hips");
        turned.setRotation(hips, new Quaternionf().rotateY((float) Math.PI / 2));
        rig.run(springs, solver, turned, 0.05f, 1 / 60f);
        float[] swung = springs.points(0);
        Assertions.assertTrue(
                point(swung, 3).distance(0, 1 - 4 * BONE, -0.15f) > 0.01f,
                "The swing didn't move the chain");
        rig.run(springs, solver, pose, 5, 1 / 60f);
        float[] points = springs.points(0);
        for (int i = 0; i < 4; ++i) {
            Vector3f expected = new Vector3f(0, 1 - BONE * (i + 1), -0.15f);
            Assertions.assertTrue(
                    point(points, i).distance(expected) < 0.01f,
                    "Joint " + i + " at " + point(points, i) + ", not " + expected);
        }
    }

    /** A chain hanging through a capsule is pushed out of it, and stays out. */
    @Test
    void testKeepsOutOfCapsules() {
        Setup rig = new Setup();
        // Wider than the 0.1 m the hanging joints sit from the bar at rest, so it must push
        float radius = 0.15f;
        SpringChains springs =
                new SpringChains(
                        rig.skeleton,
                        Setup.plan(
                                "{ \"stiffness\": 0, \"damping\": 4, \"gravity\": 1, \"colliders\":"
                                        + " [ { \"from\": \"barLeft\", \"to\": \"barRight\","
                                        + " \"radius\": "
                                        + radius
                                        + " } ] }"),
                        List.of("tail"));
        PoseSolver solver = new PoseSolver(rig.skeleton);
        Pose pose = new Pose(rig.skeleton);
        Proportions neutral = Proportions.neutral(rig.skeleton);
        for (int frame = 0; frame < 300; ++frame) {
            solver.solve(pose, neutral);
            springs.update(1 / 60f, solver, 0);
            float[] points = springs.points(0);
            for (int i = 0; i < 4; ++i) {
                Vector3f p = point(points, i);
                // Distance from the bar's line, along x at y 0.5, z -0.15
                float distance = new Vector3f(0, p.y - 0.5f, p.z + 0.15f).length();
                Assertions.assertTrue(
                        distance > radius - 0.01f,
                        "Joint " + i + " at " + p + " is inside the bar on frame " + frame);
            }
        }
    }

    /**
     * A capsule along another chain counts only while that chain is worn: the hanging chain falls
     * through the bar when the bar's chain isn't worn, and is kept out of it when it is.
     */
    @Test
    void testCapsuleOnAnotherChain() {
        Setup rig = new Setup();
        // Wider than the 0.1 m the hanging joints sit from the bar at rest
        float radius = 0.15f;
        BodyPlan plan =
                DataFormat.readDefinition(
                        JsonText.parse(
                                """
                                { "id": "test:plan", "rig": "test:rig", "variants": [ { "id": "a" } ],
                                  "extensions": [
                                    { "id": "tail", "root": "hips", "bones": ["c1", "c2", "c3", "c4"],
                                      "spring": { "stiffness": 0, "damping": 4, "gravity": 1,
                                        "colliders": [ { "from": "barLeft", "to": "barRight",
                                                         "radius": 0.15 } ] } },
                                    { "id": "bar", "root": "root", "bones": ["barLeft", "barRight"] }
                                  ] }
                                """,
                                error -> Assertions.fail(error.toString())),
                        BodyPlan.class,
                        FAIL);
        float[] nearest = new float[2];
        List<List<String>> worn = List.of(List.of("tail"), List.of("bar", "tail"));
        for (int w = 0; w < worn.size(); ++w) {
            SpringChains springs = new SpringChains(rig.skeleton, plan, worn.get(w));
            PoseSolver solver = new PoseSolver(rig.skeleton);
            Pose pose = new Pose(rig.skeleton);
            Proportions neutral = Proportions.neutral(rig.skeleton);
            nearest[w] = Float.POSITIVE_INFINITY;
            for (int frame = 0; frame < 300; ++frame) {
                solver.solve(pose, neutral);
                springs.update(1 / 60f, solver, 0);
                float[] points = springs.points(0);
                for (int i = 0; i < 4; ++i) {
                    Vector3f p = point(points, i);
                    nearest[w] =
                            Math.min(nearest[w], new Vector3f(0, p.y - 0.5f, p.z + 0.15f).length());
                }
            }
        }
        Assertions.assertTrue(nearest[0] < radius - 0.01f, "Fell through: " + nearest[0]);
        Assertions.assertTrue(nearest[1] > radius - 0.01f, "Kept out: " + nearest[1]);
    }

    /**
     * A one-way capsule sends points near it out its way, round to its far side, rather than
     * straight away from its line: the chain hanging through the bar ends up behind it.
     */
    @Test
    void testOneWayCapsule() {
        Setup rig = new Setup();
        SpringChains springs =
                new SpringChains(
                        rig.skeleton,
                        Setup.plan(
                                "{ \"stiffness\": 0, \"damping\": 4, \"gravity\": 1, \"colliders\":"
                                        + " [ { \"from\": \"barLeft\", \"to\": \"barRight\","
                                        + " \"radius\": 0.15, \"out\": [0, 0, -1] } ] }"),
                        List.of("tail"));
        PoseSolver solver = new PoseSolver(rig.skeleton);
        Pose pose = new Pose(rig.skeleton);
        Proportions neutral = Proportions.neutral(rig.skeleton);
        for (int frame = 0; frame < 120; ++frame) {
            solver.solve(pose, neutral);
            springs.update(1 / 60f, solver, 0);
        }
        float[] points = springs.points(0);
        for (int i = 0; i < 4; ++i) {
            Vector3f p = point(points, i);
            float across = Math.abs(p.y - 0.5f);
            if (across < 0.15f) {
                // Behind the bar, at least as far as its surface reaches level with the joint
                float reach = (float) Math.sqrt(0.15f * 0.15f - across * across);
                Assertions.assertTrue(
                        p.z <= -0.15f - reach + 0.01f, "Joint " + i + " at " + p + " isn't behind");
            }
        }
    }

    /**
     * A chain going round a one-way capsule folds at one joint unless it resists bending; a bend
     * stiffness spreads the bend over its joints, still behind the bar.
     */
    @Test
    void testBendSpreads() {
        float[] sharpest = new float[2];
        String[] bends = {"0", "400"};
        for (int r = 0; r < 2; ++r) {
            Setup rig = new Setup();
            SpringChains springs =
                    new SpringChains(
                            rig.skeleton,
                            Setup.plan(
                                    "{ \"stiffness\": 6, \"damping\": 4, \"gravity\": 1, \"bend\": "
                                            + bends[r]
                                            + ", \"colliders\": [ { \"from\": \"barLeft\","
                                            + " \"to\": \"barRight\", \"radius\": 0.15,"
                                            + " \"out\": [0, 0, -1] } ] }"),
                            List.of("tail"));
            PoseSolver solver = new PoseSolver(rig.skeleton);
            Pose pose = new Pose(rig.skeleton);
            Proportions neutral = Proportions.neutral(rig.skeleton);
            for (int frame = 0; frame < 240; ++frame) {
                solver.solve(pose, neutral);
                springs.update(1 / 60f, solver, 0);
            }
            float[] points = springs.points(0);
            Vector3f before = solver.model(rig.skeleton.index("c1")).getTranslation(new Vector3f());
            Vector3f last = null;
            for (int i = 0; i < 4; ++i) {
                Vector3f p = point(points, i);
                Vector3f along = new Vector3f(p).sub(before);
                if (last != null) {
                    sharpest[r] = Math.max(sharpest[r], last.angle(along));
                }
                last = along;
                before = p;
            }
            Assertions.assertTrue(
                    point(points, 3).z < -0.15f,
                    "The tip at " + point(points, 3) + " isn't behind");
        }
        Assertions.assertTrue(
                sharpest[1] < 0.7f * sharpest[0],
                "Sharpest bend "
                        + Math.toDegrees(sharpest[1])
                        + " deg with stiffness, "
                        + Math.toDegrees(sharpest[0])
                        + " without");
    }

    /** A chain settles the same at 30 frames a second as at 144. */
    @Test
    void testSameAtAnyFrameRate() {
        float[][] results = new float[2][];
        float[] frames = {1 / 30f, 1 / 144f};
        for (int r = 0; r < 2; ++r) {
            Setup rig = new Setup(new Quaternionf().rotateX(-(float) Math.PI / 2));
            SpringChains springs =
                    new SpringChains(
                            rig.skeleton,
                            Setup.plan("{ \"stiffness\": 10, \"damping\": 3, \"gravity\": 1 }"),
                            List.of("tail"));
            PoseSolver solver = new PoseSolver(rig.skeleton);
            rig.run(springs, solver, new Pose(rig.skeleton), 1.5f, frames[r]);
            results[r] = springs.points(0);
        }
        for (int i = 0; i < 4; ++i) {
            Assertions.assertTrue(
                    point(results[0], i).distance(point(results[1], i)) < 0.02f,
                    "Joint " + i + ": " + point(results[0], i) + " vs " + point(results[1], i));
        }
    }

    /** Chains without a spring, or that the plan lacks, aren't moved. */
    @Test
    void testOnlySprungChains() {
        Setup rig = new Setup();
        BodyPlan still =
                DataFormat.readDefinition(
                        JsonText.parse(
                                """
                                { "id": "test:plan", "rig": "test:rig", "variants": [ { "id": "a" } ],
                                  "extensions": [ { "id": "tail", "root": "hips",
                                    "bones": ["c1", "c2", "c3", "c4"] } ] }
                                """,
                                error -> Assertions.fail(error.toString())),
                        BodyPlan.class,
                        FAIL);
        Assertions.assertTrue(new SpringChains(rig.skeleton, still, List.of("tail")).isEmpty());
        Assertions.assertTrue(
                new SpringChains(rig.skeleton, Setup.plan("{}"), List.of("cape")).isEmpty());
        Assertions.assertFalse(
                new SpringChains(rig.skeleton, Setup.plan("{}"), List.of("tail")).isEmpty());
    }
}
