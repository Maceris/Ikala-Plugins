package com.ikalagaming.characters.registry;

import com.ikalagaming.characters.data.DataFormat;
import com.ikalagaming.characters.definition.Kind;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * The JSON Schemas for each kind of definition, which give modders autocomplete and inline errors
 * in their editors. They are generated from the definition records, and a copy is kept in the
 * plugin's resources under {@value #RESOURCE_FOLDER}, which a test keeps up to date.
 *
 * @author Ches Burks
 */
public final class Schemas {
    /** Where the schemas are in the plugin's resources. */
    public static final String RESOURCE_FOLDER = "schemas";

    private Schemas() {}

    /**
     * The schema file name for a kind.
     *
     * @param kind The kind.
     * @return The file name, like {@code bodyPlan.schema.json}.
     */
    public static String fileName(@NonNull Kind kind) {
        return kind.getFolder() + ".schema.json";
    }

    /**
     * Generate the schema text for each kind.
     *
     * @return The schema text by kind.
     */
    public static Map<Kind, String> generate() {
        Map<Kind, String> schemas = new EnumMap<>(Kind.class);
        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        for (Kind kind : Kind.values()) {
            JsonObject schema =
                    DataFormat.schema(
                            kind.getType().asSubclass(Record.class),
                            "Ikala character " + kind.getFolder());
            schemas.put(kind, gson.toJson(schema) + "\n");
        }
        return schemas;
    }

    /**
     * Write the schemas to a folder.
     *
     * @param folder The folder, which is created if needed.
     * @throws IOException If they can't be written.
     */
    public static void write(@NonNull Path folder) throws IOException {
        Files.createDirectories(folder);
        for (Map.Entry<Kind, String> schema : generate().entrySet()) {
            Files.writeString(
                    folder.resolve(fileName(schema.getKey())),
                    schema.getValue(),
                    StandardCharsets.UTF_8);
        }
    }

    /**
     * Write the schemas to the folder given as the only argument.
     *
     * @param args The folder.
     * @throws IOException If they can't be written.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: Schemas <output folder>");
            System.exit(1);
        }
        write(Path.of(args[0]));
    }
}
