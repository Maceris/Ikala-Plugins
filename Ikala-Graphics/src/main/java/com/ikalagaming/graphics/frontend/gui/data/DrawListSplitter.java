package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGuiInternal;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits draw list commands into separate channels, which are then merged back in channel order.
 * This allows drawing out of order, e.g. drawing a background after the contents in front of it
 * were submitted. Used by tables, and available through {@link DrawList#channelsSplit(int)}.
 *
 * <p>Each command in a draw list carries its own clip rectangle (baked into its quad) and refers to
 * points and details by absolute index, so channels only need their own command and vertex buffers.
 * Points and details are shared. Unlike ImGui, there is no draw call merging to optimize, so
 * merging just concatenates the channels. Splits can be nested, since a nested splitter treats the
 * current channel as its own channel 0.
 */
@Slf4j
public class DrawListSplitter {
    /** The initial capacity of channel buffers, in commands. */
    private static final int INITIAL_CAPACITY = 32;

    /** Buffers for one channel. */
    private static class Channel {
        ByteBuffer commandBuffer;
        ByteBuffer vertexBuffer;
    }

    /** The current channel index. */
    @Getter private int currentChannel;

    /** The number of channels in use, 0 or 1 if not split. */
    @Getter private int channelCount;

    /**
     * The channels. Channel 0 holds the draw list's own buffers while another channel is current.
     * Buffers for other channels are kept around between splits to avoid reallocating.
     */
    private final List<Channel> channels;

    public DrawListSplitter() {
        currentChannel = 0;
        channelCount = 0;
        channels = new ArrayList<>();
    }

    /** Release the stored channel buffers. Do not call while split. */
    public void clearFreeMemory() {
        if (channelCount > 1) {
            IkGuiInternal.reportError(
                    log, "Clearing the memory of a draw list splitter while it is split");
        }
        channels.clear();
        currentChannel = 0;
        channelCount = 0;
    }

    /**
     * Discard the split state without merging, restoring the draw list's own buffers. Used when the
     * draw list is cleared while it is still split.
     *
     * @param drawList The draw list that was split.
     */
    public void discard(@NonNull DrawList drawList) {
        if (channelCount <= 1) {
            return;
        }
        setCurrentChannel(drawList, 0);
        for (int i = 1; i < channelCount; ++i) {
            channels.get(i).commandBuffer.clear();
            channels.get(i).vertexBuffer.clear();
        }
        channelCount = 1;
    }

    /**
     * Merge all channels back into the draw list, in channel order. Channel 0 comes first.
     *
     * @param drawList The draw list that was split.
     */
    public void merge(@NonNull DrawList drawList) {
        if (channelCount <= 1) {
            return;
        }
        setCurrentChannel(drawList, 0);
        for (int i = 1; i < channelCount; ++i) {
            final Channel channel = channels.get(i);
            channel.commandBuffer.flip();
            channel.vertexBuffer.flip();
            drawList.commandBuffer = append(drawList.commandBuffer, channel.commandBuffer);
            drawList.vertexBuffer = append(drawList.vertexBuffer, channel.vertexBuffer);
            channel.commandBuffer.clear();
            channel.vertexBuffer.clear();
        }
        channelCount = 1;
    }

    /**
     * Set the channel that subsequent commands are added to.
     *
     * @param drawList The draw list that was split.
     * @param index The channel index.
     */
    public void setCurrentChannel(@NonNull DrawList drawList, int index) {
        if (index < 0 || index >= channelCount) {
            IkGuiInternal.reportError(
                    log, "Draw channel {} out of range (0 to {})", index, channelCount - 1);
            return;
        }
        if (currentChannel == index) {
            return;
        }
        // Store the draw list buffers in the current channel, which may have grown, then swap in
        // the buffers for the new channel
        final Channel current = channels.get(currentChannel);
        current.commandBuffer = drawList.commandBuffer;
        current.vertexBuffer = drawList.vertexBuffer;
        final Channel next = channels.get(index);
        drawList.commandBuffer = next.commandBuffer;
        drawList.vertexBuffer = next.vertexBuffer;
        currentChannel = index;
    }

    /**
     * Split the draw list into channels. The current contents of the draw list are in channel 0,
     * which is current after splitting.
     *
     * @param drawList The draw list to split.
     * @param count The number of channels, at least 1.
     */
    public void split(@NonNull DrawList drawList, int count) {
        if (currentChannel != 0 || channelCount > 1) {
            IkGuiInternal.reportError(
                    log, "Nested channel splitting with the same splitter, merge first");
            return;
        }
        while (channels.size() < count) {
            final Channel channel = new Channel();
            channel.commandBuffer =
                    ByteBuffer.allocateDirect(INITIAL_CAPACITY * DrawData.SIZE_OF_DRAW_COMMAND)
                            .order(ByteOrder.nativeOrder());
            channel.vertexBuffer =
                    ByteBuffer.allocateDirect(INITIAL_CAPACITY * DrawData.SIZE_OF_QUAD_VERTICES)
                            .order(ByteOrder.nativeOrder());
            channels.add(channel);
        }
        for (int i = 1; i < count; ++i) {
            channels.get(i).commandBuffer.clear();
            channels.get(i).vertexBuffer.clear();
        }
        channelCount = count;
        currentChannel = 0;
    }

    /**
     * Append the readable contents of one buffer to another, growing the destination if needed.
     *
     * @param destination The buffer being written to.
     * @param source The buffer to read from, flipped for reading.
     * @return The destination, which may be a new buffer.
     */
    private static ByteBuffer append(@NonNull ByteBuffer destination, @NonNull ByteBuffer source) {
        if (destination.remaining() <= source.remaining()) {
            int capacity = Math.max(destination.capacity(), 1);
            while (capacity - destination.position() <= source.remaining()) {
                capacity *= 2;
            }
            final ByteBuffer grown =
                    ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
            destination.flip();
            grown.put(destination);
            destination = grown;
        }
        destination.put(source);
        return destination;
    }
}
