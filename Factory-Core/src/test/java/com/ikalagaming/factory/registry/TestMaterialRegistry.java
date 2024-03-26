package com.ikalagaming.factory.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.world.Material;
import com.ikalagaming.localization.Localization;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Tests for tags.
 *
 * @author Ches Burks
 */
class TestMaterialRegistry {

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

    private TagRegistry tagRegistry;

    private MaterialRegistry materialRegistry;

    @BeforeEach
    void setUp() {
        tagRegistry = new TagRegistry();
        materialRegistry = new MaterialRegistry(tagRegistry);
    }

    /** Test that we can retrieve materials. */
    @Test
    void testFetchMaterial() {

        assertTrue(tagRegistry.addTag("solid"));
        assertTrue(tagRegistry.addTag("powder", "solid"));

        final String name = "dust";
        assertTrue(materialRegistry.addMaterial("parent"));
        assertTrue(materialRegistry.addMaterial(name, List.of("powder"), "parent"));

        assertTrue(materialRegistry.materialExists(name));
        assertFalse(materialRegistry.materialExists(name + "y"));
        Optional<Material> maybeMaterial = materialRegistry.findMaterial(name);
        assertTrue(maybeMaterial.isPresent());
        assertEquals(name, maybeMaterial.get().name());
        assertEquals("parent", maybeMaterial.get().parent().name());

        assertTrue(materialRegistry.materialHasTag(name, "solid"));
        assertTrue(materialRegistry.materialHasTag(name, "powder"));
    }

    /** Test the creation of materials. */
    @Test
    void testMaterialCreation() {

        assertDoesNotThrow(() -> materialRegistry.addMaterial("simple"));

        assertTrue(tagRegistry.addTag("normal_tag"));

        assertAll(
                () -> assertTrue(materialRegistry.addMaterial("Sample1")),
                () -> assertTrue(materialRegistry.addMaterial("Sample2", new ArrayList<>())),
                () -> assertTrue(materialRegistry.addMaterial("Sample3", "simple")),
                () -> assertTrue(materialRegistry.addMaterial("Sample4", List.of("normal_tag"))),
                () -> assertTrue(materialRegistry.addMaterial("Sample5", new ArrayList<>(), null)),
                () ->
                        assertTrue(
                                materialRegistry.addMaterial(
                                        "Sample6", new ArrayList<>(), "simple")));
    }

    /** Check for the negative scenarios for material creation. */
    @Test
    void testMaterialCreationNegative() {
        assertDoesNotThrow(() -> materialRegistry.addMaterial("parent"));

        final String nullString = null;
        final List<String> nullList = null;
        final List<String> normalList = new ArrayList<>();

        assertThrows(
                NullPointerException.class,
                () -> materialRegistry.addMaterial(nullString, normalList, nullString));
        assertThrows(
                NullPointerException.class, () -> materialRegistry.addMaterial("Sample", nullList));
        assertThrows(
                NullPointerException.class,
                () -> materialRegistry.addMaterial("Sample", nullString));
        assertThrows(NullPointerException.class, () -> materialRegistry.addMaterial(nullString));

        assertFalse(materialRegistry.addMaterial("recursive", List.of(), "recursive"));
        assertFalse(materialRegistry.addMaterial("sample2", List.of("does_not_exist")));
        assertFalse(materialRegistry.addMaterial("parent"));
        assertFalse(materialRegistry.addMaterial("parent", List.of()));
        assertFalse(materialRegistry.addMaterial("parent", List.of(), "parent"));
    }

    /** Test that tags are de-duplicated when we inherit from parent materials. */
    @Test
    void testTagDeduplication() {

        assertTrue(tagRegistry.addTag("solid"));
        assertTrue(tagRegistry.addTag("metal", "solid"));
        assertTrue(tagRegistry.addTag("rare_metal", "metal"));

        assertTrue(materialRegistry.addMaterial("gold", List.of("rare_metal", "solid")));
        assertTrue(materialRegistry.addMaterial("refined_gold", List.of("rare_metal"), "gold"));

        Optional<Material> maybeGold = materialRegistry.findMaterial("gold");
        assertTrue(maybeGold.isPresent());
        assertEquals(1, maybeGold.get().tags().size());
        assertEquals("rare_metal", maybeGold.get().tags().get(0).name());

        Optional<Material> maybeRefinedGold = materialRegistry.findMaterial("refined_gold");
        assertTrue(maybeRefinedGold.isPresent());
        assertEquals(1, maybeRefinedGold.get().tags().size());
        assertEquals("rare_metal", maybeRefinedGold.get().tags().get(0).name());
    }

    @Test
    void testTagDeduplicationSharedParentMaterial() {

        assertTrue(tagRegistry.addTag("container"));
        assertTrue(tagRegistry.addTag("liquid_container", "container"));
        assertTrue(tagRegistry.addTag("gas_container", "container"));

        assertTrue(materialRegistry.addMaterial("boxium", List.of("container")));
        assertTrue(
                materialRegistry.addMaterial(
                        "box_orbium", List.of("liquid_container", "gas_container"), "boxium"));

        Optional<Material> maybeBoxium = materialRegistry.findMaterial("boxium");
        assertTrue(maybeBoxium.isPresent());
        assertEquals(1, maybeBoxium.get().tags().size());
        assertTrue(materialRegistry.materialHasTag("boxium", "container"));

        Optional<Material> maybeRefinedGold = materialRegistry.findMaterial("box_orbium");
        assertTrue(maybeRefinedGold.isPresent());
        assertEquals(2, maybeRefinedGold.get().tags().size());
        assertTrue(materialRegistry.materialHasTag("box_orbium", "liquid_container"));
        assertTrue(materialRegistry.materialHasTag("box_orbium", "gas_container"));
    }

    @Test
    void testTagDeduplicationSharedParentTag() {

        assertTrue(tagRegistry.addTag("container"));
        assertTrue(tagRegistry.addTag("liquid_container", "container"));
        assertTrue(tagRegistry.addTag("gas_container", "container"));

        assertTrue(
                materialRegistry.addMaterial(
                        "boxium", List.of("liquid_container", "gas_container")));

        Optional<Material> maybeRefinedGold = materialRegistry.findMaterial("boxium");
        assertTrue(maybeRefinedGold.isPresent());
        assertEquals(2, maybeRefinedGold.get().tags().size());
        assertTrue(materialRegistry.materialHasTag("boxium", "liquid_container"));
        assertTrue(materialRegistry.materialHasTag("boxium", "gas_container"));
    }

    @Test
    void testTagDeduplicationTagAndParentTag() {

        assertTrue(tagRegistry.addTag("container"));
        assertTrue(tagRegistry.addTag("liquid_container", "container"));

        assertTrue(
                materialRegistry.addMaterial("boxium", List.of("liquid_container", "container")));

        Optional<Material> maybeRefinedGold = materialRegistry.findMaterial("boxium");
        assertTrue(maybeRefinedGold.isPresent());
        assertEquals(1, maybeRefinedGold.get().tags().size());
        assertTrue(materialRegistry.materialHasTag("boxium", "liquid_container"));
    }
}
