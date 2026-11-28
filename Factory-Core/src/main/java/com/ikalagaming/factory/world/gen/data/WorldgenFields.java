package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.util.schema.Field;

import lombok.NonNull;

import java.util.List;

/**
 * The kinds of field world generation data shares, for schemas: density, noise, condition and
 * fluids values (an ID, or an object of a registered type, or a number for a density), block IDs,
 * axes and per-axis numbers. Field names here are placeholders a schema renames.
 */
public final class WorldgenFields {
    /** Where a kind's files are, for references, under a content folder. */
    private static final String WORLDGEN = "worldgen/";

    /** The folder of blocks, which block IDs name. */
    private static final String BLOCKS = "blocks";

    /** Not made. */
    private WorldgenFields() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * The kind folder a reference to a world generation kind names.
     *
     * @param kind The kind.
     * @return The folder under a content folder, like {@code worldgen/noise}.
     */
    public static String folder(@NonNull Kind kind) {
        return WORLDGEN + kind.getFolder();
    }

    /**
     * A density value: a number, a density file's ID, or a node of a registered type.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field density(@NonNull String name, @NonNull String description) {
        return Field.union(
                        name,
                        description,
                        Field.number("value", 0, -Double.MAX_VALUE, Double.MAX_VALUE, "A constant"),
                        Field.reference("id", folder(Kind.DENSITY), null, "A density file"),
                        Field.typed("node", WorldgenTypes.DENSITY, "A density node"))
                .asRequired();
    }

    /**
     * A list of density values.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field densities(@NonNull String name, @NonNull String description) {
        return Field.list(name, density("value", "A density value"), description).asRequired();
    }

    /**
     * A noise: a noise file's ID, or a noise of a registered type.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field noise(@NonNull String name, @NonNull String description) {
        return Field.union(
                        name,
                        description,
                        Field.reference("id", folder(Kind.NOISE), null, "A noise file"),
                        Field.typed("noise", WorldgenTypes.NOISE, "A noise"))
                .asRequired();
    }

    /**
     * A block rule condition, of a registered type.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field condition(@NonNull String name, @NonNull String description) {
        return Field.typed(name, WorldgenTypes.CONDITION, description).asRequired();
    }

    /**
     * How open space fills with fluid: a fluids file's ID, or fluids of a registered type.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, optional.
     */
    public static Field fluids(@NonNull String name, @NonNull String description) {
        return Field.union(
                name,
                description,
                Field.reference("id", folder(Kind.FLUIDS), null, "A fluids file"),
                Field.typed("fluids", WorldgenTypes.FLUIDS, "Fluids"));
    }

    /**
     * A block's ID.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field block(@NonNull String name, @NonNull String description) {
        return Field.reference(name, BLOCKS, null, description).asRequired();
    }

    /**
     * A list of block IDs.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field blocks(@NonNull String name, @NonNull String description) {
        return Field.textList(name, BLOCKS, description).asRequired();
    }

    /**
     * A reference to a world generation file.
     *
     * @param name The key.
     * @param kind The kind it names.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field id(@NonNull String name, @NonNull Kind kind, @NonNull String description) {
        return Field.reference(name, folder(kind), null, description).asRequired();
    }

    /**
     * An axis: x, y or z.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field axis(@NonNull String name, @NonNull String description) {
        return Field.choice(name, "y", description, "x", "y", "z").asRequired();
    }

    /**
     * A number with no default, which must be given.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field number(@NonNull String name, @NonNull String description) {
        return Field.number(name, 0, -Double.MAX_VALUE, Double.MAX_VALUE, description).asRequired();
    }

    /**
     * A number that may be left out.
     *
     * @param name The key.
     * @param fallback Its default.
     * @param description What it means.
     * @return The field.
     */
    public static Field number(@NonNull String name, double fallback, @NonNull String description) {
        return Field.number(name, fallback, -Double.MAX_VALUE, Double.MAX_VALUE, description);
    }

    /**
     * A whole number that may be left out.
     *
     * @param name The key.
     * @param fallback Its default.
     * @param description What it means.
     * @return The field.
     */
    public static Field integer(@NonNull String name, long fallback, @NonNull String description) {
        return Field.integer(name, fallback, Integer.MIN_VALUE, Integer.MAX_VALUE, description);
    }

    /**
     * Three numbers, x, y and z, which must be given.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field point(@NonNull String name, @NonNull String description) {
        return Field.numbers(name, DensityNode.AXIS_COUNT, description).asRequired();
    }

    /**
     * Numbers per axis: three, x, y and z, or one for all of them, which must be given.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field perAxis(@NonNull String name, @NonNull String description) {
        return Field.union(
                        name,
                        description,
                        Field.number("all", 0, -Double.MAX_VALUE, Double.MAX_VALUE, "For all axes"),
                        Field.numbers("each", DensityNode.AXIS_COUNT, "For x, y and z"))
                .asRequired();
    }

    /**
     * A range, its two ends, lowest first, which must be given.
     *
     * @param name The key.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field range(@NonNull String name, @NonNull String description) {
        return Field.numbers(name, 2, description).asRequired();
    }

    /**
     * A list of objects of one schema.
     *
     * @param name The key.
     * @param schema Each object's fields.
     * @param description What it means.
     * @return The field, required.
     */
    public static Field objects(
            @NonNull String name,
            com.ikalagaming.util.schema.@NonNull Schema schema,
            @NonNull String description) {
        return Field.list(name, Field.object("entry", schema, "An entry"), description)
                .asRequired();
    }

    /**
     * Optional, for a field the helpers make required.
     *
     * @param field The field.
     * @return The same field, optional with no default.
     */
    public static Field optional(@NonNull Field field) {
        return field.withDefault(null);
    }

    /**
     * Choices, the list form for schemas built from enums.
     *
     * @param name The key.
     * @param fallback The default.
     * @param description What it means.
     * @param choices The words.
     * @return The field.
     */
    public static Field choice(
            @NonNull String name,
            @NonNull String fallback,
            @NonNull String description,
            @NonNull List<String> choices) {
        return Field.choice(name, fallback, description, choices.toArray(String[]::new));
    }
}
