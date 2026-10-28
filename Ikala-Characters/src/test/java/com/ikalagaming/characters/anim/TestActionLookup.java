package com.ikalagaming.characters.anim;

import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.data.Problem;
import com.ikalagaming.characters.registry.CharacterLoader;
import com.ikalagaming.characters.registry.CharacterRegistry;
import com.ikalagaming.characters.registry.ContentSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for how {@link ActionLookup} finds actions, on the test content's biped sets and zombie
 * style.
 *
 * @author Ches Burks
 */
class TestActionLookup {
    /** The biped plan. */
    private static final Id BIPED = Id.parse("ikala:biped");

    /** The base set. */
    private static final Id BASE = Id.parse("ikala:anim/biped");

    /** The zombie style. */
    private static final Id ZOMBIE = Id.parse("ikala:style/zombie");

    /** The test content's definitions. */
    private static CharacterRegistry registry;

    /**
     * Load the test content.
     *
     * @throws URISyntaxException If the content can't be found.
     */
    @BeforeAll
    static void load() throws URISyntaxException {
        Path content = Path.of(TestActionLookup.class.getResource("/content/ikala").toURI());
        CharacterLoader.Result result =
                CharacterLoader.load(List.of(new ContentSource("ikala", content, "test")));
        Assertions.assertTrue(
                result.problems().isEmpty(),
                result.problems().stream().map(Problem::toString).collect(Collectors.joining()));
        registry = result.registry();
    }

    /**
     * The first clip's name an action resolves to.
     *
     * @param lookup The lookup.
     * @param action The action.
     * @return The clip, as file#name.
     */
    private static String clip(ActionLookup lookup, String action) {
        return lookup.resolve(action).action().clips().get(0).clip();
    }

    /** Actions come from the sets, and a variant's own override them. */
    @Test
    void testSetsAndVariants() {
        ActionLookup male = new ActionLookup(registry, BIPED, "male", List.of());
        ActionLookup.Resolved idle = male.resolve("idle");
        Assertions.assertTrue(idle.exact());
        Assertions.assertEquals(BASE, idle.source());
        Assertions.assertEquals("test.glb#idle", clip(male, "idle"));
        Assertions.assertEquals(
                Id.parse("ikala:anim/biped_emotes"), male.resolve("emote.wave").source());

        ActionLookup female = new ActionLookup(registry, BIPED, "female", List.of());
        Assertions.assertEquals("test.glb#idle_female", clip(female, "idle"));
        Assertions.assertEquals("test.glb#walk", clip(female, "move"));
    }

    /** A style overrides what it has, and the sets give the rest. */
    @Test
    void testStyle() {
        ActionLookup zombie = new ActionLookup(registry, BIPED, "male", List.of(ZOMBIE));
        Assertions.assertEquals("test.glb#shamble", clip(zombie, "move"));
        Assertions.assertEquals(ZOMBIE, zombie.resolve("attack.melee").source());
        Assertions.assertEquals("test.glb#idle", clip(zombie, "idle"));
        // A style for another plan, or a missing one, is skipped
        ActionLookup missing =
                new ActionLookup(registry, BIPED, "male", List.of(Id.parse("ikala:style/none")));
        Assertions.assertEquals("test.glb#walk", clip(missing, "move"));
    }

    /** Missing actions fall back to their parents, then to idle. */
    @Test
    void testFallback() {
        ActionLookup lookup = new ActionLookup(registry, BIPED, "male", List.of());
        ActionLookup.Resolved second = lookup.resolve("attack.melee.2");
        Assertions.assertFalse(second.exact());
        Assertions.assertEquals("attack.melee.2", second.requested());
        Assertions.assertEquals("attack.melee", second.found());
        Assertions.assertEquals(ActionLookup.IDLE, lookup.resolve("emote.dance").found());
        Assertions.assertEquals(ActionLookup.IDLE, lookup.resolve("fly").found());

        Assertions.assertEquals("attack.melee", ActionLookup.parent("attack.melee.2"));
        Assertions.assertNull(ActionLookup.parent("attack"));
        Assertions.assertTrue(lookup.actions().containsAll(List.of("emote.wave", "death")));

        ActionLookup nothing = new ActionLookup(registry, Id.parse("ikala:quad"), "a", List.of());
        Assertions.assertTrue(nothing.isEmpty());
        Assertions.assertNull(nothing.resolve("idle"));
    }
}
