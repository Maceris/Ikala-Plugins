package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.exceptions.ModelException;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.launcher.PluginFolder.ResourceType;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Utility class for loading in models from file. */
@Slf4j
public class ModelLoader {

    /**
     * Information needed to loadBindless a model.
     *
     * @param modelId The ID to supply the model.
     * @param pluginName The plugin that contains the model.
     * @param modelPath The path to the model from the resource directory.
     * @param materialCache The material cache so we can reuse materials.
     * @param animation Whether we want to set up vertices for animation.
     */
    public record ModelLoadRequest(
            @NonNull String modelId,
            @NonNull String pluginName,
            @NonNull String modelPath,
            @NonNull MaterialCache materialCache,
            boolean animation) {}

    /**
     * Lists of weights and bones that affect the vertices, ordered by vertex ID. Each vertex has
     * {@link com.ikalagaming.graphics.graph.MeshData#MAX_WEIGHTS} entries in each list, and if
     * there are less than that number of bones affecting the vertex, the remaining positions are
     * filled with zeroes.
     *
     * <p>Each index in the weights list is the weight corresponding to the bone ID in the bone ID
     * list at the same position.
     *
     * @param weights The weight list.
     * @param boneIDs The bone ID list.
     */
    public record AnimMeshData(float[] weights, int[] boneIDs) {
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AnimMeshData other)) {
                return false;
            }
            return Arrays.equals(weights, other.weights) && Arrays.equals(boneIDs, other.boneIDs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(weights), Arrays.hashCode(boneIDs));
        }

        @Override
        public String toString() {
            return String.format(
                    "AnimMeshData[weights=%s, boneIDs=%s]",
                    Arrays.toString(weights), Arrays.toString(boneIDs));
        }
    }

    /**
     * A bone for animation.
     *
     * @param boneID The ID of the bone.
     * @param boneName The name of the bone.
     * @param offsetMatrix The offset matrix for the bone.
     */
    private record Bone(int boneID, String boneName, Matrix4f offsetMatrix) {}

    /**
     * A mapping of how much a specific bone affects a specific vertex.
     *
     * @param boneID The ID of the bone.
     * @param vertexID The ID of the vertex.
     * @param weight The weight of the bone on the vertex.
     */
    private record VertexWeight(int boneID, int vertexID, float weight) {}

    /** The list of models we have queued up to loadBindless. */
    private static final Deque<ModelLoadRequest> loadQueue = new ConcurrentLinkedDeque<>();

    /** The maximum number of bones that are allowed in a model. */
    public static final int MAX_BONES = 256;

    /** Load a model from the model loading queue. Does nothing if the queue is empty. */
    public static void loadModel() {
        ModelLoadRequest request = ModelLoader.loadQueue.poll();
        if (request == null) {
            return;
        }
        Model loaded = ModelLoader.loadModel(request);
        // TODO(ches) set up animations
        GraphicsManager.getRenderInstance().initializeModel(loaded);
        GraphicsManager.getScene().addModel(loaded);
    }

    /**
     * Load a model right now and return it.
     *
     * @param request Data required to loadBindless the model.
     * @return The newly loaded model.
     */
    public static Model loadModel(@NonNull ModelLoadRequest request) {
        File file =
                PluginFolder.getResource(
                        request.pluginName(), ResourceType.DATA, request.modelPath());
        String fixedPath = file.getAbsolutePath();

        if (!file.exists()) {

            String error =
                    SafeResourceLoader.getStringFormatted(
                            "MODEL_PATH_MISSING",
                            GraphicsPlugin.getResourceBundle(),
                            file.getAbsolutePath());
            log.info(error);
            throw new ModelException(error);
        }
        String modelDir = file.getParent();
        // TODO(ches) actually loadBindless the mesh data
        return new Model(request.modelId());
    }

    /**
     * Put in a request to loadBindless a model later, on the main thread.
     *
     * @param request The details about what model to loadBindless.
     */
    public static void loadModelLater(ModelLoadRequest request) {
        ModelLoader.loadQueue.add(request);
    }

    /** Private constructor to prevent instantiation. */
    private ModelLoader() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
