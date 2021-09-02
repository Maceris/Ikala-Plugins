package com.ikalagaming.ecs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests for the {@link ECSManager} class.
 *
 * @author Ches Burks
 *
 */
public class TestECSManager {

	/**
	 * Setup before each test.
	 */
	@Before
	public void beforeTest() {
		ECSManager.clear();
	}

	/**
	 * Test that components can be stored and retrieved.
	 */
	@Test
	public void testComponentRecovery() {
		UUID entity1 = ECSManager.createEntity();
		UUID entity2 = ECSManager.createEntity();

		TestA a1 = new TestA();
		a1.setTestInt(3);
		TestA a2 = new TestA();
		a2.setTestInt(5);
		TestB b1 = new TestB();
		b1.setTestString("Example");

		ECSManager.addComponent(entity1, a1);
		ECSManager.addComponent(entity1, b1);
		ECSManager.addComponent(entity2, a2);

		TestA retA1 = ECSManager.getComponent(entity1, TestA.class).get();
		TestB retB1 = ECSManager.getComponent(entity1, TestB.class).get();
		TestA retA2 = ECSManager.getComponent(entity2, TestA.class).get();

		Assert.assertEquals(a1, retA1);
		Assert.assertEquals(a2, retA2);
		Assert.assertEquals(b1, retB1);

		Assert.assertEquals(a1.getTestInt(), retA1.getTestInt());
		Assert.assertEquals(a2.getTestInt(), retA2.getTestInt());
		Assert.assertEquals(b1.getTestString(), retB1.getTestString());
	}

	/**
	 * Tests the {@link ECSManager#containsComponent(UUID, Class)} method.
	 */
	@Test
	public void testContainsComponent() {
		UUID hasA = ECSManager.createEntity();
		UUID hasBoth = ECSManager.createEntity();
		UUID hasB = ECSManager.createEntity();
		UUID hasNone = ECSManager.createEntity();

		TestA a1 = new TestA();
		TestA a2 = new TestA();
		TestB b1 = new TestB();
		TestB b2 = new TestB();

		ECSManager.addComponent(hasA, a1);
		ECSManager.addComponent(hasBoth, a2);
		ECSManager.addComponent(hasBoth, b1);
		ECSManager.addComponent(hasB, b2);

		Assert.assertTrue(ECSManager.containsComponent(hasA, TestA.class));
		Assert.assertTrue(ECSManager.containsComponent(hasBoth, TestA.class));
		Assert.assertTrue(ECSManager.containsComponent(hasBoth, TestB.class));
		Assert.assertTrue(ECSManager.containsComponent(hasB, TestB.class));

		Assert.assertFalse(ECSManager.containsComponent(hasA, TestB.class));
		Assert.assertFalse(ECSManager.containsComponent(hasB, TestA.class));
		Assert.assertFalse(ECSManager.containsComponent(hasNone, TestA.class));
		Assert.assertFalse(ECSManager.containsComponent(hasNone, TestB.class));
	}

	/**
	 * Test that deleting entities works.
	 */
	@Test
	public void testEntityDeletion() {
		UUID entity1 = ECSManager.createEntity();
		UUID entity2 = ECSManager.createEntity();

		TestA a1 = new TestA();
		a1.setTestInt(3);
		TestA a2 = new TestA();
		a2.setTestInt(5);
		TestB b1 = new TestB();
		b1.setTestString("Example");

		ECSManager.addComponent(entity1, a1);
		ECSManager.addComponent(entity1, b1);
		ECSManager.addComponent(entity2, a2);

		ECSManager.destroyEntity(entity1);

		Optional<TestA> retA1 = ECSManager.getComponent(entity1, TestA.class);
		Assert.assertFalse(retA1.isPresent());
		Optional<TestB> retB1 = ECSManager.getComponent(entity1, TestB.class);
		Assert.assertFalse(retB1.isPresent());

		Optional<TestA> retA2 = ECSManager.getComponent(entity2, TestA.class);
		Assert.assertTrue(retA2.isPresent());
		ECSManager.destroyEntity(entity2);
		retA2 = ECSManager.getComponent(entity2, TestA.class);
		Assert.assertFalse(retA2.isPresent());

	}

	/**
	 * Test various edge cases for entity deletion.
	 */
	@Test
	public void testEntityDeletionEdgeCases() {
		// nothing there
		ECSManager.destroyEntity(UUID.randomUUID());

		// destroy twice
		UUID entity1 = ECSManager.createEntity();
		ECSManager.destroyEntity(entity1);
		ECSManager.destroyEntity(entity1);

		// shared components
		UUID entity2 = ECSManager.createEntity();
		UUID entity3 = ECSManager.createEntity();

		TestA a1 = new TestA();
		a1.setTestInt(3);
		ECSManager.addComponent(entity2, a1);
		ECSManager.addComponent(entity3, a1);
		ECSManager.destroyEntity(entity2);

		Optional<TestA> retA1 = ECSManager.getComponent(entity3, TestA.class);
		Assert.assertTrue(retA1.isPresent());
		Assert.assertEquals(a1.getTestInt(), retA1.get().getTestInt());
		ECSManager.destroyEntity(entity3);

		retA1 = ECSManager.getComponent(entity3, TestA.class);
		Assert.assertFalse(retA1.isPresent());
	}

	/**
	 * Tests the {@link ECSManager#getAllComponents(Class)} method.
	 */
	@Test
	public void testGetAllComponents() {
		// Empty case
		List<TestA> aComponents = ECSManager.getAllComponents(TestA.class);
		Assert.assertNotNull(aComponents);
		Assert.assertTrue(aComponents.isEmpty());

		// Normal flow
		UUID entity1 = ECSManager.createEntity();
		UUID entity2 = ECSManager.createEntity();
		UUID entity3 = ECSManager.createEntity();

		TestA a1 = new TestA();
		TestA a2 = new TestA();
		TestB b1 = new TestB();
		TestB b3 = new TestB();

		ECSManager.addComponent(entity1, a1);
		ECSManager.addComponent(entity1, b1);
		ECSManager.addComponent(entity2, a2);
		ECSManager.addComponent(entity3, b3);

		aComponents = ECSManager.getAllComponents(TestA.class);
		List<TestB> bComponents = ECSManager.getAllComponents(TestB.class);

		Assert.assertNotNull(aComponents);
		Assert.assertEquals(2, aComponents.size());

		Assert.assertNotNull(bComponents);
		Assert.assertEquals(2, bComponents.size());

		Assert.assertTrue(aComponents.contains(a1));
		Assert.assertTrue(aComponents.contains(a2));

		Assert.assertTrue(bComponents.contains(b1));
		Assert.assertTrue(bComponents.contains(b3));

		// See if removing components works as expected
		ECSManager.removeComponent(entity1, TestA.class);
		aComponents = ECSManager.getAllComponents(TestA.class);
		Assert.assertNotNull(aComponents);
		Assert.assertEquals(1, aComponents.size());
		Assert.assertTrue(aComponents.contains(a2));
	}

	/**
	 * Tests the {@link ECSManager#getAllEntities()} method.
	 */
	@Test
	public void testGetAllEntities() {
		List<UUID> entities = ECSManager.getAllEntities();
		Assert.assertNotNull(entities);
		Assert.assertTrue(entities.isEmpty());

		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 5; ++i) {
			ids.add(ECSManager.createEntity());
		}

		entities = ECSManager.getAllEntities();
		Assert.assertNotNull(entities);
		Assert.assertEquals(ids.size(), entities.size());
		entities.forEach(id -> Assert.assertTrue(ids.contains(id)));

		UUID removed = ids.remove(0);
		ECSManager.destroyEntity(removed);

		entities = ECSManager.getAllEntities();
		Assert.assertNotNull(entities);
		Assert.assertEquals(ids.size(), entities.size());
		entities.forEach(id -> Assert.assertTrue(ids.contains(id)));
		Assert.assertFalse(entities.contains(removed));
	}

	/**
	 * Tests the {@link ECSManager#getAllEntitiesWithComponent(Class...)}
	 * method.
	 */
	@Test
	public void testGetAllEntitiesWithComponent() {
		List<UUID> blank = ECSManager.getAllEntitiesWithComponent(TestA.class);
		Assert.assertNotNull(blank);
		Assert.assertTrue(blank.isEmpty());

		UUID hasA = ECSManager.createEntity();
		UUID hasBoth = ECSManager.createEntity();
		UUID hasB = ECSManager.createEntity();
		UUID hasNone = ECSManager.createEntity();

		TestA a1 = new TestA();
		TestA a2 = new TestA();
		TestB b1 = new TestB();
		TestB b2 = new TestB();

		ECSManager.addComponent(hasA, a1);
		ECSManager.addComponent(hasBoth, a2);
		ECSManager.addComponent(hasBoth, b1);
		ECSManager.addComponent(hasB, b2);

		List<UUID> withA = ECSManager.getAllEntitiesWithComponent(TestA.class);
		List<UUID> withB = ECSManager.getAllEntitiesWithComponent(TestB.class);
		List<UUID> withAB =
			ECSManager.getAllEntitiesWithComponent(TestA.class, TestB.class);

		Assert.assertNotNull(withA);
		Assert.assertNotNull(withB);
		Assert.assertNotNull(withAB);

		Assert.assertEquals(2, withA.size());
		Assert.assertEquals(2, withB.size());
		Assert.assertEquals(1, withAB.size());

		Assert.assertTrue(withA.contains(hasA));
		Assert.assertTrue(withA.contains(hasBoth));

		Assert.assertTrue(withB.contains(hasB));
		Assert.assertTrue(withB.contains(hasBoth));

		Assert.assertTrue(withAB.contains(hasBoth));

		Assert.assertFalse(withA.contains(hasNone));
		Assert.assertFalse(withB.contains(hasNone));
		Assert.assertFalse(withAB.contains(hasNone));
	}

}
