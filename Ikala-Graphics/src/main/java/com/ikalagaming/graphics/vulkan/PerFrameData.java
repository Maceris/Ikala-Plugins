package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.Model;

import java.util.BitSet;
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

    /**
     * Whether each material is translucent, a uint by material index, read by culling. Host
     * coherent, written by the instance draw update stage.
     */
    public SharedBuffer materialTransparency;

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
     * The standard mesh slots whose draw commands {@link #sceneDrawCommands} holds as drawable, as
     * last written: only those and the slots drawable now are written again.
     */
    public final BitSet drawnStandard = new BitSet();

    /**
     * The baked mesh slots whose draw commands it holds as drawable, like {@link #drawnStandard}.
     */
    public final BitSet drawnBaked = new BitSet();

    /**
     * The command buffer, standard slots and baked slots its draw commands were laid out for, or -1
     * before the first: anything else, and every command is written again.
     */
    public final long[] drawnLayout = {-1, -1, -1};

    /**
     * Indirect draw commands for level of detail clusters this frame, written by the culling pass:
     * {@link LodRenderer#LIST_SLOTS} lists of {@link #lodCapacity} plus {@link #lodPairCapacity}
     * commands each.
     */
    public SharedBuffer lodDrawCommands;

    /**
     * How many commands each level of detail list has, then the culling pass's counters, see {@link
     * LodRenderer#COUNTER_COUNT}.
     */
    public SharedBuffer lodCounts;

    /** Every level of detail world slot this frame, see {@link LodCullMath}. */
    public SharedBuffer lodWorlds;

    /** How many level of detail worlds are drawn this frame. */
    public int lodWorldCount;

    /** How many entries the cluster table has. */
    public int lodCapacity;

    /**
     * How many entries the pair table has; each level of detail list has room for {@link
     * #lodCapacity} plus this many commands.
     */
    public int lodPairCapacity;

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

    /**
     * The cascades' maps of what moves, drawn every frame, for the cached cascades; null for one
     * that isn't cached. Shared between frames like the cascade maps.
     */
    public TextureInfoVulkan[] cascadeDynamicShadows;

    /**
     * A copy of a cached cascade's map, which the map is drawn back from shifted along when the
     * cascade moves across the light. Shared between frames and cascades, used one at a time.
     */
    public TextureInfoVulkan cascadeScrollCopy;

    /** This frame's sky occlusion view: its matrix and what of its map is drawn. */
    public CascadeShadowSplit skyView;

    /** The sky occlusion map, shared between frames like the shadow maps. */
    public TextureInfoVulkan skyMap;

    /** The point light shadow atlas of what doesn't move, shared between frames. */
    public TextureInfoVulkan pointAtlas;

    /** The point light shadow atlas of what moves, shared between frames. */
    public TextureInfoVulkan pointDynamicAtlas;

    /** This frame's point shadow map entries for the light stage, see {@link PointShadows}. */
    public SharedBuffer pointShadowMaps;

    /** Whether anything that moves casts shadows this frame: standard meshes or listed models. */
    public boolean dynamicShadowCasters;

    /** Base color, normal, tangent, material, and depth. */
    public GBuffer gBuffer;

    /**
     * A texture that is rendered to before the filter stage, if there is a filter stage. Unused if
     * there's no filter, as we can just bind the final texture directly.
     */
    public TextureInfoVulkan preFilterTexture;

    /**
     * The lit scene in linear light, before tone mapping, drawn by the light, skybox and
     * transparency stages and read by the tone map stage. Registered in the bindless array.
     */
    public TextureInfoVulkan sceneColor;

    /**
     * The scene's color behind the water, copied from {@link #sceneColor} before the water stage
     * draws over it, which the water shows through its surface. Registered in the bindless array.
     */
    public TextureInfoVulkan waterBehindColor;

    /**
     * The scene's depth behind the water, copied from the G-buffer's before the water stage writes
     * its own. Registered in the bindless array.
     */
    public TextureInfoVulkan waterBehindDepth;

    /**
     * The water's depth, before the transparent stages draw what lies behind the water, which they
     * leave out what is in front of; then 0, so the second time they draw what is in front. See
     * {@code waterGate} in {@code light_inputs.glsl}. Registered in the bindless array.
     */
    public TextureInfoVulkan waterGate;

    /** Whether the gate holds the water's depth this frame, so what lies behind it is drawn. */
    public boolean waterGated;

    /**
     * The scene color after temporal anti-aliasing, in linear light: what the tone map stage reads
     * while it runs, and the history the next frame blends into. Registered in the bindless array.
     */
    public TextureInfoVulkan taaColor;

    /** Whether the temporal anti-aliasing stage wrote {@link #taaColor} this frame. */
    public boolean taaApplied;

    /**
     * The translucent layers' lit color times opacity times weight, and opacity times weight, added
     * up by the transparent stage and read by the resolve. Registered in the bindless array.
     */
    public TextureInfoVulkan oitAccum;

    /**
     * The translucent layers' summed extinction, -log(1 - opacity), added up by the transparent
     * stage and read by the resolve. Registered in the bindless array.
     */
    public TextureInfoVulkan oitExtinction;

    /**
     * Whether this frame's translucent surfaces are weighted by the voxel-based transparency
     * volume, read once at the start of the transparency stages so they all agree.
     */
    public boolean voxelTransparency;

    /**
     * Whether the light stage filled the fog volume this frame, for the global fog or medium
     * volumes, so the sky and the transparent stages see through it too.
     */
    public boolean fogFilled;

    /**
     * The texture we render to just before moving over to the swapchain. The filter stage renders
     * here if present, otherwise the scene can be rendered here directly. The GUI is rendered on
     * top of here as well at the end.
     */
    public TextureInfoVulkan finalTexture;
}
