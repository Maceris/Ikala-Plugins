package com.ikalagaming.characters.anim;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.AnimationSet;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Skeleton;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link ActionClip} events.
 *
 * @author Ches Burks
 */
class TestActionClip {
    /** A one-bone skeleton. */
    static final Skeleton SKELETON =
            new Skeleton(
                    new Rig(
                            Id.parse("test:rig"),
                            List.of(new Rig.Bone("root", null, Rig.BoneKind.DEFORM))),
                    Map.of("root", new Skeleton.Rest(new Vector3f(), new Quaternionf())));

    /**
     * A one-second clip with events at 0, 0.25 and 0.75.
     *
     * @param looping Whether it loops.
     * @return The clip.
     */
    private static ActionClip clip(boolean looping) {
        AnimationSet.ClipUse use =
                new AnimationSet.ClipUse(
                        "a.glb#a",
                        2f,
                        1,
                        looping,
                        1,
                        List.of(
                                new AnimationSet.Event(0.75f, "c", null),
                                new AnimationSet.Event(0, "a", null),
                                new AnimationSet.Event(0.25f, "b", null)));
        return new ActionClip(new Clip(SKELETON, "a", 1, looping, List.of(), List.of()), use);
    }

    /**
     * The events between two times, joined.
     *
     * @param clip The clip.
     * @param from The earlier time.
     * @param to The later time.
     * @return The events.
     */
    private static String events(ActionClip clip, float from, float to) {
        List<String> names = new ArrayList<>();
        clip.events(from, to, event -> names.add(event.event()));
        return String.join("", names);
    }

    /** Events fire once per pass, in order, at the start of their window and across loops. */
    @Test
    void testEvents() {
        ActionClip looping = clip(true);
        Assertions.assertTrue(looping.hasSpeed());
        Assertions.assertEquals("ab", events(looping, 0, 0.5f));
        Assertions.assertEquals("c", events(looping, 0.5f, 1));
        Assertions.assertEquals("ca", events(looping, 0.5f, 1.1f));
        Assertions.assertEquals("cab", events(looping, 1.75f, 2.3f));
        Assertions.assertEquals("", events(looping, 0.3f, 0.3f));

        ActionClip once = clip(false);
        Assertions.assertEquals("abc", events(once, 0, 5));
        Assertions.assertEquals("", events(once, 1, 2));
    }
}
