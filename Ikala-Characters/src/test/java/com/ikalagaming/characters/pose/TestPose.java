package com.ikalagaming.characters.pose;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.JsonText;
import com.ikalagaming.characters.data.Report;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.Slider;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for posing: {@link Skeleton}, {@link Clip}, {@link Proportions} and {@link PoseSolver}, on
 * a small rig with Y up: a root, hips, one leg and one arm along X.
 *
 * @author Ches Burks
 */
class TestPose {
    /** How close floats must be. */
    private static final float EPSILON = 1e-5f;

    /** The test rig. */
    private Rig rig;

    /** The test skeleton. */
    private Skeleton skeleton;

    /** The test plan, whose leg is thigh.L, shin.L, foot.L. */
    private BodyPlan plan;

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

    /**
     * Read a definition from JSON that must be valid.
     *
     * @param <T> The definition type.
     * @param text The JSON.
     * @param type The definition type.
     * @return The definition.
     */
    private static <T extends Record> T read(String text, Class<T> type) {
        return DataFormat.readDefinition(
                JsonText.parse(text, error -> Assertions.fail(error.toString())), type, FAIL);
    }

    /** Build the test rig, skeleton and plan. */
    @BeforeEach
    void setUp() {
        rig =
                new Rig(
                        Id.parse("test:rig"),
                        List.of(
                                new Rig.Bone("root", null, Rig.BoneKind.DEFORM),
                                new Rig.Bone("hips", "root", Rig.BoneKind.DEFORM),
                                new Rig.Bone("thigh.L", "hips", Rig.BoneKind.DEFORM),
                                new Rig.Bone("shin.L", "thigh.L", Rig.BoneKind.DEFORM),
                                new Rig.Bone("foot.L", "shin.L", Rig.BoneKind.DEFORM),
                                new Rig.Bone("arm", "hips", Rig.BoneKind.DEFORM),
                                new Rig.Bone("forearm", "arm", Rig.BoneKind.DEFORM),
                                new Rig.Bone("girth.forearm", "forearm", Rig.BoneKind.SHAPE),
                                new Rig.Bone("hand", "forearm", Rig.BoneKind.DEFORM)));
        Map<String, Skeleton.Rest> rest = new HashMap<>();
        rest.put("root", rest(0, 0, 0));
        rest.put("hips", rest(0, 1, 0));
        rest.put("thigh.L", rest(0.1f, 0, 0));
        rest.put("shin.L", rest(0, -0.45f, 0));
        rest.put("foot.L", rest(0, -0.45f, 0));
        rest.put("arm", rest(0.2f, 0.4f, 0));
        rest.put("forearm", rest(0.3f, 0, 0));
        rest.put("girth.forearm", rest(0, 0, 0));
        rest.put("hand", rest(0.25f, 0, 0));
        skeleton = new Skeleton(rig, rest);
        plan =
                read(
                        """
                        { "id": "test:plan", "rig": "test:rig", "variants": [ { "id": "a" } ],
                          "ik": { "feet": [ { "chain": ["thigh.L", "shin.L", "foot.L"] } ] } }
                        """,
                        BodyPlan.class);
    }

    /**
     * A rest transform with no rotation.
     *
     * @param x The offset's x.
     * @param y The offset's y.
     * @param z The offset's z.
     * @return The transform.
     */
    private static Skeleton.Rest rest(float x, float y, float z) {
        return new Skeleton.Rest(new Vector3f(x, y, z), new Quaternionf());
    }

    /**
     * Where a bone ends up in model space.
     *
     * @param solver The solver, after a solve.
     * @param bone The bone's name.
     * @return Its origin.
     */
    private Vector3f position(PoseSolver solver, String bone) {
        return solver.model(skeleton.index(bone)).getTranslation(new Vector3f());
    }

    /**
     * Where a vertex weighted fully to a bone ends up.
     *
     * @param palette The palette.
     * @param bone The bone's name.
     * @param bindPosition The vertex at rest.
     * @return The vertex now.
     */
    private Vector3f skinned(float[] palette, String bone, Vector3fc bindPosition) {
        Matrix4f matrix =
                new Matrix4f().set(palette, skeleton.index(bone) * Skeleton.MATRIX_FLOATS);
        return matrix.transformPosition(bindPosition, new Vector3f());
    }

    /**
     * Assert two vectors are equal within {@link #EPSILON}.
     *
     * @param expected What it should be.
     * @param actual What it is.
     */
    private static void assertVector(Vector3fc expected, Vector3fc actual) {
        Assertions.assertTrue(
                expected.equals(actual, EPSILON), "Expected " + expected + " but was " + actual);
    }

    /** At rest with neutral proportions, every skinning matrix is identity. */
    @Test
    void testRestPaletteIsIdentity() {
        PoseSolver solver = new PoseSolver(skeleton);
        Proportions neutral = Proportions.neutral(skeleton);
        solver.solve(new Pose(skeleton), neutral);
        float[] palette = new float[skeleton.size() * Skeleton.MATRIX_FLOATS];
        solver.writePalette(neutral, palette, 0);
        for (int bone = 0; bone < skeleton.size(); ++bone) {
            Matrix4f matrix = new Matrix4f().set(palette, bone * Skeleton.MATRIX_FLOATS);
            Assertions.assertTrue(
                    matrix.equals(new Matrix4f(), EPSILON), skeleton.name(bone) + " " + matrix);
        }
        assertVector(new Vector3f(0.75f, 1.4f, 0), position(solver, "hand"));
    }

    /** Rotating a bone swings its children around it, and its vertices with it. */
    @Test
    void testRotationMovesChildren() {
        Pose pose = new Pose(skeleton);
        pose.setRotation(skeleton.index("forearm"), new Quaternionf().rotateZ((float) Math.PI / 2));
        PoseSolver solver = new PoseSolver(skeleton);
        Proportions neutral = Proportions.neutral(skeleton);
        solver.solve(pose, neutral);
        assertVector(new Vector3f(0.5f, 1.65f, 0), position(solver, "hand"));

        float[] palette = new float[skeleton.size() * Skeleton.MATRIX_FLOATS];
        solver.writePalette(neutral, palette, 0);
        // A vertex halfway along the forearm swings up with it
        assertVector(
                new Vector3f(0.5f, 1.525f, 0),
                skinned(palette, "forearm", new Vector3f(0.625f, 1.4f, 0)));
    }

    /** Length scales stretch offsets and move children; shape scales don't pass on. */
    @Test
    void testLengthAndShape() {
        Slider sliders =
                read(
                        """
                        { "id": "test:slider/arm", "ops": [
                          { "bones": ["hand"], "type": "length", "min": 0.5, "max": 2 },
                          { "bones": ["girth.forearm"], "type": "shape", "min": 0.5, "max": 2,
                            "axes": "yz" } ] }
                        """,
                        Slider.class);
        Proportions proportions =
                Proportions.fromSliders(
                        skeleton, plan, List.of(sliders), Map.of(sliders.id(), new float[] {1}), 1);
        PoseSolver solver = new PoseSolver(skeleton);
        solver.solve(new Pose(skeleton), proportions);
        float[] palette = new float[skeleton.size() * Skeleton.MATRIX_FLOATS];
        solver.writePalette(proportions, palette, 0);

        // The forearm is twice as long, so the hand moves out
        assertVector(new Vector3f(1.0f, 1.4f, 0), position(solver, "hand"));
        // Girth doubles across the bone but not along it
        assertVector(
                new Vector3f(0.6f, 1.5f, 0.1f),
                skinned(palette, "girth.forearm", new Vector3f(0.6f, 1.45f, 0.05f)));
        // A vertex on the hand stays with the hand, unaffected by the girth
        assertVector(
                new Vector3f(1.1f, 1.45f, 0),
                skinned(palette, "hand", new Vector3f(0.85f, 1.45f, 0)));
    }

    /** The overall scale scales everything from the root. */
    @Test
    void testOverallScale() {
        Proportions proportions = Proportions.fromSliders(skeleton, plan, List.of(), Map.of(), 2);
        PoseSolver solver = new PoseSolver(skeleton);
        solver.solve(new Pose(skeleton), proportions);
        assertVector(new Vector3f(1.5f, 2.8f, 0), position(solver, "hand"));
    }

    /** Longer legs raise the hips and lengthen root motion. */
    @Test
    void testLegScale() {
        Slider legs =
                read(
                        """
                        { "id": "test:slider/legs", "ops": [
                          { "bones": ["shin.L", "foot.L"], "type": "length", "min": 0.8, "max": 1.2 } ] }
                        """,
                        Slider.class);
        Proportions proportions =
                Proportions.fromSliders(
                        skeleton, plan, List.of(legs), Map.of(legs.id(), new float[] {1}), 1);
        Assertions.assertEquals(1.2f, proportions.getLegScale(), EPSILON);

        Pose pose = new Pose(skeleton);
        pose.setTranslation(skeleton.index("root"), new Vector3f(0, 0, 1));
        PoseSolver solver = new PoseSolver(skeleton);
        solver.solve(pose, proportions);
        assertVector(new Vector3f(0, 0, 1.2f), position(solver, "root"));
        assertVector(new Vector3f(0, 1.2f, 1.2f), position(solver, "hips"));
        // The ankle, 0.1 m up at rest, rises with the leg; foot IK will plant the sole later
        Assertions.assertEquals(0.12f, position(solver, "foot.L").y(), EPSILON);
    }

    /** Op scales pass 1 at 0, reach min and max at -1 and 1, and keep going past them. */
    @Test
    void testOpScale() {
        Slider.Op op = new Slider.Op(List.of("a"), Slider.OpType.SHAPE, 0.5f, 1.5f, "xyz");
        Assertions.assertEquals(1, op.scale(0), EPSILON);
        Assertions.assertEquals(0.5f, op.scale(-1), EPSILON);
        Assertions.assertEquals(1.5f, op.scale(1), EPSILON);
        Assertions.assertEquals(2, op.scale(2), EPSILON);
        Assertions.assertEquals(Slider.Op.MIN_SCALE, op.scale(-2), EPSILON);
    }

    /** Vector sliders scale each axis by its own value, and need three values. */
    @Test
    void testVectorSlider() {
        Slider nose =
                read(
                        """
                        { "id": "test:slider/girth", "vector": true, "ops": [
                          { "bones": ["girth.forearm"], "type": "shape", "min": 0.5, "max": 2 } ] }
                        """,
                        Slider.class);
        Proportions proportions =
                Proportions.fromSliders(
                        skeleton,
                        plan,
                        List.of(nose),
                        Map.of(nose.id(), new float[] {1, 0, -1}),
                        1);
        int bone = skeleton.index("girth.forearm");
        Assertions.assertEquals(2, proportions.shape(bone, 0), EPSILON);
        Assertions.assertEquals(1, proportions.shape(bone, 1), EPSILON);
        Assertions.assertEquals(0.5f, proportions.shape(bone, 2), EPSILON);
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        Proportions.fromSliders(
                                skeleton,
                                plan,
                                List.of(nose),
                                Map.of(nose.id(), new float[] {1}),
                                1));
    }

    /** Clips interpolate between keys, loop or hold, and only touch their own bones. */
    @Test
    void testClipSampling() {
        Quaternionf quarter = new Quaternionf().rotateZ((float) Math.PI / 2);
        Clip.RotationTrack turn =
                new Clip.RotationTrack(
                        "forearm",
                        new float[] {0, 1},
                        new float[] {0, 0, 0, 1, quarter.x, quarter.y, quarter.z, quarter.w});
        Clip.TranslationTrack walk =
                new Clip.TranslationTrack(
                        "root", new float[] {0, 1}, new float[] {0, 0, 0, 0, 0, 2});
        Clip looping = new Clip(skeleton, "loop", 1, true, List.of(turn), List.of(walk));
        Clip once = new Clip(skeleton, "once", 1, false, List.of(turn), List.of(walk));

        Pose pose = new Pose(skeleton);
        Quaternionf rotation = new Quaternionf();
        Vector3f translation = new Vector3f();
        int forearm = skeleton.index("forearm");

        looping.sample(0.5f, pose);
        Assertions.assertEquals(Math.PI / 4, pose.rotation(forearm, rotation).angle(), EPSILON);
        assertVector(new Vector3f(0, 0, 1), pose.translation(skeleton.index("root"), translation));

        looping.sample(1.25f, pose);
        Assertions.assertEquals(Math.PI / 8, pose.rotation(forearm, rotation).angle(), EPSILON);

        once.sample(5, pose);
        Assertions.assertEquals(Math.PI / 2, pose.rotation(forearm, rotation).angle(), EPSILON);
        once.sample(-1, pose);
        Assertions.assertEquals(0, pose.rotation(forearm, rotation).angle(), EPSILON);

        // The hand isn't animated, so it keeps whatever it had
        pose.setRotation(skeleton.index("hand"), quarter);
        looping.sample(0.3f, pose);
        Assertions.assertEquals(
                Math.PI / 2, pose.rotation(skeleton.index("hand"), rotation).angle(), EPSILON);
    }

    /** Broken skeletons and clips are refused. */
    @Test
    void testRejects() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Skeleton(rig, Map.of()));
        Clip.RotationTrack missing =
                new Clip.RotationTrack("tail", new float[] {0}, new float[] {0, 0, 0, 1});
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new Clip(skeleton, "c", 1, true, List.of(missing), List.of()));
        Clip.RotationTrack backwards =
                new Clip.RotationTrack(
                        "hand", new float[] {1, 0}, new float[] {0, 0, 0, 1, 0, 0, 0, 1});
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new Clip(skeleton, "c", 1, true, List.of(backwards), List.of()));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new Clip(skeleton, "c", 0, true, List.of(), List.of()));
    }
}
