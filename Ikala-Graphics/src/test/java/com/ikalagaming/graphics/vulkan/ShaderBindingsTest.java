package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Checks the uniform buffer offsets against the std140 layout of the uniform blocks in the Vulkan
 * shaders, worked out by hand.
 */
class ShaderBindingsTest {

    @Test
    void testLightUniformOffsets() {
        // mat4 invProjectionMatrix, mat4 invViewMatrix
        assertEquals(0, ShaderBindings.Light.INVERSE_PROJECTION_MATRIX_OFFSET);
        assertEquals(64, ShaderBindings.Light.INVERSE_VIEW_MATRIX_OFFSET);
        // AmbientLight {vec3 color; float intensity;}
        assertEquals(128, ShaderBindings.Light.AMBIENT_LIGHT_OFFSET);
        // DirectionalLight {vec3 color; float pad; vec3 direction; float intensity;}
        assertEquals(144, ShaderBindings.Light.DIRECTIONAL_LIGHT_OFFSET);
        assertEquals(176, ShaderBindings.Light.POINT_LIGHT_COUNT_OFFSET);
        assertEquals(180, ShaderBindings.Light.SPOT_LIGHT_COUNT_OFFSET);
        // Fog {vec3 color; float density; int enabled; vec3 pad;} starts on a 16 byte boundary
        assertEquals(192, ShaderBindings.Light.FOG_OFFSET);
        assertEquals(32, ShaderBindings.Light.Fog.PADDING);
        assertEquals(48, ShaderBindings.Light.Fog.SIZEOF);
        // CascadeShadow {mat4 projViewMatrix; float splitDistance;}[3], each padded to 80 bytes
        assertEquals(240, ShaderBindings.Light.CASCADE_SHADOWS_OFFSET);
        assertEquals(80, ShaderBindings.Light.CascadeShadow.ARRAY_STRIDE);
        // 8 ints for the texture indices
        assertEquals(480, ShaderBindings.Light.BASE_COLOR_SAMPLER_INDEX_OFFSET);
        assertEquals(496, ShaderBindings.Light.DEPTH_SAMPLER_INDEX_OFFSET);
        assertEquals(500, ShaderBindings.Light.SHADOW_MAP_0_INDEX_OFFSET);
        assertEquals(512, ShaderBindings.Light.UNIFORMS_BUFFER_SIZE);
    }

    @Test
    void testCullOffsets() {
        // 9 buffer addresses, 2 counts, 2 more addresses, the phase, then the baked mesh table
        assertEquals(72, ShaderBindings.Cull.PUSH_CONSTANT_SLOT_COUNT_OFFSET);
        assertEquals(80, ShaderBindings.Cull.PUSH_CONSTANT_HISTORY_OFFSET);
        assertEquals(88, ShaderBindings.Cull.PUSH_CONSTANT_VIEW_OFFSET);
        assertEquals(96, ShaderBindings.Cull.PUSH_CONSTANT_PHASE_OFFSET);
        // The baked slot count, then the baked mesh table's address on an 8 byte boundary
        assertEquals(100, ShaderBindings.Cull.PUSH_CONSTANT_BAKED_SLOT_COUNT_OFFSET);
        assertEquals(104, ShaderBindings.Cull.PUSH_CONSTANT_BAKED_MESHES_OFFSET);
        assertEquals(112, ShaderBindings.Cull.PUSH_CONSTANTS_SIZE);
        // The guaranteed minimum push constant space
        assertTrue(ShaderBindings.Cull.PUSH_CONSTANTS_SIZE <= 128);
        // View {mat4 projectionView; ivec2 size; int levels; uint flags;}
        assertEquals(64, ShaderBindings.Cull.VIEW_DEPTH_SIZE_OFFSET);
        assertEquals(72, ShaderBindings.Cull.VIEW_LEVELS_OFFSET);
        assertEquals(76, ShaderBindings.Cull.VIEW_FLAGS_OFFSET);
        assertEquals(80, ShaderBindings.Cull.VIEW_SIZE);
    }

    @Test
    void testFilterUniformOffsets() {
        // 4 mat4s, vec2 screenSize, vec2 uvScale, then ints
        assertEquals(0, ShaderBindings.Filter.PROJECTION_MATRIX_OFFSET);
        assertEquals(64, ShaderBindings.Filter.INVERSE_PROJECTION_MATRIX_OFFSET);
        assertEquals(128, ShaderBindings.Filter.VIEW_MATRIX_OFFSET);
        assertEquals(192, ShaderBindings.Filter.INVERSE_VIEW_MATRIX_OFFSET);
        assertEquals(256, ShaderBindings.Filter.SCREEN_SIZE_OFFSET);
        assertEquals(264, ShaderBindings.Filter.UV_SCALE_OFFSET);
        assertEquals(272, ShaderBindings.Filter.HAS_GBUFFER_OFFSET);
        assertEquals(276, ShaderBindings.Filter.BASE_COLOR_INDEX_OFFSET);
        assertEquals(292, ShaderBindings.Filter.DEPTH_INDEX_OFFSET);
        assertEquals(296, ShaderBindings.Filter.UNIFORMS_BUFFER_SIZE);
    }

    @Test
    void testSkyboxUniformOffsets() {
        // mat4 projectionMatrix; mat4 viewMatrix; vec4 diffuse; int hasTexture; int textureIndex;
        assertEquals(0, ShaderBindings.Skybox.PROJECTION_MATRIX_OFFSET);
        assertEquals(64, ShaderBindings.Skybox.VIEW_MATRIX_OFFSET);
        assertEquals(128, ShaderBindings.Skybox.DIFFUSE_OFFSET);
        assertEquals(144, ShaderBindings.Skybox.HAS_TEXTURE_OFFSET);
        assertEquals(148, ShaderBindings.Skybox.TEXTURE_INDEX_OFFSET);
        assertEquals(152, ShaderBindings.Skybox.UNIFORMS_BUFFER_SIZE);
    }

    @Test
    void testGuiUniformOffsets() {
        // vec2 scale; int fontTexture; vec2 displayPosition;
        assertEquals(0, ShaderBindings.GUI.UNIFORM_BUFFER_SCALE_OFFSET);
        assertEquals(8, ShaderBindings.GUI.UNIFORM_BUFFER_FONT_TEXTURE_OFFSET);
        assertEquals(16, ShaderBindings.GUI.UNIFORM_BUFFER_DISPLAY_POSITION_OFFSET);
        assertEquals(24, ShaderBindings.GUI.UNIFORMS_BUFFER_SIZE);
    }
}
