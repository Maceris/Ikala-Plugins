package com.ikalagaming.graphics.frontend.gui.data;

public record DrawCommand(
        float clipMinX,
        float clipMinY,
        float clipMaxX,
        float clipMaxY,
        int textureID,
        int vertexOffset,
        int indexOffset,
        int elementCount) {}
