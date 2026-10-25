package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.gltf.ImportedCharacter;

import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link PartCategories}.
 *
 * @author Ches Burks
 */
class TestPartCategories {
    /**
     * A part spanning some heights.
     *
     * @param name Its name.
     * @param bottom Its lowest point.
     * @param top Its highest point.
     * @return The part.
     */
    private static ImportedCharacter.Part part(String name, float bottom, float top) {
        return new ImportedCharacter.Part(
                name,
                new float[0],
                new int[0],
                new float[0],
                new int[0],
                -1,
                new Vector3f(-0.2f, bottom, -0.1f),
                new Vector3f(0.2f, top, 0.1f));
    }

    /** Slots go to the head, upper body or lower body by how high their meshes sit. */
    @Test
    void testRegions() {
        List<ImportedCharacter.Part> parts = new ArrayList<>();
        parts.add(part("human_body_default", 0, 1.9f));
        parts.add(part("human_hair_1", 1.7f, 2));
        parts.add(part("human_helmet", 1.65f, 2.05f));
        parts.add(part("human_heavy_armor", 0.9f, 1.6f));
        parts.add(part("human_boots", 0, 0.5f));
        parts.add(part("human_feet_default", 0, 0.1f));
        Rig rig =
                new Rig(
                        Id.parse("test:rig/one"),
                        List.of(new Rig.Bone("root", null, Rig.BoneKind.DEFORM)));
        ImportedCharacter character =
                new ImportedCharacter(
                        "test", rig, Map.of(), parts, List.of(), List.of(), List.of());
        PartSlots slots = PartSlots.of(character);
        PartCategories categories = PartCategories.of(character, slots);

        Assertions.assertEquals(
                List.of(PartCategories.HEAD, PartCategories.UPPER_BODY, PartCategories.LOWER_BODY),
                categories.categories().stream().map(PartCategories.Category::id).toList());
        Assertions.assertEquals(
                List.of("hair", "helmet"), slotIds(slots, categories.categories().get(0)));
        Assertions.assertEquals(
                List.of("body", "armor"), slotIds(slots, categories.categories().get(1)));
        Assertions.assertEquals(
                List.of("boots", "feet"), slotIds(slots, categories.categories().get(2)));

        // Each category's box is around its own meshes, the whole box around all of them
        Assertions.assertEquals(1.65f, categories.categories().get(0).min().y, 1e-6f);
        Assertions.assertEquals(0.5f, categories.categories().get(2).max().y, 1e-6f);
        Assertions.assertEquals(2.05f, categories.max().y, 1e-6f);
    }

    /**
     * The IDs of a category's slots.
     *
     * @param slots The slots.
     * @param category The category.
     * @return The IDs.
     */
    private static List<String> slotIds(PartSlots slots, PartCategories.Category category) {
        return category.slots().stream().map(s -> slots.slots().get(s).id()).toList();
    }
}
