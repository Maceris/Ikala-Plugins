/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.graph;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Defines a quad that is used to render in the lighting pass. */
@Getter
@Setter
@Deprecated
public class QuadMesh {
    /** The number of vertices in the mesh. */
    public static final int VERTEX_COUNT = 6;

    /**
     * The Vertex Array Object for the mesh.
     *
     * @return The VAO.
     */
    private int vaoID;

    /** The list of VBOs created, so we can clean them up. */
    private List<Integer> vboIDList = new ArrayList<>();
}
