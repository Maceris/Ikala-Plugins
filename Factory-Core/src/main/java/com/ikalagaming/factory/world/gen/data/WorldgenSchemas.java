package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.Schema;
import com.ikalagaming.util.schema.Variants;

import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The schemas of the world generation kinds {@link WorldgenCompiler} builds itself, and of the
 * objects inside them, beside the registered types' in {@link WorldgenTypes}: what its builders
 * warn unknown fields against, with the defaults they use. Each kind's file is one {@link #root}
 * field, for editors.
 */
public final class WorldgenSchemas {

    /**
     * Types fixed in code, each with its schema.
     *
     * @param name What the types are.
     * @param types The schemas by type name.
     */
    record Fixed(String name, Map<String, Schema> types) implements Variants {
        @Override
        public SortedSet<String> names() {
            return new TreeSet<>(types.keySet());
        }

        @Override
        public Optional<Schema> schemaOf(@NonNull String type) {
            return Optional.ofNullable(types.get(type));
        }
    }

    /** A parameter file: a named density, shown over a range. */
    public static final Schema PARAMETER =
            Schema.of(
                    "parameter",
                    WorldgenFields.density("density", "Its value everywhere"),
                    Field.numbers(
                            "display_range", 2, "The range to show it over; 0 to 1 by default"));

    /** A terrain style: a shape, or an overlay unioned with or carved from shapes. */
    public static final Schema TERRAIN_STYLE =
            Schema.of(
                    "terrain style",
                    Field.choice(
                                    "kind",
                                    "shape",
                                    "A shape, blended with the others by weight, or an overlay",
                                    "shape",
                                    "overlay")
                            .asRequired(),
                    WorldgenFields.density("weight", "How much of it there is where, 0 to 1"),
                    WorldgenFields.density("density", "Its terrain"),
                    Field.choice(
                            "op",
                            "union",
                            "An overlay's: added to, or carved from",
                            "union",
                            "carve"),
                    Field.integer(
                            "priority",
                            0,
                            Integer.MIN_VALUE,
                            Integer.MAX_VALUE,
                            "An overlay's order among overlays"),
                    Field.numbers(
                            "interpolation",
                            3,
                            "An overlay's sampling cells, in blocks, at least 1; 4, 8, 4 by default"),
                    Field.textList("tags", null, "Its tags"));

    /** A block rule's outcome: a block, or more rules. */
    static final Schema THEN =
            Schema.of(
                    "then",
                    WorldgenFields.optional(WorldgenFields.block("block", "The block placed")),
                    Field.union(
                            "rules",
                            "Rules tried in its place: a list, a block rules file, or an object",
                            Field.list(
                                    "list",
                                    Field.object("rule", WorldgenSchemas::rule, "A rule"),
                                    "The rules"),
                            Field.reference(
                                    "id", WorldgenFields.folder(Kind.BLOCK_RULES), null, "A file"),
                            Field.object("object", WorldgenSchemas::rules, "Rules")));

    /** A block rule: a condition and what follows from it. */
    static final Schema RULE =
            Schema.of(
                    "rule",
                    WorldgenFields.optional(
                            WorldgenFields.condition("if", "When it applies; always if left out")),
                    Field.object("then", THEN, "A block, or more rules").asRequired());

    /** Block rules: the first rule that applies decides a block. */
    public static final Schema BLOCK_RULES =
            Schema.of(
                    "block rules",
                    Field.list("rules", Field.object("rule", RULE, "A rule"), "The rules, in order")
                            .asRequired());

    /** A biome: where its climate is, and its block rules. */
    public static final Schema BIOME =
            Schema.of(
                    "biome",
                    Field.map(
                            "climate",
                            Field.numbers("range", 2, "The range of the parameter, lowest first"),
                            "Each climate parameter's range, by name"),
                    Field.number(
                            "weight",
                            1,
                            Double.MIN_VALUE,
                            Double.MAX_VALUE,
                            "How likely it is where climates overlap; above 0"),
                    Field.union(
                            "block_rules",
                            "Its block rules: a file, or an object",
                            Field.reference(
                                    "id", WorldgenFields.folder(Kind.BLOCK_RULES), null, "A file"),
                            Field.object("object", BLOCK_RULES, "Rules")),
                    Field.object("visual", Schema.of("visual"), "How it looks; not read yet"),
                    Field.textList("tags", null, "Its tags, for #tag biome sets"));

    /** A tree species. */
    public static final Schema TREE_SPECIES =
            Schema.of(
                    "tree species",
                    Field.choice("type", "broadleaf", "Its shape", "broadleaf").asRequired(),
                    WorldgenFields.block("trunk", "Its trunk's block"),
                    WorldgenFields.block("leaves", "Its leaves' block"),
                    WorldgenFields.range("height", "How tall, in blocks: 1 to 512"),
                    WorldgenFields.range(
                            "crown_start", "Where its crown starts, 0 to 1 up the trunk"),
                    WorldgenFields.range("crown_radius", "Its crown's radius, 0.5 to 64 blocks"),
                    WorldgenFields.range(
                            "leaf_radius", "Each leaf cluster's radius, 0 to 32 blocks"));

    /** A block feature's choice of block. */
    static final Schema BLOCK_ENTRY =
            Schema.of(
                    "block entry",
                    WorldgenFields.block("block", "The block"),
                    Field.number(
                            "weight",
                            1,
                            Double.MIN_VALUE,
                            Double.MAX_VALUE,
                            "How likely it is among the others; above 0"));

    /** The feature types: a tree, or one of some blocks. */
    public static final Variants FEATURE_TYPES =
            new Fixed(
                    "feature",
                    Map.of(
                            WorldgenCompiler.FEATURE_TREE,
                            Schema.of(
                                    "tree feature",
                                    WorldgenFields.id(
                                            "species", Kind.TREE_SPECIES, "The tree species")),
                            WorldgenCompiler.FEATURE_BLOCK,
                            Schema.of(
                                    "block feature",
                                    WorldgenFields.objects(
                                            "blocks",
                                            BLOCK_ENTRY,
                                            "The blocks, chosen by weight"))));

    /** How far apart a cell placement's features are. */
    static final Schema SPACING =
            Schema.of(
                    "spacing",
                    Field.number("min", 0, 0, Double.MAX_VALUE, "The least distance, in blocks"),
                    Field.number(
                            "max", 0, 0, Double.MAX_VALUE, "The most distance; min by default"),
                    WorldgenFields.optional(
                            WorldgenFields.density("by", "Where between min and max, 0 to 1")));

    /** The placement step types. */
    public static final Variants PLACEMENT_TYPES =
            new Fixed(
                    "placement",
                    Map.of(
                            "each_floor",
                            Schema.of(
                                    "each floor",
                                    Field.number(
                                            "chance", 1, 0, 1, "How likely on each floor block")),
                            "cells",
                            Schema.of(
                                    "cells",
                                    Field.union(
                                                    "size",
                                                    "The cells' size: n for n by 32 by n, or three"
                                                            + " numbers",
                                                    Field.integer(
                                                            "n",
                                                            1,
                                                            1,
                                                            Integer.MAX_VALUE,
                                                            "Across and deep"),
                                                    Field.numbers("each", 3, "x, y and z"))
                                            .asRequired(),
                                    Field.integer(
                                            "candidates",
                                            1,
                                            1,
                                            Integer.MAX_VALUE,
                                            "Places tried in each cell"),
                                    Field.object("spacing", SPACING, "How far apart they are")),
                            "attach",
                            Schema.of(
                                    "attach",
                                    Field.choice("to", "floor", "What it stands on", "floor")),
                            "condition",
                            Schema.of(
                                    "condition",
                                    WorldgenFields.condition("if", "Where it may be placed"))));

    /** A placed feature: a feature, where, and how often. */
    public static final Schema PLACED_FEATURE =
            Schema.of(
                    "placed feature",
                    Field.union(
                                    "feature",
                                    "The feature: a file, or an object",
                                    Field.reference(
                                            "id",
                                            WorldgenFields.folder(Kind.FEATURE),
                                            null,
                                            "A file"),
                                    Field.typed("object", FEATURE_TYPES, "A feature"))
                            .asRequired(),
                    Field.union(
                                    "biomes",
                                    "The biomes it is in: an ID or #tag, or a list of them",
                                    Field.reference(
                                            "id",
                                            WorldgenFields.folder(Kind.BIOME),
                                            null,
                                            "A biome"),
                                    Field.textList(
                                            "list", WorldgenFields.folder(Kind.BIOME), "Biomes"))
                            .asRequired(),
                    Field.choice(
                            "layer",
                            "small",
                            "Small features each floor, or large ones in cells",
                            "small",
                            "large"),
                    Field.integer(
                            "priority",
                            0,
                            Integer.MIN_VALUE,
                            Integer.MAX_VALUE,
                            "Its order among features"),
                    Field.list(
                                    "placement",
                                    Field.typed("step", PLACEMENT_TYPES, "A step"),
                                    "How it is placed, step by step")
                            .asRequired());

    /** A world's edges. */
    static final Schema BORDER =
            Schema.of(
                    "border",
                    WorldgenFields.point("min", "Its least corner"),
                    WorldgenFields.point("max", "Its greatest corner"),
                    Field.integer(
                            "kill_distance",
                            1024,
                            0,
                            Integer.MAX_VALUE,
                            "How far past it things are removed"));

    /** Where players appear. */
    static final Schema ANCHOR =
            Schema.of(
                    "anchor",
                    Field.choice("type", "floor", "What they stand on", "floor"),
                    Field.integer("min_air", 3, 1, Integer.MAX_VALUE, "Open blocks above it"));

    /** Where players appear. */
    static final Schema SPAWN =
            Schema.of(
                    "spawn",
                    Field.object("anchor", ANCHOR, "What they stand on"),
                    Field.numbers("origin", List.of(0.0, 0.0, 0.0), "Where the search starts"),
                    Field.integer("radius", 4096, 0, Integer.MAX_VALUE, "How far it searches"),
                    Field.numbers(
                            "y_range",
                            List.of(-64.0, 320.0),
                            "The heights it searches, lowest first"));

    /** A world type: its terrain, biomes, fluids and edges. */
    public static final Schema WORLD_TYPE =
            Schema.of(
                    "world type",
                    Field.object("border", BORDER, "Its edges; none by default"),
                    WorldgenFields.optional(
                            WorldgenFields.density("density", "Its terrain, without styles")),
                    Field.numbers(
                            "interpolation",
                            3,
                            "Its sampling cells, in blocks, at least 1; 4, 8, 4 by default"),
                    WorldgenFields.block("default_block", "What solid ground is"),
                    Field.textList(
                            "parameters", WorldgenFields.folder(Kind.PARAMETER), "Its parameters"),
                    Field.textList("biomes", WorldgenFields.folder(Kind.BIOME), "Its biomes"),
                    WorldgenFields.id("fallback_biome", Kind.BIOME, "Where no biome fits"),
                    Field.textList(
                            "styles",
                            WorldgenFields.folder(Kind.TERRAIN_STYLE),
                            "Its terrain styles, in place of density"),
                    Field.reference(
                            "fallback_style",
                            WorldgenFields.folder(Kind.TERRAIN_STYLE),
                            null,
                            "A shape where no style fits; needed with styles"),
                    Field.number(
                            "style_fade",
                            64,
                            Double.MIN_VALUE,
                            Double.MAX_VALUE,
                            "How far styles blend, in blocks"),
                    WorldgenFields.fluids(
                            "fluids", "Where fluid fills open space; none by default"),
                    Field.textList("structure_sets", null, "Its structures; not read yet"),
                    Field.object("spawn", SPAWN, "Where players appear"));

    /** Not made. */
    private WorldgenSchemas() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * A block rule's fields, for rules holding rules.
     *
     * @return The schema.
     */
    private static Schema rule() {
        return RULE;
    }

    /**
     * Block rules' fields, for rules holding rules.
     *
     * @return The schema.
     */
    private static Schema rules() {
        return BLOCK_RULES;
    }

    /**
     * What a kind's file is, as one field: an object of the kind's schema, a typed object, or for
     * densities a density value.
     *
     * @param kind The kind.
     * @return The field, named after the kind.
     */
    public static Field root(@NonNull Kind kind) {
        final String name = kind.getFolder();
        return switch (kind) {
            case NOISE -> Field.typed(name, WorldgenTypes.NOISE, "A noise");
            case DENSITY -> WorldgenFields.density(name, "A density value");
            case PARAMETER -> Field.object(name, PARAMETER, "A parameter");
            case TERRAIN_STYLE -> Field.object(name, TERRAIN_STYLE, "A terrain style");
            case BIOME -> Field.object(name, BIOME, "A biome");
            case BLOCK_RULES -> Field.object(name, BLOCK_RULES, "Block rules");
            case FLUIDS -> Field.typed(name, WorldgenTypes.FLUIDS, "Fluids");
            case TREE_SPECIES -> Field.object(name, TREE_SPECIES, "A tree species");
            case FEATURE -> Field.typed(name, FEATURE_TYPES, "A feature");
            case PLACED_FEATURE -> Field.object(name, PLACED_FEATURE, "A placed feature");
            case WORLD_TYPE -> Field.object(name, WORLD_TYPE, "A world type");
        };
    }
}
