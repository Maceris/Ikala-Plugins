package com.ikalagaming.converter.texgraph;

import lombok.NonNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A shared parameter file, {@code *.texparams.yml}: values many texture graphs read, like a block
 * palette or a global wear amount, as UE's material parameter collections are. A graph lists the
 * files it reads; its own parameters of the same name win. Changing a value here changes every
 * graph that reads it, which bakes again.
 *
 * <pre>
 * texture parameters: 1
 * parameters:
 *   wear: {type: scalar, value: 0.3}
 * </pre>
 *
 * Files are read again only when they change on disk. Safe from any thread.
 */
public final class SharedParameters {
    /** What a shared parameter file's name ends with. */
    public static final String SUFFIX = ".texparams.yml";

    /** The format's version, written first. */
    public static final int FORMAT = 1;

    /** The key holding the format's version. */
    static final String FORMAT_KEY = "texture parameters";

    /** The key holding the parameters. */
    static final String PARAMETERS = "parameters";

    /** Files read, with when they were last changed, so unchanged ones aren't read again. */
    private static final Map<Path, Read> CACHE = new ConcurrentHashMap<>();

    /**
     * A file as read.
     *
     * @param changed When the file was last changed.
     * @param size Its size in bytes.
     * @param parameters Its parameters, by name.
     * @param problems What couldn't be read.
     */
    public record Read(
            FileTime changed,
            long size,
            Map<String, GraphParameter> parameters,
            List<String> problems) {}

    /** Not made. */
    private SharedParameters() {}

    /**
     * Read a shared parameter file, or reuse what was read if it hasn't changed.
     *
     * @param file The file.
     * @return What it holds.
     * @throws IOException If it can't be read.
     * @throws IllegalArgumentException If it isn't a shared parameter file.
     */
    public static Read read(@NonNull Path file) throws IOException {
        final Path key = file.toAbsolutePath().normalize();
        final FileTime changed = Files.getLastModifiedTime(key);
        final long size = Files.size(key);
        final Read cached = CACHE.get(key);
        if (cached != null && cached.changed().equals(changed) && cached.size() == size) {
            return cached;
        }
        final Read read = parse(Files.readString(key), changed, size);
        CACHE.put(key, read);
        return read;
    }

    /**
     * Read a shared parameter file's text.
     *
     * @param text The YAML.
     * @param changed When the file was changed.
     * @param size The file's size.
     * @return What it holds.
     * @throws IllegalArgumentException If it isn't a shared parameter file.
     */
    static Read parse(String text, FileTime changed, long size) {
        final Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        if (!(document instanceof Map<?, ?> root) || !root.containsKey(FORMAT_KEY)) {
            throw new IllegalArgumentException(
                    "Not a shared parameter file: it has no '" + FORMAT_KEY + "'");
        }
        final List<String> problems = new ArrayList<>();
        final Map<String, GraphParameter> parameters =
                GraphParameter.readAll(root.get(PARAMETERS), problems);
        return new Read(changed, size, Map.copyOf(parameters), List.copyOf(problems));
    }

    /**
     * The text of a shared parameter file.
     *
     * @param parameters Its parameters.
     * @return The YAML.
     */
    public static String write(@NonNull Collection<GraphParameter> parameters) {
        return FORMAT_KEY + ": " + FORMAT + "\n" + GraphParameter.writeAll(PARAMETERS, parameters);
    }

    /**
     * Write a shared parameter file.
     *
     * @param file The file.
     * @param parameters Its parameters.
     * @throws IOException If it can't be written.
     */
    public static void save(@NonNull Path file, @NonNull Collection<GraphParameter> parameters)
            throws IOException {
        Files.writeString(file, write(parameters));
        CACHE.remove(file.toAbsolutePath().normalize());
    }
}
