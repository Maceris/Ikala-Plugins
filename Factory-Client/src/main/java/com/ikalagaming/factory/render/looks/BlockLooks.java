package com.ikalagaming.factory.render.looks;

import com.ikalagaming.factory.registry.ContentFolders;
import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.GraphicsContext;
import com.ikalagaming.graphics.TextureFilter;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.TextureWrap;
import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.AssetFormat;
import com.ikalagaming.graphics.asset.MaterialSection;
import com.ikalagaming.graphics.asset.MeshSection;
import com.ikalagaming.graphics.asset.Section;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Finds block looks, their models and textures in plugins' content folders ({@link
 * ContentFolders}): {@code looks/block/<id>.kvt}, {@code models/<path>.ika} (meshes exported from
 * the asset editor, see {@link MeshSection}), {@code render_materials/<path>.ika} (see {@link
 * MaterialSection}) and {@code textures/<path>.png}, each in the folder of the plugin whose
 * namespace the name has. A later plugin's {@code overrides/<namespace>/} wins over the plugin's
 * own file, and resource packs will go in front of them later.
 *
 * <p>Textures are pixel art, sampled so their pixels stay sharp up close, of any size: a model's
 * texture coordinates decide which part of it each face shows. Render thread only.
 */
@Slf4j
public final class BlockLooks implements AutoCloseable {

    /** The folder under a content folder holding block looks. */
    public static final String LOOKS_FOLDER = "looks/block";

    /** The folder under a content folder holding models. */
    public static final String MODELS_FOLDER = "models";

    /** The extension of models. */
    public static final String MODEL_EXTENSION = AssetFormat.EXTENSION;

    /**
     * The folder under a content folder holding render materials: how surfaces reflect light, as
     * opposed to the gameplay materials in {@code materials}.
     */
    public static final String MATERIALS_FOLDER = "render_materials";

    /** The extension of materials. */
    public static final String MATERIAL_EXTENSION = AssetFormat.EXTENSION;

    /** The folder under a content folder holding textures. */
    public static final String TEXTURES_FOLDER = "textures";

    /** The extension of look files. */
    public static final String LOOK_EXTENSION = ".kvt";

    /** The extension of textures. */
    public static final String TEXTURE_EXTENSION = ".png";

    /** The namespace of names that don't give one. */
    public static final String DEFAULT_NAMESPACE = "lotomation";

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

    /** The content folders searched, in load order. */
    private final List<Path> roots;

    /** Looks by block name; empty for blocks that have none. */
    private final Map<String, Optional<BlockLook>> looks = new HashMap<>();

    /** Models by name; null for ones that couldn't be loaded. */
    private final Map<String, MeshData> models = new HashMap<>();

    /** Materials by name; null for ones that couldn't be read. */
    private final Map<String, Material> materials = new HashMap<>();

    /** Textures by name; null for ones that couldn't be loaded. */
    private final Map<String, TextureHandle> textures = new HashMap<>();

    /** Normal maps by their texture's name; null for textures that have none. */
    private final Map<String, TextureHandle> normalMaps = new HashMap<>();

    /** The images textures were loaded from, by name, for working out what parts of them show. */
    private final Map<String, BufferedImage> images = new HashMap<>();

    /**
     * Looks from some folders.
     *
     * @param graphics The graphics to load textures with.
     * @param roots The content folders to search, in load order.
     */
    public BlockLooks(@NonNull GraphicsContext graphics, @NonNull List<Path> roots) {
        this.graphics = graphics;
        this.roots = List.copyOf(roots);
    }

    /**
     * The folders the game's looks are in: the installed plugins' content folders.
     *
     * @return The folders, in load order.
     */
    public static List<Path> defaultRoots() {
        return ContentFolders.installed();
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
     * A material, read the first time it is asked for. Callers copy its values into their own
     * materials, since each face has its own texture.
     *
     * @param name The material's name, such as {@code lotomation:block/stone}.
     * @return The material, with no textures, or null if it is missing or can't be read, which is
     *     logged once.
     */
    public Material material(@NonNull String name) {
        if (materials.containsKey(name)) {
            return materials.get(name);
        }
        final Material material = readMaterial(name);
        materials.put(name, material);
        return material;
    }

    /**
     * Read a material file. Its texture names aren't used yet: a look's textures give each face its
     * base color.
     *
     * @param name The material's name.
     * @return The material, or null.
     */
    private Material readMaterial(@NonNull String name) {
        final Optional<Path> file = find(name, MATERIALS_FOLDER, MATERIAL_EXTENSION);
        if (file.isEmpty()) {
            log.warn("The material {} wasn't found", name);
            return null;
        }
        try {
            return MaterialSection.read(AssetFile.open(file.get())).material();
        } catch (RuntimeException e) {
            log.warn("Couldn't read the material {}", file.get(), e);
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
     * A texture's normal map, {@code <name>.normal.png} beside it, loaded the first time it is
     * asked for. See {@link BlockNormals} for making them.
     *
     * @param name The texture's name, such as {@code lotomation:block/stone}.
     * @return The normal map, or null if the texture has none or it can't be read.
     */
    public TextureHandle normalMap(@NonNull String name) {
        if (normalMaps.containsKey(name)) {
            return normalMaps.get(name);
        }
        final String mapName = name + BlockNormals.NORMAL_SUFFIX;
        TextureHandle map = null;
        if (find(mapName, TEXTURES_FOLDER, TEXTURE_EXTENSION).isPresent()) {
            map = loadImage(mapName, Format.R8G8B8A8_UNORM);
        }
        normalMaps.put(name, map);
        return map;
    }

    /**
     * The image a texture was loaded from, loading the texture if it hasn't been.
     *
     * @param name The texture's name.
     * @return The image, or null if it is missing or can't be read.
     */
    public BufferedImage image(@NonNull String name) {
        texture(name);
        return images.get(name);
    }

    /**
     * Load a texture.
     *
     * @param name The texture's name.
     * @return The texture, or null.
     */
    private TextureHandle loadTexture(@NonNull String name) {
        return loadImage(name, Format.R8G8B8A8_SRGB);
    }

    /**
     * Load an image from the textures folder, pixel-sharp.
     *
     * @param name The image's name, without the extension.
     * @param format How its values are read: sRGB for colors, UNORM for data like normals.
     * @return The texture, or null.
     */
    private TextureHandle loadImage(@NonNull String name, @NonNull Format format) {
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
        if (image == null) {
            log.warn("The texture {} isn't an image", file.get());
            return null;
        }
        if (format == Format.R8G8B8A8_SRGB) {
            images.put(name, image);
        }
        final byte[] pixels = rgba(image);
        final ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
        try {
            buffer.put(pixels).flip();
            // Clamped, since a model's faces take parts of it that don't wrap into each other
            return graphics.textures()
                    .load(
                            buffer,
                            format,
                            image.getWidth(),
                            image.getHeight(),
                            TextureWrap.CLAMP,
                            TextureFilter.PIXELS);
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
     * Find a namespaced file in this one's folders.
     *
     * @param name The name, such as {@code lotomation:stone}; with no namespace, {@value
     *     #DEFAULT_NAMESPACE}.
     * @param folder The kind's folder.
     * @param extension The file's extension.
     * @return The file, or empty if no folder has it.
     */
    private Optional<Path> find(
            @NonNull String name, @NonNull String folder, @NonNull String extension) {
        return find(roots, name, folder, extension);
    }

    /**
     * Find a namespaced file in some content folders: the last override of it, or else the file in
     * its own plugin's folder.
     *
     * @param roots The content folders, in load order.
     * @param name The name, such as {@code lotomation:stone}; with no namespace, {@value
     *     #DEFAULT_NAMESPACE}.
     * @param folder The kind's folder.
     * @param extension The file's extension.
     * @return The file, or empty if no folder has it or the name isn't valid, which is logged.
     */
    static Optional<Path> find(
            @NonNull List<Path> roots,
            @NonNull String name,
            @NonNull String folder,
            @NonNull String extension) {
        final String id = qualified(name);
        if (id == null) {
            log.warn("{} isn't a valid name", name);
            return Optional.empty();
        }
        return ContentFolders.find(roots, folder, id, extension);
    }

    /**
     * A name with its namespace.
     *
     * @param name The name, with or without a namespace.
     * @return The name with {@value #DEFAULT_NAMESPACE} if it gave none, or null if it isn't a
     *     valid name, such as one that could leave its folder.
     */
    static String qualified(@NonNull String name) {
        final String id = name.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + name : name;
        return ContentFolders.isValidId(id) && !id.contains("..") ? id : null;
    }

    /** Release every texture. */
    @Override
    public void close() {
        if (!graphics.isClosed()) {
            textures.values().forEach(graphics.textures()::release);
            normalMaps.values().forEach(graphics.textures()::release);
        }
        textures.clear();
        normalMaps.clear();
        images.clear();
        models.clear();
        materials.clear();
        looks.clear();
    }
}
