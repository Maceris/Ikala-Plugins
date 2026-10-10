package com.ikalagaming.graphics.vulkan.stages;

import com.ikalagaming.graphics.MeshHandle;
import com.ikalagaming.graphics.MeshKind;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Observer;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugVisualizers;
import com.ikalagaming.graphics.vulkan.DepthPyramid;
import com.ikalagaming.graphics.vulkan.FrustumPlanes;
import com.ikalagaming.graphics.vulkan.InstanceTable;
import com.ikalagaming.graphics.vulkan.MeshRegistry;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sets up this frame's draws for the GPU to fill in. Culling on the GPU decides which instances
 * each pass draws; this stage gives it what it needs, at a cost that depends on the number of
 * meshes and models, not instances:
 *
 * <ul>
 *   <li>One draw command per mesh slot per visible list, with its instance count at zero and its
 *       first instance pointing at the room set aside for that mesh's visible instances.
 *   <li>The frustum planes of each pass: the scene, from the observer, then each shadow cascade.
 *   <li>The occlusion view: whether the scene is culled against the depth pyramid, and how to
 *       project onto it.
 * </ul>
 *
 * <p>There are more visible lists than passes, since the scene is culled in two phases, see {@code
 * cull.comp}: the early scene list, the late scene list, then a list for each cascade.
 *
 * <p>Each {@link MeshKind} has its own block of commands, since each is drawn with its own pipeline
 * from its own buffers: by kind, then list, then mesh slot, see {@link #commandIndex}. Standard
 * meshes have a command per slot of the main mesh table, and each baked kind one per slot of the
 * baked mesh table; culling only fills in a baked slot's command in the block of its mesh's kind.
 *
 * <p>Models culling can't draw, animated ones, get CPU-written commands and visible entries after
 * the culled ones, as do all models while the normal or tangent lines are shown.
 */
@Slf4j
public class InstanceDrawUpdate implements RenderStage {

    /** The size of a VkDrawIndexedIndirectCommand in bytes. */
    public static final int DRAW_COMMAND_SIZE = 5 * Integer.BYTES;

    /** The size of one visible instance entry: its slot and its material. */
    public static final int VISIBLE_ENTRY_SIZE = 2 * Integer.BYTES;

    /** How many frusta are culled against: the scene, then each shadow cascade. */
    public static final int PASS_COUNT = 1 + CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT;

    /** The visible list of what the scene draws before the depth pyramid is built. */
    public static final int LIST_SCENE_EARLY = 0;

    /** The visible list of what the scene draws after the occlusion test. */
    public static final int LIST_SCENE_LATE = 1;

    /** The visible list of the first shadow cascade, the others following it. */
    public static final int LIST_FIRST_CASCADE = 2;

    /** How many visible lists there are, each with its own draw commands. */
    public static final int LIST_COUNT =
            LIST_FIRST_CASCADE + CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT;

    /** The counter of meshes the occlusion test hid, after one counter per list. */
    public static final int COUNTER_OCCLUDED = LIST_COUNT;

    /** How many counters the culling pass keeps. */
    public static final int COUNTER_COUNT = COUNTER_OCCLUDED + 1;

    /**
     * Where a mesh slot's draw command for one list is, with the commands laid out by kind, then
     * list, then mesh slot. {@code cull.comp} works this out the same way.
     *
     * @param kind The kind of mesh.
     * @param list The visible list.
     * @param slot The mesh's slot in its kind's mesh table.
     * @param standardSlots How many slots the main mesh table has.
     * @param bakedSlots How many slots the baked mesh table has.
     * @return The command's index in the draw command buffer.
     */
    public static int commandIndex(
            @NonNull MeshKind kind, int list, int slot, int standardSlots, int bakedSlots) {
        final int kindStart =
                kind == MeshKind.STANDARD
                        ? 0
                        : LIST_COUNT * standardSlots
                                + (kind.ordinal() - 1) * LIST_COUNT * bakedSlots;
        return kindStart + list * slotsOf(kind, standardSlots, bakedSlots) + slot;
    }

    /**
     * How many commands each list has for a kind of mesh.
     *
     * @param kind The kind.
     * @param standardSlots How many slots the main mesh table has.
     * @param bakedSlots How many slots the baked mesh table has.
     * @return The command count.
     */
    public static int slotsOf(@NonNull MeshKind kind, int standardSlots, int bakedSlots) {
        return kind == MeshKind.STANDARD ? standardSlots : bakedSlots;
    }

    /**
     * How many culled draw commands there are in all, every list of every kind.
     *
     * @param standardSlots How many slots the main mesh table has.
     * @param bakedSlots How many slots the baked mesh table has.
     * @return The command count.
     */
    public static int culledCommandCount(int standardSlots, int bakedSlots) {
        int count = 0;
        for (MeshKind kind : MeshKind.ALL) {
            count += LIST_COUNT * slotsOf(kind, standardSlots, bakedSlots);
        }
        return count;
    }

    /** One model's instances, collected while the instance registry is locked. */
    private static final class ModelInstances {
        /** The slot of each instance. */
        private int[] slots = new int[16];

        /** The material index of each mesh of each instance, mesh by mesh. */
        private int[] materials;

        /** The animation matrix offset of each instance, or -1 for the bind pose. */
        private int[] animationOffsets = new int[16];

        /** How many instances there are. */
        private int count;

        /** How many meshes the model has. */
        private final int meshCount;

        /** The model's own material index for each mesh. */
        private final int[] meshMaterials;

        /**
         * Start collecting a model's instances.
         *
         * @param model The model.
         * @param materialCache Looks up material indices.
         */
        ModelInstances(@NonNull Model model, @NonNull MaterialCache materialCache) {
            meshCount = model.getMeshDataList().size();
            meshMaterials = new int[meshCount];
            for (int mesh = 0; mesh < meshCount; ++mesh) {
                meshMaterials[mesh] =
                        materialCache.getMaterialIndex(
                                model.getMeshDataList().get(mesh).getMaterial());
            }
            materials = new int[16 * Math.max(1, meshCount)];
        }

        /**
         * Add an instance.
         *
         * @param slot Its slot.
         * @param overrides Its material overrides, null entries for none.
         * @param animationOffset Its animation matrix offset, or -1.
         * @param materialCache Looks up material indices.
         */
        void add(
                int slot,
                Material[] overrides,
                int animationOffset,
                @NonNull MaterialCache materialCache) {
            if (count == slots.length) {
                slots = Arrays.copyOf(slots, count * 2);
                animationOffsets = Arrays.copyOf(animationOffsets, count * 2);
                materials = Arrays.copyOf(materials, count * 2 * Math.max(1, meshCount));
            }
            slots[count] = slot;
            animationOffsets[count] = animationOffset;
            for (int mesh = 0; mesh < meshCount; ++mesh) {
                Material override = mesh < overrides.length ? overrides[mesh] : null;
                // Index 0 is the default material, which means no override
                int index = override == null ? 0 : materialCache.getMaterialIndex(override);
                materials[count * meshCount + mesh] = index != 0 ? index : meshMaterials[mesh];
            }
            count += 1;
        }
    }

    @Override
    public void render(
            Scene scene,
            @NonNull Window window,
            @NonNull VulkanState vulkanState,
            int renderConfig) {
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];
        final InstanceTable instances = vulkanState.instances;
        frameData.modelDrawInfo.clear();
        readCounters(frameData, instances);

        // Culling needs the cascades before the shadow stage draws them
        CascadeShadowSplit.updateCascadeShadows(frameData.cascadeShadowSplits, scene);

        final InstanceTable.Snapshot snapshot = instances.getSnapshot();
        final MeshRegistry meshes = vulkanState.geometry.getRegistry();
        final MeshRegistry bakedMeshes = vulkanState.bakedGeometry.getRegistry();
        final int meshSlots = meshes.getSlotCapacity();
        final int bakedSlots = bakedMeshes.getSlotCapacity();
        frameData.instanceCount = snapshot.slotCapacity();
        frameData.meshSlotCount = meshSlots;
        frameData.bakedSlotCount = bakedSlots;

        // Room in each list for every instance of every mesh, kind by kind and mesh slot by mesh
        // slot, counted from the same snapshot the culling pass sees, so it can't overflow
        final int[][] firstVisible = new int[MeshKind.ALL.length][];
        for (MeshKind kind : MeshKind.ALL) {
            firstVisible[kind.ordinal()] = new int[slotsOf(kind, meshSlots, bakedSlots)];
        }
        final int culledEntries = layoutVisible(snapshot.models(), firstVisible);

        // Models culling doesn't draw, with their instances
        final DebugVisualizers visualizers = scene.getDebugVisualizers();
        final boolean debugLines = visualizers.isNormals() || visualizers.isTangents();
        final MaterialCache materialCache = scene.getMaterialCache();
        final Map<Model, ModelInstances> listed = new LinkedHashMap<>();
        for (InstanceTable.ModelCount modelCount : snapshot.models()) {
            final Model model = modelCount.model();
            if ((!model.isAnimated() && !debugLines) || isBaked(model)) {
                continue;
            }
            final ModelInstances modelInstances = new ModelInstances(model, materialCache);
            instances
                    .getRegistry()
                    .visit(
                            model,
                            (slot, overrides, animation) ->
                                    modelInstances.add(
                                            slot,
                                            overrides,
                                            model.isAnimated()
                                                    ? Model.getAnimationMatrixOffset(animation)
                                                    : -1,
                                            materialCache));
            if (modelInstances.count > 0) {
                listed.put(model, modelInstances);
            }
        }

        // Lay out the listed models after the culled lists
        final int culledCommands = culledCommandCount(meshSlots, bakedSlots);
        int visibleCount = LIST_COUNT * culledEntries;
        int commandCount = culledCommands;
        int poseCount = 0;
        final Map<Model, Poses> modelPoses = new LinkedHashMap<>();
        for (var entry : listed.entrySet()) {
            final Model model = entry.getKey();
            final ModelInstances modelInstances = entry.getValue();
            int modelPoseCount = 0;
            if (model.isAnimated()) {
                Poses poses = findPoses(modelInstances);
                modelPoses.put(model, poses);
                modelPoseCount = poses.matrixOffsets().length;
            }
            final int commandsPerMesh = model.isAnimated() ? modelInstances.count : 1;
            frameData.modelDrawInfo.put(
                    model,
                    new PerFrameData.ModelDrawInfo(
                            visibleCount,
                            commandCount,
                            commandsPerMesh,
                            poseCount,
                            modelPoseCount));
            visibleCount += modelInstances.count * modelInstances.meshCount;
            commandCount += commandsPerMesh * modelInstances.meshCount;
            poseCount += modelPoseCount;
        }

        frameData.sceneModelMatrices.ensureCapacity(
                (long) snapshot.slotCapacity()
                        * PipelineManagerVulkan.MODEL_MATRIX_SIZE
                        * Float.BYTES,
                vulkanState);
        frameData.visibleInstances.ensureCapacity(
                (long) visibleCount * VISIBLE_ENTRY_SIZE, vulkanState);
        frameData.sceneDrawCommands.ensureCapacity(
                (long) commandCount * DRAW_COMMAND_SIZE, vulkanState);
        frameData.animationOffsets.ensureCapacity((long) poseCount * Integer.BYTES, vulkanState);
        frameData.cullFrusta.ensureCapacity(
                (long) PASS_COUNT * ShaderBindings.Cull.PLANES_PER_PASS * 4 * Float.BYTES,
                vulkanState);
        frameData.cullCounters.ensureCapacity((long) COUNTER_COUNT * Integer.BYTES, vulkanState);

        // The buffers are host coherent and the GPU is done with this frame's copies
        final ByteBuffer commands =
                MemoryUtil.memByteBuffer(
                        frameData.sceneDrawCommands.allocationInfo.pMappedData(),
                        Math.max(1, commandCount) * DRAW_COMMAND_SIZE);
        writeCulledCommands(
                commands,
                meshes,
                MeshKind.STANDARD,
                meshSlots,
                bakedSlots,
                firstVisible,
                culledEntries);
        for (MeshKind kind : MeshKind.ALL) {
            if (kind.isBaked()) {
                writeCulledCommands(
                        commands,
                        bakedMeshes,
                        kind,
                        meshSlots,
                        bakedSlots,
                        firstVisible,
                        culledEntries);
            }
        }
        writeFrusta(scene, frameData, visualizers.isCullingDisabled());
        writeOcclusionView(scene, window, vulkanState, frameData, visualizers);

        if (listed.isEmpty()) {
            return;
        }
        final IntBuffer visible =
                MemoryUtil.memIntBuffer(
                        frameData.visibleInstances.allocationInfo.pMappedData(), visibleCount * 2);
        final IntBuffer animationOffsets =
                MemoryUtil.memIntBuffer(
                        frameData.animationOffsets.allocationInfo.pMappedData(), poseCount);
        frameData.modelDrawInfo.forEach(
                (model, info) -> {
                    final ModelInstances modelInstances = listed.get(model);
                    writeVisible(info, modelInstances, visible);
                    Poses poses = modelPoses.get(model);
                    if (poses != null) {
                        animationOffsets.put(info.firstPose(), poses.matrixOffsets());
                    }
                    writeListedCommands(model, info, modelInstances, poses, meshes, commands);
                });
    }

    /**
     * Whether a model is a baked section, which only culling draws.
     *
     * @param model The model.
     * @return True if its meshes are baked.
     */
    private static boolean isBaked(@NonNull Model model) {
        for (MeshData mesh : model.getMeshDataList()) {
            if (mesh.getMesh() != null && mesh.getMesh().kind().isBaked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set aside room in a culled list for every instance of every mesh, kind by kind and mesh slot
     * by mesh slot. A mesh slot used by more than one model, as when a released mesh's slot was
     * reused, gets room for all of them; culling only fills it for the live mesh.
     *
     * @param models Every model with instances, and how many each has.
     * @param firstVisible Receives where each mesh slot's room starts, one array per {@link
     *     MeshKind} ordinal, as long as that kind has slots.
     * @return How many entries a list needs in all.
     */
    static int layoutVisible(
            @NonNull List<InstanceTable.ModelCount> models,
            int @NonNull [] @NonNull [] firstVisible) {
        final int[][] perSlot = new int[firstVisible.length][];
        for (int kind = 0; kind < firstVisible.length; ++kind) {
            perSlot[kind] = new int[firstVisible[kind].length];
        }
        for (InstanceTable.ModelCount modelCount : models) {
            // Animated models are drawn from CPU-written commands instead
            if (modelCount.model().isAnimated()) {
                continue;
            }
            for (MeshData mesh : modelCount.model().getMeshDataList()) {
                MeshHandle handle = mesh.getMesh();
                if (handle == null) {
                    continue;
                }
                final int[] slots = perSlot[handle.kind().ordinal()];
                if (handle.slot() < slots.length) {
                    slots[handle.slot()] += modelCount.instanceCount();
                }
            }
        }
        int total = 0;
        for (int kind = 0; kind < firstVisible.length; ++kind) {
            for (int slot = 0; slot < firstVisible[kind].length; ++slot) {
                firstVisible[kind][slot] = total;
                total += perSlot[kind][slot];
            }
        }
        return total;
    }

    /**
     * Read back how many meshes each list drew, and how many were hidden, the last time this
     * frame's buffers were used, then clear the counters for the culling pass.
     *
     * @param frameData This frame's data, which the GPU is done with.
     * @param instances Where to note the counts.
     */
    private static void readCounters(
            @NonNull PerFrameData frameData, @NonNull InstanceTable instances) {
        if (frameData.cullCounters.allocationInfo.size() < (long) COUNTER_COUNT * Integer.BYTES) {
            return;
        }
        IntBuffer counters =
                MemoryUtil.memIntBuffer(
                        frameData.cullCounters.allocationInfo.pMappedData(), COUNTER_COUNT);
        int[] counts = new int[COUNTER_COUNT];
        counters.get(0, counts);
        instances.setDrawnCounts(counts);
        for (int i = 0; i < COUNTER_COUNT; ++i) {
            counters.put(i, 0);
        }
    }

    /**
     * Write the draw command of every mesh slot of one kind for every culled list, with no
     * instances yet. The baked kinds share a mesh table, so each baked slot gets a command in every
     * baked kind's block; culling only adds instances in the block of the mesh's own kind.
     *
     * @param commands The mapped draw command buffer.
     * @param meshes The mesh table the kind lives in.
     * @param kind The kind of mesh.
     * @param standardSlots How many slots the main mesh table has.
     * @param bakedSlots How many slots the baked mesh table has.
     * @param firstVisible Where each mesh slot's room starts in a visible list, by kind.
     * @param listEntries How many visible entries each list has room for.
     */
    private static void writeCulledCommands(
            @NonNull ByteBuffer commands,
            @NonNull MeshRegistry meshes,
            @NonNull MeshKind kind,
            int standardSlots,
            int bakedSlots,
            int @NonNull [] @NonNull [] firstVisible,
            int listEntries) {
        final int slots = slotsOf(kind, standardSlots, bakedSlots);
        final int[] kindFirst = firstVisible[kind.ordinal()];
        meshes.visitAll(
                (slot, resident, generation, vertexOffset, firstIndex, indexCount, min, max) -> {
                    if (slot >= slots) {
                        return;
                    }
                    for (int list = 0; list < LIST_COUNT; ++list) {
                        writeCommand(
                                commands,
                                commandIndex(kind, list, slot, standardSlots, bakedSlots)
                                        * DRAW_COMMAND_SIZE,
                                resident ? indexCount : 0,
                                0,
                                firstIndex,
                                vertexOffset,
                                list * listEntries + kindFirst[slot]);
                    }
                });
    }

    /**
     * Write the frustum planes of each pass, in render space.
     *
     * @param scene The scene.
     * @param frameData This frame's data, with the cascades already worked out.
     * @param disabled Whether culling is turned off, so every plane accepts everything.
     */
    private static void writeFrusta(
            @NonNull Scene scene, @NonNull PerFrameData frameData, boolean disabled) {
        final FloatBuffer planes =
                MemoryUtil.memFloatBuffer(
                        frameData.cullFrusta.allocationInfo.pMappedData(),
                        PASS_COUNT * ShaderBindings.Cull.PLANES_PER_PASS * 4);
        final float[] out = new float[ShaderBindings.Cull.PLANES_PER_PASS * 4];
        if (disabled) {
            FrustumPlanes.acceptAll(out);
            for (int pass = 0; pass < PASS_COUNT; ++pass) {
                planes.put(pass * out.length, out);
            }
            return;
        }
        // The observer may be frozen somewhere else, so its planes move by the difference
        final Observer observer = scene.getObserver();
        final Vector3dc camera = scene.getCamera().getPosition();
        final Matrix4f observerProjectionView =
                new Matrix4f(observer.getProjectionMatrix()).mul(observer.getViewMatrix());
        FrustumPlanes.extract(observerProjectionView, observer.getPosition(), camera, out);
        planes.put(0, out);
        final CascadeShadowSplit[] cascades = frameData.cascadeShadowSplits;
        for (int i = 0; i < cascades.length; ++i) {
            final Matrix4fc cascade = cascades[i].getProjViewMatrix();
            FrustumPlanes.extract(cascade, out);
            planes.put((1 + i) * out.length, out);
        }
    }

    /**
     * Decide whether the scene is culled against the depth pyramid this frame, and whether the
     * pyramid is built from this frame's depth, then write what the occlusion test needs.
     *
     * <p>The pyramid is built every frame unless the observer is frozen, when it keeps the one it
     * saw last so the frozen view keeps culling the same, wherever the camera goes. Until a pyramid
     * has been built, as after a resize, the occlusion test lets everything through.
     *
     * @param scene The scene.
     * @param window The window, whose size the scene is drawn at.
     * @param state The Vulkan state.
     * @param frameData This frame's data.
     * @param visualizers The debug switches.
     */
    private static void writeOcclusionView(
            @NonNull Scene scene,
            @NonNull Window window,
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            @NonNull DebugVisualizers visualizers) {
        final DepthPyramid pyramid = state.depthPyramid;
        frameData.occlusion =
                !visualizers.isCullingDisabled() && !visualizers.isOcclusionDisabled();
        frameData.buildPyramid = frameData.occlusion && !scene.isObserverFrozen();
        if (frameData.buildPyramid) {
            // The same size the scene draws at, the g-buffer can be bigger than the window
            final Observer observer = scene.getObserver();
            pyramid.markBuilt(
                    Math.min(window.getWidth(), frameData.gBuffer.width()),
                    Math.min(window.getHeight(), frameData.gBuffer.height()),
                    observer.getPosition(),
                    new Matrix4f(observer.getProjectionMatrix()).mul(observer.getViewMatrix()));
        }

        final ByteBuffer view =
                MemoryUtil.memByteBuffer(
                        frameData.cullView.allocationInfo.pMappedData(),
                        ShaderBindings.Cull.VIEW_SIZE);
        pyramid.projectionFrom(scene.getCamera().getPosition(), new Matrix4f())
                .get(ShaderBindings.Cull.VIEW_MATRIX_OFFSET, view);
        view.putInt(ShaderBindings.Cull.VIEW_DEPTH_SIZE_OFFSET, pyramid.getDepthWidth());
        view.putInt(
                ShaderBindings.Cull.VIEW_DEPTH_SIZE_OFFSET + Integer.BYTES,
                pyramid.getDepthHeight());
        view.putInt(ShaderBindings.Cull.VIEW_LEVELS_OFFSET, pyramid.getLevels());
        int flags = 0;
        if (frameData.occlusion) {
            flags |= ShaderBindings.Cull.FLAG_OCCLUSION;
        }
        if (pyramid.isValid()) {
            flags |= ShaderBindings.Cull.FLAG_PYRAMID_VALID;
        }
        view.putInt(ShaderBindings.Cull.VIEW_FLAGS_OFFSET, flags);
    }

    /**
     * Write a listed model's visible entries: one group per mesh, one entry per instance.
     *
     * @param info Where the model's data goes.
     * @param modelInstances Its instances.
     * @param visible The mapped visible list, two ints per entry.
     */
    private static void writeVisible(
            @NonNull PerFrameData.ModelDrawInfo info,
            @NonNull ModelInstances modelInstances,
            @NonNull IntBuffer visible) {
        final int count = modelInstances.count;
        for (int mesh = 0; mesh < modelInstances.meshCount; ++mesh) {
            for (int i = 0; i < count; ++i) {
                final int entry = info.firstVisible() + mesh * count + i;
                visible.put(entry * 2, modelInstances.slots[i]);
                visible.put(
                        entry * 2 + 1,
                        modelInstances.materials[i * modelInstances.meshCount + mesh]);
            }
        }
    }

    /**
     * Which pose each instance of an animated model is in.
     *
     * @param matrixOffsets The animation matrix offset of each distinct pose, or -1 for the bind
     *     pose.
     * @param instancePoses The index into the poses for each instance.
     */
    private record Poses(int[] matrixOffsets, int[] instancePoses) {}

    /**
     * Group the instances of an animated model by pose. Instances on the same frame of the same
     * animation end up with identical vertices, so they share one copy, and every instance that
     * isn't animating shares the bind pose.
     *
     * @param modelInstances The animated model's instances.
     * @return The poses.
     */
    private static Poses findPoses(@NonNull ModelInstances modelInstances) {
        Map<Integer, Integer> poseByOffset = new LinkedHashMap<>();
        int[] instancePoses = new int[modelInstances.count];
        for (int i = 0; i < modelInstances.count; ++i) {
            final int offset = modelInstances.animationOffsets[i];
            instancePoses[i] = poseByOffset.computeIfAbsent(offset, ignored -> poseByOffset.size());
        }
        int[] matrixOffsets = new int[poseByOffset.size()];
        poseByOffset.forEach((offset, pose) -> matrixOffsets[pose] = offset);
        return new Poses(matrixOffsets, instancePoses);
    }

    /**
     * Write a listed model's draw commands, one group per mesh. Animated models draw each instance
     * separately from the animation output, which has a copy of the vertices for each pose back to
     * back. Other models are listed for the normal and tangent lines, one instanced draw per mesh
     * from the shared vertex buffer. Meshes that aren't resident draw nothing.
     *
     * @param model The model.
     * @param info Where the model's data goes.
     * @param modelInstances Its instances.
     * @param poses The pose of each instance, null if the model isn't animated.
     * @param meshes Where each mesh is in the shared buffers.
     * @param commands The mapped draw command buffer.
     */
    private static void writeListedCommands(
            @NonNull Model model,
            @NonNull PerFrameData.ModelDrawInfo info,
            @NonNull ModelInstances modelInstances,
            Poses poses,
            @NonNull MeshRegistry meshes,
            @NonNull ByteBuffer commands) {
        final int count = modelInstances.count;
        final List<MeshData> meshList = model.getMeshDataList();
        int position = info.firstCommand() * DRAW_COMMAND_SIZE;
        for (int meshIndex = 0; meshIndex < meshList.size(); ++meshIndex) {
            final MeshData mesh = meshList.get(meshIndex);
            final MeshRegistry.Location location = meshes.locate(mesh.getMesh());
            final int indexCount = location == null ? 0 : mesh.getIndices().length;
            final int firstIndex = location == null ? 0 : location.firstIndex();
            final int meshFirstVisible = info.firstVisible() + meshIndex * count;

            if (poses != null) {
                for (int i = 0; i < count; ++i) {
                    // The vertices of this instance's pose, with this instance's matrix
                    final int vertexOffset = poses.instancePoses()[i] * mesh.getVertexCount();
                    writeCommand(
                            commands,
                            position,
                            indexCount,
                            location == null ? 0 : 1,
                            firstIndex,
                            vertexOffset,
                            meshFirstVisible + i);
                    position += DRAW_COMMAND_SIZE;
                }
            } else {
                writeCommand(
                        commands,
                        position,
                        indexCount,
                        location == null ? 0 : count,
                        firstIndex,
                        location == null ? 0 : location.vertexOffset(),
                        meshFirstVisible);
                position += DRAW_COMMAND_SIZE;
            }
        }
    }

    /**
     * Write one VkDrawIndexedIndirectCommand.
     *
     * @param commands The mapped draw command buffer.
     * @param position Where the command goes, in bytes.
     * @param indexCount The number of indices to draw.
     * @param instanceCount The number of instances.
     * @param firstIndex The first index in the index buffer.
     * @param vertexOffset What is added to each index to find its vertex.
     * @param firstInstance The first instance, which picks the first visible entry.
     */
    private static void writeCommand(
            @NonNull ByteBuffer commands,
            int position,
            int indexCount,
            int instanceCount,
            int firstIndex,
            int vertexOffset,
            int firstInstance) {
        commands.putInt(position, indexCount);
        commands.putInt(position + Integer.BYTES, instanceCount);
        commands.putInt(position + 2 * Integer.BYTES, firstIndex);
        commands.putInt(position + 3 * Integer.BYTES, vertexOffset);
        commands.putInt(position + 4 * Integer.BYTES, firstInstance);
    }
}
