package com.ikalagaming.factory.registry;

import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;

import java.util.Map;
import java.util.Optional;

/**
 * The fields of the game's flat content files, by kind folder: what their readers read, with the
 * defaults they use, so the game checks files and editors make forms from the same declarations.
 * Block looks and water looks are read by the client, but declared here, where tools that don't
 * load the client can find them. World generation's kinds declare theirs with their factories.
 */
public final class ContentSchemas {
    /** The kind folder of block looks. */
    public static final String LOOKS_FOLDER = "looks/block";

    /** The kind folder of water looks. */
    public static final String WATER_LOOKS_FOLDER = "looks/water";

    /** The model block looks use when they name none. */
    public static final String DEFAULT_MODEL = "lotomation:block/cube_all";

    /** The model name of a fluid, whose shape is made in code. */
    public static final String FLUID_MODEL = "fluid";

    /** A tag: a name definitions share, maybe under a parent. */
    public static final Schema TAG =
            Schema.of(
                    "tag",
                    Field.reference(
                            DefinitionLoader.KEY_PARENT,
                            DefinitionLoader.TAGS_FOLDER,
                            null,
                            "The tag this one is a kind of; everything tagged with this one has it"
                                    + " too"));

    /** A material: what blocks and items are made of. */
    public static final Schema MATERIAL =
            Schema.of(
                    "material",
                    Field.textList(
                            DefinitionLoader.KEY_TAGS,
                            DefinitionLoader.TAGS_FOLDER,
                            "Its tags, like solid or flammable"),
                    Field.reference(
                            DefinitionLoader.KEY_PARENT,
                            DefinitionLoader.MATERIALS_FOLDER,
                            null,
                            "The material this one is a kind of, whose tags it has too"));

    /** A block. */
    public static final Schema BLOCK =
            Schema.of(
                    "block",
                    Field.reference(
                            DefinitionLoader.KEY_MATERIAL,
                            DefinitionLoader.MATERIALS_FOLDER,
                            null,
                            "What it is made of"),
                    Field.textList(
                            DefinitionLoader.KEY_TAGS,
                            DefinitionLoader.TAGS_FOLDER,
                            "Its own tags, beside its material's"),
                    Field.choice(
                            DefinitionLoader.KEY_COLLISION,
                            "full",
                            "Whether things collide with it: full, a whole block, or none",
                            "full",
                            "none"),
                    Field.flag(
                            DefinitionLoader.KEY_ITEM,
                            true,
                            "Whether it is also an item, with its material and tags"));

    /** An item that isn't a block. */
    public static final Schema ITEM =
            Schema.of(
                    "item",
                    Field.reference(
                            DefinitionLoader.KEY_MATERIAL,
                            DefinitionLoader.MATERIALS_FOLDER,
                            null,
                            "What it is made of"),
                    Field.textList(
                            DefinitionLoader.KEY_TAGS, DefinitionLoader.TAGS_FOLDER, "Its tags"));

    /** A block's look: what it is drawn with. */
    public static final Schema LOOK =
            Schema.of(
                    "look",
                    Field.reference(
                            "model",
                            "models",
                            DEFAULT_MODEL,
                            "Its model, or fluid for a fluid, whose shape follows its level"),
                    Field.reference("texture", "textures", null, "Its texture"),
                    Field.reference(
                            "material",
                            "render_materials",
                            null,
                            "How its surface reflects light; the default is matte"));

    /** How water looks in a biome: what is in it, and how far the wind has blown over it. */
    public static final Schema WATER_LOOK =
            Schema.of(
                    "water look",
                    Field.number(
                            "chlorophyll",
                            0,
                            0,
                            Double.MAX_VALUE,
                            "Algae, in mg/m^3: green, and cloudier as it rises"),
                    Field.number(
                            "cdom",
                            0,
                            0,
                            Double.MAX_VALUE,
                            "Dissolved organic matter, absorption at 440 nm per meter: yellow"
                                    + " brown"),
                    Field.number(
                            "sediment",
                            0,
                            0,
                            Double.MAX_VALUE,
                            "Suspended sediment, in g/m^3: silty and scattering"),
                    Field.number(
                            "fetch",
                            Double.POSITIVE_INFINITY,
                            0,
                            Double.POSITIVE_INFINITY,
                            "How far the wind has blown over the water, in meters; waves grow with"
                                    + " it, without limit by default"));

    /** The schemas by kind folder. */
    private static final Map<String, Schema> BY_FOLDER =
            Map.of(
                    DefinitionLoader.TAGS_FOLDER,
                    TAG,
                    DefinitionLoader.MATERIALS_FOLDER,
                    MATERIAL,
                    DefinitionLoader.BLOCKS_FOLDER,
                    BLOCK,
                    DefinitionLoader.ITEMS_FOLDER,
                    ITEM,
                    LOOKS_FOLDER,
                    LOOK,
                    WATER_LOOKS_FOLDER,
                    WATER_LOOK);

    /** Not made. */
    private ContentSchemas() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * The schema of a kind of content file.
     *
     * @param folder The kind folder, like {@code blocks} or {@code looks/water}.
     * @return The schema, or empty if the kind has none here.
     */
    public static Optional<Schema> forFolder(@NonNull String folder) {
        return Optional.ofNullable(BY_FOLDER.get(folder));
    }
}
