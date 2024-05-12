/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.frontend.Material;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.PluginFolder;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/** Handles rendering for the skybox. */
public class SkyBoxRender {

    /** The shader program for the skybox. */
    private final Shader shaderProgram;

    /** The uniform map for the shader program. */
    private UniformsMap uniformsMap;

    /** The cameras view matrix. */
    private final Matrix4f viewMatrix;

    /** The ID of the OpenGL VAO for the skybox. */
    private int vaoID;

    /** A list of vertex buffer objects for the skybox mesh. */
    private final int[] vboIDList = new int[3];

    /** The number of vertices in the skybox mesh. */
    private int vertexCount;

    private final Material skyboxMaterial;

    /** Set up the renderer. */
    public SkyBoxRender() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/skybox.vert", Shader.Type.VERTEX));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/skybox.frag", Shader.Type.FRAGMENT));
        shaderProgram = new ShaderOpenGL(shaderModuleDataList);
        viewMatrix = new Matrix4f();
        createUniforms();
        setupBuffers();
        skyboxMaterial = new Material();
        skyboxMaterial.setDiffuseColor(new Vector4f(0.65f, 0.65f, 0.65f, 1f));
        skyboxMaterial.setAmbientColor(new Vector4f(0.65f, 0.65f, 0.65f, 1f));
        skyboxMaterial.setSpecularColor(new Vector4f(0.65f, 0.65f, 0.65f, 1f));
        skyboxMaterial.setReflectance(0f);
        var texturePath =
                PluginFolder.getResource(
                                GraphicsPlugin.PLUGIN_NAME,
                                PluginFolder.ResourceType.DATA,
                                "textures/skybox.png")
                        .getAbsolutePath();
        skyboxMaterial.setTexture(GraphicsManager.getTextureLoader().load(texturePath));
    }

    /** Load the mesh data into VBOs. */
    private void setupBuffers() {
        final float[] positions =
                new float[] {
                    // Left Face
                    -1, 1, 1, //
                    -1, -1, 1, //
                    -1, 1, -1, //
                    -1, -1, -1, //
                    // Front Face
                    1, 1, -1, //
                    1, -1, -1, //
                    // Right Face
                    1, 1, 1, //
                    1, -1, 1, //
                    // Back Face
                    -1, 1, 1, //
                    -1, -1, 1, //
                    // Top Face
                    -1, 1, 1, //
                    1, 1, 1, //
                    // Bottom Face
                    -1, -1, 1, //
                    1, -1, 1
                };
        final float[] textureCoordinates =
                new float[] {
                    // Left Face
                    0,
                    1f / 3,
                    0,
                    2f / 3,
                    0.25f,
                    1f / 3,
                    0.25f,
                    2f / 3,
                    // Front Face
                    0.5f,
                    1f / 3,
                    0.5f,
                    2f / 3,
                    // Right Face
                    0.75f,
                    1f / 3,
                    0.75f,
                    2f / 3,
                    // Back Face
                    1,
                    1f / 3,
                    1,
                    2f / 3,
                    // Top Face
                    0.25f,
                    0,
                    0.5f,
                    0,
                    // Bottom Face
                    0.25f,
                    1,
                    0.5f,
                    1
                };

        final int[] indices =
                new int[] {
                    0, 1, 2, // Left Upper
                    2, 1, 3, // Left Lower
                    4, 2, 3, // Front Upper
                    4, 3, 5, // Front Lower
                    6, 4, 5, // Right Upper
                    6, 5, 7, // Right Lower
                    8, 6, 7, // Back Upper
                    8, 7, 9, // Back Lower
                    11, 10, 2, // Top Upper
                    11, 2, 4, // Top Lower
                    5, 3, 12, // Bottom Upper
                    5, 12, 13 // Bottom Lower
                };

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vertexCount = indices.length;

            vaoID = glGenVertexArrays();
            glBindVertexArray(vaoID);

            // Positions VBO
            glGenBuffers(vboIDList);

            final int vboPositions = vboIDList[0];
            final int vboTextureCoordinates = vboIDList[1];
            final int vboIndices = vboIDList[2];

            FloatBuffer positionsBuffer = stack.callocFloat(positions.length);
            positionsBuffer.put(0, positions);
            glBindBuffer(GL_ARRAY_BUFFER, vboPositions);
            glBufferData(GL_ARRAY_BUFFER, positionsBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

            FloatBuffer textureCoordinatesBuffer = stack.callocFloat(textureCoordinates.length);
            textureCoordinatesBuffer.put(0, textureCoordinates);
            glBindBuffer(GL_ARRAY_BUFFER, vboTextureCoordinates);
            glBufferData(GL_ARRAY_BUFFER, textureCoordinatesBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

            IntBuffer indicesBuffer = stack.callocInt(indices.length);
            indicesBuffer.put(0, indices);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboIndices);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }
    }

    /** Clean up the shader. */
    public void cleanup() {
        shaderProgram.cleanup();
        glDeleteBuffers(vboIDList);
        glDeleteVertexArrays(vaoID);
    }

    /** Set up uniforms for the shader. */
    private void createUniforms() {
        uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Skybox.PROJECTION_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Skybox.VIEW_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Skybox.DIFFUSE);
        uniformsMap.createUniform(ShaderUniforms.Skybox.TEXTURE_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Skybox.HAS_TEXTURE);
    }

    /**
     * Render the skybox.
     *
     * @param scene The scene to render.
     */
    public void render(@NonNull Scene scene) {
        shaderProgram.bind();

        uniformsMap.setUniform(
                ShaderUniforms.Skybox.PROJECTION_MATRIX,
                scene.getProjection().getProjectionMatrix());
        viewMatrix.set(scene.getCamera().getViewMatrix());
        viewMatrix.m30(0);
        viewMatrix.m31(0);
        viewMatrix.m32(0);
        uniformsMap.setUniform(ShaderUniforms.Skybox.VIEW_MATRIX, viewMatrix);
        uniformsMap.setUniform(ShaderUniforms.Skybox.TEXTURE_SAMPLER, 0);

        uniformsMap.setUniform(ShaderUniforms.Skybox.DIFFUSE, skyboxMaterial.getDiffuseColor());

        Texture texture = skyboxMaterial.getTexture();
        boolean hasTexture = false;
        if (texture != null) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture.id());
            hasTexture = true;
        }

        uniformsMap.setUniform(ShaderUniforms.Skybox.HAS_TEXTURE, hasTexture ? 1 : 0);

        glBindVertexArray(vaoID);

        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);

        shaderProgram.unbind();
    }
}
