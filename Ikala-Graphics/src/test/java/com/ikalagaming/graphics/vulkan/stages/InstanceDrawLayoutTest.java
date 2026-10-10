package com.ikalagaming.graphics.vulkan.stages;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.vulkan.InstanceTable;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

/** How each culled pass's visible list is divided between mesh slots. */
class InstanceDrawLayoutTest {

    private static MeshData mesh(int slot) {
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
            mesh.setMesh(new MeshHandle(slot, 0, 0, 0, new Vector3f(), new Vector3f()));
        }
        return mesh;
    }

    private static Model model(String id, MeshData... meshes) {
        Model model = new Model(id);
        model.getMeshDataList().addAll(List.of(meshes));
        return model;
    }

    @Test
    void everyMeshGetsRoomForEveryInstanceOfItsModel() {
        // A two mesh model in slots 0 and 3, and a one mesh model in slot 1
        Model house = model("house", mesh(0), mesh(3));
        Model tree = model("tree", mesh(1));
        int[] first = new int[5];
        int total =
                InstanceDrawUpdate.layoutVisible(
                        List.of(
                                new InstanceTable.ModelCount(house, 4),
                                new InstanceTable.ModelCount(tree, 10)),
                        5,
                        first);
        // Slot 0: 4 houses, slot 1: 10 trees, slot 2: nothing, slot 3: 4 houses, slot 4: nothing
        assertArrayEquals(new int[] {0, 4, 14, 14, 18}, first);
        assertEquals(18, total);
    }

    @Test
    void sharedSlotsAddUpAndUnregisteredMeshesTakeNoRoom() {
        // A released mesh's slot reused by another model: both get room, culling fills one
        Model old = model("old", mesh(0), mesh(-1));
        Model reused = model("reused", mesh(0));
        int[] first = new int[2];
        int total =
                InstanceDrawUpdate.layoutVisible(
                        List.of(
                                new InstanceTable.ModelCount(old, 2),
                                new InstanceTable.ModelCount(reused, 3)),
                        2,
                        first);
        assertArrayEquals(new int[] {0, 5}, first);
        assertEquals(5, total);
    }
}
