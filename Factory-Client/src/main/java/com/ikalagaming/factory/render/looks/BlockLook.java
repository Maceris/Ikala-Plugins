package com.ikalagaming.factory.render.looks;

import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;

import lombok.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * What a block looks like, read from its look file: its model, the textures on its faces and the
 * surface values that apply where there is no map for them. See the Block Looks design doc.
 *
 * <p>A look file is text KVT, such as {@code {model:"lotomation:block/cube",
 * textures:{top:"lotomation:block/grass_top", side:"lotomation:block/grass_side",
 * bottom:"lotomation:block/dirt"}}}. Model and texture names are namespaced paths under the
 * namespace's {@code models} and {@code textures} folders, without the extension. {@code all} sets
 * every face, and {@code top}, {@code bottom} and {@code side} override it, by which way a face
 * points in the world.
 *
 * @param model The model's name, or {@value #FLUID} for a fluid, whose shape is made in code since
 *     it follows the fluid's level.
 * @param side The texture on its four sides, or null for none.
 * @param top The texture on its top, or null for none.
 * @param bottom The texture on its bottom, or null for none.
 * @param roughness How rough it is, from 0 (polished) to 1.
 * @param metallic How metallic it is, from 0 to 1.
 * @param emission How brightly it glows in its own color, 0 for not at all.
 * @param transparency How it lets light through.
 */
public record BlockLook(
        @NonNull String model,
        String side,
        String top,
        String bottom,
        float roughness,
        float metallic,
        float emission,
        @NonNull Transparency transparency) {

    /** The key for the model. */
    public static final String KEY_MODEL = "model";

    /** The key for the textures. */
    public static final String KEY_TEXTURES = "textures";

    /** The texture key for every face. */
    public static final String FACE_ALL = "all";

    /** The texture key for the four sides. */
    public static final String FACE_SIDE = "side";

    /** The texture key for the top. */
    public static final String FACE_TOP = "top";

    /** The texture key for the bottom. */
    public static final String FACE_BOTTOM = "bottom";

    /** The key for roughness. */
    public static final String KEY_ROUGHNESS = "roughness";

    /** The key for how metallic it is. */
    public static final String KEY_METALLIC = "metallic";

    /** The key for emission. */
    public static final String KEY_EMISSION = "emission";

    /** The key for transparency. */
    public static final String KEY_TRANSPARENCY = "transparency";

    /** Roughness when a look doesn't say: a matte surface. */
    public static final float DEFAULT_ROUGHNESS = 0.85f;

    /** The model of a fluid, whose shape is made in code. */
    public static final String FLUID = "fluid";

    /** The model when a look doesn't say: a full block. */
    public static final String DEFAULT_MODEL = "lotomation:block/cube";

    /**
     * Whether it is a fluid.
     *
     * @return True if its model is {@value #FLUID}.
     */
    public boolean isFluid() {
        return FLUID.equals(model);
    }

    /** How a block lets light through. */
    public enum Transparency {
        /** Solid. */
        OPAQUE,
        /** Holes where the texture's alpha is low, like leaves. */
        CUTOUT,
        /** See-through by the texture's alpha, like glass or water. */
        TRANSLUCENT
    }

    /**
     * Read a look file.
     *
     * @param text The file's text.
     * @return The look, or empty if the text isn't a KVT tree.
     * @throws IllegalArgumentException If a value is of the wrong kind or unknown, such as a model
     *     that doesn't exist.
     */
    public static Optional<BlockLook> parse(@NonNull String text) {
        return TreeStringSerialization.fromString(text).map(BlockLook::fromTree);
    }

    /**
     * Read a look from its tree.
     *
     * @param root The tree.
     * @return The look.
     * @throws IllegalArgumentException If a value is of the wrong kind or unknown.
     */
    private static BlockLook fromTree(@NonNull Node root) {
        final String named = text(root, KEY_MODEL);
        final String model = named == null ? DEFAULT_MODEL : named;
        String all = null;
        String side = null;
        String top = null;
        String bottom = null;
        if (root.hasChild(KEY_TEXTURES)) {
            if (!(value(root, KEY_TEXTURES) instanceof Node textures)) {
                throw new IllegalArgumentException(KEY_TEXTURES + " must be a tree");
            }
            all = text(textures, FACE_ALL);
            side = text(textures, FACE_SIDE);
            top = text(textures, FACE_TOP);
            bottom = text(textures, FACE_BOTTOM);
        }
        return new BlockLook(
                model,
                side == null ? all : side,
                top == null ? all : top,
                bottom == null ? all : bottom,
                number(root, KEY_ROUGHNESS, DEFAULT_ROUGHNESS),
                number(root, KEY_METALLIC, 0),
                number(root, KEY_EMISSION, 0),
                choice(
                        Transparency.class,
                        text(root, KEY_TRANSPARENCY),
                        FLUID.equals(model) ? Transparency.TRANSLUCENT : Transparency.OPAQUE));
    }

    /**
     * A value in a tree, or null if it is missing, without the tree's warning.
     *
     * @param node The tree.
     * @param key The key, with no periods.
     * @return The value, or null.
     */
    private static Object value(@NonNull Node node, @NonNull String key) {
        return node.hasChild(key) ? node.get(key) : null;
    }

    /**
     * Text in a tree.
     *
     * @param node The tree.
     * @param key The key.
     * @return The text, or null if it is missing.
     * @throws IllegalArgumentException If it isn't text.
     */
    private static String text(@NonNull Node node, @NonNull String key) {
        final Object value = value(node, key);
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException(key + " must be text");
    }

    /**
     * A number in a tree.
     *
     * @param node The tree.
     * @param key The key.
     * @param fallback The number if it is missing.
     * @return The number.
     * @throws IllegalArgumentException If it isn't a number.
     */
    private static float number(@NonNull Node node, @NonNull String key, float fallback) {
        final Object value = value(node, key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.floatValue();
        }
        throw new IllegalArgumentException(key + " must be a number");
    }

    /**
     * One of an enum's values, named in lower case.
     *
     * @param type The enum.
     * @param name The name, or null.
     * @param fallback The value if the name is null.
     * @param <E> The enum.
     * @return The value.
     * @throws IllegalArgumentException If no value has that name.
     */
    private static <E extends Enum<E>> E choice(
            @NonNull Class<E> type, String name, @NonNull E fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown " + type.getSimpleName().toLowerCase(Locale.ROOT) + " " + name, e);
        }
    }
}
