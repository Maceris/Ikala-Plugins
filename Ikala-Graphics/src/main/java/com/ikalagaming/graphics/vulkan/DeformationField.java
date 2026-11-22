package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.SurfaceStamp;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.imageio.ImageIO;

/**
 * The prints pressed into soft surfaces around the camera, see {@link DeformMath} for the field's
 * layout: a buffer of {@link DeformMath#SIZE} by {@link DeformMath#SIZE} texels the window scrolls
 * over, the column table of which surfaces stand where, and the stamps queued for the next frame.
 * The {@code DeformUpdate} stage clears what newly comes into the window, fills prints back in, and
 * presses the stamps, each frame.
 *
 * <p>The column table covers {@link DeformMath#TABLE_SIZE} columns a side around the camera, by
 * world column modulo that, each column's surfaces tagged with the column, so only the columns
 * newly covered are written as the camera moves, and all of them when the layers change. It is kept
 * on the CPU and copied to each frame in flight's buffer when it changed.
 */
@Slf4j
public final class DeformationField {

    /** The size of a column table entry: its column, top, bottom, refill rate and flags, padded. */
    public static final int ENTRY_SIZE = 8 * Integer.BYTES;

    /** The size of a stamp in the stamp buffer: four vec4s, see {@code deform.comp}. */
    public static final int STAMP_SIZE = 16 * Float.BYTES;

    /** Texels a workgroup of the stamp pass covers along each axis. */
    public static final int STAMP_GROUP = 8;

    /** The most a stamp's box may span, in texels: 8 m. */
    private static final int MAX_STAMP_TEXELS = 256;

    /** The most stamps pressed a frame; more wait for the next. */
    public static final int MAX_STAMPS = 1024;

    /** The longest step of time refill takes at once, in seconds, so a stall doesn't fill all. */
    private static final double MAX_STEP = 0.1;

    /** A rectangle of world texels to clear: its first texel along x and z, and its size. */
    public record Clear(long x, long z, int width, int depth) {}

    /** The field, every frame's, device local. */
    @Getter private final SharedBuffer field;

    /** Each frame in flight's column table. */
    private final SharedBuffer[] tables = new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Which version of the table each frame's buffer holds. */
    private final long[] tableVersions = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's stamps. */
    private final SharedBuffer[] stampBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The table as built, kept on the CPU. */
    private final ByteBuffer table;

    /** Counts changes to {@link #table}. */
    private long tableVersion = 1;

    /** The registry version the table was built from, and its first column along x and z. */
    private long builtFrom = -1;

    private int tableX0;

    private int tableZ0;

    /** Whether the window has a place yet. */
    private boolean placed;

    /**
     * Whether anything was pressed since the field was last cleared whole: until then it is all
     * untouched, and clearing and filling back in are skipped.
     */
    private boolean pressed;

    /** The window's first world texel along x and z. */
    @Getter private long originX;

    @Getter private long originZ;

    /** When the field last filled back in, from {@link System#nanoTime()}. */
    private long lastNanos;

    /** Stamps for the next frame. */
    private final ConcurrentLinkedQueue<SurfaceStamp> queued = new ConcurrentLinkedQueue<>();

    /**
     * Make the field, cleared.
     *
     * @param state The Vulkan state.
     */
    public DeformationField(@NonNull VulkanState state) {
        final long bytes = (long) DeformMath.SIZE * DeformMath.SIZE * Integer.BYTES;
        field =
                SharedBuffer.allocateDeviceLocal(
                        bytes,
                        state,
                        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        final long tableBytes =
                (long) DeformMath.TABLE_SIZE
                        * DeformMath.TABLE_SIZE
                        * DeformMath.SLOTS
                        * ENTRY_SIZE;
        table = ByteBuffer.allocateDirect((int) tableBytes).order(ByteOrder.nativeOrder());
        for (int i = 0; i < tables.length; ++i) {
            tables[i] =
                    SharedBuffer.allocate(tableBytes, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            stampBuffers[i] =
                    SharedBuffer.allocate(
                            (long) MAX_STAMPS * STAMP_SIZE,
                            state,
                            VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        state.immediateCommands.submit(
                state, commandBuffer -> vkCmdFillBuffer(commandBuffer, field.buffer, 0, bytes, 0));
    }

    /**
     * Queue a stamp for the next frame.
     *
     * @param stamp The stamp.
     */
    public void stamp(@NonNull SurfaceStamp stamp) {
        queued.add(stamp);
    }

    /**
     * What a frame does, from {@link #prepare}.
     *
     * @param clears The rectangles newly in the window, to clear first.
     * @param refill Whether to fill prints back in: false while nothing has been pressed.
     * @param seconds How long to fill prints back in over.
     * @param stamps How many stamps to press.
     * @param stampGroups How many workgroups pressing them takes.
     * @param table The column table's address.
     * @param stampAddress The stamps' address.
     */
    public record Frame(
            @NonNull List<Clear> clears,
            boolean refill,
            float seconds,
            int stamps,
            int stampGroups,
            long table,
            long stampAddress) {}

    /**
     * Get a frame ready: move the window to the camera, bring the column table up to date, and
     * write the stamps queued.
     *
     * @param state The Vulkan state.
     * @param frame The frame in flight.
     * @param camera The camera's position.
     * @param registry The surfaces.
     * @return What the frame does.
     */
    public Frame prepare(
            @NonNull VulkanState state,
            int frame,
            @NonNull Vector3dc camera,
            @NonNull SurfaceRegistry registry) {
        final List<Clear> clears = follow(camera);
        updateTable(camera, registry);
        if (tableVersions[frame] != tableVersion) {
            MemoryUtil.memCopy(
                    MemoryUtil.memAddress(table),
                    tables[frame].allocationInfo.pMappedData(),
                    table.capacity());
            tableVersions[frame] = tableVersion;
        }
        final long now = System.nanoTime();
        final double seconds = lastNanos == 0 ? 0 : Math.min(MAX_STEP, (now - lastNanos) / 1e9);
        lastNanos = now;
        final long address = stampBuffers[frame].allocationInfo.pMappedData();
        int stamps = 0;
        int groups = 0;
        SurfaceStamp stamp;
        while (stamps < MAX_STAMPS && (stamp = queued.poll()) != null) {
            final int added = writeStamp(address + (long) stamps * STAMP_SIZE, stamp, groups);
            if (added > 0) {
                groups += added;
                ++stamps;
            }
        }
        final boolean wasPressed = pressed;
        pressed |= stamps > 0;
        return new Frame(
                wasPressed ? clears : List.of(),
                wasPressed,
                (float) seconds,
                stamps,
                groups,
                tables[frame].deviceAddress,
                stampBuffers[frame].deviceAddress);
    }

    /**
     * Move the window to the camera.
     *
     * @param camera The camera's position.
     * @return The rectangles newly in the window.
     */
    private List<Clear> follow(@NonNull Vector3dc camera) {
        final int size = DeformMath.SIZE;
        final long x = (long) Math.floor(camera.x() * DeformMath.TEXELS_PER_METER) - size / 2;
        final long z = (long) Math.floor(camera.z() * DeformMath.TEXELS_PER_METER) - size / 2;
        final List<Clear> clears = new ArrayList<>(2);
        final long dx = x - originX;
        final long dz = z - originZ;
        if (!placed || Math.abs(dx) >= size || Math.abs(dz) >= size) {
            clears.add(new Clear(x, z, size, size));
            if (!pressed) {
                // Already all untouched
                clears.clear();
            }
        } else {
            if (dx > 0) {
                clears.add(new Clear(originX + size, z, (int) dx, size));
            } else if (dx < 0) {
                clears.add(new Clear(x, z, (int) -dx, size));
            }
            if (dz > 0) {
                clears.add(new Clear(x, originZ + size, size, (int) dz));
            } else if (dz < 0) {
                clears.add(new Clear(x, z, size, (int) -dz));
            }
        }
        placed = true;
        originX = x;
        originZ = z;
        return clears;
    }

    /**
     * Bring the column table up to date: every column when the layers changed or the camera jumped,
     * else the columns newly covered.
     *
     * @param camera The camera's position.
     * @param registry The surfaces.
     */
    private void updateTable(@NonNull Vector3dc camera, @NonNull SurfaceRegistry registry) {
        final int size = DeformMath.TABLE_SIZE;
        final int x0 = (int) Math.floor(camera.x()) - size / 2;
        final int z0 = (int) Math.floor(camera.z()) - size / 2;
        final long version = registry.version();
        final int dx = x0 - tableX0;
        final int dz = z0 - tableZ0;
        if (version != builtFrom || Math.abs(dx) >= size || Math.abs(dz) >= size) {
            fillColumns(registry, x0, z0, size, size);
        } else if (dx != 0 || dz != 0) {
            if (dx > 0) {
                fillColumns(registry, tableX0 + size, z0, dx, size);
            } else if (dx < 0) {
                fillColumns(registry, x0, z0, -dx, size);
            }
            if (dz > 0) {
                fillColumns(registry, x0, tableZ0 + size, size, dz);
            } else if (dz < 0) {
                fillColumns(registry, x0, z0, size, -dz);
            }
        } else {
            return;
        }
        builtFrom = version;
        tableX0 = x0;
        tableZ0 = z0;
        ++tableVersion;
    }

    /**
     * Write a rectangle of columns into the table, emptying them first.
     *
     * @param registry The surfaces.
     * @param x0 The first column along x.
     * @param z0 The first column along z.
     * @param width How many along x.
     * @param depth How many along z.
     */
    private void fillColumns(
            @NonNull SurfaceRegistry registry, int x0, int z0, int width, int depth) {
        for (int z = z0; z < z0 + depth; ++z) {
            for (int x = x0; x < x0 + width; ++x) {
                for (int slot = 0; slot < DeformMath.SLOTS; ++slot) {
                    final int at = entry(x, z, slot);
                    table.putInt(at, x);
                    table.putInt(at + Integer.BYTES, z);
                    table.putInt(at + 5 * Integer.BYTES, 0);
                }
            }
        }
        registry.fill(
                x0,
                z0,
                width,
                depth,
                (x, z, slot, top, bottom, refill, flags) -> {
                    final int at = entry(x, z, slot);
                    table.putFloat(at + 2 * Integer.BYTES, top);
                    table.putFloat(at + 3 * Integer.BYTES, bottom);
                    table.putFloat(at + 4 * Integer.BYTES, refill);
                    table.putInt(at + 5 * Integer.BYTES, flags);
                });
    }

    /**
     * Where a column's slot is in the table, in bytes.
     *
     * @param x The world column along x.
     * @param z The world column along z.
     * @param slot The slot.
     * @return The byte offset.
     */
    private static int entry(int x, int z, int slot) {
        final int size = DeformMath.TABLE_SIZE;
        final int column = Math.floorMod(z, size) * size + Math.floorMod(x, size);
        return (column * DeformMath.SLOTS + slot) * ENTRY_SIZE;
    }

    /**
     * Write one stamp for the stamp pass, if any of it is in the window.
     *
     * @param at Where it goes.
     * @param stamp The stamp.
     * @param firstGroup The first workgroup it takes.
     * @return How many workgroups it takes, 0 if it is outside the window.
     */
    private int writeStamp(long at, @NonNull SurfaceStamp stamp, int firstGroup) {
        final double texels = DeformMath.TEXELS_PER_METER;
        final double reach =
                switch (stamp.shape()) {
                            case ELLIPSE -> Math.max(stamp.sizeA(), stamp.sizeB());
                            case BOX -> Math.hypot(stamp.sizeA(), stamp.sizeB());
                            case CAPSULE -> stamp.sizeA();
                        }
                        + Math.max(0, stamp.rimWidth());
        final double minX = Math.min(stamp.x(), stamp.toX()) - reach;
        final double maxX = Math.max(stamp.x(), stamp.toX()) + reach;
        final double minZ = Math.min(stamp.z(), stamp.toZ()) - reach;
        final double maxZ = Math.max(stamp.z(), stamp.toZ()) + reach;
        final long x0 = Math.max(originX, (long) Math.floor(minX * texels));
        final long z0 = Math.max(originZ, (long) Math.floor(minZ * texels));
        final long x1 = Math.min(originX + DeformMath.SIZE, (long) Math.ceil(maxX * texels));
        final long z1 = Math.min(originZ + DeformMath.SIZE, (long) Math.ceil(maxZ * texels));
        if (x1 <= x0 || z1 <= z0) {
            return 0;
        }
        final int width = (int) Math.min(MAX_STAMP_TEXELS, x1 - x0);
        final int depth = (int) Math.min(MAX_STAMP_TEXELS, z1 - z0);
        final int across = (width + STAMP_GROUP - 1) / STAMP_GROUP;
        final int down = (depth + STAMP_GROUP - 1) / STAMP_GROUP;
        final float angle = stamp.angle();
        MemoryUtil.memPutFloat(at, (float) ((stamp.x() * texels - originX) / texels));
        MemoryUtil.memPutFloat(at + 4, (float) ((stamp.z() * texels - originZ) / texels));
        MemoryUtil.memPutFloat(at + 8, (float) stamp.y());
        MemoryUtil.memPutFloat(at + 12, stamp.rimHeight());
        MemoryUtil.memPutFloat(at + 16, (float) ((stamp.toX() * texels - originX) / texels));
        MemoryUtil.memPutFloat(at + 20, (float) ((stamp.toZ() * texels - originZ) / texels));
        MemoryUtil.memPutFloat(at + 24, stamp.sizeA());
        MemoryUtil.memPutFloat(at + 28, stamp.sizeB());
        MemoryUtil.memPutFloat(at + 32, (float) Math.cos(angle));
        MemoryUtil.memPutFloat(at + 36, (float) Math.sin(angle));
        MemoryUtil.memPutFloat(at + 40, stamp.depth());
        MemoryUtil.memPutFloat(at + 44, Math.max(0, stamp.rimWidth()));
        MemoryUtil.memPutInt(at + 48, (int) (x0 - originX));
        MemoryUtil.memPutInt(at + 52, (int) (z0 - originZ));
        MemoryUtil.memPutInt(at + 56, width | (stamp.shape().ordinal() << 16));
        MemoryUtil.memPutInt(at + 60, firstGroup);
        return across * down;
    }

    /**
     * Save the field's window as a picture, for debugging: red how deep each texel is pressed, up
     * to a quarter meter, green how high its rim, up to 5 cm, blue where anything was pressed,
     * brighter by the top of the surface pressed, the camera in the middle, +x to the right and +z
     * down. Waits for the device. Render thread.
     *
     * @param state The Vulkan state.
     * @param path Where to write the PNG.
     */
    public void dump(@NonNull VulkanState state, @NonNull Path path) {
        final int size = DeformMath.SIZE;
        final long bytes = (long) size * size * Integer.BYTES;
        checkError(vkDeviceWaitIdle(state.device.logical));
        final SharedBuffer readback =
                SharedBuffer.allocate(bytes, state, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        try {
            state.immediateCommands.submit(
                    state,
                    commandBuffer -> {
                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            vkCmdCopyBuffer(
                                    commandBuffer,
                                    field.buffer,
                                    readback.buffer,
                                    VkBufferCopy.calloc(1, stack).size(bytes));
                        }
                    });
            final long address = readback.allocationInfo.pMappedData();
            final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int z = 0; z < size; ++z) {
                for (int x = 0; x < size; ++x) {
                    final int texel =
                            MemoryUtil.memGetInt(
                                    address
                                            + (long) DeformMath.index(originX + x, originZ + z)
                                                    * Integer.BYTES);
                    final double depth = DeformMath.depth(texel);
                    final int red = (int) Math.clamp(depth / 0.25 * 255, 0, 255);
                    final int green = (int) Math.clamp(-depth / 0.05 * 255, 0, 255);
                    // The stamped surface's top, in a band of 8 steps, to tell stacked ones apart
                    final int blue = texel == 0 ? 0 : 64 + (texel & 0x7) * 24;
                    image.setRGB(x, z, (red << 16) | (green << 8) | blue);
                }
            }
            ImageIO.write(image, "png", path.toFile());
            log.info("Saved the deformation field to {}", path);
        } catch (IOException e) {
            log.warn("Couldn't save the deformation field to {}", path, e);
        } finally {
            SharedBuffer.free(readback, state);
        }
    }

    /**
     * Free everything.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        SharedBuffer.free(field, state);
        for (int i = 0; i < tables.length; ++i) {
            SharedBuffer.free(tables[i], state);
            SharedBuffer.free(stampBuffers[i], state);
        }
    }
}
