/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import lombok.Getter;
import lombok.Setter;

/** A buffer for the geometry pass that is used for deferred shading. */
@Getter
@Setter
public class GBuffer {
    /**
     * The maximum number of textures, which are used for albedo, normals, specular, and depth,
     * respectively.
     */
    private static final int TOTAL_TEXTURES = 4;

    /**
     * The frame buffer ID.
     *
     * @return The buffer ID.
     */
    private int gBufferId;

    /**
     * The height in pixels.
     *
     * @return The buffer (window) height.
     */
    private int height;

    /**
     * The textures for the buffer.
     *
     * @return The list of texture IDs.
     */
    private int[] textureIDs = new int[TOTAL_TEXTURES];

    /**
     * The width in pixels.
     *
     * @return The buffer (window) width.
     */
    private int width;
}
