package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL43.glVertexAttribBinding;
import static org.lwjgl.opengl.GL43.glVertexAttribFormat;

import lombok.Getter;

/** Buffers for indirect drawing of models. */
@Getter
public class RenderBuffers {

    private int vao;

    /** Set up the buffers. */
    public void initialize() {
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        int offset = 0;
        // Positions
        glEnableVertexAttribArray(0);
        glVertexAttribFormat(0, 3, GL_FLOAT, false, offset);
        glVertexAttribBinding(0, 0);
        offset += 3 * 4;

        // Normals
        glEnableVertexAttribArray(1);
        glVertexAttribFormat(1, 3, GL_FLOAT, false, offset);
        glVertexAttribBinding(1, 0);
        offset += 3 * 4;

        // Tangents
        glEnableVertexAttribArray(2);
        glVertexAttribFormat(2, 3, GL_FLOAT, false, offset);
        glVertexAttribBinding(2, 0);
        offset += 3 * 4;

        // Bitangents
        glEnableVertexAttribArray(3);
        glVertexAttribFormat(3, 3, GL_FLOAT, false, offset);
        glVertexAttribBinding(3, 0);
        offset += 3 * 4;

        // Texture coordinates
        glEnableVertexAttribArray(4);
        glVertexAttribFormat(4, 2, GL_FLOAT, false, offset);
        glVertexAttribBinding(4, 0);

        glBindVertexArray(0);
    }

    /** Clean up all the data. */
    public void cleanup() {
        glDeleteVertexArrays(vao);
    }
}
