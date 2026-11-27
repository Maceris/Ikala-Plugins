package com.ikalagaming.converter;

import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.AssetFormat;
import com.ikalagaming.graphics.asset.AssetFormatException;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.MaterialSection;
import com.ikalagaming.graphics.asset.MaterialText;
import com.ikalagaming.graphics.graph.Material;

import lombok.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Converts assets between the binary {@code .ika} form the game loads and a text form people and
 * tools can read and edit. Materials only for now: a {@code .ika} holding a material becomes a
 * {@code .material.yml} file (see {@link MaterialText}), and back.
 *
 * <p>Runs without the editor's window or plugins, from the editor's {@code bin/convertAsset}:
 *
 * <pre>
 * convertAsset stone.ika                 writes stone.material.yml beside it
 * convertAsset stone.material.yml        writes stone.ika beside it
 * convertAsset a.ika b.material.yml ...  each to its other form
 * convertAsset stone.ika -o out.yml      to a named file, for one input
 * </pre>
 */
public final class AssetConvert {

    /** The ending of a material's text form. */
    public static final String MATERIAL_TEXT = ".material.yml";

    /** What converted files name as their exporter. */
    public static final String EXPORTER = "Ikala Asset Editor convert";

    /** The option naming the output file. */
    private static final String OUTPUT_OPTION = "-o";

    /** Not to be made. */
    private AssetConvert() {}

    /**
     * Convert files from the command line. Exits with 0 if all went well, 1 if any failed, and 2 if
     * the arguments are wrong.
     *
     * @param args The files, see the class description.
     */
    public static void main(String[] args) {
        System.exit(run(List.of(args), System.out, System.err));
    }

    /**
     * Convert files as the command line asks.
     *
     * @param args The arguments.
     * @param out Where to report each file written.
     * @param err Where to report problems.
     * @return The exit code: 0 if all went well, 1 if any failed, 2 if the arguments are wrong.
     */
    static int run(@NonNull List<String> args, @NonNull PrintStream out, @NonNull PrintStream err) {
        final List<Path> inputs = new ArrayList<>();
        Path output = null;
        for (int i = 0; i < args.size(); ++i) {
            if (OUTPUT_OPTION.equals(args.get(i))) {
                if (i + 1 >= args.size() || output != null) {
                    return usage(err);
                }
                output = Path.of(args.get(++i));
            } else {
                inputs.add(Path.of(args.get(i)));
            }
        }
        if (inputs.isEmpty() || (output != null && inputs.size() != 1)) {
            return usage(err);
        }
        int code = 0;
        for (Path input : inputs) {
            try {
                final Path target = output != null ? output : otherForm(input);
                convert(input, target);
                out.println(input + " -> " + target);
            } catch (IllegalArgumentException | AssetFormatException | UncheckedIOException e) {
                err.println(input + ": " + e.getMessage());
                code = 1;
            }
        }
        return code;
    }

    /**
     * Say how to use it.
     *
     * @param err Where to.
     * @return The exit code for wrong arguments.
     */
    private static int usage(@NonNull PrintStream err) {
        err.println(
                "Usage: convertAsset <file>... | convertAsset <file> -o <output>\n"
                        + "  <name>.ika (a material) becomes <name>"
                        + MATERIAL_TEXT
                        + ", and back.");
        return 2;
    }

    /**
     * Where a file's other form goes by default: beside it, with the other ending.
     *
     * @param input The file.
     * @return The other form's path.
     * @throws IllegalArgumentException If the file has neither ending.
     */
    public static Path otherForm(@NonNull Path input) {
        final String name = input.getFileName().toString();
        final String lower = name.toLowerCase(Locale.ROOT);
        final String other;
        if (lower.endsWith(MATERIAL_TEXT)) {
            other =
                    name.substring(0, name.length() - MATERIAL_TEXT.length())
                            + AssetFormat.EXTENSION;
        } else if (lower.endsWith(AssetFormat.EXTENSION)) {
            other =
                    name.substring(0, name.length() - AssetFormat.EXTENSION.length())
                            + MATERIAL_TEXT;
        } else {
            throw new IllegalArgumentException(
                    "Not a " + AssetFormat.EXTENSION + " or " + MATERIAL_TEXT + " file");
        }
        return input.resolveSibling(other);
    }

    /**
     * Convert a file to its other form: text if it is binary, binary if it is text, by its ending.
     *
     * @param input The file to read.
     * @param output The file to write, replaced if it exists.
     * @throws IllegalArgumentException If the input has neither ending.
     * @throws AssetFormatException If the input can't be read as a material.
     * @throws UncheckedIOException If a file can't be read or written.
     */
    public static void convert(@NonNull Path input, @NonNull Path output) {
        final boolean text = isText(input);
        if (text == isText(output)) {
            throw new IllegalArgumentException(
                    output.getFileName() + " isn't the other form of " + input.getFileName());
        }
        final MaterialSection.Stored material = readMaterial(input);
        writeMaterial(output, material.material(), material.textures(), input);
    }

    /**
     * Whether a file is the text form, by its ending.
     *
     * @param file The file.
     * @return True for text, false for binary.
     * @throws IllegalArgumentException If it has neither ending.
     */
    private static boolean isText(@NonNull Path file) {
        final String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(MATERIAL_TEXT)) {
            return true;
        }
        if (lower.endsWith(AssetFormat.EXTENSION)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Not a " + AssetFormat.EXTENSION + " or " + MATERIAL_TEXT + " file");
    }

    /**
     * Read a material, in either form by the file's ending.
     *
     * @param file The file.
     * @return The material's values, with no textures set, and its texture names.
     * @throws IllegalArgumentException If the file has neither ending.
     * @throws AssetFormatException If it can't be read as a material.
     * @throws UncheckedIOException If it can't be read.
     */
    public static MaterialSection.Stored readMaterial(@NonNull Path file) {
        if (!isText(file)) {
            return MaterialSection.read(AssetFile.open(file));
        }
        try {
            return MaterialText.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Write a material, in either form by the file's ending.
     *
     * @param file The file, replaced if it exists.
     * @param material The material's values.
     * @param textures The names of its textures.
     * @param source The file it came from, named in the binary form's metadata, or null.
     * @throws IllegalArgumentException If the file has neither ending.
     * @throws UncheckedIOException If it can't be written.
     */
    public static void writeMaterial(
            @NonNull Path file,
            @NonNull Material material,
            @NonNull MaterialSection.Textures textures,
            Path source) {
        try {
            if (isText(file)) {
                Files.writeString(
                        file, MaterialText.write(material, textures), StandardCharsets.UTF_8);
                return;
            }
            AssetMetadata metadata = AssetMetadata.of(EXPORTER, version());
            if (source != null) {
                metadata = metadata.with(AssetMetadata.SOURCE, source.getFileName().toString());
            }
            final ByteBuffer bytes = MaterialSection.file(material, textures, metadata);
            try (FileChannel channel =
                    FileChannel.open(
                            file,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * This editor's version, from its own plugin.yml, since the plugin system may not be running.
     *
     * @return The version, or empty if it can't be found.
     */
    public static String version() {
        try {
            final URL jar = AssetConvert.class.getProtectionDomain().getCodeSource().getLocation();
            final URL info =
                    jar.getPath().endsWith(".jar")
                            ? URI.create("jar:" + jar + "!/plugin.yml").toURL()
                            : jar.toURI().resolve("plugin.yml").toURL();
            try (InputStream in = info.openStream()) {
                final Properties properties = new Properties();
                properties.load(in);
                return properties.getProperty("version", "").strip();
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            return "";
        }
    }
}
