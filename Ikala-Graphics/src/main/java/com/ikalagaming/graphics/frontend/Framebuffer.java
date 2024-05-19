package com.ikalagaming.graphics.frontend;

import java.util.Arrays;
import java.util.Objects;

public record Framebuffer(long id, int width, int height, long[] textures) {
    @Override
    public int hashCode() {
        return Objects.hash(id, width, height, Arrays.hashCode(textures));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Framebuffer other)) {
            return false;
        }
        if (other.id != id || other.width != width || other.height != height) {
            return false;
        }
        return Arrays.equals(other.textures, textures);
    }

    @Override
    public String toString() {
        return String.format(
                "[id=%d, width=%d, height=%d, textures=%s]",
                id, width, height, Arrays.toString(textures));
    }
}
