package com.ikalagaming.graphics.backend.vulkan;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.util.FileUtils;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
public class ShaderVulkan implements Shader {

    /** It's always just main. */
    private static final String ENTRY_POINT = "main";

    /** The VkShaderModule's. */
    private final long[] shaderModules;

    /** A reference to the state that was used during creation of the shader. */
    @NonNull private final VulkanState state;

    /** The uniform map for this shader. */
    @Setter private @NonNull UniformsMap uniforms;

    /**
     * Compile a module to SPIR-V and return the VkShaderModule handle.
     *
     * @param moduleData The module we want to compile.
     * @param compiler The shaderc compiler handle.
     * @param options The shaderc compiler options.
     * @return The VkShaderModule handle.
     * @throws RenderException If there were compilation errors.
     */
    private static long compileModule(
            @NonNull ShaderModuleData moduleData,
            @NonNull VulkanState state,
            long compiler,
            long options) {
        long result = 0;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            String sourceCode = readModule(moduleData);

            int shaderType = mapShaderType(moduleData.shaderType());
            String shaderName = Paths.get(moduleData.shaderFile()).getFileName().toString();

            result =
                    shaderc_compile_into_spv(
                            compiler, sourceCode, shaderType, shaderName, ENTRY_POINT, options);

            if (shaderc_result_get_compilation_status(result)
                    != shaderc_compilation_status_success) {
                String errorMessage = shaderc_result_get_error_message(result);
                log.error("Error compiling shader: {}", errorMessage);
                throw new RenderException(errorMessage);
            }

            ByteBuffer spirvBytes = shaderc_result_get_bytes(result);
            assert spirvBytes != null;
            VkShaderModuleCreateInfo shaderModuleCreateInfo =
                    VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(spirvBytes);

            LongBuffer longOutput = stack.callocLong(1);
            checkError(
                    vkCreateShaderModule(
                            state.device.logical, shaderModuleCreateInfo, null, longOutput));
            return longOutput.get(0);
        } finally {
            shaderc_result_release(result);
        }
    }

    /**
     * Map the shader type to a Shaderc constant version.
     *
     * @param type The type of shader.
     * @return The corresponding Shaderc type.
     */
    private static int mapShaderType(@NonNull Shader.Type type) {
        return switch (type) {
            case VERTEX -> shaderc_vertex_shader;
            case FRAGMENT -> shaderc_fragment_shader;
            case COMPUTE -> shaderc_compute_shader;
        };
    }

    /**
     * Log that we were missing a module and throw an exception.
     *
     * @param module The module we were missing.
     */
    private static void reportMissingModule(@NonNull ShaderModuleData module) {
        final String error =
                SafeResourceLoader.format(
                        "Shader of type {} not found at ({}) '{}'",
                        module.shaderType().toString(),
                        module.location().toString(),
                        module.shaderFile());
        log.warn(error);
        throw new ShaderException(error);
    }

    /**
     * Look up a File, based on the location type and path provided in the module data.
     *
     * @param module The module data we want to find shader code for.
     * @return The file that the module points to.
     * @throws ShaderException If the shader can not be found.
     */
    private static String readModule(@NonNull ShaderModuleData module) {
        if (module.location() == Location.BUNDLED) {
            try (InputStream stream =
                    ShaderVulkan.class.getClassLoader().getResourceAsStream(module.shaderFile())) {
                if (stream == null) {
                    reportMissingModule(module);
                } else {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                reportMissingModule(module);
            }
        } else if (module.location() == Location.DATA_FOLDER) {
            File file =
                    PluginFolder.getResource(
                            GraphicsPlugin.PLUGIN_NAME,
                            PluginFolder.ResourceType.DATA,
                            module.shaderFile());
            return FileUtils.readAsString(file);
        }
        // Won't happen (tm)
        reportMissingModule(module);
        return "";
    }

    /**
     * Create a new shader program.
     *
     * @param shaderModuleDataList The list of shader modules for the program.
     * @throws ShaderException If a new program could not be created.
     */
    public ShaderVulkan(
            @NonNull List<ShaderModuleData> shaderModuleDataList, @NonNull VulkanState state) {
        this.state = state;

        // TODO(ches) do we need a program ID??
        this.uniforms = new UniformsMapVulkan((int) VK_NULL_HANDLE);

        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();

        shaderc_compile_options_set_optimization_level(
                options, shaderc_optimization_level_performance);
        shaderc_compile_options_set_target_env(
                options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_3);

        try {
            shaderModules = new long[shaderModuleDataList.size()];
            for (int i = 0; i < shaderModuleDataList.size(); i++) {
                shaderModules[i] =
                        compileModule(shaderModuleDataList.get(i), state, compiler, options);
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    @Override
    public void bind() {}

    @Override
    public void free() {
        for (int i = 0; i < shaderModules.length; i++) {
            vkDestroyShaderModule(state.device.logical, shaderModules[i], null);
            shaderModules[i] = VK_NULL_HANDLE;
        }
    }

    @Override
    public int getProgramID() {
        return 0;
    }

    @Override
    public UniformsMap getUniformMap() {
        return uniforms;
    }

    @Override
    public void unbind() {}
}
