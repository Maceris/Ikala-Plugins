package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.InstanceHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Bookkeeping for everything placed in the scene, without Vulkan. */
class InstanceRegistryTest {

    /** A changed slot, as handed to the GPU table. */
    private record Change(int slot, boolean alive, Vector3d position, float scale) {}

    private InstanceRegistry registry;
    private Model ball;
    private Model cube;

    private static Model model(String id, int meshes) {
        Model model = new Model(id);
        for (int i = 0; i < meshes; ++i) {
            model.getMeshDataList()
                    .add(
                            new MeshData(
                                    new Vector3f(),
                                    new Vector3f(),
                                    0,
                                    new float[0],
                                    new int[0],
                                    0,
                                    new byte[0]));
        }
        return model;
    }

    @BeforeEach
    void setUp() {
        registry = new InstanceRegistry();
        ball = model("ball", 2);
        cube = model("cube", 1);
    }

    private InstanceHandle place(String owner, Model model, double x) {
        return registry.place(owner, model, new Vector3d(x, 0, 0), new Quaternionf(), 1);
    }

    private List<Change> takeChanged() {
        List<Change> changes = new ArrayList<>();
        registry.takeChanged(
                (slot, alive, position, rotation, scale) ->
                        changes.add(new Change(slot, alive, new Vector3d(position), scale)));
        return changes;
    }

    private int[] slotsOf(Model model) {
        List<Integer> slots = new ArrayList<>();
        registry.visit(model, (slot, materials, animation) -> slots.add(slot));
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    @Test
    void onlyChangedInstancesAreHandedOverOnce() {
        InstanceHandle a = place("p", ball, 1);
        InstanceHandle b = place("p", ball, 2);
        assertEquals(2, takeChanged().size());
        assertTrue(takeChanged().isEmpty(), "Nothing changed since");

        assertTrue(registry.transform("p", b, new Vector3d(5, 6, 7), null, Float.NaN));
        List<Change> changes = takeChanged();
        assertEquals(List.of(new Change(b.slot(), true, new Vector3d(5, 6, 7), 1)), changes);

        // Keeping the position while changing the scale
        registry.transform("p", a, null, null, 2);
        assertEquals(List.of(new Change(a.slot(), true, new Vector3d(1, 0, 0), 2)), takeChanged());
    }

    @Test
    void removedInstancesGoStaleAndClearTheirSlot() {
        InstanceHandle a = place("p", ball, 1);
        takeChanged();

        int slot = registry.remove("p", a);
        assertEquals(a.slot(), slot);
        assertFalse(registry.isValid(a));
        assertEquals(-1, registry.remove("p", a), "Removing twice does nothing");
        assertFalse(registry.transform("p", a, new Vector3d(), null, Float.NaN));
        // The GPU copy is told the slot is empty
        assertEquals(List.of(new Change(slot, false, new Vector3d(), 0)), takeChanged());

        // The slot isn't reused until it is freed
        InstanceHandle b = place("p", ball, 2);
        assertTrue(b.slot() != slot);
        registry.free(slot);
        InstanceHandle c = place("p", ball, 3);
        assertEquals(slot, c.slot());
        assertEquals(a.generation() + 1, c.generation());
        assertFalse(registry.isValid(a));
    }

    @Test
    void pluginsCantChangeEachOthersInstances() {
        InstanceHandle mine = place("mine#1", ball, 1);
        takeChanged();
        assertFalse(registry.transform("theirs#2", mine, new Vector3d(9, 9, 9), null, 3));
        assertFalse(registry.setMaterial("theirs#2", mine, 0, new Material()));
        assertEquals(-1, registry.remove("theirs#2", mine));
        assertTrue(registry.isValid(mine));
        assertTrue(takeChanged().isEmpty());
        assertEquals("mine#1", registry.ownerOf(mine));
    }

    @Test
    void unloadingRemovesOnlyThatPluginsInstances() {
        InstanceHandle mine = place("mine#1", ball, 1);
        place("mine#1", cube, 2);
        InstanceHandle theirs = place("theirs#2", ball, 3);

        assertEquals(2, registry.removeAllOwnedBy("mine#1").size());
        assertFalse(registry.isValid(mine));
        assertTrue(registry.isValid(theirs));
        assertEquals(1, registry.getInstanceCount());
        assertArrayEquals(new int[] {theirs.slot()}, slotsOf(ball));
        assertEquals(0, registry.countOf(cube));
    }

    @Test
    void eachModelListsItsInstancesInOrder() {
        InstanceHandle a = place("p", ball, 1);
        InstanceHandle b = place("p", cube, 2);
        InstanceHandle c = place("p", ball, 3);
        assertArrayEquals(new int[] {a.slot(), c.slot()}, slotsOf(ball));
        assertArrayEquals(new int[] {b.slot()}, slotsOf(cube));

        registry.remove("p", a);
        assertArrayEquals(new int[] {c.slot()}, slotsOf(ball));
        assertEquals(2, registry.removeAllOf(ball).size() + registry.removeAllOf(cube).size());
        assertEquals(0, registry.getInstanceCount());
    }

    @Test
    void materialOverridesArePerMesh() {
        InstanceHandle a = place("p", ball, 1);
        Material red = new Material();
        registry.setMaterial("p", a, 1, red);
        List<Material[]> seen = new ArrayList<>();
        registry.visit(ball, (slot, materials, animation) -> seen.add(materials.clone()));
        assertNull(seen.getFirst()[0]);
        assertSame(red, seen.getFirst()[1]);
        assertThrows(IndexOutOfBoundsException.class, () -> registry.setMaterial("p", a, 2, red));
    }

    @Test
    void positionsAndRotationsCanBeReadBack() {
        Quaternionf turned = new Quaternionf().rotateY(1);
        InstanceHandle a = registry.place("p", ball, new Vector3d(1e9, -2, 3.5), turned, 1);
        Vector3d position = new Vector3d();
        Quaternionf rotation = new Quaternionf();
        assertTrue(registry.getPosition(a, position));
        assertTrue(registry.getRotation(a, rotation));
        assertEquals(new Vector3d(1e9, -2, 3.5), position);
        assertEquals(turned, rotation);
    }
}
