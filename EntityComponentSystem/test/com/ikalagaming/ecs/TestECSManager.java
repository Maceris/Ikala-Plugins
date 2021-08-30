package com.ikalagaming.ecs;

import org.junit.Assert;
import org.junit.Test;

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
}
