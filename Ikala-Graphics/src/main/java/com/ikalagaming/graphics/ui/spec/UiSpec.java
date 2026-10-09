package com.ikalagaming.graphics.ui.spec;

import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.style.Style;

import lombok.NonNull;

import java.nio.file.Path;
import java.util.List;

/**
 * A loaded UI spec: how its surface is placed, and the tree of nodes inside it. Immutable, and
 * holds no plugin classes, so it can be kept and opened many times. Load one with {@link
 * SpecLoader}.
 *
 * @param source Where the spec came from, for messages.
 * @param surface The surface settings.
 * @param content The root node.
 * @param files The files the spec was read from, the spec first and then its imports, for hot
 *     reload. Empty if it wasn't read from files.
 */
public record UiSpec(
        @NonNull String source,
        @NonNull SurfaceSpec surface,
        @NonNull NodeSpec content,
        @NonNull List<Path> files) {

    /**
     * The settings of the surface a spec is shown in.
     *
     * @param id The surface ID, unique among all surfaces.
     * @param anchors Where it sits on the screen.
     * @param width How its width is decided.
     * @param height How its height is decided.
     * @param layer How it stacks with other windows.
     * @param movable Whether the user can move it.
     * @param transparent Whether its background is left out.
     * @param classes Its style classes.
     * @param style Its own style.
     */
    public record SurfaceSpec(
            @NonNull String id,
            @NonNull Anchors anchors,
            @NonNull Sizing width,
            @NonNull Sizing height,
            @NonNull Layer layer,
            boolean movable,
            boolean transparent,
            @NonNull List<String> classes,
            @NonNull Style style) {}
}
