package com.ikalagaming.converter.index;

import com.ikalagaming.converter.AssetConvert;
import com.ikalagaming.graphics.asset.MaterialSection;
import com.ikalagaming.graphics.asset.MaterialText;
import com.ikalagaming.graphics.graph.Material;
import com.ikalagaming.util.schema.Schema;

import lombok.NonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A render material's values, in either form: its binary {@code .ika} or its {@code .material.yml}
 * source. Writing one writes the other the index says it makes or is made from, so the two stay the
 * same; the text form lists only what differs from the defaults. Textures aren't values here, and
 * are kept.
 */
public final class MaterialValues implements AssetValues {
    /** The other form of the file written, if any. */
    private final @Nullable Path twin;

    /**
     * Read and write a render material.
     *
     * @param twin The file's other form, the source that makes it or the material it makes, or null
     *     if there is none.
     */
    public MaterialValues(@Nullable Path twin) {
        this.twin = twin;
    }

    /**
     * The values of a render material file found through the index: its twin is what the index
     * links it to by "makes".
     *
     * @param entry The file's entry.
     * @param snapshot The index.
     * @return The values.
     */
    public static MaterialValues of(
            AssetIndex.@NonNull Entry entry, AssetIndex.@NonNull Snapshot snapshot) {
        Path twin = null;
        for (AssetIndex.Link link : snapshot.uses(entry.file())) {
            if ("makes".equals(link.label()) && link.file() != null) {
                twin = link.file();
            }
        }
        for (AssetIndex.Link link : snapshot.usedBy(entry.file())) {
            if ("makes".equals(link.label())) {
                twin = link.file();
            }
        }
        return new MaterialValues(twin);
    }

    @Override
    public Schema schema() {
        return MaterialText.SCHEMA;
    }

    @Override
    public Schema.Values read(@NonNull Path file) throws IOException {
        try {
            return MaterialText.SCHEMA.read(
                    MaterialText.values(AssetConvert.readMaterial(file).material()));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public void write(@NonNull Path file, @NonNull Map<String, Object> values) throws IOException {
        final Material material = MaterialText.fromValues(values);
        try {
            writeOne(file, material);
            if (twin != null && Files.isRegularFile(twin)) {
                writeOne(twin, material);
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Write one form, keeping its textures.
     *
     * @param file The file.
     * @param material The values.
     * @throws IOException If it can't be written.
     */
    private static void writeOne(Path file, Material material) throws IOException {
        final MaterialSection.Textures textures = AssetConvert.readMaterial(file).textures();
        if (file.getFileName().toString().endsWith(AssetConvert.MATERIAL_TEXT)) {
            Files.writeString(
                    file, MaterialText.writeDiffering(material, textures), StandardCharsets.UTF_8);
        } else {
            AssetConvert.writeMaterial(file, material, textures, null);
        }
    }
}
