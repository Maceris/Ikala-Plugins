package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.gltf.ImportedCharacter;

import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for {@link PartSlots}.
 *
 * @author Ches Burks
 */
class TestPartSlots {
    /**
     * A character with parts of these names and nothing else.
     *
     * @param names The part names.
     * @return The character.
     */
    private static ImportedCharacter character(String... names) {
        List<ImportedCharacter.Part> parts =
                java.util.Arrays.stream(names)
                        .map(
                                name ->
                                        new ImportedCharacter.Part(
                                                name,
                                                new float[0],
                                                new int[0],
                                                new float[0],
                                                new int[0],
                                                -1,
                                                new Vector3f(),
                                                new Vector3f()))
                        .toList();
        Rig rig =
                new Rig(
                        Id.parse("test:rig/one"),
                        List.of(new Rig.Bone("root", null, Rig.BoneKind.DEFORM)));
        return new ImportedCharacter("test", rig, Map.of(), parts, List.of(), List.of(), List.of());
    }

    /** Modular parts sort into slots by the word before their number or default. */
    @Test
    void testModular() {
        PartSlots slots =
                PartSlots.of(
                        character(
                                "human_male_body_default",
                                "human_male_hair_1",
                                "human_male_hair_2",
                                "human_male_head_2",
                                "human_male_head_default",
                                "human_male_heavy_armor",
                                "human_male_light_armor_2.0",
                                "human_male_light_armor_2.1",
                                "human_male_light_armor_3.0",
                                "human_male_light_armor_3.1",
                                "training_dummy"));
        List<String> ids = slots.slots().stream().map(PartSlots.Slot::id).toList();
        Assertions.assertEquals(List.of("body", "hair", "head", "armor", "dummy"), ids);

        PartSlots.Slot head = slots.slots().get(2);
        Assertions.assertFalse(head.optional());
        Assertions.assertEquals(List.of("head 2", "head default"), head.labels());
        Assertions.assertEquals(1, head.initial());

        PartSlots.Slot armor = slots.slots().get(3);
        Assertions.assertTrue(armor.optional());
        Assertions.assertEquals(-1, armor.initial());
        Assertions.assertEquals(
                List.of("heavy armor", "light armor 2", "light armor 3"), armor.labels());
        // A mesh's primitives are one choice
        Assertions.assertEquals(List.of(List.of(5), List.of(6, 7), List.of(8, 9)), armor.options());

        // Hair starts on its first, so modular characters aren't bald
        Assertions.assertEquals(List.of(0, 1, 4), slots.initialParts());
    }

    /** Without default parts, every part is its own slot and is worn. */
    @Test
    void testWhole() {
        PartSlots slots = PartSlots.of(character("mannequin_head", "mannequin_foot.L"));
        Assertions.assertEquals(2, slots.slots().size());
        Assertions.assertEquals(List.of("head"), slots.slots().get(0).labels());
        Assertions.assertFalse(slots.slots().get(1).optional());
        Assertions.assertEquals(List.of(0, 1), slots.initialParts());
    }
}
