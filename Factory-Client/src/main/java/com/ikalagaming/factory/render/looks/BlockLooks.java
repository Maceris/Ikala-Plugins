package com.ikalagaming.factory.render.looks;

import com.ikalagaming.factory.FactoryClientPlugin;
import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.TextureWrap;
import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.AssetFormat;
import com.ikalagaming.graphics.asset.MeshSection;
import com.ikalagaming.graphics.asset.Section;
import com.ikalagaming.graphics.benchmark.BlockTextures;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.launcher.PluginFolder;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Finds block looks, their models and textures in the namespaced asset layout: {@code
 * assets/<namespace>/looks/block/<id>.kvt}, {@code assets/<namespace>/models/<path>.ika} (meshes
 * exported from the asset editor, see {@link MeshSection}) and {@code
 * assets/<namespace>/textures/<path>.png}. Folders are searched in order and the first that has a
 * file wins, so resource packs can go in front of the base game later.
 *
 * <p>Textures are pixel art, sampled linearly for now, so each is enlarged {@value #SCALE} times on
 * load to keep its pixels sharp. Render thread only.
 */
@Slf4j
public final class BlockLooks implements AutoCloseable {

    /** The folder under each root holding the namespaces. */
    public static final String ASSETS_FOLDER = "assets";

    /** The folder under a namespace holding block looks. */
    public static final String LOOKS_FOLDER = "looks/block";

    /** The folder under a namespace holding models. */
    public static final String MODELS_FOLDER = "models";

    /** The extension of models. */
    public static final String MODEL_EXTENSION = AssetFormat.EXTENSION;

    /** The folder under a namespace holding textures. */
    public static final String TEXTURES_FOLDER = "textures";

    /** The extension of look files. */
    public static final String LOOK_EXTENSION = ".kvt";

    /** The extension of textures. */
    public static final String TEXTURE_EXTENSION = ".png";

    /** The namespace of names that don't give one. */
    public static final String DEFAULT_NAMESPACE = "lotomation";

    /** How many times each texture is enlarged on load. */
    static final int SCALE = 4;

    /** Bytes per pixel. */
    private static final int CHANNELS = 4;

    /** Bits to shift for alpha in a packed color. */
    private static final int ALPHA_SHIFT = 24;

    /** Bits to shift for red in a packed color. */
    private static final int RED_SHIFT = 16;

    /** Bits to shift for green in a packed color. */
    private static final int GREEN_SHIFT = 8;

    /** The graphics to load textures with, or null to only read looks. */
    private final GraphicsContext graphics;

    /** The folders searched, in order. */
    private final List<Path> roots;

    /** Looks by block name; empty for blocks that have none. */
    private final Map<String, Optional<BlockLook>> looks = new HashMap<>();

    /** Models by name; null for ones that couldn't be loaded. */
    private final Map<String, MeshData> models = new HashMap<>();

    /** Textures by name; null for ones that couldn't be loaded. */
    private final Map<String, TextureHandle> textures = new HashMap<>();

    /**
     * Looks from some folders.
     *
     * @param graphics The graphics to load textures with.
     * @param roots The folders to search, in order, each holding an {@value #ASSETS_FOLDER} folder.
     */
    public BlockLooks(@NonNull GraphicsContext graphics, @NonNull List<Path> roots) {
        this.graphics = graphics;
        this.roots = List.copyOf(roots);
    }

    /**
     * The folders the game's looks are in: Factory-Client's data folder.
     *
     * @return The folders, in search order.
     */
    public static List<Path> defaultRoots() {
        final List<Path> out = new ArrayList<>();
        out.add(
                PluginFolder.getResource(
                                FactoryClientPlugin.PLUGIN_NAME, PluginFolder.ResourceType.DATA, "")
                        .toPath());
        return out;
    }

    /**
     * A block's look.
     *
     * @param block The block's name, such as {@code lotomation:stone}.
     * @return The look, or empty if it has none or its file is broken, which is logged once.
     */
    public Optional<BlockLook> look(@NonNull String block) {
        return looks.computeIfAbsent(block, this::readLook);
    }

    /**
     * Read a block's look file.
     *
     * @param block The block's name.
     * @return The look, or empty.
     */
    private Optional<BlockLook> readLook(@NonNull String block) {
        final Optional<Path> file = find(block, LOOKS_FOLDER, LOOK_EXTENSION);
        if (file.isEmpty()) {
            log.debug("{} has no look file", block);
            return Optional.empty();
        }
        try {
            final Optional<BlockLook> look =
                    BlockLook.parse(Files.readString(file.get(), StandardCharsets.UTF_8));
            if (look.isEmpty()) {
                log.warn("The look file {} isn't a KVT tree", file.get());
            }
            return look;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Couldn't read the look file {}", file.get(), e);
            return Optional.empty();
        }
    }

    /**
     * A model's shape, read the first time it is asked for. Callers make their own mesh from it,
     * since each block gives it its own material.
     *
     * @param name The model's name, such as {@code lotomation:block/cube}.
     * @return The shape, with no material, or null if it is missing or can't be read, which is
     *     logged once.
     */
    public MeshData model(@NonNull String name) {
        if (models.containsKey(name)) {
            return models.get(name);
        }
        final MeshData model = readModel(name);
        models.put(name, model);
        return model;
    }

    /**
     * Read a model file.
     *
     * @param name The model's name.
     * @return The shape, or null.
     */
    private MeshData readModel(@NonNull String name) {
        final Optional<Path> file = find(name, MODELS_FOLDER, MODEL_EXTENSION);
        if (file.isEmpty()) {
            log.warn("The model {} wasn't found", name);
            return null;
        }
        try {
            final Optional<Section> mesh = AssetFile.open(file.get()).section(MeshSection.TAG);
            if (mesh.isEmpty()) {
                log.warn("The model {} has no mesh", file.get());
                return null;
            }
            final MeshData model = MeshSection.decode(mesh.get());
            log.debug("Loaded the model {}: {} triangles", name, model.getIndices().length / 3);
            return model;
        } catch (RuntimeException e) {
            log.warn("Couldn't read the model {}", file.get(), e);
            return null;
        }
    }

    /**
     * A texture, loaded the first time it is asked for.
     *
     * @param name The texture's name, such as {@code lotomation:block/stone}.
     * @return The texture, or null if it is missing or can't be read, which is logged once.
     */
    public TextureHandle texture(@NonNull String name) {
        if (textures.containsKey(name)) {
            return textures.get(name);
        }
        final TextureHandle texture = loadTexture(name);
        textures.put(name, texture);
        return texture;
    }

    /**
     * Load a texture.
     *
     * @param name The texture's name.
     * @return The texture, or null.
     */
    private TextureHandle loadTexture(@NonNull String name) {
        final Optional<Path> file = find(name, TEXTURES_FOLDER, TEXTURE_EXTENSION);
        if (file.isEmpty()) {
            log.warn("The texture {} wasn't found", name);
            return null;
        }
        final BufferedImage image;
        try {
            image = ImageIO.read(file.get().toFile());
        } catch (IOException e) {
            log.warn("Couldn't read the texture {}", file.get(), e);
            return null;
        }
        if (image == null || image.getWidth() != image.getHeight()) {
            log.warn("The texture {} must be a square image", file.get());
            return null;
        }
        final int size = image.getWidth();
        final byte[] pixels = BlockTextures.enlarge(rgba(image), size, SCALE);
        final ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
        try {
            buffer.put(pixels).flip();
            return graphics.textures()
                    .load(
                            buffer,
                            Format.R8G8B8A8_SRGB,
                            size * SCALE,
                            size * SCALE,
                            TextureWrap.REPEAT);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * An image's pixels, rows from the top, red, green, blue and alpha bytes each.
     *
     * @param image The image.
     * @return The pixels.
     */
    static byte[] rgba(@NonNull BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final byte[] out = new byte[width * height * CHANNELS];
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final int argb = image.getRGB(x, y);
                final int at = (y * width + x) * CHANNELS;
                out[at] = (byte) (argb >> RED_SHIFT);
                out[at + 1] = (byte) (argb >> GREEN_SHIFT);
                out[at + 2] = (byte) argb;
                out[at + 3] = (byte) (argb >> ALPHA_SHIFT);
            }
        }
        return out;
    }

    /**
     * Find a namespaced file in the first folder that has it.
     *
     * @param name The name, such as {@code lotomation:stone}; with no namespace, {@value
     *     #DEFAULT_NAMESPACE}.
     * @param folder The folder under the namespace.
     * @param extension The file's extension.
     * @return The file, or empty if no folder has it.
     */
    private Optional<Path> find(
            @NonNull String name, @NonNull String folder, @NonNull String extension) {
        final String relative = relativePath(name, folder, extension);
        if (relative == null) {
            log.warn("{} isn't a valid name", name);
            return Optional.empty();
        }
        for (Path root : roots) {
            final Path file = root.resolve(relative);
            if (Files.isRegularFile(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /**
     * Where a namespaced file is, under a root.
     *
     * @param name The name, with or without a namespace.
     * @param folder The folder under the namespace.
     * @param extension The file's extension.
     * @return The path, with forward slashes, or null if the name could leave the folder.
     */
    static String relativePath(
            @NonNull String name, @NonNull String folder, @NonNull String extension) {
        final int colon = name.indexOf(':');
        final String namespace = colon < 0 ? DEFAULT_NAMESPACE : name.substring(0, colon);
        final String path = name.substring(colon + 1);
        if (namespace.isEmpty()
                || path.isEmpty()
                || !namespace.matches("[a-z0-9_.-]+")
                || !path.matches("[a-z0-9_.-]+(/[a-z0-9_.-]+)*")
                || path.contains("..")) {
            return null;
        }
        return ASSETS_FOLDER + "/" + namespace + "/" + folder + "/" + path + extension;
    }

    /** Release every texture. */
    @Override
    public void close() {
        if (!graphics.isClosed()) {
            textures.values().forEach(graphics.textures()::release);
        }
        textures.clear();
        models.clear();
        looks.clear();
    }
}
