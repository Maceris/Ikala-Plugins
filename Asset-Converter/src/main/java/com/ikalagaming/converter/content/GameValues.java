package com.ikalagaming.converter.content;

import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.AssetValues;
import com.ikalagaming.factory.kvt.KvtSchemas;
import com.ikalagaming.factory.kvt.NodeType;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.registry.ContentSchemas;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * Read and write files of a kind.
     *
     * @param schema The kind's schema.
     * @param numbers How new numbers are stored.
     */
    private GameValues(Schema schema, NodeType numbers) {
        this.schema = schema;
        this.numbers = numbers;
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
                return Optional.of(new GameValues(schema, numbers));
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
        final String text = Files.readString(file, StandardCharsets.UTF_8);
        return KvtSchemas.read(
                TreeStringSerialization.fromString(text)
                        .orElseThrow(() -> new IOException(file.getFileName() + " isn't KVT")),
                schema);
    }

    @Override
    public void write(@NonNull Path file, @NonNull Map<String, Object> values) throws IOException {
        final String original =
                Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        Files.writeString(
                file, KvtSchemas.write(original, schema, values, numbers), StandardCharsets.UTF_8);
    }
}
