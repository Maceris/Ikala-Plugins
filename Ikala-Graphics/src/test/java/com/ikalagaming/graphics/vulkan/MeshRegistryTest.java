package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.MeshHandle;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

/** Mesh bookkeeping in the shared geometry buffers, without Vulkan. */
class MeshRegistryTest {

    private static final Vector3f MIN = new Vector3f(-1);
    private static final Vector3f MAX = new Vector3f(1);

    private static MeshRegistry.Reservation add(MeshRegistry registry, String owner, int vertices) {
        return registry.add(owner, vertices, vertices * 3, MIN, MAX);
    }

    @Test
    void meshesArePendingUntilUploaded() {
        MeshRegistry registry = new MeshRegistry(100, 300);
        MeshRegistry.Reservation first = add(registry, "a", 10);
        MeshRegistry.Reservation second = add(registry, "a", 20);
        MeshHandle handle = second.handle();

        assertEquals(new MeshRegistry.Location(10, 30), second.location());
        assertEquals(20, handle.vertexCount());
        assertEquals(60, handle.indexCount());
        assertTrue(registry.isValid(handle));
        assertFalse(registry.isResident(handle));
        assertNull(registry.locate(handle), "Nothing draws a pending mesh");
        assertEquals(second.location(), registry.locatePending(handle));

        assertTrue(registry.markResident(handle));
        assertTrue(registry.isResident(handle));
        assertEquals(second.location(), registry.locate(handle));
        assertFalse(registry.isResident(first.handle()));
        assertEquals(2, registry.getMeshCount());
    }

    @Test
    void removedMeshesGoStaleButKeepTheirSpaceUntilFreed() {
        MeshRegistry registry = new MeshRegistry(100, 300);
        MeshHandle old = add(registry, "a", 10).handle();
        registry.markResident(old);

        MeshRegistry.Retired retired = registry.remove(old);
        assertFalse(registry.isValid(old));
        assertNull(registry.locate(old));
        assertNull(registry.remove(old), "Removing twice does nothing");
        assertEquals(10, registry.getVerticesUsed(), "Frames in flight may still draw it");

        // A new mesh can't take the slot or the space yet
        MeshHandle next = add(registry, "a", 10).handle();
        assertNotEquals(old.slot(), next.slot());
        assertEquals(new MeshRegistry.Location(10, 30), registry.locatePending(next));

        registry.free(retired);
        assertEquals(10, registry.getVerticesUsed());
        MeshRegistry.Reservation reused = add(registry, "a", 10);
        assertEquals(old.slot(), reused.handle().slot());
        assertEquals(old.generation() + 1, reused.handle().generation());
        assertEquals(new MeshRegistry.Location(0, 0), reused.location());
        // The old handle stays stale even though its slot is taken again
        assertFalse(registry.isValid(old));
    }

    @Test
    void unloadingRemovesOnlyThatPluginsMeshes() {
        MeshRegistry registry = new MeshRegistry(100, 300);
        MeshHandle mine = add(registry, "mine#1", 5).handle();
        add(registry, "mine#1", 5);
        MeshHandle theirs = add(registry, "theirs#2", 5).handle();

        List<MeshRegistry.Retired> removed = registry.removeAllOwnedBy("mine#1");
        assertEquals(2, removed.size());
        assertFalse(registry.isValid(mine));
        assertTrue(registry.isValid(theirs));
        assertEquals(0, registry.countOwnedBy("mine#1"));
        assertEquals(1, registry.countOwnedBy("theirs#2"));
        assertEquals("theirs#2", registry.ownerOf(theirs));
        assertNull(registry.ownerOf(mine));
    }

    @Test
    void theSpaceGrowsForBigMeshes() {
        MeshRegistry registry = new MeshRegistry(16, 48);
        add(registry, "a", 100);
        assertTrue(registry.getVertexCapacity() >= 100);
        assertTrue(registry.getIndexCapacity() >= 300);
    }

    @Test
    void handlesFromAnotherRegistryOrSlotAreStale() {
        MeshRegistry registry = new MeshRegistry(100, 300);
        MeshHandle handle = add(registry, "a", 1).handle();
        assertFalse(registry.isValid(new MeshHandle(handle.slot() + 5, 0, 1, 3, MIN, MAX)));
        assertFalse(
                registry.isValid(
                        new MeshHandle(handle.slot(), handle.generation() + 1, 1, 3, MIN, MAX)));
        assertFalse(registry.isValid(null));
    }

    @Test
    void indicesPastTheLastVertexAreRejected() {
        ByteBuffer indices = MemoryUtil.memAlloc(3 * Integer.BYTES);
        try {
            indices.putInt(0).putInt(1).putInt(2).flip();
            GeometryArena.checkIndices(indices, 3);
            assertEquals(0, indices.position(), "The buffer is left as it was");

            indices.putInt(2 * Integer.BYTES, 3);
            String message =
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> GeometryArena.checkIndices(indices, 3))
                            .getMessage();
            assertTrue(message.contains("Index 3 at 2 is past the last of 3 vertices"), message);

            // Negative indices are huge to the GPU
            indices.putInt(2 * Integer.BYTES, -1);
            assertThrows(
                    IllegalArgumentException.class, () -> GeometryArena.checkIndices(indices, 3));
        } finally {
            MemoryUtil.memFree(indices);
        }
    }
}
