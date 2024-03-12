package com.ikalagaming.factory.world;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.localization.Localization;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Tests for the world.
 *
 * @author Ches Burks
 */
class TestTagRegistry {

    private TagRegistry tagRegistry;
    private static MockedStatic<FactoryPlugin> fakePlugin;

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        var bundle =
                ResourceBundle.getBundle(
                        "com.ikalagaming.factory.strings", Localization.getLocale());

        fakePlugin = Mockito.mockStatic(FactoryPlugin.class);
        fakePlugin.when(FactoryPlugin::getResourceBundle).thenReturn(bundle);
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        fakePlugin.close();
    }

    @BeforeEach
    void setUp() {
        tagRegistry = new TagRegistry();
    }

    /** Test adding new tags. */
    @Test
    void testAddingTags() {

        Assertions.assertTrue(tagRegistry.addTag("testing_tag_001"));
        Assertions.assertTrue(tagRegistry.addTag("testing_tag_001_variant", "testing_tag_001"));
        Assertions.assertFalse(tagRegistry.addTag("testing_tag_001"));

        Assertions.assertFalse(tagRegistry.addTag("testing_tag_002", "does_not_exist"));
        Assertions.assertFalse(tagRegistry.addTag("same", "same"));

        Assertions.assertThrows(NullPointerException.class, () -> tagRegistry.addTag(null));
        Assertions.assertThrows(NullPointerException.class, () -> tagRegistry.addTag(null, null));
    }

    /** Test that we can retrieve tags. */
    @Test
    void testFetchTag() {

        final String parentName = "liquid";
        Assertions.assertTrue(tagRegistry.addTag(parentName));
        Assertions.assertTrue(tagRegistry.tagExists(parentName));

        Optional<Tag> maybeLiquid = tagRegistry.findTag(parentName);
        Assertions.assertTrue(maybeLiquid.isPresent());
        Assertions.assertEquals(parentName, maybeLiquid.get().name());

        final String childName = "sludge";
        Assertions.assertTrue(tagRegistry.addTag(childName, parentName));
        Assertions.assertTrue(tagRegistry.tagExists(parentName));
        Assertions.assertTrue(tagRegistry.tagExists(childName));

        Optional<Tag> maybeSludge = tagRegistry.findTag(childName);
        Assertions.assertTrue(maybeSludge.isPresent());
        Assertions.assertEquals(childName, maybeSludge.get().name());
        Assertions.assertEquals(maybeLiquid.get(), maybeSludge.get().parent());
    }
}
