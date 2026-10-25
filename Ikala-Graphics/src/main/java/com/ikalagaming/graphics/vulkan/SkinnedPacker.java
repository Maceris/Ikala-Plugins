package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.SkinnedPart;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.vulkan.stages.InstanceDrawUpdate;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Packs a frame's skinned instances into what {@code skinning.glsl} reads, and the draw commands
 * that draw them. Everything is rewritten each frame, since posed instances change every frame.
 *
 * <p>The data buffer holds the instances, then their palettes:
 *
 * <ul>
 *   <li>Each instance is {@value #INSTANCE_SIZE} bytes: its model matrix relative to the camera,
 *       the index of its first palette matrix, counted in matrices from the start of the buffer,
 *       and its hidden regions' mask as two halves.
 *   <li>Palettes start at the next multiple of {@value #MATRIX_SIZE} bytes after the instances, so
 *       the whole buffer can be read as matrices too.
 * </ul>
 *
 * <p>The draws buffer holds a {@value #DRAW_SIZE} byte record per drawn part: its instance and its
 * material. Each draw command's first instance is its record's index. Scene commands come first,
 * for the instances the camera can see; shadow commands start at {@link Counts#shadowFirst()}, for
 * every instance that casts shadows, seen or not.
 *
 * @author Ches Burks
 */
public final class SkinnedPacker {
    /** The size of a 4x4 float matrix, in bytes. */
    public static final int MATRIX_SIZE = 16 * Float.BYTES;

    /** The size of one instance in the data buffer, in bytes. */
    public static final int INSTANCE_SIZE = MATRIX_SIZE + 4 * Integer.BYTES;

    /** The size of one draw record, in bytes. */
    public static final int DRAW_SIZE = 4 * Integer.BYTES;

    /** Finds where a mesh is in the skinned geometry buffers. */
    @FunctionalInterface
    public interface Locator {
        /**
         * Find a mesh.
         *
         * @param part The part whose mesh to find.
         * @return Where it is, or null if it isn't uploaded or was released.
         */
        MeshRegistry.Location locate(@NonNull SkinnedPart part);
    }

    /** Hands out the buffers to pack into, once the sizes are known. */
    public interface Buffers {
        /**
         * The data buffer: instances, then palettes.
         *
         * @param bytes How many bytes are needed.
         * @return A buffer with at least that many bytes from position 0.
         */
        ByteBuffer data(long bytes);

        /**
         * The draw records.
         *
         * @param bytes How many bytes are needed.
         * @return A buffer with at least that many bytes from position 0.
         */
        ByteBuffer draws(long bytes);

        /**
         * The draw commands.
         *
         * @param bytes How many bytes are needed.
         * @return A buffer with at least that many bytes from position 0.
         */
        ByteBuffer commands(long bytes);
    }

    /**
     * What was packed.
     *
     * @param instances How many instances were packed.
     * @param sceneCommands How many scene draw commands there are, from the first.
     * @param shadowFirst The index of the first shadow draw command.
     * @param shadowCommands How many shadow draw commands there are.
     */
    public record Counts(int instances, int sceneCommands, int shadowFirst, int shadowCommands) {
        /** Nothing packed. */
        public static final Counts NONE = new Counts(0, 0, 0, 0);
    }

    /** Scratch for each instance's model matrix. */
    private final Matrix4f model = new Matrix4f();

    /** Scratch for the instance's position relative to the camera. */
    private final Vector3d relative = new Vector3d();

    /** Scratch for the instance's render space bounds. */
    private final Vector3f boundsMin = new Vector3f();

    /** Scratch for the instance's render space bounds. */
    private final Vector3f boundsMax = new Vector3f();

    /** Scratch for the bounds' center. */
    private final Vector3f center = new Vector3f();

    /** Scratch for the bounds' half size. */
    private final Vector3f extents = new Vector3f();

    /** Whether each packed instance is seen by the camera, kept to avoid allocating. */
    private boolean[] seen = new boolean[0];

    /**
     * Pack every drawable instance in a registry.
     *
     * @param registry The instances.
     * @param locator Finds the meshes.
     * @param materials Turns a material into its index in the materials buffer.
     * @param camera The camera's world position, the origin of render space.
     * @param scenePlanes The frustum planes to cull the scene against, in render space, see {@link
     *     FrustumPlanes}.
     * @param buffers Where to pack.
     * @return What was packed.
     */
    public Counts pack(
            @NonNull SkinnedRegistry registry,
            @NonNull Locator locator,
            @NonNull ToIntFunction<Material> materials,
            @NonNull Vector3dc camera,
            float @NonNull [] scenePlanes,
            @NonNull Buffers buffers) {
        final Counts[] result = {Counts.NONE};
        registry.read(
                entries ->
                        result[0] =
                                pack(entries, locator, materials, camera, scenePlanes, buffers));
        return result[0];
    }

    /**
     * Pack instances, which mustn't change while this runs.
     *
     * @param entries The instances.
     * @param locator Finds the meshes.
     * @param materials Turns a material into its index in the materials buffer.
     * @param camera The camera's world position.
     * @param scenePlanes The frustum planes to cull the scene against.
     * @param buffers Where to pack.
     * @return What was packed.
     */
    Counts pack(
            @NonNull List<SkinnedRegistry.Entry> entries,
            @NonNull Locator locator,
            @NonNull ToIntFunction<Material> materials,
            @NonNull Vector3dc camera,
            float @NonNull [] scenePlanes,
            @NonNull Buffers buffers) {
        if (seen.length < entries.size()) {
            seen = new boolean[entries.size() * 2];
        }
        // First work out what is drawn, to size the buffers
        int instances = 0;
        int matrices = 0;
        int draws = 0;
        for (int i = 0; i < entries.size(); ++i) {
            SkinnedRegistry.Entry entry = entries.get(i);
            seen[i] = inView(entry, camera, scenePlanes);
            if (!seen[i] && !entry.castsShadows) {
                continue;
            }
            ++instances;
            matrices += entry.boneCount;
            draws += entry.parts.size();
        }
        if (instances == 0) {
            return Counts.NONE;
        }

        final long paletteStart = SharedBuffer.align((long) instances * INSTANCE_SIZE, MATRIX_SIZE);
        final ByteBuffer data =
                buffers.data(paletteStart + (long) matrices * MATRIX_SIZE)
                        .order(ByteOrder.LITTLE_ENDIAN);
        final ByteBuffer drawRecords =
                buffers.draws((long) draws * DRAW_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        final ByteBuffer commands =
                buffers.commands(2L * draws * InstanceDrawUpdate.DRAW_COMMAND_SIZE)
                        .order(ByteOrder.LITTLE_ENDIAN);

        int instance = 0;
        int paletteMatrix = (int) (paletteStart / MATRIX_SIZE);
        int draw = 0;
        int sceneCommands = 0;
        int shadowCommands = 0;
        for (int i = 0; i < entries.size(); ++i) {
            SkinnedRegistry.Entry entry = entries.get(i);
            final boolean shadows = entry.castsShadows;
            if (!seen[i] && !shadows) {
                continue;
            }
            modelMatrix(entry, camera).get(instance * INSTANCE_SIZE, data);
            final int base = instance * INSTANCE_SIZE + MATRIX_SIZE;
            data.putInt(base, paletteMatrix);
            data.putInt(base + Integer.BYTES, (int) entry.hiddenRegions);
            data.putInt(base + 2 * Integer.BYTES, (int) (entry.hiddenRegions >>> 32));
            data.putInt(base + 3 * Integer.BYTES, 0);
            final int paletteFloats = entry.boneCount * SkinnedRegistry.MATRIX_FLOATS;
            final int paletteByte = paletteMatrix * MATRIX_SIZE;
            for (int f = 0; f < paletteFloats; ++f) {
                data.putFloat(paletteByte + f * Float.BYTES, entry.palette[f]);
            }

            for (SkinnedPart part : entry.parts) {
                MeshRegistry.Location location = locator.locate(part);
                if (location == null) {
                    continue;
                }
                drawRecords.putInt(draw * DRAW_SIZE, instance);
                drawRecords.putInt(
                        draw * DRAW_SIZE + Integer.BYTES, materials.applyAsInt(part.material()));
                drawRecords.putInt(draw * DRAW_SIZE + 2 * Integer.BYTES, 0);
                drawRecords.putInt(draw * DRAW_SIZE + 3 * Integer.BYTES, 0);
                final int indexCount = part.mesh().mesh().indexCount();
                if (seen[i]) {
                    writeCommand(commands, sceneCommands++, indexCount, location, draw);
                }
                if (shadows) {
                    writeCommand(commands, draws + shadowCommands++, indexCount, location, draw);
                }
                ++draw;
            }
            ++instance;
            paletteMatrix += entry.boneCount;
        }
        return new Counts(instance, sceneCommands, draws, shadowCommands);
    }

    /**
     * Work out an instance's model matrix relative to the camera, subtracting in double precision.
     *
     * @param entry The instance.
     * @param camera The camera's world position.
     * @return The matrix, in scratch space.
     */
    private Matrix4f modelMatrix(SkinnedRegistry.Entry entry, Vector3dc camera) {
        entry.position.sub(camera, relative);
        return model.translationRotateScale(
                (float) relative.x,
                (float) relative.y,
                (float) relative.z,
                entry.rotation.x,
                entry.rotation.y,
                entry.rotation.z,
                entry.rotation.w,
                entry.scale,
                entry.scale,
                entry.scale);
    }

    /**
     * Whether an instance's posed bounds are in the camera's view.
     *
     * @param entry The instance.
     * @param camera The camera's world position.
     * @param planes The frustum planes, in render space.
     * @return True if it may be seen.
     */
    private boolean inView(SkinnedRegistry.Entry entry, Vector3dc camera, float[] planes) {
        modelMatrix(entry, camera)
                .transformAab(
                        entry.boundsMin.x,
                        entry.boundsMin.y,
                        entry.boundsMin.z,
                        entry.boundsMax.x,
                        entry.boundsMax.y,
                        entry.boundsMax.z,
                        boundsMin,
                        boundsMax);
        boundsMin.add(boundsMax, center).mul(0.5f);
        boundsMax.sub(boundsMin, extents).mul(0.5f);
        return FrustumPlanes.boxInside(planes, center, extents);
    }

    /**
     * Write one VkDrawIndexedIndirectCommand.
     *
     * @param commands The command buffer.
     * @param index Which command.
     * @param indexCount The mesh's index count.
     * @param location Where the mesh is.
     * @param draw The draw record, which goes in as the first instance.
     */
    private static void writeCommand(
            ByteBuffer commands,
            int index,
            int indexCount,
            MeshRegistry.Location location,
            int draw) {
        final int at = index * InstanceDrawUpdate.DRAW_COMMAND_SIZE;
        commands.putInt(at, indexCount);
        commands.putInt(at + Integer.BYTES, 1);
        commands.putInt(at + 2 * Integer.BYTES, location.firstIndex());
        commands.putInt(at + 3 * Integer.BYTES, location.vertexOffset());
        commands.putInt(at + 4 * Integer.BYTES, draw);
    }
}
