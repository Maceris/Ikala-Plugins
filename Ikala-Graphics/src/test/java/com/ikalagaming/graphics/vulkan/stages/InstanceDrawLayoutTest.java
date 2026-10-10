package com.ikalagaming.graphics.vulkan.stages;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.vulkan.InstanceTable;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** How each culled list is divided between mesh slots and kinds of mesh. */
class InstanceDrawLayoutTest {

    private static MeshData mesh(int slot) {
        return mesh(slot, MeshKind.STANDARD);
    }

    private static MeshData mesh(int slot, MeshKind kind) {
        MeshData mesh =
                new MeshData(
                        new Vector3f(),
                        new Vector3f(),
                        0,
                        new float[0],
                        new int[0],
                        0,
                        new byte[0]);
        if (slot >= 0) {
            mesh.setMesh(new MeshHandle(slot, 0, 0, 0, new Vector3f(), new Vector3f(), kind));
        }
        return mesh;
    }

    private static Model model(String id, MeshData... meshes) {
        Model model = new Model(id);
        model.getMeshDataList().addAll(List.of(meshes));
        return model;
    }

    /** Room for each kind: the given standard slots, the given baked slots for each baked kind. */
    private static int[][] room(int standardSlots, int bakedSlots) {
        int[][] firstVisible = new int[MeshKind.ALL.length][];
        for (MeshKind kind : MeshKind.ALL) {
            firstVisible[kind.ordinal()] =
                    new int[InstanceDrawUpdate.slotsOf(kind, standardSlots, bakedSlots)];
        }
        return firstVisible;
    }

    @Test
    void everyMeshGetsRoomForEveryInstanceOfItsModel() {
        // A two mesh model in slots 0 and 3, and a one mesh model in slot 1
        Model house = model("house", mesh(0), mesh(3));
        Model tree = model("tree", mesh(1));
        int[][] first = room(5, 0);
        int total =
                InstanceDrawUpdate.layoutVisible(
                        List.of(
                                new InstanceTable.ModelCount(house, 4),
                                new InstanceTable.ModelCount(tree, 10)),
                        first);
        // Slot 0: 4 houses, slot 1: 10 trees, slot 2: nothing, slot 3: 4 houses, slot 4: nothing
        assertArrayEquals(new int[] {0, 4, 14, 14, 18}, first[MeshKind.STANDARD.ordinal()]);
        assertEquals(18, total);
    }

    @Test
    void sharedSlotsAddUpAndUnregisteredMeshesTakeNoRoom() {
        // A released mesh's slot reused by another model: both get room, culling fills one
        Model old = model("old", mesh(0), mesh(-1));
        Model reused = model("reused", mesh(0));
        int[][] first = room(2, 0);
        int total =
                InstanceDrawUpdate.layoutVisible(
                        List.of(
                                new InstanceTable.ModelCount(old, 2),
                                new InstanceTable.ModelCount(reused, 3)),
                        first);
        assertArrayEquals(new int[] {0, 5}, first[MeshKind.STANDARD.ordinal()]);
        assertEquals(5, total);
    }

    @Test
    void bakedMeshesGetRoomInTheirKindsBlock() {
        // A tree, and a section with opaque and translucent buckets in baked slots 1 and 0
        Model tree = model("tree", mesh(0));
        Model section =
                model(
                        "section",
                        mesh(1, MeshKind.BAKED_OPAQUE),
                        mesh(0, MeshKind.BAKED_TRANSLUCENT));
        int[][] first = room(1, 2);
        int total =
                InstanceDrawUpdate.layoutVisible(
                        List.of(
                                new InstanceTable.ModelCount(tree, 3),
                                new InstanceTable.ModelCount(section, 1)),
                        first);
        // Standard first, then each baked kind's slots in kind order
        assertArrayEquals(new int[] {0}, first[MeshKind.STANDARD.ordinal()]);
        assertArrayEquals(new int[] {3, 3}, first[MeshKind.BAKED_OPAQUE.ordinal()]);
        assertArrayEquals(new int[] {4, 4}, first[MeshKind.BAKED_CUTOUT.ordinal()]);
        assertArrayEquals(new int[] {4, 5}, first[MeshKind.BAKED_TRANSLUCENT.ordinal()]);
        assertEquals(5, total);
    }

    @Test
    void commandBlocksDontOverlap() {
        final int standardSlots = 3;
        final int bakedSlots = 2;
        final int count = InstanceDrawUpdate.culledCommandCount(standardSlots, bakedSlots);
        assertEquals(InstanceDrawUpdate.LIST_COUNT * (3 + 3 * 2), count);
        Set<Integer> used = new HashSet<>();
        for (MeshKind kind : MeshKind.ALL) {
            final int slots = InstanceDrawUpdate.slotsOf(kind, standardSlots, bakedSlots);
            for (int list = 0; list < InstanceDrawUpdate.LIST_COUNT; ++list) {
                // A list's commands for one kind are consecutive, so one indirect call draws them
                final int start =
                        InstanceDrawUpdate.commandIndex(kind, list, 0, standardSlots, bakedSlots);
                for (int slot = 0; slot < slots; ++slot) {
                    final int index =
                            InstanceDrawUpdate.commandIndex(
                                    kind, list, slot, standardSlots, bakedSlots);
                    assertEquals(start + slot, index);
                    assertTrue(index < count);
                    assertTrue(used.add(index), "Command " + index + " is used twice");
                }
            }
        }
        assertEquals(count, used.size());
    }
}
