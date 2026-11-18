package com.ikalagaming.converter;

import com.ikalagaming.converter.scene.ImportedScene;
import com.ikalagaming.converter.scene.ImportedScene.ImportedMaterial;
import com.ikalagaming.converter.scene.ImportedScene.TextureRef;
import com.ikalagaming.converter.scene.ImportedScene.TextureSlot;
import com.ikalagaming.converter.scene.SceneImporter;
import com.ikalagaming.converter.scene.ScenePlacement;
import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.exceptions.ModelException;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Loads a model file as one {@link Model}, every mesh baked into the file's world space. Reading is
 * {@link SceneImporter}'s, the only Assimp reader, so models get its checks and fixes; this only
 * flattens the scene and makes its materials, loading their base color and normal textures.
 */
@Slf4j
public class ModelConverter {

    /** Where baked meshes are measured from: the file's own origin. */
    private static final Vector3d FILE_ORIGIN = new Vector3d();

    /**
     * Information needed to load a model.
     *
     * @param modelId The ID to supply the model.
     * @param pluginName The plugin that contains the model.
     * @param modelPath The path to the model from the resource directory.
     * @param materialCache The material cache the model's materials are added to.
     * @param animation Whether we want to set up vertices for animation. Not supported: models are
     *     read without bones or animations, and asking for them is logged and ignored.
     */
    public record ModelLoadRequest(
            @NonNull String modelId,
            @NonNull String pluginName,
            @NonNull String modelPath,
            @NonNull MaterialCache materialCache,
            boolean animation) {}

    /**
     * Load a model right now and return it.
     *
     * @param request Data required to load the model.
     * @return The newly loaded model, its materials in the request's cache.
     * @throws ModelException If the file is missing or can't be read.
     */
    public static Model loadModel(@NonNull ModelConverter.ModelLoadRequest request) {
        File file =
                PluginFolder.getResource(
                        request.pluginName(), PluginFolder.ResourceType.DATA, request.modelPath());
        if (!file.exists()) {
            final String error =
                    SafeResourceLoader.format(
                            "Model path does not exist [{}]", file.getAbsolutePath());
            log.info(error);
            throw new ModelException(error);
        }
        if (request.animation()) {
            log.warn(
                    "Model {} was asked for with animation, which isn't supported; loading it"
                            + " without",
                    request.modelId());
        }

        final ImportedScene imported;
        try {
            imported = SceneImporter.read(file.toPath(), message -> {});
        } catch (IOException e) {
            final String error =
                    SafeResourceLoader.format(
                            "Error loading model [{}]: {}", file.getAbsolutePath(), e.getMessage());
            log.info(error);
            throw new ModelException(error, e);
        }
        for (String warning : imported.warnings()) {
            log.debug("Model {}: {}", request.modelId(), warning);
        }

        final Textures textures = GraphicsManager.forPlugin(request.pluginName()).textures();
        final List<Material> materials = new ArrayList<>();
        for (ImportedMaterial source : imported.materials()) {
            final Material material = material(source, textures);
            request.materialCache().addMaterial(material);
            materials.add(material);
        }

        final Model result = new Model(request.modelId());
        // Every node's meshes, in world space, as the file arranges them
        final Deque<ImportedScene.Node> nodes = new ArrayDeque<>();
        nodes.push(imported.root());
        while (!nodes.isEmpty()) {
            final ImportedScene.Node node = nodes.pop();
            for (int index : node.meshes()) {
                final ImportedScene.Mesh mesh = imported.meshes().get(index);
                final MeshData baked =
                        ScenePlacement.bakeMesh(mesh.data(), node.world(), FILE_ORIGIN);
                baked.setMaterial(materialFor(mesh.material(), materials, request));
                result.getMeshDataList().add(baked);
            }
            // Pushed in reverse, so meshes come out in the file's order
            final List<ImportedScene.Node> children = node.children();
            for (int i = children.size() - 1; i >= 0; --i) {
                nodes.push(children.get(i));
            }
        }
        return result;
    }

    /**
     * A mesh's material, or a default one added to the cache if the file gave it none.
     *
     * @param index The mesh's material index.
     * @param materials The file's materials.
     * @param request The request, whose cache a default goes in.
     * @return The material.
     */
    private static Material materialFor(
            int index, @NonNull List<Material> materials, @NonNull ModelLoadRequest request) {
        if (index >= 0 && index < materials.size()) {
            return materials.get(index);
        }
        final Material fallback = new Material();
        request.materialCache().addMaterial(fallback);
        materials.add(fallback);
        return fallback;
    }

    /**
     * Make a material from what the file says, with its base color and normal textures loaded.
     *
     * @param source The imported material.
     * @param textures Where the model's plugin loads textures.
     * @return The material.
     */
    private static Material material(@NonNull ImportedMaterial source, @NonNull Textures textures) {
        final Material material = ScenePlacement.material(source);
        final TextureRef base = source.textures().get(TextureSlot.BASE_COLOR);
        if (base != null && base.found() != null) {
            material.setTexture(textures.load(base.found().toString()));
        }
        final TextureRef normal = source.textures().get(TextureSlot.NORMAL);
        if (normal != null && normal.found() != null) {
            material.setNormalMap(textures.load(normal.found().toString(), ColorSpace.LINEAR));
        }
        return material;
    }

    /** Static methods only. */
    private ModelConverter() {}
}
