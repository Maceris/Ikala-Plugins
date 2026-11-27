package com.ikalagaming.converter.content;

import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.registry.ContentFolders;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;

/**
 * Reads blocks' looks from the game's content folders, the way the game does, as files: a look
 * names a model, a texture and a material by ID, and each ID is a file in its kind's folder. The
 * folders are the installed content plugins' and, since the editor doesn't load the game's content
 * itself, the folder of every look file opened. Uses Factory-Core's classes, so only touch it when
 * {@link GameContent#available()}. Safe from any thread.
 */
@Slf4j
public final class BlockContent {
    /** Where looks of blocks are, in a content folder. */
    public static final String LOOKS = "looks/block";

    /** A look file's extension. */
    public static final String LOOK_EXTENSION = ".kvt";

    /** Where models are. */
    public static final String MODELS = "models";

    /** Where render materials are. */
    public static final String MATERIALS = "render_materials";

    /** Where textures are. */
    public static final String TEXTURES = "textures";

    /** A model's or material's extension: a container. */
    public static final String CONTAINER_EXTENSION = ".ika";

    /** A texture's extension. */
    public static final String TEXTURE_EXTENSION = ".png";

    /** What a normal map's name adds before the extension. */
    public static final String NORMAL_SUFFIX = ".normal";

    /** The model a look gets when it doesn't name one: a cube with the texture on every face. */
    public static final String DEFAULT_MODEL = "lotomation:block/cube_all";

    /** The model name a fluid's look has, whose shape is made in code. */
    public static final String FLUID = "fluid";

    /** Folders learned from files opened, beyond the installed ones. */
    private static final List<Path> OPENED = new CopyOnWriteArrayList<>();

    /** Not made. */
    private BlockContent() {}

    /**
     * A block's look: what it is drawn with.
     *
     * @param id The block's ID, like {@code lotomation:grass}.
     * @param model The model's ID.
     * @param texture The texture's ID, or null for none.
     * @param material The render material's ID, or null for the default one.
     */
    public record Look(
            @NonNull String id,
            @NonNull String model,
            @Nullable String texture,
            @Nullable String material) {}

    /**
     * The content folders, in load order: the installed ones, then those of files opened.
     *
     * @return The folders.
     */
    public static List<Path> folders() {
        final List<Path> folders = new ArrayList<>(ContentFolders.installed());
        for (Path folder : OPENED) {
            if (!folders.contains(folder)) {
                folders.add(folder);
            }
        }
        return folders;
    }

    /**
     * The content folder a file is in, and the file's ID in it, learning the folder for later
     * lookups.
     *
     * @param file A file in a kind's folder, like {@code .../data/looks/block/grass.kvt}.
     * @param kind The kind's folder, like {@value #LOOKS}.
     * @param extension The kind's extension.
     * @return The ID, or empty if the file isn't in a content folder's kind folder.
     */
    public static Optional<String> idOf(
            @NonNull Path file, @NonNull String kind, @NonNull String extension) {
        final Path absolute = file.toAbsolutePath().normalize();
        final String name = absolute.getFileName().toString();
        if (!name.endsWith(extension)) {
            return Optional.empty();
        }
        // Climb to the kind's folder, then its content folder
        final String[] kindParts = kind.split("/");
        Path at = absolute.getParent();
        final List<String> path = new ArrayList<>();
        path.add(name.substring(0, name.length() - extension.length()));
        while (at != null) {
            if (endsWith(at, kindParts)) {
                Path folder = at;
                for (int i = 0; i < kindParts.length; ++i) {
                    folder = folder.getParent();
                }
                if (folder == null) {
                    return Optional.empty();
                }
                final Optional<String> namespace = ContentFolders.namespace(folder);
                if (namespace.isEmpty()) {
                    return Optional.empty();
                }
                final String id = namespace.get() + ":" + String.join("/", path);
                if (!ContentFolders.isValidId(id)) {
                    return Optional.empty();
                }
                if (!OPENED.contains(folder)) {
                    OPENED.add(folder);
                }
                return Optional.of(id);
            }
            if (at.getFileName() == null) {
                // The root, past every folder
                return Optional.empty();
            }
            path.addFirst(at.getFileName().toString());
            at = at.getParent();
        }
        return Optional.empty();
    }

    /**
     * Whether a folder's path ends with some names.
     *
     * @param folder The folder.
     * @param parts The names, outermost first.
     * @return True if its last names are those.
     */
    private static boolean endsWith(Path folder, String[] parts) {
        Path at = folder;
        for (int i = parts.length - 1; i >= 0; --i) {
            if (at == null || at.getFileName() == null) {
                return false;
            }
            if (!at.getFileName().toString().equals(parts[i])) {
                return false;
            }
            at = at.getParent();
        }
        return true;
    }

    /**
     * Read a look file.
     *
     * @param file The file.
     * @return The look, with its ID from where the file is, or empty if it can't be read.
     */
    public static Optional<Look> readLook(@NonNull Path file) {
        final Optional<String> id = idOf(file, LOOKS, LOOK_EXTENSION);
        try {
            final String text = Files.readString(file);
            return parse(id.orElse(file.getFileName().toString()), text);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Could not read the look {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * A block's look, by the block's ID.
     *
     * @param blockId The ID, like {@code lotomation:grass}.
     * @return The look, or empty if no content folder has it.
     */
    public static Optional<Look> look(@NonNull String blockId) {
        return ContentFolders.find(folders(), LOOKS, blockId, LOOK_EXTENSION)
                .flatMap(BlockContent::readLook);
    }

    /**
     * Read a look's text.
     *
     * @param id The block's ID.
     * @param text The KVT text.
     * @return The look, or empty if the text isn't a KVT tree.
     * @throws IllegalArgumentException If a value isn't text.
     */
    public static Optional<Look> parse(@NonNull String id, @NonNull String text) {
        return TreeStringSerialization.fromString(text)
                .map(
                        root -> {
                            final String model = text(root, "model");
                            return new Look(
                                    id,
                                    model == null || FLUID.equals(model) ? DEFAULT_MODEL : model,
                                    text(root, "texture"),
                                    text(root, "material"));
                        });
    }

    /**
     * Text in a tree.
     *
     * @param node The tree.
     * @param key The key.
     * @return The text, or null if it is missing.
     * @throws IllegalArgumentException If it isn't text.
     */
    private static String text(Node node, String key) {
        final Object value = node.hasChild(key) ? node.get(key) : null;
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException(key + " must be text");
    }

    /**
     * A look's model file.
     *
     * @param look The look.
     * @return The file, or empty if no content folder has it.
     */
    public static Optional<Path> modelFile(@NonNull Look look) {
        return ContentFolders.find(folders(), MODELS, look.model(), CONTAINER_EXTENSION);
    }

    /**
     * A look's texture file.
     *
     * @param look The look.
     * @return The file, or empty if it has none or no content folder has it.
     */
    public static Optional<Path> textureFile(@NonNull Look look) {
        return look.texture() == null
                ? Optional.empty()
                : ContentFolders.find(folders(), TEXTURES, look.texture(), TEXTURE_EXTENSION);
    }

    /**
     * A look's normal map, beside its texture.
     *
     * @param look The look.
     * @return The file, or empty if there is none.
     */
    public static Optional<Path> normalFile(@NonNull Look look) {
        return textureFile(look)
                .map(
                        texture -> {
                            final String name = texture.getFileName().toString();
                            return texture.resolveSibling(
                                    name.substring(0, name.length() - TEXTURE_EXTENSION.length())
                                            + NORMAL_SUFFIX
                                            + TEXTURE_EXTENSION);
                        })
                .filter(Files::isRegularFile);
    }

    /**
     * A look's render material file.
     *
     * @param look The look.
     * @return The file, or empty if it names none or no content folder has it.
     */
    public static Optional<Path> materialFile(@NonNull Look look) {
        return look.material() == null
                ? Optional.empty()
                : ContentFolders.find(folders(), MATERIALS, look.material(), CONTAINER_EXTENSION);
    }
}
