package com.ikalagaming.graphics.vulkan.stages;

import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;

import java.util.List;

/**
 * The transparent stages, run as one: twice a frame where there is water, first for what lies
 * behind the water, before it copies the scene to see through, and then for what is in front of it,
 * see {@code waterGate} in {@code light_inputs.glsl}. The stages are shared between the two; the
 * first run only happens when the water gate was drawn.
 */
public class TransparencyChain implements RenderStage {

    /** The stages, in order. */
    private final List<RenderStage> stages;

    /** Whether this is the run for what lies behind the water. */
    private final boolean behindWater;

    /**
     * Set up a run of the transparent stages.
     *
     * @param stages The stages, in order; initialized and cleaned up by whoever owns them.
     * @param behindWater Whether this is the run for what lies behind the water, which only happens
     *     when the water gate was drawn.
     */
    public TransparencyChain(@NonNull List<RenderStage> stages, boolean behindWater) {
        this.stages = List.copyOf(stages);
        this.behindWater = behindWater;
    }

    @Override
    public void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            RenderConfig renderConfig) {
        if (behindWater && !state.perFrameData[state.frameIndex].waterGated) {
            return;
        }
        for (RenderStage stage : stages) {
            stage.render(scene, sceneTarget, state, renderConfig);
        }
    }

    @Override
    public String name() {
        return behindWater ? "TransparencyBehindWater" : "Transparency";
    }
}
