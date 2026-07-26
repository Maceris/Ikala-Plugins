package com.ikalagaming.graphics;

import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryUtil;

import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks buffers to automatically free once closed. Intended to be used in a try-with-resources,
 * does nothing if garbage collected normally.
 */
@Slf4j
public class BufferHolder implements AutoCloseable {

    /** The buffers to free. */
    private final List<Buffer> buffers;

    /** Create a new instance. Intended to be used in a try-with-resources. */
    public BufferHolder() {
        buffers = new ArrayList<>();
    }

    /**
     * Add a new buffer to the list to automatically free with {@link MemoryUtil#memFree(Buffer)}.
     *
     * @param buffer The buffer to add.
     */
    public void add(Buffer buffer) {
        buffers.add(buffer);
    }

    @Override
    public void close() {
        for (Buffer buffer : buffers) {
            try {
                MemoryUtil.memFree(buffer);
            } catch (Exception e) {
                log.warn("Exception freeing buffers", e);
            }
        }
        buffers.clear();
    }
}
