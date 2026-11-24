package com.ikalagaming.factory.registry;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.plugins.PluginInfo;
import com.ikalagaming.plugins.PluginManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Where content comes from: the data folders of the plugins that build on Factory-Core, such as the
 * base game's {@code Lotomation} plugin and every mod.
 *
 * <p>A content folder is a plugin's data folder, {@code plugins/<Plugin>/data}, and its namespace
 * is the plugin's name in lower case, so the folder can be read without the plugin running, as the
 * world generation tools do. Each kind of content has its own folder, and a file's ID comes from
 * its path: {@code data/blocks/stone.kvt} in the {@code Lotomation} plugin is {@code
 * lotomation:stone}. A plugin replaces another's files under {@code overrides/<namespace>/}, laid
 * out the same way.
 *
 * <p>Folders are read in order, and a file from a later folder replaces one with the same ID from
 * an earlier one. {@link #installed()} orders them by their plugins' dependencies, so a plugin's
 * overrides come after the plugin they override.
 */
@Slf4j
public final class ContentFolders {

    /** The folder under a content folder holding files that replace other namespaces' files. */
    public static final String OVERRIDES_FOLDER = "overrides";

    /** What a namespace must look like. */
    private static final Pattern NAMESPACE = Pattern.compile(RegistryConstants.MOD_NAME_FORMAT);

    /** What each part of a file's path must look like, without its extension. */
    private static final Pattern PATH_SEGMENT =
            Pattern.compile(RegistryConstants.RESOURCE_NAME_FORMAT);

    /** Why a file in a kind's folder was not read. */
    public enum Problem {
        /** It doesn't have the kind's extension. */
        WRONG_EXTENSION,
        /** Its path can't be part of an ID. */
        BAD_NAME
    }

    /**
     * A file that was not read.
     *
     * @param file The file.
     * @param problem Why.
     */
    public record Skipped(@NonNull Path file, @NonNull Problem problem) {}

    /**
     * The files of one kind in some content folders.
     *
     * @param files The files by ID, sorted, with later folders' files in place of earlier ones.
     * @param skipped Files that were not read, and why.
     */
    public record Listing(@NonNull SortedMap<String, Path> files, @NonNull List<Skipped> skipped) {}

    /**
     * The namespace of a plugin's content.
     *
     * @param pluginName The plugin's name.
     * @return The name in lower case.
     */
    public static String namespaceOf(@NonNull String pluginName) {
        return pluginName.toLowerCase(Locale.ROOT);
    }

    /**
     * The namespace of a content folder: the name of the plugin folder it is in, in lower case.
     *
     * @param folder The content folder, such as {@code plugins/Lotomation/data}.
     * @return The namespace, or empty if the folder's plugin folder doesn't make a valid one.
     */
    public static Optional<String> namespace(@NonNull Path folder) {
        final Path pluginFolder = folder.toAbsolutePath().normalize().getParent();
        if (pluginFolder == null || pluginFolder.getFileName() == null) {
            return Optional.empty();
        }
        final String namespace = namespaceOf(pluginFolder.getFileName().toString());
        return NAMESPACE.matcher(namespace).matches() ? Optional.of(namespace) : Optional.empty();
    }

    /**
     * The content folders of the enabled plugins that build on Factory-Core, ordered so each comes
     * after the plugins it depends on, and otherwise by name. Plugins without a data folder are
     * left out.
     *
     * @return The folders, in load order, or none if no plugin manager is running.
     */
    public static List<Path> installed() {
        final Optional<PluginManager> manager = PluginManager.getExistingInstance();
        if (manager.isEmpty()) {
            return List.of();
        }
        final Map<String, List<String>> dependencies = new TreeMap<>();
        for (String name : manager.get().getLoadedPlugins().keySet()) {
            if (!manager.get().isEnabled(name)) {
                continue;
            }
            manager.get()
                    .getInfo(name)
                    .ifPresent(info -> dependencies.put(name, dependenciesOf(info)));
        }
        final List<Path> folders = new ArrayList<>();
        for (String plugin : order(dependencies, FactoryPlugin.PLUGIN_NAME)) {
            final Path folder =
                    PluginFolder.getResource(plugin, PluginFolder.ResourceType.DATA, "").toPath();
            if (!Files.isDirectory(folder)) {
                continue;
            }
            if (namespace(folder).isEmpty()) {
                log.warn(
                        "Plugin {} can't hold content, since {} isn't a valid namespace",
                        plugin,
                        namespaceOf(plugin));
                continue;
            }
            folders.add(folder);
        }
        return folders;
    }

    /**
     * A plugin's dependencies, hard and soft, which all load before it.
     *
     * @param info The plugin's info.
     * @return The names.
     */
    private static List<String> dependenciesOf(@NonNull PluginInfo info) {
        final List<String> out = new ArrayList<>(info.getDependencies());
        out.addAll(info.getSoftDependencies());
        return out;
    }

    /**
     * The plugins that build on a base plugin, each after the plugins it depends on.
     *
     * @param dependencies Each plugin's dependencies, by plugin name. Names not in the map are not
     *     loaded.
     * @param base The plugin that content builds on, which is itself left out.
     * @return The plugins that depend on the base, directly or not, in load order: dependencies
     *     first, otherwise by name.
     */
    static List<String> order(
            @NonNull Map<String, List<String>> dependencies, @NonNull String base) {
        final List<String> out = new ArrayList<>();
        final Set<String> visited = new HashSet<>();
        for (String name : new TreeSet<>(dependencies.keySet())) {
            visit(name, dependencies, visited, out);
        }
        out.removeIf(
                name -> name.equals(base) || !buildsOn(name, base, dependencies, new HashSet<>()));
        return out;
    }

    /**
     * Add a plugin after its dependencies, depth first.
     *
     * @param name The plugin.
     * @param dependencies Each plugin's dependencies.
     * @param visited Plugins already added or being added, which also stops at cycles.
     * @param out The order so far.
     */
    private static void visit(
            @NonNull String name,
            @NonNull Map<String, List<String>> dependencies,
            @NonNull Set<String> visited,
            @NonNull List<String> out) {
        if (!dependencies.containsKey(name) || !visited.add(name)) {
            return;
        }
        for (String dependency : new TreeSet<>(dependencies.get(name))) {
            visit(dependency, dependencies, visited, out);
        }
        out.add(name);
    }

    /**
     * Whether a plugin depends on another, directly or not.
     *
     * @param name The plugin.
     * @param base The other plugin.
     * @param dependencies Each plugin's dependencies.
     * @param seen Plugins already checked, which stops at cycles.
     * @return True if it does.
     */
    private static boolean buildsOn(
            @NonNull String name,
            @NonNull String base,
            @NonNull Map<String, List<String>> dependencies,
            @NonNull Set<String> seen) {
        if (!seen.add(name)) {
            return false;
        }
        for (String dependency : dependencies.getOrDefault(name, List.of())) {
            if (dependency.equals(base) || buildsOn(dependency, base, dependencies, seen)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every file of a kind in some content folders, by ID.
     *
     * @param folders The content folders, in load order.
     * @param kind The kind's folder, such as {@code blocks} or {@code worldgen/density}.
     * @param extension The kind's file extension, such as {@code .kvt}.
     * @return The files, with later folders' files in place of earlier ones', and what was skipped.
     */
    public static Listing files(
            @NonNull List<Path> folders, @NonNull String kind, @NonNull String extension) {
        final SortedMap<String, Path> files = new TreeMap<>();
        final List<Skipped> skipped = new ArrayList<>();
        for (Path folder : folders) {
            final Optional<String> namespace = namespace(folder);
            if (namespace.isEmpty()) {
                log.warn(
                        "{} isn't in a plugin folder with a valid name, so it was skipped", folder);
                continue;
            }
            list(folder.resolve(kind), namespace.get(), extension, files, skipped);
            for (Path other : sortedChildren(folder.resolve(OVERRIDES_FOLDER))) {
                final String otherNamespace = other.getFileName().toString();
                if (!Files.isDirectory(other)) {
                    continue;
                }
                if (!NAMESPACE.matcher(otherNamespace).matches()) {
                    skipped.add(new Skipped(other, Problem.BAD_NAME));
                    continue;
                }
                list(other.resolve(kind), otherNamespace, extension, files, skipped);
            }
        }
        return new Listing(files, skipped);
    }

    /**
     * The file for an ID, from the last folder that has it, as an override or its own.
     *
     * @param folders The content folders, in load order.
     * @param kind The kind's folder.
     * @param id The ID, such as {@code lotomation:block/stone}.
     * @param extension The kind's file extension.
     * @return The file, or empty if no folder has it or the ID isn't valid.
     */
    public static Optional<Path> find(
            @NonNull List<Path> folders,
            @NonNull String kind,
            @NonNull String id,
            @NonNull String extension) {
        final int colon = id.indexOf(':');
        if (colon < 0 || !isValidId(id)) {
            return Optional.empty();
        }
        final String namespace = id.substring(0, colon);
        final String relative = kind + "/" + id.substring(colon + 1) + extension;
        for (int i = folders.size() - 1; i >= 0; --i) {
            final Path folder = folders.get(i);
            final Optional<String> own = namespace(folder);
            if (own.isEmpty()) {
                continue;
            }
            final Path file =
                    own.get().equals(namespace)
                            ? folder.resolve(relative)
                            : folder.resolve(OVERRIDES_FOLDER).resolve(namespace).resolve(relative);
            if (Files.isRegularFile(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether an ID is a namespace, a colon and a path of valid names.
     *
     * @param id The ID.
     * @return True if it is valid.
     */
    public static boolean isValidId(@NonNull String id) {
        final int colon = id.indexOf(':');
        return colon > 0
                && NAMESPACE.matcher(id.substring(0, colon)).matches()
                && isValidPath(id.substring(colon + 1));
    }

    /**
     * Whether the path part of an ID is well formed.
     *
     * @param path The path, like {@code block/stone}.
     * @return True if every part is a valid name.
     */
    public static boolean isValidPath(@NonNull String path) {
        for (String segment : path.split("/", -1)) {
            if (!PATH_SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * List one kind's folder in one namespace.
     *
     * @param kindFolder The kind's folder, which may not exist.
     * @param namespace The namespace its files are in.
     * @param extension The kind's file extension.
     * @param files Where to put the files by ID.
     * @param skipped Where to put the files that can't be read.
     */
    private static void list(
            @NonNull Path kindFolder,
            @NonNull String namespace,
            @NonNull String extension,
            @NonNull SortedMap<String, Path> files,
            @NonNull List<Skipped> skipped) {
        if (!Files.isDirectory(kindFolder)) {
            return;
        }
        final List<Path> found;
        try (Stream<Path> walk = Files.walk(kindFolder)) {
            found =
                    walk.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString))
                            .toList();
        } catch (IOException e) {
            log.warn("Couldn't list {}: {}", kindFolder, e.getMessage());
            return;
        }
        for (Path file : found) {
            final String relative = kindFolder.relativize(file).toString().replace('\\', '/');
            if (!relative.endsWith(extension)) {
                skipped.add(new Skipped(file, Problem.WRONG_EXTENSION));
                continue;
            }
            final String path = relative.substring(0, relative.length() - extension.length());
            if (!isValidPath(path)) {
                skipped.add(new Skipped(file, Problem.BAD_NAME));
                continue;
            }
            files.put(namespace + RegistryConstants.NAME_SEPARATOR + path, file);
        }
    }

    /**
     * The entries of a folder, sorted by name.
     *
     * @param folder The folder, which may not exist.
     * @return The entries.
     */
    private static List<Path> sortedChildren(@NonNull Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(folder)) {
            return list.sorted(Comparator.comparing((Function<Path, String>) Path::toString))
                    .toList();
        } catch (IOException e) {
            log.warn("Couldn't list {}: {}", folder, e.getMessage());
            return List.of();
        }
    }

    /** Private constructor so this class is not instantiated. */
    private ContentFolders() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
