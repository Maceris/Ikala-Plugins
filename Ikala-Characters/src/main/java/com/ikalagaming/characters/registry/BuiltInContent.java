package com.ikalagaming.characters.registry;

import lombok.NonNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The definitions that ship inside this plugin, like the {@code ikala:biped} plan, which load
 * before any content folders.
 *
 * @author Ches Burks
 */
public final class BuiltInContent {
    /** The namespace of the built-in definitions. */
    public static final String NAMESPACE = "ikala";

    /** Where the built-in definitions are in the plugin's resources. */
    public static final String RESOURCE_FOLDER = "characters/" + NAMESPACE;

    /** What to call the built-in definitions in messages. */
    public static final String SOURCE_NAME = "Ikala-Characters";

    /** The URI scheme of resources inside a jar. */
    private static final String JAR_SCHEME = "jar";

    private BuiltInContent() {}

    /**
     * Load the built-in definitions followed by other sources.
     *
     * @param others The other sources, in order.
     * @return The registry and problems.
     * @throws IOException If the built-in definitions can't be found or opened.
     */
    public static CharacterLoader.Result loadWith(@NonNull List<ContentSource> others)
            throws IOException {
        URL url = BuiltInContent.class.getClassLoader().getResource(RESOURCE_FOLDER);
        if (url == null) {
            throw new IOException("The built-in definitions are missing from the plugin");
        }
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException("The built-in definitions have a broken location " + url, e);
        }
        if (!JAR_SCHEME.equals(uri.getScheme())) {
            return load(Path.of(uri), others);
        }
        // Inside a jar the folder is only readable through a zip file system, opened for the load
        FileSystem opened = null;
        try {
            try {
                opened = FileSystems.newFileSystem(uri, Map.of());
            } catch (FileSystemAlreadyExistsException e) {
                // Someone else has it open, so use theirs and leave it open
            }
            return load(Path.of(uri), others);
        } finally {
            if (opened != null) {
                opened.close();
            }
        }
    }

    /**
     * Load the built-in folder and the others.
     *
     * @param folder The built-in folder.
     * @param others The other sources.
     * @return The registry and problems.
     */
    private static CharacterLoader.Result load(Path folder, List<ContentSource> others) {
        List<ContentSource> sources = new ArrayList<>();
        sources.add(new ContentSource(NAMESPACE, folder, SOURCE_NAME));
        sources.addAll(others);
        return CharacterLoader.load(sources);
    }
}
