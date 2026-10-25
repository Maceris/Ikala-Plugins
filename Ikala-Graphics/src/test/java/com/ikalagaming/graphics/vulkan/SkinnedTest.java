package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.SkinnedInstanceHandle;
import com.ikalagaming.graphics.SkinnedMeshHandle;
import com.ikalagaming.graphics.SkinnedPart;
import com.ikalagaming.graphics.Skins;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.vulkan.stages.InstanceDrawUpdate;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

/**
 * Tests for the skinned mesh path's CPU side: {@link SkinnedVertex}, {@link SkinnedRegistry} and
 * {@link SkinnedPacker}.
 *
 * @author Ches Burks
 */
class SkinnedTest {
    /** The owner used for most instances. */
    private static final String OWNER = "test#1";

    /**
     * A skinned mesh handle that isn't in any arena, for the registry and packer.
     *
     * @param slot Its slot.
     * @param joints How many bones it needs.
     * @param indexCount Its index count.
     * @return The handle.
     */
    private static SkinnedMeshHandle mesh(int slot, int joints, int indexCount) {
        return new SkinnedMeshHandle(
                new MeshHandle(slot, 0, 3, indexCount, new Vector3f(), new Vector3f()), joints);
    }

    /**
     * An identity palette.
     *
     * @param bones How many bones.
     * @return The palette.
     */
    private static float[] identity(int bones) {
        float[] palette = new float[bones * SkinnedRegistry.MATRIX_FLOATS];
        for (int b = 0; b < bones; ++b) {
            new Matrix4f().get(palette, b * SkinnedRegistry.MATRIX_FLOATS);
        }
        return palette;
    }

    /** Vertices pack into the format, with weights adding up to 255 and the bones needed found. */
    @Test
    void testPackVertex() {
        float[] vertices = new float[2 * SkinnedVertex.SCENE_FLOATS];
        vertices[0] = 1.5f;
        vertices[SkinnedVertex.SCENE_FLOATS + 1] = -2;
        int[] joints = {3, 7, 0, 0, 9, 0, 0, 0};
        // A bone with no weight doesn't count toward the bones needed, but a vertex with no
        // weights at all follows its first bone, here bone 9
        float[] weights = {1, 1, 1, 0, 0, 0, 0, 0};
        ByteBuffer out = ByteBuffer.allocate(2 * SkinnedVertex.SIZE);
        int needed = SkinnedVertex.pack(vertices, joints, weights, new int[] {4, 255}, out);
        Assertions.assertEquals(10, needed);
        Assertions.assertEquals(2 * SkinnedVertex.SIZE, out.position());

        ByteBuffer read = out.flip().order(ByteOrder.LITTLE_ENDIAN);
        Assertions.assertEquals(1.5f, read.getFloat(0));
        Assertions.assertEquals(7, read.getShort(SkinnedVertex.JOINTS_OFFSET + 2));
        int sum = 0;
        for (int b = 0; b < 4; ++b) {
            sum += read.get(SkinnedVertex.WEIGHTS_OFFSET + b) & 0xFF;
        }
        Assertions.assertEquals(255, sum);
        Assertions.assertEquals(4, read.getInt(SkinnedVertex.REGION_OFFSET));
        // All-zero weights follow the first bone
        Assertions.assertEquals(
                255, read.get(SkinnedVertex.SIZE + SkinnedVertex.WEIGHTS_OFFSET) & 0xFF);
        Assertions.assertEquals(255, read.getInt(SkinnedVertex.SIZE + SkinnedVertex.REGION_OFFSET));
    }

    /** Malformed vertex data is refused. */
    @Test
    void testPackRejects() {
        float[] vertices = new float[SkinnedVertex.SCENE_FLOATS];
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> SkinnedVertex.pack(vertices, new int[3], new float[4], null, room()));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkinnedVertex.pack(
                                vertices, new int[] {70_000, 0, 0, 0}, new float[4], null, room()));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkinnedVertex.pack(
                                vertices, new int[4], new float[] {-1, 0, 0, 0}, null, room()));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        SkinnedVertex.pack(
                                vertices, new int[4], new float[4], new int[] {64}, room()));
    }

    /**
     * Room for one packed vertex.
     *
     * @return A new buffer.
     */
    private static ByteBuffer room() {
        return ByteBuffer.allocate(SkinnedVertex.SIZE);
    }

    /** Handles go stale when removed, owners are enforced, and limits are checked. */
    @Test
    void testRegistry() {
        SkinnedRegistry registry = new SkinnedRegistry();
        SkinnedInstanceHandle first =
                registry.place(OWNER, 4, new Vector3d(), new Quaternionf(), 1);
        Assertions.assertTrue(registry.isValid(first));
        Assertions.assertFalse(registry.setParts("other#2", first, List.of()));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        registry.setParts(
                                OWNER, first, List.of(new SkinnedPart(mesh(0, 5, 3), null))));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        registry.setPose(
                                OWNER, first, new float[16], 0, new Vector3f(), new Vector3f()));
        Assertions.assertTrue(registry.remove(OWNER, first));
        Assertions.assertFalse(registry.isValid(first));

        // The slot is reused with a new generation, so the old handle stays stale
        SkinnedInstanceHandle second =
                registry.place(OWNER, 4, new Vector3d(), new Quaternionf(), 1);
        Assertions.assertEquals(first.slot(), second.slot());
        Assertions.assertFalse(registry.isValid(first));
        Assertions.assertFalse(registry.setHiddenRegions(OWNER, first, 1));

        registry.place("other#2", 1, new Vector3d(), new Quaternionf(), 1);
        Assertions.assertEquals(1, registry.removeAllOwnedBy(OWNER));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> registry.place(OWNER, 0, new Vector3d(), new Quaternionf(), 1));
    }

    /** A packer's output as plain buffers. */
    private static final class Heap implements SkinnedPacker.Buffers {
        ByteBuffer data;
        ByteBuffer draws;
        ByteBuffer commands;

        @Override
        public ByteBuffer data(long bytes) {
            return data = ByteBuffer.allocateDirect((int) bytes);
        }

        @Override
        public ByteBuffer draws(long bytes) {
            return draws = ByteBuffer.allocateDirect((int) bytes);
        }

        @Override
        public ByteBuffer commands(long bytes) {
            return commands = ByteBuffer.allocateDirect((int) bytes);
        }
    }

    /**
     * Frustum planes looking down -z from the origin.
     *
     * @return The planes.
     */
    private static float[] planes() {
        float[] planes = new float[FrustumPlanes.FLOATS];
        FrustumPlanes.extract(
                new Matrix4f().perspective((float) Math.toRadians(60), 1, 0.1f, 100), planes);
        return planes;
    }

    /**
     * Place a posed instance with parts.
     *
     * @param registry The registry.
     * @param z Its world z.
     * @param bones Its bone count.
     * @param parts Its parts.
     * @return The handle.
     */
    private static SkinnedInstanceHandle posed(
            SkinnedRegistry registry, double z, int bones, List<SkinnedPart> parts) {
        SkinnedInstanceHandle handle =
                registry.place(OWNER, bones, new Vector3d(0, 0, z), new Quaternionf(), 1);
        registry.setParts(OWNER, handle, parts);
        registry.setPose(
                OWNER, handle, identity(bones), 0, new Vector3f(-1, 0, -1), new Vector3f(1, 2, 1));
        return handle;
    }

    /** Instances are laid out as skinning.glsl reads them, with commands for what is seen. */
    @Test
    void testPackLayout() {
        SkinnedRegistry registry = new SkinnedRegistry();
        Material red = new Material();
        SkinnedPart body = new SkinnedPart(mesh(1, 2, 30), red);
        SkinnedPart head = new SkinnedPart(mesh(2, 2, 12), null);
        SkinnedInstanceHandle front = posed(registry, -10, 3, List.of(body, head));
        registry.setHiddenRegions(OWNER, front, 0x8000_0001_0000_0002L);
        // Behind the camera: not in the scene, but still casting shadows
        SkinnedInstanceHandle behind = posed(registry, 10, 2, List.of(body));
        // Behind and casting no shadows: left out entirely
        SkinnedInstanceHandle hidden = posed(registry, 20, 2, List.of(head));
        registry.setCastsShadows(OWNER, hidden, false);
        // Not uploaded yet: its part is skipped
        SkinnedPart pending = new SkinnedPart(mesh(3, 1, 6), null);
        posed(registry, -5, 1, List.of(pending));

        Map<Integer, MeshRegistry.Location> locations =
                Map.of(
                        1,
                        new MeshRegistry.Location(100, 200),
                        2,
                        new MeshRegistry.Location(300, 400));
        Heap heap = new Heap();
        SkinnedPacker.Counts counts =
                new SkinnedPacker()
                        .pack(
                                registry,
                                part -> locations.get(part.mesh().mesh().slot()),
                                material -> material == red ? 7 : 0,
                                texture -> 0,
                                new Vector3d(0, 0, 0),
                                planes(),
                                heap);

        Assertions.assertEquals(3, counts.instances());
        Assertions.assertEquals(2, counts.sceneCommands());
        Assertions.assertEquals(3, counts.shadowCommands());
        // Four parts were packed: the front's two, the behind's one and the pending one's
        Assertions.assertEquals(4, counts.shadowFirst());

        ByteBuffer data = heap.data.order(ByteOrder.LITTLE_ENDIAN);
        Matrix4f model = new Matrix4f().set(data.position(0));
        Assertions.assertEquals(-10, model.m32(), 1e-6);
        // Three instances of 112 bytes, then palettes from byte 384: matrix 6
        Assertions.assertEquals(6, data.getInt(SkinnedPacker.MATRIX_SIZE));
        Assertions.assertEquals(2, data.getInt(SkinnedPacker.MATRIX_SIZE + 4));
        Assertions.assertEquals(0x8000_0001, data.getInt(SkinnedPacker.MATRIX_SIZE + 8));
        Assertions.assertEquals(
                6 + 3, data.getInt(SkinnedPacker.INSTANCE_SIZE + SkinnedPacker.MATRIX_SIZE));
        Assertions.assertEquals(
                1, new Matrix4f().set(data.position(6 * SkinnedPacker.MATRIX_SIZE)).m00());

        ByteBuffer draws = heap.draws.order(ByteOrder.LITTLE_ENDIAN);
        Assertions.assertEquals(7, draws.getInt(4));
        Assertions.assertEquals(1, draws.getInt(2 * SkinnedPacker.DRAW_SIZE));

        ByteBuffer commands = heap.commands.order(ByteOrder.LITTLE_ENDIAN);
        Assertions.assertEquals(30, commands.getInt(0));
        Assertions.assertEquals(200, commands.getInt(8));
        Assertions.assertEquals(100, commands.getInt(12));
        Assertions.assertEquals(1, commands.getInt(InstanceDrawUpdate.DRAW_COMMAND_SIZE + 16));
        // The behind instance's shadow command points at its draw record, the third
        int shadowThird = (counts.shadowFirst() + 2) * InstanceDrawUpdate.DRAW_COMMAND_SIZE;
        Assertions.assertEquals(2, commands.getInt(shadowThird + 16));
    }

    /** Tint colors go in the instance and each part's slots and mask in its draw record. */
    @Test
    void testPackTints() {
        SkinnedRegistry registry = new SkinnedRegistry();
        TextureHandle mask =
                new TextureHandle(3, 1, 4, 4, com.ikalagaming.graphics.Format.R8G8B8A8_UNORM);
        int slots = SkinnedPart.slots(2, -1, 0, -1);
        SkinnedPart jacket = new SkinnedPart(mesh(1, 2, 30), null, mask, slots);
        SkinnedPart plain = new SkinnedPart(mesh(2, 2, 12), null);
        SkinnedInstanceHandle handle = posed(registry, -10, 2, List.of(jacket, plain));
        int[] tints = {0xFF00_00FF, 0xFF00_FF00, 0xFFFF_0000};
        Assertions.assertTrue(registry.setTints(OWNER, handle, tints));

        Map<Integer, MeshRegistry.Location> locations =
                Map.of(
                        1,
                        new MeshRegistry.Location(100, 200),
                        2,
                        new MeshRegistry.Location(300, 400));
        Heap heap = new Heap();
        new SkinnedPacker()
                .pack(
                        registry,
                        part -> locations.get(part.mesh().mesh().slot()),
                        material -> 0,
                        texture -> texture == mask ? 42 : 0,
                        new Vector3d(),
                        planes(),
                        heap);

        ByteBuffer data = heap.data.order(ByteOrder.LITTLE_ENDIAN);
        int tintBase = SkinnedPacker.MATRIX_SIZE + 4 * Integer.BYTES;
        Assertions.assertEquals(0xFF00_00FF, data.getInt(tintBase));
        Assertions.assertEquals(0xFFFF_0000, data.getInt(tintBase + 2 * Integer.BYTES));
        // Slots not set stay white
        Assertions.assertEquals(
                0xFFFF_FFFF, data.getInt(tintBase + (Skins.TINT_SLOTS - 1) * Integer.BYTES));

        ByteBuffer draws = heap.draws.order(ByteOrder.LITTLE_ENDIAN);
        Assertions.assertEquals(0xFF00_FF02, draws.getInt(2 * Integer.BYTES));
        Assertions.assertEquals(42, draws.getInt(3 * Integer.BYTES));
        Assertions.assertEquals(SkinnedPart.NO_TINT, draws.getInt(SkinnedPacker.DRAW_SIZE + 8));
        Assertions.assertEquals(0, draws.getInt(SkinnedPacker.DRAW_SIZE + 12));

        Assertions.assertThrows(
                IllegalArgumentException.class, () -> SkinnedPart.slots(8, 0, 0, 0));
    }

    /** Nothing posed, nothing packed. */
    @Test
    void testPackEmpty() {
        SkinnedRegistry registry = new SkinnedRegistry();
        registry.place(OWNER, 2, new Vector3d(), new Quaternionf(), 1);
        Heap heap = new Heap();
        Assertions.assertEquals(
                SkinnedPacker.Counts.NONE,
                new SkinnedPacker()
                        .pack(
                                registry,
                                part -> null,
                                material -> 0,
                                texture -> 0,
                                new Vector3d(),
                                planes(),
                                heap));
        Assertions.assertNull(heap.data);
    }
}
