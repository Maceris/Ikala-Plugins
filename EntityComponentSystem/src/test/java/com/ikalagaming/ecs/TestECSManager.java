package com.ikalagaming.ecs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests for the {@link ECSManager} class.
 *
 * @author Ches Burks
 */
public class TestECSManager {

    /** Setup before each test. */
    @BeforeEach
    public void beforeTest() {
        ECSManager.clear();
    }

    /** Test that the clear method really clears everything. */
    @Test
    public void testClear() {
        UUID entity1 = ECSManager.createEntity();
        UUID entity2 = ECSManager.createEntity();
        ECSManager.createEntity();

        TestA a1 = new TestA();
        a1.setTestInt(3);
        TestA a2 = new TestA();
        a2.setTestInt(5);
        TestB b1 = new TestB();
        b1.setTestString("Example");

        ECSManager.addComponent(entity1, a1);
        ECSManager.addComponent(entity1, b1);
        ECSManager.addComponent(entity2, a2);

        ECSManager.clear();

        List<UUID> entities = ECSManager.getAllEntities();
        Assertions.assertNotNull(entities);
        Assertions.assertTrue(entities.isEmpty());

        List<TestA> aComponents = ECSManager.getAllComponents(TestA.class);
        Assertions.assertNotNull(aComponents);
        Assertions.assertTrue(aComponents.isEmpty());
        List<TestB> bComponents = ECSManager.getAllComponents(TestB.class);
        Assertions.assertNotNull(bComponents);
        Assertions.assertTrue(bComponents.isEmpty());

        List<UUID> aEntities = ECSManager.getAllEntitiesWithComponent(TestA.class);
        Assertions.assertNotNull(aEntities);
        Assertions.assertTrue(aEntities.isEmpty());
        List<UUID> bEntities = ECSManager.getAllEntitiesWithComponent(TestB.class);
        Assertions.assertNotNull(bEntities);
        Assertions.assertTrue(bEntities.isEmpty());

        Assertions.assertFalse(ECSManager.getComponent(entity1, TestA.class).isPresent());
        Assertions.assertFalse(ECSManager.getComponent(entity1, TestB.class).isPresent());
        Assertions.assertFalse(ECSManager.getComponent(entity2, TestA.class).isPresent());
    }

    /** Test that components can be stored and retrieved. */
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

        Assertions.assertEquals(a1, retA1);
        Assertions.assertEquals(a2, retA2);
        Assertions.assertEquals(b1, retB1);

        Assertions.assertEquals(a1.getTestInt(), retA1.getTestInt());
        Assertions.assertEquals(a2.getTestInt(), retA2.getTestInt());
        Assertions.assertEquals(b1.getTestString(), retB1.getTestString());
    }

    /** Tests the {@link ECSManager#containsComponent(UUID, Class)} method. */
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

        Assertions.assertTrue(ECSManager.containsComponent(hasA, TestA.class));
        Assertions.assertTrue(ECSManager.containsComponent(hasBoth, TestA.class));
        Assertions.assertTrue(ECSManager.containsComponent(hasBoth, TestB.class));
        Assertions.assertTrue(ECSManager.containsComponent(hasB, TestB.class));

        Assertions.assertFalse(ECSManager.containsComponent(hasA, TestB.class));
        Assertions.assertFalse(ECSManager.containsComponent(hasB, TestA.class));
        Assertions.assertFalse(ECSManager.containsComponent(hasNone, TestA.class));
        Assertions.assertFalse(ECSManager.containsComponent(hasNone, TestB.class));
    }

    /** Test that deleting entities works. */
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
        Assertions.assertFalse(retA1.isPresent());
        Optional<TestB> retB1 = ECSManager.getComponent(entity1, TestB.class);
        Assertions.assertFalse(retB1.isPresent());

        Optional<TestA> retA2 = ECSManager.getComponent(entity2, TestA.class);
        Assertions.assertTrue(retA2.isPresent());
        ECSManager.destroyEntity(entity2);
        retA2 = ECSManager.getComponent(entity2, TestA.class);
        Assertions.assertFalse(retA2.isPresent());
    }

    /** Test various edge cases for entity deletion. */
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
        Assertions.assertTrue(retA1.isPresent());
        Assertions.assertEquals(a1.getTestInt(), retA1.get().getTestInt());
        ECSManager.destroyEntity(entity3);

        retA1 = ECSManager.getComponent(entity3, TestA.class);
        Assertions.assertFalse(retA1.isPresent());
    }

    /** Tests the {@link ECSManager#getAllComponents(Class)} method. */
    @Test
    public void testGetAllComponents() {
        // Empty case
        List<TestA> aComponents = ECSManager.getAllComponents(TestA.class);
        Assertions.assertNotNull(aComponents);
        Assertions.assertTrue(aComponents.isEmpty());

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

        Assertions.assertNotNull(aComponents);
        Assertions.assertEquals(2, aComponents.size());

        Assertions.assertNotNull(bComponents);
        Assertions.assertEquals(2, bComponents.size());

        Assertions.assertTrue(aComponents.contains(a1));
        Assertions.assertTrue(aComponents.contains(a2));

        Assertions.assertTrue(bComponents.contains(b1));
        Assertions.assertTrue(bComponents.contains(b3));

        // See if removing components works as expected
        ECSManager.removeComponent(entity1, TestA.class);
        aComponents = ECSManager.getAllComponents(TestA.class);
        Assertions.assertNotNull(aComponents);
        Assertions.assertEquals(1, aComponents.size());
        Assertions.assertTrue(aComponents.contains(a2));
    }

    /** Tests the {@link ECSManager#getAllEntities()} method. */
    @Test
    public void testGetAllEntities() {
        List<UUID> entities = ECSManager.getAllEntities();
        Assertions.assertNotNull(entities);
        Assertions.assertTrue(entities.isEmpty());

        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            ids.add(ECSManager.createEntity());
        }

        entities = ECSManager.getAllEntities();
        Assertions.assertNotNull(entities);
        Assertions.assertEquals(ids.size(), entities.size());
        entities.forEach(id -> Assertions.assertTrue(ids.contains(id)));

        UUID removed = ids.remove(0);
        ECSManager.destroyEntity(removed);

        entities = ECSManager.getAllEntities();
        Assertions.assertNotNull(entities);
        Assertions.assertEquals(ids.size(), entities.size());
        entities.forEach(id -> Assertions.assertTrue(ids.contains(id)));
        Assertions.assertFalse(entities.contains(removed));
    }

    /** Tests the {@link ECSManager#getAllEntitiesWithComponent(Class...)} method. */
    @Test
    public void testGetAllEntitiesWithComponent() {
        List<UUID> blank = ECSManager.getAllEntitiesWithComponent(TestA.class);
        Assertions.assertNotNull(blank);
        Assertions.assertTrue(blank.isEmpty());

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
        List<UUID> withAB = ECSManager.getAllEntitiesWithComponent(TestA.class, TestB.class);

        Assertions.assertNotNull(withA);
        Assertions.assertNotNull(withB);
        Assertions.assertNotNull(withAB);

        Assertions.assertEquals(2, withA.size());
        Assertions.assertEquals(2, withB.size());
        Assertions.assertEquals(1, withAB.size());

        Assertions.assertTrue(withA.contains(hasA));
        Assertions.assertTrue(withA.contains(hasBoth));

        Assertions.assertTrue(withB.contains(hasB));
        Assertions.assertTrue(withB.contains(hasBoth));

        Assertions.assertTrue(withAB.contains(hasBoth));

        Assertions.assertFalse(withA.contains(hasNone));
        Assertions.assertFalse(withB.contains(hasNone));
        Assertions.assertFalse(withAB.contains(hasNone));
    }

    /** Test the removal of components. */
    @Test
    public void testRemoveComponent() {
        UUID entity1 = ECSManager.createEntity();
        UUID entity2 = ECSManager.createEntity();

        TestA a1 = new TestA();
        a1.setTestInt(3);
        TestB b1 = new TestB();
        b1.setTestString("Example");

        ECSManager.addComponent(entity1, a1);
        ECSManager.addComponent(entity2, b1);

        Assertions.assertTrue(ECSManager.containsComponent(entity1, TestA.class));
        ECSManager.removeComponent(entity1, TestA.class);
        Assertions.assertFalse(ECSManager.containsComponent(entity1, TestA.class));
        Assertions.assertFalse(ECSManager.getComponent(entity1, TestA.class).isPresent());

        List<TestA> empty = ECSManager.getAllComponents(TestA.class);
        Assertions.assertNotNull(empty);
        Assertions.assertTrue(empty.isEmpty());

        List<UUID> entities = ECSManager.getAllEntitiesWithComponent(TestA.class);
        Assertions.assertNotNull(entities);
        Assertions.assertTrue(entities.isEmpty());

        // does not affect B
        List<TestB> notEmpty = ECSManager.getAllComponents(TestB.class);
        Assertions.assertNotNull(notEmpty);
        Assertions.assertFalse(notEmpty.isEmpty());

        entities = ECSManager.getAllEntitiesWithComponent(TestB.class);
        Assertions.assertNotNull(entities);
        Assertions.assertFalse(entities.isEmpty());
    }

    /** Handle edge cases for removing components. */
    @Test
    public void testRemoveComponentEdgeCases() {
        // No exceptions should occur
        ECSManager.removeComponent(UUID.randomUUID(), TestA.class);

        UUID entity1 = ECSManager.createEntity();
        TestA a1 = new TestA();

        ECSManager.addComponent(entity1, a1);
        Assertions.assertTrue(ECSManager.containsComponent(entity1, TestA.class));

        // Remove twice
        ECSManager.removeComponent(entity1, TestA.class);
        ECSManager.removeComponent(entity1, TestA.class);

        Assertions.assertFalse(ECSManager.containsComponent(entity1, TestA.class));
        Assertions.assertFalse(ECSManager.getComponent(entity1, TestA.class).isPresent());
    }
}
