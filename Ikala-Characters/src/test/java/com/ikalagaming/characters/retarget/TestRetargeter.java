package com.ikalagaming.characters.retarget;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.RigMap;
import com.ikalagaming.characters.gltf.GltfImporter;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Proportions;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.BuiltInContent;
import com.ikalagaming.characters.registry.CharacterRegistry;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link Retargeter}: a small rig whose bones point differently from the source's, and
 * the RPG test character onto the biped when the test content is there.
 *
 * @author Ches Burks
 */
class TestRetargeter {
    /** Where the exported test art is, relative to the plugin's folder. */
    private static final Path TEST_CONTENT =
            Path.of("../../Ikala-Asset-Editor/test_content/characters");

    /** How close positions must be, in meters. */
    private static final float EPSILON = 1e-3f;

    /** The built-in definitions. */
    private static CharacterRegistry registry;

    /**
     * Load the built-in definitions.
     *
     * @throws Exception If they can't be read.
     */
    @BeforeAll
    static void load() throws Exception {
        registry = BuiltInContent.loadWith(List.of()).registry();
    }

    /**
     * Where each bone is, in model space, at a time in a clip.
     *
     * @param skeleton The skeleton.
     * @param clip The clip.
     * @param time The time.
     * @return Each bone's position.
     */
    private static Vector3f[] positions(Skeleton skeleton, Clip clip, float time) {
        Pose pose = new Pose(skeleton);
        clip.sample(time, pose);
        PoseSolver solver = new PoseSolver(skeleton);
        solver.solve(pose, Proportions.neutral(skeleton));
        Vector3f[] positions = new Vector3f[skeleton.size()];
        for (int i = 0; i < positions.length; ++i) {
            positions[i] = solver.model(i).getTranslation(new Vector3f());
        }
        return positions;
    }

    /**
     * A rest transform.
     *
     * @param translation The offset.
     * @param rotation The rotation.
     * @return The rest.
     */
    private static Skeleton.Rest rest(Vector3f translation, Quaternionf rotation) {
        return new Skeleton.Rest(translation, rotation);
    }

    /** A chain whose bones point and twist differently from the source's follows its motion. */
    @Test
    void testDifferentAxes() {
        // The source: a root and an arm straight out along x, with the arm's axis along x
        Rig sourceRig =
                new Rig(
                        Id.parse("test:rig/source"),
                        List.of(
                                new Rig.Bone("base", null, Rig.BoneKind.DEFORM),
                                new Rig.Bone("arm", "base", Rig.BoneKind.DEFORM),
                                new Rig.Bone("tip", "arm", Rig.BoneKind.DEFORM)));
        Quaternionf alongX = new Quaternionf().rotateZ((float) -Math.PI / 2);
        Map<String, Skeleton.Rest> sourceRest = new HashMap<>();
        sourceRest.put("base", rest(new Vector3f(), new Quaternionf()));
        sourceRest.put("arm", rest(new Vector3f(0, 1, 0), alongX));
        sourceRest.put("tip", rest(new Vector3f(0, 0.5f, 0), new Quaternionf()));
        // A clip swinging the arm up, by turning it about its local z
        Quaternionf up = new Quaternionf(alongX).rotateZ((float) Math.PI / 2);
        ImportedCharacter.ClipData swing =
                new ImportedCharacter.ClipData(
                        "swing",
                        1,
                        true,
                        List.of(
                                new Clip.RotationTrack(
                                        "arm",
                                        new float[] {0, 1},
                                        new float[] {
                                            alongX.x, alongX.y, alongX.z, alongX.w, up.x, up.y,
                                            up.z, up.w
                                        })),
                        List.of());
        ImportedCharacter source =
                new ImportedCharacter(
                        "source",
                        sourceRig,
                        sourceRest,
                        List.of(),
                        List.of(swing),
                        List.of(),
                        List.of());

        // The target: an unmapped root, a mapped shoulder whose reference points up the y axis
        // with a twist, and an unmapped end
        Rig targetRig =
                new Rig(
                        Id.parse("test:rig/target"),
                        List.of(
                                new Rig.Bone("root", null, Rig.BoneKind.DEFORM),
                                new Rig.Bone("hips", "root", Rig.BoneKind.DEFORM),
                                new Rig.Bone("shoulder", "hips", Rig.BoneKind.DEFORM),
                                new Rig.Bone("hand", "shoulder", Rig.BoneKind.DEFORM)));
        Map<String, Skeleton.Rest> reference = new HashMap<>();
        reference.put("root", rest(new Vector3f(), new Quaternionf()));
        reference.put("hips", rest(new Vector3f(), new Quaternionf()));
        reference.put("shoulder", rest(new Vector3f(0, 2, 0), new Quaternionf().rotateY(0.7f)));
        reference.put("hand", rest(new Vector3f(0, 0.3f, 0), new Quaternionf()));
        RigMap map =
                new RigMap(
                        Id.parse("test:rig_map/chain"),
                        targetRig.id(),
                        null,
                        Map.of("base", "hips", "arm", "shoulder", "tip", "hand"),
                        List.of(),
                        1,
                        null);

        ImportedCharacter moved = Retargeter.retarget(source, map, targetRig, reference);
        Skeleton sourceSkeleton = source.skeleton();
        Skeleton targetSkeleton = moved.skeleton();
        Clip sourceClip = swing.bind(sourceSkeleton);
        Clip targetClip = moved.clip("swing").bind(targetSkeleton);
        for (float time : new float[] {0, 0.3f, 0.7f, 1}) {
            Vector3f[] from = positions(sourceSkeleton, sourceClip, time);
            Vector3f[] to = positions(targetSkeleton, targetClip, time);
            // The tip and the hand, and the arm and the shoulder, are where each other are
            Assertions.assertTrue(
                    from[2].equals(to[3], EPSILON), time + ": " + from[2] + " " + to[3]);
            Assertions.assertTrue(
                    from[1].equals(to[2], EPSILON), time + ": " + from[1] + " " + to[2]);
        }
    }

    /**
     * The RPG character on the biped moves its hands, feet and head where the original does.
     *
     * @throws Exception If the test art can't be read.
     */
    @Test
    void testRpgOntoBiped() throws Exception {
        Path rpg = TEST_CONTENT.resolve("rpg_human/rpg_male.glb");
        Path mannequin = TEST_CONTENT.resolve("mannequin/mannequin.glb");
        Assumptions.assumeTrue(
                Files.exists(rpg) && Files.exists(mannequin), "No test art in the test content");
        Rig biped = registry.get(Rig.class, Id.parse("ikala:rig/biped"));
        RigMap map = registry.get(RigMap.class, Id.parse("ikala:rig_map/modular_rpg_human"));
        ImportedCharacter source = GltfImporter.read(rpg);
        ImportedCharacter reference = GltfImporter.read(mannequin).onRig(biped);
        ImportedCharacter moved = Retargeter.retarget(source, map, biped, reference.rest());

        Assertions.assertEquals(biped, moved.rig());
        Assertions.assertEquals(source.clips().size(), moved.clips().size());
        for (ImportedCharacter.Part part : moved.parts()) {
            for (int joint : part.joints()) {
                Assertions.assertTrue(joint >= 0 && joint < biped.bones().size());
            }
        }

        // At rest the new skeleton's palette is identity, so the meshes draw where they were
        Skeleton skeleton = moved.skeleton();
        PoseSolver solver = new PoseSolver(skeleton);
        solver.solve(new Pose(skeleton), Proportions.neutral(skeleton));
        float[] palette = new float[skeleton.size() * 16];
        solver.writePalette(Proportions.neutral(skeleton), palette, 0);
        for (int bone = 0; bone < skeleton.size(); ++bone) {
            Assertions.assertTrue(
                    new Matrix4f().set(palette, bone * 16).equals(new Matrix4f(), 1e-4f));
        }

        Skeleton sourceSkeleton = source.skeleton();
        Map<String, String> pairs =
                Map.of(
                        "palm_L", "hand.L",
                        "palm_R", "hand.R",
                        "foot_L", "foot.L",
                        "toes_R", "toe.R",
                        "head", "head",
                        "pelvis", "hips",
                        "wep_pos_R", "socket.grip.R");
        for (String clipName : new String[] {"run", "idle", "cast_two_handed_melee_2", "death"}) {
            Clip from = source.clip(clipName).bind(sourceSkeleton);
            Clip to = moved.clip(clipName).bind(skeleton);
            for (float time : new float[] {0, from.getDuration() / 3, from.getDuration() * 0.8f}) {
                Vector3f[] a = positions(sourceSkeleton, from, time);
                Vector3f[] b = positions(skeleton, to, time);
                for (Map.Entry<String, String> pair : pairs.entrySet()) {
                    Vector3f expected = a[sourceSkeleton.index(pair.getKey())];
                    Vector3f actual = b[skeleton.index(pair.getValue())];
                    // Samples land between source keys, so allow for interpolating differently
                    Assertions.assertTrue(
                            expected.distance(actual) < 0.02f,
                            clipName + " at " + time + ": " + pair + " " + expected + " vs "
                                    + actual);
                }
            }
        }
    }
}
