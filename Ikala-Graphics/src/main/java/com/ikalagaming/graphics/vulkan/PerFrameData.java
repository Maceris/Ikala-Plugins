package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.Model;

import java.util.Map;

/** Data buffers for a frame, only the data that the CPU cares about. */
public class PerFrameData {
    /**
     * The index of the first bone matrix in each animated model's animation buffer, per pose.
     * Entities on the same animation frame share a pose, so it's only skinned once. Every animated
     * model's poses are packed back to back, see {@link ModelDrawInfo#firstPose()}.
     */
    public SharedBuffer animationOffsets;

    public SharedBuffer guiUniforms;
    public SharedBuffer guiCommands;
    public SharedBuffer guiPoints;
    public SharedBuffer guiPointDetails;

    /** Vertices for every GUI draw list this frame, packed back to back. */
    public SharedBuffer guiVertices;

    /** Maps draw data texture IDs to bindless texture slots. */
    public SharedBuffer guiTextureIndices;

    /** Staging buffer for font glyphs that are copied into the font atlas this frame. */
    public SharedBuffer guiFontStaging;

    public SharedBuffer lightUniforms;

    /** The point lights and spotlights, in view space, read by the light stage. */
    public SharedBuffer lights;

    /** How many lights the light cull stage wrote into {@link #lights} this frame. */
    public int lightCount;

    /**
     * Each cluster's light count and light indices, written by the light cull stage and read by the
     * light stage. See {@link ShaderBindings.LightCull#CLUSTERS_BUFFER_SIZE}.
     */
    public SharedBuffer lightClusters;

    /**
     * What light culling found, for the debug window. Host coherent, read back and cleared once
     * this frame's fence has been waited on.
     */
    public SharedBuffer lightClusterStats;

    public SharedBuffer sceneUniforms;

    /**
     * The model matrix of every instance slot this frame, relative to the camera. Written on the
     * GPU by the instance transform stage.
     */
    public SharedBuffer sceneModelMatrices;

    /** How many instance slots there are this frame. */
    public int instanceCount;

    /**
     * What each pass draws: (instance slot, material) pairs, grouped by mesh, which draws find by
     * their first instance. The first regions are filled by the culling pass, one per visible list
     * (see {@code InstanceDrawUpdate.LIST_COUNT}); after them come the CPU-written groups of {@link
     * #modelDrawInfo}.
     */
    public SharedBuffer visibleInstances;

    /** The frustum planes of each pass this frame, for the culling pass. */
    public SharedBuffer cullFrusta;

    /**
     * How many meshes each visible list drew, then how many the occlusion test hid, counted by the
     * culling pass and read back later.
     */
    public SharedBuffer cullCounters;

    /**
     * What the occlusion test needs this frame: the matrix that projects onto the depth pyramid,
     * its size, and the flags. See {@code ShaderBindings.Cull.VIEW_SIZE}.
     */
    public SharedBuffer cullView;

    /** Whether the scene is culled against the depth pyramid this frame. */
    public boolean occlusion;

    /** Whether the depth pyramid is built this frame, rather than kept from before. */
    public boolean buildPyramid;

    /**
     * How many mesh slots there are this frame, which is how many draw commands each visible list
     * has, starting at {@code list * meshSlotCount} in {@link #sceneDrawCommands}.
     */
    public int meshSlotCount;

    /**
     * How many slots the baked mesh table has this frame, which is how many draw commands each
     * visible list of each baked kind has. See {@code InstanceDrawUpdate.commandIndex}.
     */
    public int bakedSlotCount;

    /**
     * Indirect draw commands this frame: one per mesh slot per visible list, which the culling pass
     * fills in, then the CPU-written commands of {@link #modelDrawInfo}.
     */
    public SharedBuffer sceneDrawCommands;

    /**
     * Models drawn from CPU-written commands this frame, which culling doesn't handle: animated
     * models, and every model while the normal or tangent lines are shown.
     */
    public Map<Model, ModelDrawInfo> modelDrawInfo;

    /**
     * Where a model's CPU-written data starts in the packed per-frame scene buffers.
     *
     * @param firstVisible Where its entries start in {@link #visibleInstances}: one group per mesh,
     *     each with one entry per instance.
     * @param firstCommand The index of the first draw command in {@link #sceneDrawCommands}. Each
     *     mesh has {@code commandCount} commands, one mesh after another.
     * @param commandCount How many draw commands each mesh has.
     * @param firstPose For animated models, where the model's poses start in {@link
     *     #animationOffsets}. Each pose gets its own copy of the mesh vertices in the animation
     *     target buffers.
     * @param poseCount For animated models, the number of distinct poses the entities are in.
     */
    public record ModelDrawInfo(
            int firstVisible, int firstCommand, int commandCount, int firstPose, int poseCount) {}

    public SharedBuffer skyboxUniforms;

    /** Line vertices for debug shapes, see {@link DebugGeometry}. */
    public SharedBuffer debugVertices;

    /** Uniforms for the filter stage, which give filters access to the g-buffer. */
    public SharedBuffer filterUniforms;

    /** Materials used by the light and scene render phases. */
    public SharedBuffer materials;

    public CascadeShadowSplit[] cascadeShadowSplits;
    public TextureInfoVulkan[] cascadeShadows;

    /** Base color, normal, tangent, material, and depth. */
    public GBuffer gBuffer;

    /**
     * A texture that is rendered to before the filter stage, if there is a filter stage. Unused if
     * there's no filter, as we can just bind the final texture directly.
     */
    public TextureInfoVulkan preFilterTexture;

    /**
     * The texture we render to just before moving over to the swapchain. The filter stage renders
     * here if present, otherwise the scene can be rendered here directly. The GUI is rendered on
     * top of here as well at the end.
     */
    public TextureInfoVulkan finalTexture;
}
