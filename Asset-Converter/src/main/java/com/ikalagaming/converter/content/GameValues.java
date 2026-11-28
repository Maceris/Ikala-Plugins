package com.ikalagaming.converter.content;

import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.AssetValues;
import com.ikalagaming.factory.kvt.KvtSchemas;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.NodeType;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.registry.ContentSchemas;
import com.ikalagaming.factory.world.gen.data.Kind;
import com.ikalagaming.factory.world.gen.data.WorldgenFields;
import com.ikalagaming.factory.world.gen.data.WorldgenSchemas;
import com.ikalagaming.factory.world.gen.data.WorldgenTypes;
import com.ikalagaming.util.schema.Field;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * The game's content files' values, read and written through the schemas Factory-Core declares
 * beside its readers. Uses Factory-Core's classes, so only touch it when {@link
 * GameContent#available()}.
 */
public final class GameValues implements AssetValues {
    /** The kind folders with schemas, longest first, so looks/water is tried before looks. */
    private static final List<String> FOLDERS =
            List.of(
                    ContentSchemas.WATER_LOOKS_FOLDER,
                    ContentSchemas.LOOKS_FOLDER,
                    "materials",
                    "blocks",
                    "items",
                    "tags");

    /** The schema. */
    private final Schema schema;

    /** How new numbers are stored. */
    private final NodeType numbers;

    /**
     * The field a whole file is, as a world generation file is one value, or null when the file's
     * keys are the schema's fields.
     */
    private final @Nullable Field root;

    /**
     * Read and write files of a kind.
     *
     * @param schema The kind's schema.
     * @param numbers How new numbers are stored.
     * @param root The field the whole file is, or null.
     */
    private GameValues(Schema schema, NodeType numbers, @Nullable Field root) {
        this.schema = schema;
        this.numbers = numbers;
        this.root = root;
    }

    /**
     * The values of a content file, if its kind has a schema.
     *
     * @param entry The file's entry.
     * @param snapshot The index, for the file's root.
     * @return The values, or empty if the file isn't in a kind with one.
     */
    public static Optional<AssetValues> of(
            AssetIndex.@NonNull Entry entry, AssetIndex.@NonNull Snapshot snapshot) {
        final AssetIndex.Root root = snapshot.roots().get(entry.root());
        if (!root.content() || !entry.file().toString().endsWith(".kvt")) {
            return Optional.empty();
        }
        String relative = root.folder().relativize(entry.file()).toString().replace('\\', '/');
        if (relative.startsWith("overrides/")) {
            // overrides/<namespace>/<kind>/...
            final int slash = relative.indexOf('/', "overrides/".length());
            relative = slash < 0 ? relative : relative.substring(slash + 1);
        }
        for (String folder : FOLDERS) {
            if (relative.startsWith(folder + "/")) {
                final Schema schema = ContentSchemas.forFolder(folder).orElseThrow();
                // Water looks hold floats, as the game reads them
                final NodeType numbers =
                        ContentSchemas.WATER_LOOKS_FOLDER.equals(folder)
                                ? NodeType.FLOAT
                                : NodeType.DOUBLE;
                return Optional.of(new GameValues(schema, numbers, null));
            }
        }
        // A world generation file is one value: its kind's root field
        for (Kind kind : Kind.values()) {
            if (relative.startsWith(WorldgenFields.folder(kind) + "/")) {
                WorldgenTypes.ensureRegistered();
                final Field field = WorldgenSchemas.root(kind);
                return Optional.of(
                        new GameValues(
                                Schema.of(kind.getFolder().replace('_', ' '), field),
                                NodeType.DOUBLE,
                                field));
            }
        }
        return Optional.empty();
    }

    @Override
    public Schema schema() {
        return schema;
    }

    @Override
    public Schema.Values read(@NonNull Path file) throws IOException {
        return read(Files.readString(file, StandardCharsets.UTF_8), file);
    }

    /**
     * Read a file's text.
     *
     * @param text The text.
     * @param file The file, for messages.
     * @return The values.
     * @throws IOException If it isn't KVT.
     */
    private Schema.Values read(String text, Path file) throws IOException {
        final Node tree =
                TreeStringSerialization.fromString(text)
                        .orElseThrow(() -> new IOException(file.getFileName() + " isn't KVT"));
        return root == null
                ? KvtSchemas.read(tree, schema)
                : schema.read(Map.of(root.name(), KvtSchemas.raw(tree)));
    }

    /**
     * Write by editing the file's text in place: only what changed from what the file holds now is
     * written, keeping its layout and comments.
     *
     * @param file The file.
     * @param values The new values.
     * @throws IOException If it can't be read or written.
     */
    @Override
    public void write(@NonNull Path file, @NonNull Map<String, Object> values) throws IOException {
        if (!Files.isRegularFile(file)) {
            Files.writeString(
                    file, KvtSchemas.write(null, schema, values, numbers), StandardCharsets.UTF_8);
            return;
        }
        final String original = Files.readString(file, StandardCharsets.UTF_8);
        final Schema.Values now = read(original, file);
        final String edited;
        try {
            edited =
                    root == null
                            ? KvtSchemas.edit(original, schema, now.given(), values, numbers)
                            : KvtSchemas.edit(
                                    original,
                                    root,
                                    now.given().get(root.name()),
                                    values.get(root.name()),
                                    numbers);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        Files.writeString(file, edited, StandardCharsets.UTF_8);
    }
}
