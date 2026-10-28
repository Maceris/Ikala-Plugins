package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK13.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.scene.WaterBody;
import com.ikalagaming.graphics.scene.WaterRegistry;
import com.ikalagaming.graphics.scene.WaterTile;

import lombok.NonNull;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where the water is around the camera, for the fog volume to fill with the water's medium, see
 * {@code fog_inject.comp}, and for particles to stop at, see {@code particle_simulate.comp}: a
 * square of {@link #SIZE} cells of a meter, each its water's surface and bottom heights and which
 * water body fills it, with each body's optics. Brought up to date once a frame, by whichever stage
 * needs it first: rebuilt when the water changes or the camera wanders from its middle; the
 * camera's place is written every frame.
 *
 * <p>The buffer: the map's corner, in world cells, its size and how many bodies; the corner
 * relative to the camera and the camera's height; the bodies, {@link #BODY_SIZE} bytes each; then
 * the cells, a vec4 each: surface height, bottom height, body index, and 1 for water or 0.
 */
public class WaterVolume {

    /** Cells along each side of the map, of a meter. */
    public static final int SIZE = 256;

    /** The most water bodies the map tells apart; past it cells take the last. */
    public static final int MAX_BODIES = 16;

    /** How far the camera may wander from the map's middle before it is rebuilt, in cells. */
    private static final int WANDER = 32;

    /** The size of the header: two ivec4 and vec4 worth, see the class comment. */
    public static final int HEADER_SIZE = 8 * Integer.BYTES;

    /** Where in the header the corner relative to the camera is, then the camera's height. */
    public static final int HEADER_CAMERA_OFFSET = 4 * Integer.BYTES;

    /** The size of one body: absorption, scattering and backscattering, a vec4 each. */
    public static final int BODY_SIZE = 3 * 4 * Float.BYTES;

    /** The size of one cell. */
    public static final int CELL_SIZE = 4 * Float.BYTES;

    /** Floats in a cell. */
    private static final int CELL_FLOATS = 4;

    /** Where the cells start. */
    public static final int CELLS_OFFSET = HEADER_SIZE + MAX_BODIES * BODY_SIZE;

    /** The buffer's size. */
    private static final long BYTES = CELLS_OFFSET + (long) SIZE * SIZE * CELL_SIZE;

    /** The tiles as of {@link #version}. */
    private final List<WaterTile> tiles = new ArrayList<>();

    /** The tiles' version the map was built from, -1 before. */
    private long version = -1;

    /** The map's corner, in world cells. */
    private int cornerX;

    private int cornerZ;

    /** Whether the map was built, and holds any water. */
    private boolean built;

    private boolean any;

    /** The bodies the map tells apart. */
    private final List<WaterBody> bodies = new ArrayList<>();

    /** The cells. */
    private final float[] cells = new float[SIZE * SIZE * CELL_FLOATS];

    /** Bumped when the cells or bodies change, and what each frame's buffer holds. */
    private long contents;

    private final long[] bufferContents = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's buffer. */
    private final SharedBuffer[] buffers = new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The frame the map was last brought up to date for, -1 before. */
    private long updatedFrame = -1;

    /** This frame's buffer's address, 0 with no water around. */
    private long address;

    /** Whether the camera is under water this frame. */
    private boolean underWater;

    /**
     * Bring the map up to date for this frame and write it to its buffer, once a frame.
     *
     * @param state The Vulkan state.
     * @param registry The scene's water.
     * @param camera The camera's position.
     */
    public void update(
            @NonNull VulkanState state,
            @NonNull WaterRegistry registry,
            @NonNull Vector3dc camera) {
        if (updatedFrame == state.frameNumber) {
            return;
        }
        updatedFrame = state.frameNumber;
        address = write(state, registry, camera);
    }

    /**
     * The map's address this frame, for particles to stop at the water.
     *
     * @return The address, or 0 with no water around.
     */
    public long mapAddress() {
        return address;
    }

    /**
     * The map's address this frame for the fog volume.
     *
     * @return The address, or 0 with no water around or the camera out of the water: the froxels
     *     are too coarse for the water's edge, and from above the surface puts its own column on,
     *     so the medium would only bleed into the air around it.
     */
    public long fogAddress() {
        return underWater ? address : 0;
    }

    /**
     * The map's corner, in world cells of a meter.
     *
     * @return Its x.
     */
    public int getCornerX() {
        return cornerX;
    }

    /**
     * The map's corner, in world cells of a meter.
     *
     * @return Its z.
     */
    public int getCornerZ() {
        return cornerZ;
    }

    /**
     * Bring the map up to date and write it to this frame's buffer.
     *
     * @param state The Vulkan state.
     * @param registry The scene's water.
     * @param camera The camera's position.
     * @return The buffer's address, or 0 with no water around.
     */
    private long write(
            @NonNull VulkanState state,
            @NonNull WaterRegistry registry,
            @NonNull Vector3dc camera) {
        final long now = registry.snapshotIfChanged(version, tiles);
        final int cameraX = (int) Math.floor(camera.x());
        final int cameraZ = (int) Math.floor(camera.z());
        final boolean wandered =
                Math.abs(cameraX - (cornerX + SIZE / 2)) > WANDER
                        || Math.abs(cameraZ - (cornerZ + SIZE / 2)) > WANDER;
        if (now != version || !built || wandered) {
            version = now;
            build(cameraX - SIZE / 2, cameraZ - SIZE / 2);
        }
        underWater = any && isUnderWater(tiles, camera);
        if (!any) {
            return 0;
        }
        final int frame = state.frameIndex;
        if (buffers[frame] == null) {
            buffers[frame] =
                    SharedBuffer.allocate(BYTES, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            bufferContents[frame] = -1;
        }
        final long address = buffers[frame].allocationInfo.pMappedData();
        if (bufferContents[frame] != contents) {
            bufferContents[frame] = contents;
            MemoryUtil.memPutInt(address, cornerX);
            MemoryUtil.memPutInt(address + Integer.BYTES, cornerZ);
            MemoryUtil.memPutInt(address + 2 * Integer.BYTES, SIZE);
            MemoryUtil.memPutInt(address + 3 * Integer.BYTES, bodies.size());
            for (int b = 0; b < bodies.size(); ++b) {
                final WaterMath.Optics optics = WaterMath.optics(bodies.get(b));
                final long at = address + HEADER_SIZE + (long) b * BODY_SIZE;
                for (int c = 0; c < WaterMath.CHANNELS; ++c) {
                    final long channel = (long) c * Float.BYTES;
                    MemoryUtil.memPutFloat(at + channel, (float) optics.absorption()[c]);
                    MemoryUtil.memPutFloat(
                            at + 4 * Float.BYTES + channel, (float) optics.scattering()[c]);
                    MemoryUtil.memPutFloat(
                            at + 8 * Float.BYTES + channel, (float) optics.backscattering()[c]);
                }
            }
            MemoryUtil.memFloatBuffer(address + CELLS_OFFSET, cells.length).put(cells);
        }
        MemoryUtil.memPutFloat(address + HEADER_CAMERA_OFFSET, (float) (cornerX - camera.x()));
        MemoryUtil.memPutFloat(
                address + HEADER_CAMERA_OFFSET + Float.BYTES, (float) (cornerZ - camera.z()));
        MemoryUtil.memPutFloat(
                address + HEADER_CAMERA_OFFSET + 2 * Float.BYTES, (float) camera.y());
        return buffers[frame].deviceAddress;
    }

    /**
     * Build the map with its corner at a world cell.
     *
     * @param newCornerX The corner along x.
     * @param newCornerZ Along z.
     */
    private void build(int newCornerX, int newCornerZ) {
        built = true;
        cornerX = newCornerX;
        cornerZ = newCornerZ;
        Arrays.fill(cells, 0);
        bodies.clear();
        any = false;
        for (WaterTile tile : tiles) {
            final double size = tile.cellSize();
            final double originX = tile.origin().x();
            final double originZ = tile.origin().z();
            if (originX > cornerX + SIZE
                    || originZ > cornerZ + SIZE
                    || originX + tile.cellsX() * size < cornerX
                    || originZ + tile.cellsZ() * size < cornerZ) {
                continue;
            }
            int body = bodies.indexOf(tile.body());
            if (body < 0) {
                if (bodies.size() < MAX_BODIES) {
                    bodies.add(tile.body());
                }
                body = bodies.size() - 1;
            }
            for (int z = 0; z < tile.cellsZ(); ++z) {
                for (int x = 0; x < tile.cellsX(); ++x) {
                    if (!tile.isWater(x, z)) {
                        continue;
                    }
                    final float surface =
                            (float)
                                    (tile.origin().y()
                                            + (tile.heightAt(x, z)
                                                            + tile.heightAt(x + 1, z)
                                                            + tile.heightAt(x, z + 1)
                                                            + tile.heightAt(x + 1, z + 1))
                                                    / 4);
                    final float bottom =
                            tile.hasBottoms()
                                    ? (float)
                                            (tile.origin().y()
                                                    + Math.min(
                                                            Math.min(
                                                                    tile.bottomAt(x, z),
                                                                    tile.bottomAt(x + 1, z)),
                                                            Math.min(
                                                                    tile.bottomAt(x, z + 1),
                                                                    tile.bottomAt(x + 1, z + 1))))
                                    : surface - WaterMesh.UNKNOWN_DEPTH;
                    final double minX = originX + x * size;
                    final double minZ = originZ + z * size;
                    for (long wz = (long) Math.floor(minZ); wz < Math.ceil(minZ + size); ++wz) {
                        for (long wx = (long) Math.floor(minX); wx < Math.ceil(minX + size); ++wx) {
                            final long lx = wx - cornerX;
                            final long lz = wz - cornerZ;
                            if (lx < 0 || lz < 0 || lx >= SIZE || lz >= SIZE) {
                                continue;
                            }
                            final int at = (int) (lz * SIZE + lx) * CELL_FLOATS;
                            // Of water stacked in a cell, as pools on a fountain, the highest
                            if (cells[at + 3] > 0 && cells[at] >= surface) {
                                continue;
                            }
                            cells[at] = surface;
                            cells[at + 1] = bottom;
                            cells[at + 2] = body;
                            cells[at + 3] = 1;
                            any = true;
                        }
                    }
                }
            }
        }
        ++contents;
    }

    /**
     * Whether a point is under water: inside a water cell, between its bottom and its surface at
     * rest.
     *
     * @param tiles The water's tiles.
     * @param point The point, in the world.
     * @return Whether it is under water.
     */
    public static boolean isUnderWater(@NonNull List<WaterTile> tiles, @NonNull Vector3dc point) {
        for (WaterTile tile : tiles) {
            final double size = tile.cellSize();
            final int x = (int) Math.floor((point.x() - tile.origin().x()) / size);
            final int z = (int) Math.floor((point.z() - tile.origin().z()) / size);
            if (x < 0 || z < 0 || x >= tile.cellsX() || z >= tile.cellsZ() || !tile.isWater(x, z)) {
                continue;
            }
            final double fx = (point.x() - tile.origin().x()) / size - x;
            final double fz = (point.z() - tile.origin().z()) / size - z;
            final double surface =
                    tile.origin().y()
                            + (1 - fz)
                                    * ((1 - fx) * tile.heightAt(x, z)
                                            + fx * tile.heightAt(x + 1, z))
                            + fz
                                    * ((1 - fx) * tile.heightAt(x, z + 1)
                                            + fx * tile.heightAt(x + 1, z + 1));
            final double bottom =
                    tile.hasBottoms()
                            ? tile.origin().y()
                                    + Math.min(
                                            Math.min(tile.bottomAt(x, z), tile.bottomAt(x + 1, z)),
                                            Math.min(
                                                    tile.bottomAt(x, z + 1),
                                                    tile.bottomAt(x + 1, z + 1)))
                            : Double.NEGATIVE_INFINITY;
            if (point.y() < surface && point.y() > bottom) {
                return true;
            }
        }
        return false;
    }

    /**
     * Free the buffers.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        for (int i = 0; i < buffers.length; ++i) {
            if (buffers[i] != null) {
                SharedBuffer.free(buffers[i], state);
                buffers[i] = null;
            }
        }
        version = -1;
        built = false;
        updatedFrame = -1;
        address = 0;
        underWater = false;
    }
}
