package com.ikalagaming.factory.world;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tests for the world.
 *
 * @author Ches Burks
 *
 */
class TestWorld {

	private static FactoryPlugin plugin;

	/**
	 * Set up before all the tests.
	 *
	 * @throws Exception If something goes wrong.
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		EventManager.getInstance();
		PluginManager.getInstance();
		TestWorld.plugin = new FactoryPlugin();
		TestWorld.plugin.onLoad();
		TestWorld.plugin.onEnable();
	}

	/**
	 * Tear down after all the tests.
	 *
	 * @throws Exception If something goes wrong.
	 */
	@AfterAll
	static void tearDownAfterClass() throws Exception {
		TestWorld.plugin.onDisable();
		TestWorld.plugin.onUnload();
		TestWorld.plugin = null;
		PluginManager.destoryInstance();
		EventManager.destoryInstance();
	}

	/**
	 * Test adding new tags.
	 */
	@Test
	void testAddingTags() {
		World world = new World();
		Assertions.assertTrue(world.loadDefaultConfigurations());
		Assertions.assertTrue(world.addTag("testing_tag_001"));
		Assertions.assertTrue(
			world.addTag("testing_tag_001_variant", "testing_tag_001"));
		Assertions.assertFalse(world.addTag("testing_tag_001"));

		Assertions
			.assertFalse(world.addTag("testing_tag_002", "does_not_exist"));
		Assertions.assertFalse(world.addTag("same", "same"));

		Assertions.assertThrows(NullPointerException.class,
			() -> world.addTag(null));
		Assertions.assertThrows(NullPointerException.class,
			() -> world.addTag(null, null));
	}

	/**
	 * Test the default tag and material loading.
	 */
	@Test
	void testDefaultLoading() {
		World world = new World();
		Assertions.assertTrue(world.loadDefaultConfigurations());

		Assertions.assertNotNull(world.getTags());
		Assertions.assertTrue(world.getTags().size() > 0);

		for (Tag tag : world.getTags()) {
			Assertions.assertNotNull(tag);
			Assertions.assertNotNull(tag.name());
		}

		Assertions.assertNotNull(world.getMaterials());
		Assertions.assertTrue(world.getMaterials().size() > 0);

		for (Material material : world.getMaterials()) {
			Assertions.assertNotNull(material);
			Assertions.assertNotNull(material.name());
		}
	}

	/**
	 * Test that we can retrieve materials.
	 */
	@Test
	void testFetchMaterial() {
		World world = new World();

		Assertions.assertTrue(world.addTag("solid"));
		Assertions.assertTrue(world.addTag("powder", "solid"));

		final String name = "dust";
		Assertions.assertTrue(world.addMaterial("parent"));
		Assertions
			.assertTrue(world.addMaterial(name, List.of("powder"), "parent"));

		Assertions.assertTrue(world.hasMaterial(name));
		Assertions.assertFalse(world.hasMaterial(name + "y"));
		Optional<Material> maybeMaterial = world.findMaterial(name);
		Assertions.assertTrue(maybeMaterial.isPresent());
		Assertions.assertEquals(name, maybeMaterial.get().name());
		Assertions.assertEquals("parent", maybeMaterial.get().parent().name());

		Assertions.assertTrue(world.hasTagMaterial("solid", name));
		Assertions.assertTrue(world.hasTagMaterial("powder", name));

	}

	/**
	 * Test that we can retrieve tags.
	 */
	@Test
	void testFetchTag() {
		World world = new World();

		final String parentName = "liquid";
		Assertions.assertTrue(world.addTag(parentName));
		Assertions.assertTrue(world.hasTag(parentName));
		Optional<Tag> maybeLiquid = world.findTag(parentName);
		Assertions.assertTrue(maybeLiquid.isPresent());
		Assertions.assertEquals(parentName, maybeLiquid.get().name());

		final String childName = "sludge";
		Assertions.assertTrue(world.addTag(childName, parentName));
		Assertions.assertTrue(world.hasTag(parentName));
		Assertions.assertTrue(world.hasTag(childName));
		Optional<Tag> maybeSludge = world.findTag(childName);
		Assertions.assertTrue(maybeSludge.isPresent());
		Assertions.assertEquals(childName, maybeSludge.get().name());
		Assertions.assertEquals(maybeLiquid.get(), maybeSludge.get().parent());
	}

	/**
	 * Test the creation of materials.
	 */
	@Test
	void testMaterialCreation() {
		World world = new World();

		Assertions.assertDoesNotThrow(() -> world.addMaterial("simple"));

		Assertions.assertTrue(world.addTag("normal_tag"));

		Assertions.assertAll(
			() -> Assertions.assertTrue(world.addMaterial("Sample1")),
			() -> Assertions
				.assertTrue(world.addMaterial("Sample2", new ArrayList<>())),
			() -> Assertions.assertTrue(world.addMaterial("Sample3", "simple")),
			() -> Assertions.assertTrue(
				world.addMaterial("Sample4", List.of("normal_tag"))),
			() -> Assertions.assertTrue(
				world.addMaterial("Sample5", new ArrayList<>(), null)),
			() -> Assertions.assertTrue(
				world.addMaterial("Sample6", new ArrayList<>(), "simple")));
	}

	/**
	 * Check for the negative scenarios for material creation.
	 */
	@Test
	void testMaterialCreationNegative() {
		World world = new World();
		Assertions.assertDoesNotThrow(() -> world.addMaterial("parent"));

		final String nullString = null;
		final List<String> nullList = null;
		final List<String> normalList = new ArrayList<>();

		Assertions.assertThrows(NullPointerException.class,
			() -> world.addMaterial(nullString, normalList, nullString));
		Assertions.assertThrows(NullPointerException.class,
			() -> world.addMaterial("Sample", nullList));
		Assertions.assertThrows(NullPointerException.class,
			() -> world.addMaterial("Sample", nullString));
		Assertions.assertThrows(NullPointerException.class,
			() -> world.addMaterial(nullString));

		Assertions.assertFalse(
			world.addMaterial("recursive", List.of(), "recursive"));
		Assertions.assertFalse(
			world.addMaterial("sample2", List.of("does_not_exist")));
		Assertions.assertFalse(world.addMaterial("parent"));
		Assertions.assertFalse(world.addMaterial("parent", List.of()));
		Assertions
			.assertFalse(world.addMaterial("parent", List.of(), "parent"));
	}

	/**
	 * Test that tags are de-duplicated when we inherit from parent materials.
	 */
	@Test
	void testTagDeduplication() {
		World world = new World();

		Assertions.assertTrue(world.addTag("solid"));
		Assertions.assertTrue(world.addTag("metal", "solid"));
		Assertions.assertTrue(world.addTag("rare_metal", "metal"));

		Assertions.assertTrue(
			world.addMaterial("gold", List.of("rare_metal", "solid")));
		Assertions.assertTrue(
			world.addMaterial("refined_gold", List.of("rare_metal"), "gold"));

		Optional<Material> maybeGold = world.findMaterial("gold");
		Assertions.assertTrue(maybeGold.isPresent());
		Assertions.assertEquals(1, maybeGold.get().tags().size());
		Assertions.assertEquals("rare_metal",
			maybeGold.get().tags().get(0).name());

		Optional<Material> maybeRefinedGold =
			world.findMaterial("refined_gold");
		Assertions.assertTrue(maybeRefinedGold.isPresent());
		Assertions.assertEquals(1, maybeRefinedGold.get().tags().size());
		Assertions.assertEquals("rare_metal",
			maybeRefinedGold.get().tags().get(0).name());
	}

	/**
	 * Test the creation of the world object.
	 */
	@Test
	void testWorldCreation() {
		Assertions.assertDoesNotThrow(World::new);
	}

}
