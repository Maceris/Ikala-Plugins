package com.ikalagaming.graphics.vulkan.stages;

import com.ikalagaming.graphics.gui.data.DrawData;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where every draw list of every window's draw data goes in the packed GUI buffers. The draw data
 * are laid out one after another, each draw list right after the one before.
 */
final class GuiLayout {

    /**
     * Where a draw list starts in each of the packed buffers.
     *
     * @param vertexBytes The byte offset in the vertex buffer.
     * @param commands The index of its first command.
     * @param points The index of its first point.
     * @param details The index of its first point detail.
     */
    record DrawListOffsets(long vertexBytes, int commands, int points, int details) {}

    /** An empty layout, for frames with no GUI. */
    static final GuiLayout EMPTY = new GuiLayout();

    /** Where each draw list of each draw data starts, by draw data. */
    private final Map<DrawData, DrawListOffsets[]> offsets = new IdentityHashMap<>();

    /** The draw data in the order they were laid out. */
    private final List<DrawData> order = new ArrayList<>();

    /** The total size of every vertex buffer, in bytes. */
    long vertexBytes;

    /** The total size of every command buffer, in bytes. */
    long commandBytes;

    /** The total size of every point buffer, in bytes. */
    long pointBytes;

    /** The total size of every point detail buffer, in bytes. */
    long detailBytes;

    /**
     * Lay out draw data one after another.
     *
     * @param drawData The draw data for each window that shows GUI. Each is laid out once.
     * @return The layout.
     */
    static GuiLayout of(@NonNull Collection<DrawData> drawData) {
        GuiLayout layout = new GuiLayout();
        for (DrawData data : drawData) {
            if (layout.offsets.containsKey(data)) {
                continue;
            }
            final int count = data.getDrawListCount();
            DrawListOffsets[] lists = new DrawListOffsets[count];
            for (int i = 0; i < count; ++i) {
                lists[i] =
                        new DrawListOffsets(
                                layout.vertexBytes,
                                (int) (layout.commandBytes / DrawData.SIZE_OF_DRAW_COMMAND),
                                (int) (layout.pointBytes / DrawData.SIZE_OF_POINT),
                                (int) (layout.detailBytes / DrawData.SIZE_OF_POINT_DETAIL));
                layout.vertexBytes += remaining(data.getDrawListVertexBuffer(i));
                layout.commandBytes += remaining(data.getDrawListCommandBuffer(i));
                layout.pointBytes += remaining(data.getDrawListPointBuffer(i));
                layout.detailBytes += remaining(data.getDrawListPointDetailBuffer(i));
            }
            layout.offsets.put(data, lists);
            layout.order.add(data);
        }
        return layout;
    }

    /**
     * Where each draw list of some draw data starts.
     *
     * @param drawData The draw data.
     * @return The offsets in draw list order, or null if the draw data isn't in this layout.
     */
    DrawListOffsets[] offsetsOf(@NonNull DrawData drawData) {
        return offsets.get(drawData);
    }

    /**
     * The draw data in this layout.
     *
     * @return The draw data, in the order they were laid out.
     */
    List<DrawData> drawData() {
        return order;
    }

    /**
     * The number of bytes left in a buffer, treating null as empty.
     *
     * @param buffer The buffer, which may be null.
     * @return The number of bytes remaining.
     */
    static int remaining(ByteBuffer buffer) {
        return buffer == null ? 0 : buffer.remaining();
    }
}
