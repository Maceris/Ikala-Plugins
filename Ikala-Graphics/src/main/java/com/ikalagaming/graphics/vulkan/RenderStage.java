package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public interface RenderStage {
    enum Type {
        ANIMATION,
        ATMOSPHERE,
        AUTO_EXPOSURE,
        TABLE_SCATTER,
        CULL,
        LOD_CULL,
        DEBUG,
        DEBUG_NORMALS,
        DEPTH_PYRAMID,
        ENVIRONMENT,
        FILTER,
        FOG_INJECT,
        FOG_INTEGRATE,
        PARTICLE,
        PARTICLE_CONTROL,
        PARTICLE_EMIT,
        PARTICLE_FROXEL,
        PARTICLE_LIGHT,
        PARTICLE_SIMULATE,
        PARTICLE_SPLASH,
        WATER,
        PARTICLE_SPLAT,
        FILTER_BASE_COLOR,
        FILTER_DEPTH,
        FILTER_MATERIAL,
        FILTER_NORMAL,
        FILTER_SPLIT,
        FILTER_TANGENT,
        GUI,
        INSTANCES,
        LIGHT,
        LIGHT_CULL,
        SCENE,
        SCENE_BAKED,
        SCENE_LOD,
        SCENE_VOLUME,
        SCENE_SKINNED,
        SHADOW_BAKED,
        SHADOW_LOD,
        SHADOW_LOD_CUTOUT,
        SHADOW_SKINNED,
        SHADOW_VOLUME,
        SHADOW_BRICK,
        SHADOW_SCROLL,
        SHADOW_CUTOUT,
        SHADOW_BAKED_CUTOUT,
        POINT_SHADOW,
        POINT_SHADOW_BAKED,
        OCCUPANCY_CLEAR,
        OCCUPANCY_VOXELIZE,
        OCCUPANCY_BRICKS,
        SKYBOX,
        TAA,
        TONEMAP,
        TRANSLUCENT,
        TRANSLUCENT_BAKED,
        OIT_RESOLVE,
        OIT_SPLAT,
        OIT_SPLAT_BAKED,
        OIT_INTEGRATE,
        VOLUME_UPLOAD,
        LOD_BAKE,
        VOLUME_SPLAT,
        VOLUME_COLOR,
        VOLUME_DEPTH
    }

    /** Set up the render stage. Must be called before rendering, should not be called twice. */
    default void initialize(@NonNull VulkanState state) {}

    /** Clean up any resources for the stage. Calling render is not valid after this point. */
    default void cleanup(@NonNull VulkanState state) {}

    void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            RenderConfig renderConfig);

    /**
     * What the stage is called in frame timings.
     *
     * @return The name, the class's simple name unless a stage says otherwise.
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
