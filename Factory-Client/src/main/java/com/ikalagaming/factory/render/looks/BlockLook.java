package com.ikalagaming.factory.render.looks;

import com.ikalagaming.factory.kvt.KvtSchemas;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.registry.ContentSchemas;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * What a block looks like, read from its look file: a model, the one texture its texture
 * coordinates take their colors from, and the material over it. See the Block Looks design doc.
 *
 * <p>A look file is text KVT, such as {@code {model:"lotomation:block/cube_bottom_top",
 * texture:"lotomation:block/grass", material:"lotomation:block/grass"}}. Names are namespaced paths
 * under the namespace's {@code models}, {@code textures} and {@code materials} folders, without the
 * extension. Which part of the texture each face shows is the model's business, so a stair or a
 * lectern needs nothing more than a cube does.
 *
 * @param model The model's name, or {@value #FLUID} for a fluid, whose shape is made in code since
 *     it follows the fluid's level.
 * @param texture The texture's name, or null for none.
 * @param material The material's name, or null for the default matte one.
 */
@Slf4j
public record BlockLook(@NonNull String model, String texture, String material) {

    /** The key for the model. */
    public static final String KEY_MODEL = "model";

    /** The key for the texture. */
    public static final String KEY_TEXTURE = "texture";

    /** The key for the material. */
    public static final String KEY_MATERIAL = "material";

    /** The model of a fluid, whose shape is made in code. */
    public static final String FLUID = ContentSchemas.FLUID_MODEL;

    /** The model when a look doesn't say: a full block with the whole texture on every face. */
    public static final String DEFAULT_MODEL = ContentSchemas.DEFAULT_MODEL;

    /**
     * Whether it is a fluid.
     *
     * @return True if its model is {@value #FLUID}.
     */
    public boolean isFluid() {
        return FLUID.equals(model);
    }

    /**
     * Read a look file.
     *
     * @param text The file's text.
     * @return The look, or empty if the text isn't a KVT tree.
     * @throws IllegalArgumentException If a value isn't text.
     */
    public static Optional<BlockLook> parse(@NonNull String text) {
        return TreeStringSerialization.fromString(text).map(BlockLook::fromTree);
    }

    /**
     * Read a look from its tree.
     *
     * @param root The tree.
     * @return The look.
     * @throws IllegalArgumentException If a value isn't text.
     */
    private static BlockLook fromTree(@NonNull Node root) {
        final Schema.Values values = KvtSchemas.read(root, ContentSchemas.LOOK);
        for (Schema.Problem problem : values.problems()) {
            if (problem.error()) {
                throw new IllegalArgumentException(problem.message());
            }
            log.warn("Look: {}", problem.message());
        }
        return new BlockLook(
                values.text(KEY_MODEL), values.text(KEY_TEXTURE), values.text(KEY_MATERIAL));
    }
}
