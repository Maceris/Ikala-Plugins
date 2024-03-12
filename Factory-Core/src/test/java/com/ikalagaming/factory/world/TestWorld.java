package com.ikalagaming.factory.world;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tests for the world.
 *
 * @author Ches Burks
 */
class TestWorld {

    private static FactoryPlugin plugin;

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        EventManager.getInstance();
        PluginManager.getInstance();
        TestWorld.plugin = new FactoryPlugin();
        TestWorld.plugin.onLoad();
        TestWorld.plugin.onEnable();
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        TestWorld.plugin.onDisable();
        TestWorld.plugin.onUnload();
        TestWorld.plugin = null;
        PluginManager.destoryInstance();
        EventManager.destoryInstance();
    }

    /** Test adding new tags. */
    @Test
    void testAddingTags() {
        World world = new World();
        assertTrue(world.loadDefaultConfigurations());
        assertTrue(world.addTag("testing_tag_001"));
        assertTrue(world.addTag("testing_tag_001_variant", "testing_tag_001"));
        assertFalse(world.addTag("testing_tag_001"));

        assertFalse(world.addTag("testing_tag_002", "does_not_exist"));
        assertFalse(world.addTag("same", "same"));

        assertThrows(NullPointerException.class, () -> world.addTag(null));
        assertThrows(NullPointerException.class, () -> world.addTag(null, null));
    }

    /** Test the default tag and material loading. */
    @Test
    void testDefaultLoading() {
        World world = new World();
        assertTrue(world.loadDefaultConfigurations());

        assertNotNull(world.getTags());
        assertFalse(world.getTags().isEmpty());

        for (Tag tag : world.getTags()) {
            assertNotNull(tag);
            assertNotNull(tag.name());
        }

        assertNotNull(world.getMaterials());
        assertFalse(world.getMaterials().isEmpty());

        for (Material material : world.getMaterials()) {
            assertNotNull(material);
            assertNotNull(material.name());
        }
    }

    /** Test that we can retrieve materials. */
    @Test
    void testFetchMaterial() {
        World world = new World();

        assertTrue(world.addTag("solid"));
        assertTrue(world.addTag("powder", "solid"));

        final String name = "dust";
        assertTrue(world.addMaterial("parent"));
        assertTrue(world.addMaterial(name, List.of("powder"), "parent"));

        assertTrue(world.hasMaterial(name));
        assertFalse(world.hasMaterial(name + "y"));
        Optional<Material> maybeMaterial = world.findMaterial(name);
        assertTrue(maybeMaterial.isPresent());
        assertEquals(name, maybeMaterial.get().name());
        assertEquals("parent", maybeMaterial.get().parent().name());

        assertTrue(world.materialHasTag(name, "solid"));
        assertTrue(world.materialHasTag(name, "powder"));
    }

    /** Test that we can retrieve tags. */
    @Test
    void testFetchTag() {
        World world = new World();

        final String parentName = "liquid";
        assertTrue(world.addTag(parentName));
        assertTrue(world.tagExists(parentName));
        Optional<Tag> maybeLiquid = world.findTag(parentName);
        assertTrue(maybeLiquid.isPresent());
        assertEquals(parentName, maybeLiquid.get().name());

        final String childName = "sludge";
        assertTrue(world.addTag(childName, parentName));
        assertTrue(world.tagExists(parentName));
        assertTrue(world.tagExists(childName));
        Optional<Tag> maybeSludge = world.findTag(childName);
        assertTrue(maybeSludge.isPresent());
        assertEquals(childName, maybeSludge.get().name());
        assertEquals(maybeLiquid.get(), maybeSludge.get().parent());
    }

    /** Test the creation of materials. */
    @Test
    void testMaterialCreation() {
        World world = new World();

        assertDoesNotThrow(() -> world.addMaterial("simple"));

        assertTrue(world.addTag("normal_tag"));

        assertAll(
                () -> assertTrue(world.addMaterial("Sample1")),
                () -> assertTrue(world.addMaterial("Sample2", new ArrayList<>())),
                () -> assertTrue(world.addMaterial("Sample3", "simple")),
                () -> assertTrue(world.addMaterial("Sample4", List.of("normal_tag"))),
                () -> assertTrue(world.addMaterial("Sample5", new ArrayList<>(), null)),
                () -> assertTrue(world.addMaterial("Sample6", new ArrayList<>(), "simple")));
    }

    /** Check for the negative scenarios for material creation. */
    @Test
    void testMaterialCreationNegative() {
        World world = new World();
        assertDoesNotThrow(() -> world.addMaterial("parent"));

        final String nullString = null;
        final List<String> nullList = null;
        final List<String> normalList = new ArrayList<>();

        assertThrows(
                NullPointerException.class,
                () -> world.addMaterial(nullString, normalList, nullString));
        assertThrows(NullPointerException.class, () -> world.addMaterial("Sample", nullList));
        assertThrows(NullPointerException.class, () -> world.addMaterial("Sample", nullString));
        assertThrows(NullPointerException.class, () -> world.addMaterial(nullString));

        assertFalse(world.addMaterial("recursive", List.of(), "recursive"));
        assertFalse(world.addMaterial("sample2", List.of("does_not_exist")));
        assertFalse(world.addMaterial("parent"));
        assertFalse(world.addMaterial("parent", List.of()));
        assertFalse(world.addMaterial("parent", List.of(), "parent"));
    }

    /** Test that tags are de-duplicated when we inherit from parent materials. */
    @Test
    void testTagDeduplication() {
        World world = new World();

        assertTrue(world.addTag("solid"));
        assertTrue(world.addTag("metal", "solid"));
        assertTrue(world.addTag("rare_metal", "metal"));

        assertTrue(world.addMaterial("gold", List.of("rare_metal", "solid")));
        assertTrue(world.addMaterial("refined_gold", List.of("rare_metal"), "gold"));

        Optional<Material> maybeGold = world.findMaterial("gold");
        assertTrue(maybeGold.isPresent());
        assertEquals(1, maybeGold.get().tags().size());
        assertEquals("rare_metal", maybeGold.get().tags().get(0).name());

        Optional<Material> maybeRefinedGold = world.findMaterial("refined_gold");
        assertTrue(maybeRefinedGold.isPresent());
        assertEquals(1, maybeRefinedGold.get().tags().size());
        assertEquals("rare_metal", maybeRefinedGold.get().tags().get(0).name());
    }

    @Test
    void testTagDeduplicationSharedParentMaterial() {
        World world = new World();

        assertTrue(world.addTag("container"));
        assertTrue(world.addTag("liquid_container", "container"));
        assertTrue(world.addTag("gas_container", "container"));

        assertTrue(world.addMaterial("boxium", List.of("container")));
        assertTrue(
                world.addMaterial(
                        "box_orbium", List.of("liquid_container", "gas_container"), "boxium"));

        Optional<Material> maybeBoxium = world.findMaterial("boxium");
        assertTrue(maybeBoxium.isPresent());
        assertEquals(1, maybeBoxium.get().tags().size());
        assertTrue(world.materialHasTag("boxium", "container"));

        Optional<Material> maybeRefinedGold = world.findMaterial("box_orbium");
        assertTrue(maybeRefinedGold.isPresent());
        assertEquals(2, maybeRefinedGold.get().tags().size());
        assertTrue(world.materialHasTag("box_orbium", "liquid_container"));
        assertTrue(world.materialHasTag("box_orbium", "gas_container"));
    }

    @Test
    void testTagDeduplicationSharedParentTag() {
        World world = new World();

        assertTrue(world.addTag("container"));
        assertTrue(world.addTag("liquid_container", "container"));
        assertTrue(world.addTag("gas_container", "container"));

        assertTrue(world.addMaterial("boxium", List.of("liquid_container", "gas_container")));

        Optional<Material> maybeRefinedGold = world.findMaterial("boxium");
        assertTrue(maybeRefinedGold.isPresent());
        assertEquals(2, maybeRefinedGold.get().tags().size());
        assertTrue(world.materialHasTag("boxium", "liquid_container"));
        assertTrue(world.materialHasTag("boxium", "gas_container"));
    }

    @Test
    void testTagDeduplicationTagAndParentTag() {
        World world = new World();

        assertTrue(world.addTag("container"));
        assertTrue(world.addTag("liquid_container", "container"));

        assertTrue(world.addMaterial("boxium", List.of("liquid_container", "container")));

        Optional<Material> maybeRefinedGold = world.findMaterial("boxium");
        assertTrue(maybeRefinedGold.isPresent());
        assertEquals(1, maybeRefinedGold.get().tags().size());
        assertTrue(world.materialHasTag("boxium", "liquid_container"));
    }

    /** Test the creation of the world object. */
    @Test
    void testWorldCreation() {
        assertDoesNotThrow(World::new);
    }
}
