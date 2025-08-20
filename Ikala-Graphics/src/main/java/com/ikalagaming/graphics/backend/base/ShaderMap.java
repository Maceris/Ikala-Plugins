package com.ikalagaming.graphics.backend.base;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/** Tracks the current shader for each render stage. */
@Slf4j
public class ShaderMap {

    private final Map<RenderStage.Type, Shader> shaders = new EnumMap<>(RenderStage.Type.class);

    /**
     * Add a shader to the cache. If there is already a shader for that stage, the old one will be
     * deleted (though maybe not immediately).
     *
     * @param type The render stage the shader is for.
     * @param shader The shader to store.
     */
    public void addShader(@NonNull RenderStage.Type type, @NonNull Shader shader) {
        Shader existing = shaders.get(type);

        if (existing != null) {
            GraphicsManager.getDeletionQueue().add(existing);
        }
        shaders.put(type, shader);
    }

    /** Clear out all shaders and schedule them for deletion. */
    public void clearAll() {
        shaders.forEach((type, shader) -> GraphicsManager.getDeletionQueue().add(shader));
        shaders.clear();
    }

    /**
     * Remove a shader without replacing it.
     *
     * @param type The type of shader to remove.
     */
    public void removeShader(@NonNull RenderStage.Type type) {
        Shader existing = shaders.remove(type);
        if (existing != null) {
            GraphicsManager.getDeletionQueue().add(existing);
        }
    }

    /**
     * Fetches the current shader for the specified render stage, which must exist at the point of
     * calling this method.
     *
     * @param type The render stage to fetch a shader for.
     * @return The shader for the specified render stage.
     * @throws ShaderException If the shader is not found.
     */
    public Shader getShader(@NonNull RenderStage.Type type) {
        var result = shaders.get(type);

        if (result == null) {
            var message =
                    SafeResourceLoader.getStringFormatted(
                            "SHADER_MISSING", GraphicsPlugin.getResourceBundle(), type.toString());
            log.error(message);
            throw new ShaderException(message);
        }
        return result;
    }
}
