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
        // int lightCount, int clusterHeatMap
        assertEquals(176, ShaderBindings.Light.LIGHT_COUNT_OFFSET);
        assertEquals(180, ShaderBindings.Light.CLUSTER_HEAT_MAP_OFFSET);
        // Fog {vec3 color; float density; int enabled; float logScale; vec3 shape;} starts on a 16
        // byte boundary, its shape on the next
        assertEquals(192, ShaderBindings.Light.FOG_OFFSET);
        assertEquals(16, ShaderBindings.Light.Fog.ENABLED);
        assertEquals(20, ShaderBindings.Light.Fog.LOG_SCALE);
        assertEquals(32, ShaderBindings.Light.Fog.SHAPE);
        assertEquals(48, ShaderBindings.Light.Fog.SIZEOF);
        // CascadeShadow {mat4 projViewMatrix; float splitDistance;}[3], each padded to 80 bytes
        assertEquals(240, ShaderBindings.Light.CASCADE_SHADOWS_OFFSET);
        assertEquals(68, ShaderBindings.Light.CascadeShadow.TEXEL_SIZE);
        assertEquals(80, ShaderBindings.Light.CascadeShadow.ARRAY_STRIDE);
        // 8 ints for the texture indices
        assertEquals(480, ShaderBindings.Light.BASE_COLOR_SAMPLER_INDEX_OFFSET);
        assertEquals(496, ShaderBindings.Light.DEPTH_SAMPLER_INDEX_OFFSET);
        assertEquals(500, ShaderBindings.Light.SHADOW_MAP_0_INDEX_OFFSET);
        // float clusterLogScale, then padded to 16 bytes
        assertEquals(512, ShaderBindings.Light.CLUSTER_LOG_SCALE_OFFSET);
        // The sky map's matrix starts on the next 16 bytes after the log scale
        assertEquals(528, ShaderBindings.Light.SKY_PROJECTION_VIEW_MATRIX_OFFSET);
        assertEquals(592, ShaderBindings.Light.SKY_MAP_INDEX_OFFSET);
        assertEquals(596, ShaderBindings.Light.SKY_DEPTH_RANGE_OFFSET);
        assertEquals(600, ShaderBindings.Light.SKY_LIGHT_FLOOR_OFFSET);
        assertEquals(604, ShaderBindings.Light.SKY_OCCLUSION_VIEW_OFFSET);
        assertEquals(608, ShaderBindings.Light.POINT_ATLAS_INDEX_OFFSET);
        assertEquals(612, ShaderBindings.Light.POINT_DYNAMIC_ATLAS_INDEX_OFFSET);
        assertEquals(624, ShaderBindings.Light.OCCUPANCY_ORIGINS_OFFSET);
        assertEquals(672, ShaderBindings.Light.OCCUPANCY_TEXELS_OFFSET);
        assertEquals(720, ShaderBindings.Light.OCCUPANCY_ENABLED_OFFSET);
        assertEquals(724, ShaderBindings.Light.OCCUPANCY_VIEW_OFFSET);
        assertEquals(728, ShaderBindings.Light.OCCUPANCY_AO_REACH_OFFSET);
        assertEquals(732, ShaderBindings.Light.OCCUPANCY_AO_VIEW_OFFSET);
        assertEquals(736, ShaderBindings.Light.OCCUPANCY_RAYS_OFFSET);
        assertEquals(740, ShaderBindings.Light.OCCUPANCY_RAY_VIEW_OFFSET);
        assertEquals(744, ShaderBindings.Light.EMISSION_SAMPLER_INDEX_OFFSET);
        assertEquals(748, ShaderBindings.Light.ENVIRONMENT_ENABLED_OFFSET);
        // A vec4, on a 16 byte boundary
        assertEquals(752, ShaderBindings.Light.ENVIRONMENT_SCALE_OFFSET);
        assertEquals(768, ShaderBindings.Light.ENVIRONMENT_OCCLUSION_OFFSET);
        assertEquals(784, ShaderBindings.Light.UNIFORMS_BUFFER_SIZE);
        // The shadow map index takes the first of the light struct's spare words
        assertEquals(56, ShaderBindings.Light.LightStruct.SHADOW_MAP);
    }

    /** Matches the push constants in taa.frag, which must fit the 128 bytes every device has. */
    @Test
    void taaPushConstantsMatchTheShader() {
        assertEquals(0, ShaderBindings.Taa.PUSH_CONSTANT_REPROJECTION_OFFSET);
        assertEquals(64, ShaderBindings.Taa.PUSH_CONSTANT_COLOR_INDEX_OFFSET);
        assertEquals(68, ShaderBindings.Taa.PUSH_CONSTANT_HISTORY_INDEX_OFFSET);
        assertEquals(72, ShaderBindings.Taa.PUSH_CONSTANT_DEPTH_INDEX_OFFSET);
        assertEquals(76, ShaderBindings.Taa.PUSH_CONSTANT_PREVIOUS_DEPTH_INDEX_OFFSET);
        assertEquals(80, ShaderBindings.Taa.PUSH_CONSTANT_EXTINCTION_INDEX_OFFSET);
        assertEquals(84, ShaderBindings.Taa.PUSH_CONSTANT_FLAGS_OFFSET);
        // A vec2 starts on 8 bytes
        assertEquals(88, ShaderBindings.Taa.PUSH_CONSTANT_SCREEN_SIZE_OFFSET);
        assertEquals(96, ShaderBindings.Taa.PUSH_CONSTANT_EXPOSURE_OFFSET);
        assertEquals(100, ShaderBindings.Taa.PUSH_CONSTANTS_SIZE);
    }

    @Test
    void testLightStructOffsets() {
        // Light {vec3 position; float range; vec3 color; float intensity; vec3 direction;
        // float cosOuter; float cosInner; uint type; vec2 pad;} under std430
        assertEquals(0, ShaderBindings.Light.LightStruct.POSITION);
        assertEquals(12, ShaderBindings.Light.LightStruct.RANGE);
        assertEquals(16, ShaderBindings.Light.LightStruct.COLOR);
        assertEquals(28, ShaderBindings.Light.LightStruct.INTENSITY);
        assertEquals(32, ShaderBindings.Light.LightStruct.DIRECTION);
        assertEquals(44, ShaderBindings.Light.LightStruct.COS_OUTER);
        assertEquals(48, ShaderBindings.Light.LightStruct.COS_INNER);
        assertEquals(52, ShaderBindings.Light.LightStruct.TYPE);
        assertEquals(64, ShaderBindings.Light.LightStruct.SIZEOF);
    }

    @Test
    void testLightCullOffsets() {
        // mat4 inverseProjection, 3 buffer addresses, uint lightCount, float logScale, float far
        assertEquals(0, ShaderBindings.LightCull.PUSH_CONSTANT_INVERSE_PROJECTION_OFFSET);
        assertEquals(64, ShaderBindings.LightCull.PUSH_CONSTANT_LIGHTS_OFFSET);
        assertEquals(72, ShaderBindings.LightCull.PUSH_CONSTANT_CLUSTERS_OFFSET);
        assertEquals(80, ShaderBindings.LightCull.PUSH_CONSTANT_STATS_OFFSET);
        assertEquals(88, ShaderBindings.LightCull.PUSH_CONSTANT_LIGHT_COUNT_OFFSET);
        assertEquals(92, ShaderBindings.LightCull.PUSH_CONSTANT_LOG_SCALE_OFFSET);
        assertEquals(96, ShaderBindings.LightCull.PUSH_CONSTANT_FAR_PLANE_OFFSET);
        assertEquals(100, ShaderBindings.LightCull.PUSH_CONSTANTS_SIZE);
        assertTrue(ShaderBindings.LightCull.PUSH_CONSTANTS_SIZE <= 128);
        // 3,456 clusters, each a count and 256 indices
        assertEquals(3456L * 257 * 4, ShaderBindings.LightCull.CLUSTERS_BUFFER_SIZE);
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
        // The material transparency address, at the end
        assertEquals(112, ShaderBindings.Cull.PUSH_CONSTANT_MATERIAL_TRANSPARENCY_OFFSET);
        assertEquals(120, ShaderBindings.Cull.PUSH_CONSTANTS_SIZE);
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
        // int physicalSky; vec4 sunAndRadius; vec4 skyScale; int fogEnabled;
        assertEquals(0, ShaderBindings.Skybox.PROJECTION_MATRIX_OFFSET);
        assertEquals(64, ShaderBindings.Skybox.VIEW_MATRIX_OFFSET);
        assertEquals(128, ShaderBindings.Skybox.DIFFUSE_OFFSET);
        assertEquals(144, ShaderBindings.Skybox.HAS_TEXTURE_OFFSET);
        assertEquals(148, ShaderBindings.Skybox.TEXTURE_INDEX_OFFSET);
        assertEquals(152, ShaderBindings.Skybox.PHYSICAL_SKY_OFFSET);
        assertEquals(156, ShaderBindings.Skybox.LAYOUT_OFFSET);
        assertEquals(160, ShaderBindings.Skybox.SUN_AND_RADIUS_OFFSET);
        assertEquals(176, ShaderBindings.Skybox.SKY_SCALE_OFFSET);
        assertEquals(192, ShaderBindings.Skybox.FOG_ENABLED_OFFSET);
        assertEquals(208, ShaderBindings.Skybox.UNIFORMS_BUFFER_SIZE);
    }

    @Test
    void testTransparencyPushConstants() {
        // vec2 screenSize; float logScale; int voxelWeights; vec2 tiles;
        assertEquals(8, ShaderBindings.Translucent.PUSH_CONSTANT_LOG_SCALE_OFFSET);
        assertEquals(12, ShaderBindings.Translucent.PUSH_CONSTANT_VOXEL_WEIGHTS_OFFSET);
        assertEquals(16, ShaderBindings.Translucent.PUSH_CONSTANT_TILES_OFFSET);
        assertEquals(24, ShaderBindings.Translucent.PUSH_CONSTANT_VOLUME_FADE_OFFSET);
        assertEquals(32, ShaderBindings.Translucent.PUSH_CONSTANT_FRAME_OFFSET);
        assertEquals(36, ShaderBindings.Translucent.PUSH_CONSTANTS_SIZE);
        // Extinction extinction; uvec2 tiles; float logScale;
        assertEquals(8, ShaderBindings.OitVolume.PUSH_CONSTANT_TILES_OFFSET);
        assertEquals(16, ShaderBindings.OitVolume.PUSH_CONSTANT_LOG_SCALE_OFFSET);
        assertEquals(20, ShaderBindings.OitVolume.PUSH_CONSTANT_VOLUME_FADE_OFFSET);
        assertEquals(28, ShaderBindings.OitVolume.PUSH_CONSTANT_FRAME_OFFSET);
        assertEquals(32, ShaderBindings.OitVolume.SPLAT_PUSH_CONSTANTS_SIZE);
        assertEquals(16, ShaderBindings.OitVolume.INTEGRATE_PUSH_CONSTANTS_SIZE);
        // int accumIndex; int extinctionIndex; int debugSlice;
        assertEquals(8, ShaderBindings.OitResolve.PUSH_CONSTANT_DEBUG_SLICE_OFFSET);
        assertEquals(12, ShaderBindings.OitResolve.PUSH_CONSTANTS_SIZE);
    }

    @Test
    void testGuiUniformOffsets() {
        // int fontTexture;
        assertEquals(0, ShaderBindings.GUI.UNIFORM_BUFFER_FONT_TEXTURE_OFFSET);
        assertEquals(16, ShaderBindings.GUI.UNIFORMS_BUFFER_SIZE);
    }

    @Test
    void testGuiPushConstantOffsets() {
        // int commandOffset; int pointOffset; int detailOffset; vec2 scale; vec2 displayPosition;
        // vec2 framebufferScale;
        assertEquals(0, ShaderBindings.GUI.PUSH_CONSTANT_COMMAND_OFFSET);
        assertEquals(4, ShaderBindings.GUI.PUSH_CONSTANT_POINT_OFFSET);
        assertEquals(8, ShaderBindings.GUI.PUSH_CONSTANT_DETAIL_OFFSET);
        assertEquals(16, ShaderBindings.GUI.PUSH_CONSTANT_SCALE_OFFSET);
        assertEquals(24, ShaderBindings.GUI.PUSH_CONSTANT_DISPLAY_POSITION_OFFSET);
        assertEquals(32, ShaderBindings.GUI.PUSH_CONSTANT_FRAMEBUFFER_SCALE_OFFSET);
        assertEquals(40, ShaderBindings.GUI.PUSH_CONSTANTS_SIZE);
    }

    /** As glslang reflects the particle shaders' push constants, buffer addresses 8 bytes each. */
    @Test
    void testParticlePushConstantOffsets() {
        // Control control; IndexList dead; uint mode; uint capacity; uint current;
        assertEquals(16, ShaderBindings.Particles.CONTROL_MODE_OFFSET);
        assertEquals(24, ShaderBindings.Particles.CONTROL_CURRENT_OFFSET);
        assertEquals(28, ShaderBindings.Particles.CONTROL_CONSTANTS_SIZE);
        // 5 addresses; vec3 shift on 16 bytes; float seconds; uint current;
        assertEquals(48, ShaderBindings.Particles.SIMULATE_SHIFT_OFFSET);
        assertEquals(60, ShaderBindings.Particles.SIMULATE_SECONDS_OFFSET);
        assertEquals(64, ShaderBindings.Particles.SIMULATE_CURRENT_OFFSET);
        assertEquals(68, ShaderBindings.Particles.SIMULATE_CONSTANTS_SIZE);
        // 5 addresses; uint emitterCount; uint total; uint next;
        assertEquals(40, ShaderBindings.Particles.EMIT_EMITTER_COUNT_OFFSET);
        assertEquals(48, ShaderBindings.Particles.EMIT_NEXT_OFFSET);
        assertEquals(52, ShaderBindings.Particles.EMIT_CONSTANTS_SIZE);
        // 2 addresses; vec3 anchor; float softScale; vec2 screenSize; vec2 tiles; float logScale;
        // int voxelWeights; float pixelScale; uint padding; address splat;
        assertEquals(16, ShaderBindings.Particles.DRAW_ANCHOR_OFFSET);
        assertEquals(28, ShaderBindings.Particles.DRAW_SOFT_SCALE_OFFSET);
        assertEquals(32, ShaderBindings.Particles.DRAW_SCREEN_SIZE_OFFSET);
        assertEquals(40, ShaderBindings.Particles.DRAW_TILES_OFFSET);
        assertEquals(48, ShaderBindings.Particles.DRAW_LOG_SCALE_OFFSET);
        assertEquals(52, ShaderBindings.Particles.DRAW_VOXEL_WEIGHTS_OFFSET);
        assertEquals(56, ShaderBindings.Particles.DRAW_PIXEL_SCALE_OFFSET);
        assertEquals(64, ShaderBindings.Particles.DRAW_SPLAT_OFFSET);
        assertEquals(72, ShaderBindings.Particles.DRAW_CONSTANTS_SIZE);
    }
}
