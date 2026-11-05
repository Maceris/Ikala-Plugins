package com.ikalagaming.graphics.vulkan.stages;

import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.List;

/**
 * The transparent stages, run as one: twice a frame where there is water, first for what lies
 * behind the water, before it copies the scene to see through, and then for what is in front of it,
 * see {@code waterGate} in {@code light_inputs.glsl}. The stages are shared between the two; the
 * first run only happens when the water gate was drawn. Each stage is timed on its own, named after
 * the stage, with {@link #BEHIND_WATER_PREFIX} first in the run behind the water; what is left
 * under the chain's own name is only the time between them.
 */
public class TransparencyChain implements RenderStage {

    /** What goes before each stage's timing name in the run behind the water. */
    public static final String BEHIND_WATER_PREFIX = "Behind water: ";

    /** The stages, in order. */
    private final List<RenderStage> stages;

    /** Each stage's timing name, in the same order. */
    private final List<String> timingNames;

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
        timingNames =
                this.stages.stream()
                        .map(
                                stage ->
                                        behindWater
                                                ? BEHIND_WATER_PREFIX + stage.name()
                                                : stage.name())
                        .toList();
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
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        for (int i = 0; i < stages.size(); ++i) {
            stages.get(i).render(scene, sceneTarget, state, renderConfig);
            state.frameTimings.mark(commandBuffer, state.frameIndex, timingNames.get(i));
        }
    }

    @Override
    public String name() {
        return behindWater ? "TransparencyBehindWater" : "Transparency";
    }
}
