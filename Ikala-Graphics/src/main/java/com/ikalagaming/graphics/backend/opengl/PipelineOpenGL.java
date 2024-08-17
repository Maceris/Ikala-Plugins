package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Renders things on the screen. */
@RequiredArgsConstructor
public class PipelineOpenGL implements Pipeline {

    /** The binding for the point light SSBO. */
    public static final int POINT_LIGHT_BINDING = 0;

    /** The binding for the spotlight SSBO. */
    public static final int SPOT_LIGHT_BINDING = 1;

    /** How many lights of each type (spot, point) that are currently supported. */
    public static final int MAX_LIGHTS_SUPPORTED = 1000;

    /** The list of render stages that this renderer uses. */
    private final RenderStage[] renderStages;

    @Override
    public void initialize(@NonNull Window window, @NonNull ShaderMap shaders) {
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        // Support for transparencies
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void render(Scene scene, ShaderMap shaders) {
        for (RenderStage stage : renderStages) {
            stage.render(scene);
        }
    }
}
