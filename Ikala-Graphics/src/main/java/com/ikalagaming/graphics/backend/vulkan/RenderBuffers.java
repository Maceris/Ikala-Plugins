package com.ikalagaming.graphics.backend.vulkan;

import lombok.Getter;

/** Buffers for indirect drawing of models. */
@Getter
public class RenderBuffers {

    private int vao;

    /** Set up the buffers. */
    public void initialize() {
        vao = 0;
        // TODO(ches) set up
    }

    /** Clean up all the data. */
    public void cleanup() {
        // TODO(ches) clean up
    }
}
