package com.ikalagaming.characters.anim;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.Skeleton;
import com.ikalagaming.characters.registry.CharacterLoader;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;

import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Tests for {@link Animator}, on the test content's biped actions with made-up clips: each clip
 * turns the spine and left thigh about x by its own angle, so a pose shows which clips it came from
 * and how much of each.
 *
 * @author Ches Burks
 */
class TestAnimator {
    /** How close angles must be, in radians. */
    private static final float EPSILON = 1e-3f;

    /** Each made-up clip's angle, by its name in its file. */
    private static final Map<String, Float> ANGLES =
            Map.of(
                    "idle", 0.1f,
                    "walk", 0.2f,
                    "run", 0.4f,
                    "slash", 1.0f,
                    "death", 1.5f,
                    "shamble", -0.3f,
                    "lurch", -1.0f,
                    "lurch_2", -1.0f);

    /** The test content's definitions. */
    private static CharacterRegistry registry;

    /** The biped plan. */
    private static BodyPlan plan;

    /** A skeleton of the biped rig, every bone at the origin unturned. */
    private static Skeleton skeleton;

    /** The animator. */
    private Animator animator;

    /** The pose it writes. */
    private Pose pose;

    /** Events heard. */
    private final List<String> events = new ArrayList<>();

    /**
     * Load the test content.
     *
     * @throws URISyntaxException If the content can't be found.
     */
    @BeforeAll
    static void load() throws URISyntaxException {
        Path content = Path.of(TestAnimator.class.getResource("/content/ikala").toURI());
        registry =
                CharacterLoader.load(List.of(new ContentSource("ikala", content, "test")))
                        .registry();
        plan = registry.get(BodyPlan.class, Id.parse("ikala:biped"));
        Rig rig = registry.get(Rig.class, plan.rig());
        Map<String, Skeleton.Rest> rest = new HashMap<>();
        for (Rig.Bone bone : rig.bones()) {
            rest.put(bone.name(), new Skeleton.Rest(new Vector3f(), new Quaternionf()));
        }
        skeleton = new Skeleton(rig, rest);
    }

    /**
     * The made-up clips an action plays.
     *
     * @param resolved The action.
     * @return Its clips: one second long, looping unless the use says not to.
     */
    private static List<ActionClip> clips(ActionLookup.Resolved resolved) {
        List<ActionClip> clips = new ArrayList<>();
        for (AnimationSet.ClipUse use : resolved.action().clips()) {
            Float angle = ANGLES.get(use.name());
            if (angle == null) {
                continue;
            }
            Quaternionf turn = new Quaternionf(new AxisAngle4f(angle, 1, 0, 0));
            float[] key = {turn.x, turn.y, turn.z, turn.w};
            List<Clip.RotationTrack> tracks =
                    List.of(
                            new Clip.RotationTrack("spine", new float[] {0}, key),
                            new Clip.RotationTrack("thigh.L", new float[] {0}, key));
            boolean loop = use.loop() == null || use.loop();
            clips.add(
                    new ActionClip(
                            new Clip(skeleton, use.name(), 1, loop, tracks, List.of()), use));
        }
        return clips;
    }

    /**
     * Start an animator for a style.
     *
     * @param styles The styles.
     */
    private void start(List<Id> styles) {
        ActionLookup lookup = new ActionLookup(registry, plan.id(), "male", styles);
        animator =
                new Animator(lookup, TestAnimator::clips, skeleton, plan, new SplittableRandom(1));
        animator.setListener(event -> events.add(event.event()));
        pose = new Pose(skeleton);
    }

    /** Start a plain male biped. */
    @BeforeEach
    void setUp() {
        start(List.of());
    }

    /**
     * A bone's angle about x in the pose.
     *
     * @param bone The bone's name.
     * @return The angle, in radians.
     */
    private float angle(String bone) {
        Quaternionf rotation = pose.rotation(skeleton.index(bone), new Quaternionf());
        float sign = rotation.w < 0 ? -1 : 1;
        return 2 * (float) Math.atan2(rotation.x * sign, rotation.w * sign);
    }

    /**
     * Update in steps.
     *
     * @param seconds How long.
     * @param steps In how many steps.
     */
    private void run(float seconds, int steps) {
        for (int i = 0; i < steps; ++i) {
            animator.update(seconds / steps, pose);
        }
    }

    /** Idle plays first, and a new movement crossfades in over its fade. */
    @Test
    void testCrossfade() {
        run(0.5f, 5);
        Assertions.assertEquals(0.1f, angle("spine"), EPSILON);
        Assertions.assertEquals("idle", animator.movement().action().found());

        animator.setMovement("move", 1.4f);
        run(0.1f, 1);
        // Halfway through the 0.2 s fade from idle to walking
        Assertions.assertEquals(0.15f, angle("spine"), EPSILON);
        run(0.2f, 2);
        Assertions.assertEquals(0.2f, angle("spine"), EPSILON);
    }

    /** Moving blends the walk and run by speed, playing past the ends at a scaled rate. */
    @Test
    void testSpeedBlend() {
        animator.setMovement("move", 2.95f);
        run(1, 10);
        // Halfway between the walk at 1.4 m/s and the run at 4.5 m/s
        Assertions.assertEquals(0.3f, angle("spine"), EPSILON);
        animator.setMovement("move", 9);
        run(0.05f, 1);
        Assertions.assertEquals(0.4f, angle("spine"), EPSILON);
        Assertions.assertEquals("run", animator.movement().clip().getClip().getName());

        // The walk's footsteps are heard while it leads the blend, once each a cycle
        start(List.of());
        animator.setMovement("move", 1.4f);
        events.clear();
        run(1, 20);
        Assertions.assertEquals(List.of("foot.L.down", "foot.R.down"), events);
    }

    /** An action plays on its bone group over the movement and fades out at its end. */
    @Test
    void testMaskedAction() {
        animator.setMovement("move", 1.4f);
        run(0.5f, 5);
        animator.play("attack.melee", false);
        run(0.2f, 4);
        Assertions.assertEquals(1.0f, angle("spine"), EPSILON);
        Assertions.assertEquals(0.2f, angle("thigh.L"), EPSILON);
        run(0.2f, 4);
        Assertions.assertTrue(events.contains("hit"), events.toString());
        Assertions.assertTrue(events.contains("fx:ikala:fx/sparks"), events.toString());

        run(1, 20);
        Assertions.assertNull(animator.action());
        Assertions.assertEquals(0.2f, angle("spine"), EPSILON);
    }

    /** A held action keeps its last frame; a stopped one fades out. */
    @Test
    void testHoldAndStop() {
        animator.play("death", true);
        run(3, 30);
        Assertions.assertEquals(1.5f, angle("thigh.L"), EPSILON);
        animator.stop();
        run(0.5f, 5);
        Assertions.assertNull(animator.action());
        Assertions.assertEquals(0.1f, angle("thigh.L"), EPSILON);
    }

    /** A style's actions replace the sets', and actions fall back to their parents. */
    @Test
    void testStyleAndFallback() {
        start(List.of(Id.parse("ikala:style/zombie")));
        animator.setMovement("move", 3);
        animator.play("attack.melee.2", false);
        run(0.1f, 2);
        // The style's attack has no bone group, so it covers the shamble on the whole body
        Assertions.assertEquals("shamble", animator.movement().clip().getClip().getName());
        Assertions.assertEquals(-0.65f, angle("thigh.L"), EPSILON);
        run(0.2f, 4);
        Assertions.assertEquals(-1.0f, angle("thigh.L"), EPSILON);
        Assertions.assertEquals("attack.melee", animator.action().action().found());
    }
}
