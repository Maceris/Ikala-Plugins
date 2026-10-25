package com.ikalagaming.characters.registry;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.definition.BodyPlan;
import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.Slider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Checks the definitions that ship in the plugin.
 *
 * @author Ches Burks
 */
class TestBuiltInContent {

    /**
     * The built-in definitions load with no problems at all, and the biped uses every slider.
     *
     * @throws Exception If they can't be read.
     */
    @Test
    void testLoadsCleanly() throws Exception {
        CharacterLoader.Result result = BuiltInContent.loadWith(List.of());
        Assertions.assertTrue(
                result.problems().isEmpty(),
                result.problems().stream()
                        .map(Problem::toString)
                        .collect(Collectors.joining("\n")));
        CharacterRegistry registry = result.registry();
        BodyPlan biped = registry.get(BodyPlan.class, Id.parse("ikala:biped"));
        Assertions.assertNotNull(biped);
        Rig rig = registry.get(Rig.class, biped.rig());
        Assertions.assertEquals(101, rig.bones().size());
        Assertions.assertEquals(
                registry.all(Slider.class).size(),
                biped.matchingSliders(registry.all(Slider.class)).size());
    }
}
