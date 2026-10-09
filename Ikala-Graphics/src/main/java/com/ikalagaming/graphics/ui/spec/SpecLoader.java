package com.ikalagaming.graphics.ui.spec;

import static com.ikalagaming.graphics.ui.spec.SpecKeys.*;

import com.ikalagaming.graphics.ui.Anchors;
import com.ikalagaming.graphics.ui.Layer;
import com.ikalagaming.graphics.ui.Sizing;
import com.ikalagaming.graphics.ui.style.Style;
import com.ikalagaming.graphics.ui.style.StyleParser;
import com.ikalagaming.graphics.ui.style.ThemeException;

import lombok.NonNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads UI specs from YAML. Templates are expanded while loading, so problems with them are found
 * straight away; problems with node properties are found when the spec is opened, since plugins can
 * add node types. Every problem names where it is, like {@code content.children[0].with: missing
 * parameter 'action' for template menu-button}.
 *
 * <pre>
 * surface:
 *   id: my-plugin/menu
 *   anchors: center            # fill, top-left ... or {at: [0.5, 0.5], offset: [0, -20]}
 *   width: fit                 # fit, grow, 240, "50%", {grow: 2, min: 100}
 * templates:
 *   import: [common.yml]       # templates from other files, next to this one
 *   big-button:
 *     params: [id, text, action]
 *     node: { type: button, id: ${id}, text: ${text}, onClick: ${action} }
 * content:
 *   type: column
 *   id: content
 *   children:
 *     - use: big-button
 *       with: { id: start, text: "@MENU_START", action: start-game }
 * </pre>
 */
public final class SpecLoader {
    /** Keys allowed at the top of a spec file. */
    private static final Set<String> TOP_LEVEL = Set.of(SURFACE, TEMPLATES, CONTENT);

    /** Keys a node has that aren't properties. */
    private static final Set<String> STRUCTURE = Set.of(TYPE, ID, CHILDREN, REPEAT);

    /** A whole value that is one template parameter, like {@code ${text}}. */
    private static final Pattern WHOLE_PARAMETER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    /** A template parameter inside text. */
    private static final Pattern PARAMETER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    /**
     * A template.
     *
     * @param name The template name.
     * @param params The parameter names.
     * @param node The node, as written, with parameters not yet replaced.
     * @param path Where the template is, for messages.
     */
    private record Template(
            String name, List<String> params, Map<String, Object> node, String path) {}

    /**
     * Load a spec file. Templates it imports are found next to it.
     *
     * @param file The file.
     * @return The spec.
     * @throws SpecException If the spec is broken.
     * @throws UncheckedIOException If a file can't be read.
     */
    public static UiSpec load(@NonNull Path file) {
        List<Path> files = new ArrayList<>();
        Map<String, Object> root = read(file, files);
        return parse(root, file.toString(), file.toAbsolutePath().getParent(), files);
    }

    /**
     * Load a spec bundled in a plugin's jar.
     *
     * @param owner A class from the plugin, whose class loader finds the resource.
     * @param resource The absolute resource path, like {@code /ui/menu.yml}.
     * @return The spec.
     * @throws SpecException If the spec is missing or broken. Imports are not allowed, since there
     *     is no folder to find them in.
     */
    public static UiSpec loadResource(@NonNull Class<?> owner, @NonNull String resource) {
        try (InputStream stream = owner.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new SpecException(resource + ": not found");
            }
            return load(stream, resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Load a spec from a stream. Imports are not allowed, since there is no folder to find them in.
     *
     * @param stream The YAML.
     * @param source Where it came from, for messages.
     * @return The spec.
     * @throws SpecException If the spec is broken.
     */
    public static UiSpec load(@NonNull InputStream stream, @NonNull String source) {
        return parse(yaml(stream, source), source, null, new ArrayList<>());
    }

    /**
     * Read a YAML file into a map.
     *
     * @param file The file.
     * @param files Collects the files read.
     * @return The top level map.
     */
    private static Map<String, Object> read(Path file, List<Path> files) {
        files.add(file.toAbsolutePath().normalize());
        try (InputStream stream = Files.newInputStream(file)) {
            return yaml(stream, file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the UI spec " + file, e);
        }
    }

    /**
     * Parse YAML into a map.
     *
     * @param stream The YAML.
     * @param source Where it came from, for messages.
     * @return The top level map.
     */
    private static Map<String, Object> yaml(InputStream stream, String source) {
        Object document;
        try {
            document = new Yaml(new SafeConstructor(new LoaderOptions())).load(stream);
        } catch (YAMLException e) {
            throw new SpecException(source + ": not valid YAML: " + e.getMessage(), e);
        }
        if (document == null) {
            throw new SpecException(source + ": the spec is empty");
        }
        return SpecValues.map(document, source);
    }

    /**
     * Turn a spec's top level map into a spec.
     *
     * @param root The top level map.
     * @param source Where it came from, for messages.
     * @param folder The folder imports are relative to, or null if imports aren't allowed.
     * @param files The files read so far, which imports are added to.
     * @return The spec.
     */
    private static UiSpec parse(
            Map<String, Object> root, String source, Path folder, List<Path> files) {
        for (String key : root.keySet()) {
            if (!TOP_LEVEL.contains(key)) {
                throw new SpecException(source + ": unknown key '" + key + "'");
            }
        }
        Map<String, Template> templates = new LinkedHashMap<>();
        if (root.containsKey(TEMPLATES)) {
            readTemplates(root.get(TEMPLATES), TEMPLATES, folder, files, templates);
        }
        if (!root.containsKey(SURFACE)) {
            throw new SpecException(missing(source, SURFACE));
        }
        if (!root.containsKey(CONTENT)) {
            throw new SpecException(missing(source, CONTENT));
        }
        UiSpec.SurfaceSpec surface = surface(root.get(SURFACE), SURFACE);
        NodeSpec content = node(root.get(CONTENT), CONTENT, templates, new ArrayDeque<>());
        return new UiSpec(source, surface, content, List.copyOf(files));
    }

    /**
     * Read a templates section, following imports.
     *
     * @param value The YAML map.
     * @param path Where it is, for messages.
     * @param folder The folder imports are relative to, or null if imports aren't allowed.
     * @param files Collects the files read.
     * @param templates Collects the templates.
     */
    private static void readTemplates(
            Object value,
            String path,
            Path folder,
            List<Path> files,
            Map<String, Template> templates) {
        Map<String, Object> map = SpecValues.map(value, path);
        if (map.containsKey(IMPORT)) {
            if (folder == null) {
                throw new SpecException(
                        at(path, IMPORT) + ": imports need a spec loaded from a file");
            }
            for (String file : SpecValues.names(map.get(IMPORT), at(path, IMPORT))) {
                Path imported = folder.resolve(file).normalize();
                if (files.contains(imported.toAbsolutePath())) {
                    continue;
                }
                Map<String, Object> other = read(imported, files);
                for (String key : other.keySet()) {
                    if (!TEMPLATES.equals(key)) {
                        throw new SpecException(
                                imported
                                        + ": template files only have '"
                                        + TEMPLATES
                                        + "', not '"
                                        + key
                                        + "'");
                    }
                }
                if (other.containsKey(TEMPLATES)) {
                    readTemplates(
                            other.get(TEMPLATES),
                            imported + ": templates",
                            imported.getParent(),
                            files,
                            templates);
                }
            }
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (IMPORT.equals(entry.getKey())) {
                continue;
            }
            String where = path + "." + entry.getKey();
            Map<String, Object> template = SpecValues.map(entry.getValue(), where);
            for (String key : template.keySet()) {
                if (!PARAMS.equals(key) && !NODE.equals(key)) {
                    throw new SpecException(
                            where + "." + key + ": templates only have " + PARAMS + " and " + NODE);
                }
            }
            List<String> params =
                    template.containsKey(PARAMS)
                            ? SpecValues.names(template.get(PARAMS), at(where, PARAMS))
                            : List.of();
            if (!template.containsKey(NODE)) {
                throw new SpecException(missing(where, NODE));
            }
            Map<String, Object> node = SpecValues.map(template.get(NODE), at(where, NODE));
            checkParameters(node, params, at(where, NODE));
            templates.put(entry.getKey(), new Template(entry.getKey(), params, node, where));
        }
    }

    /**
     * Check that a template only uses its own parameters.
     *
     * @param value Part of the template, as written.
     * @param params The parameters.
     * @param path Where it is, for messages.
     */
    private static void checkParameters(Object value, List<String> params, String path) {
        if (value instanceof String text) {
            Matcher matcher = PARAMETER.matcher(text);
            while (matcher.find()) {
                if (!params.contains(matcher.group(1))) {
                    throw new SpecException(
                            path + ": unknown parameter '" + matcher.group(1) + "'");
                }
            }
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> checkParameters(child, params, path + "." + key));
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); ++i) {
                checkParameters(list.get(i), params, path + "[" + i + "]");
            }
        }
    }

    /**
     * Read the surface settings.
     *
     * @param value The YAML map.
     * @param path Where it is, for messages.
     * @return The settings.
     */
    private static UiSpec.SurfaceSpec surface(Object value, String path) {
        Map<String, Object> map = SpecValues.map(value, path);
        Set<String> known =
                Set.of(ID, ANCHORS, WIDTH, HEIGHT, LAYER, MOVABLE, TRANSPARENT, CLASSES, STYLE);
        for (String key : map.keySet()) {
            if (!known.contains(key)) {
                throw new SpecException(path + "." + key + ": unknown surface property");
            }
        }
        if (!map.containsKey(ID)) {
            throw new SpecException(missing(path, ID));
        }
        Style style = Style.EMPTY;
        if (map.containsKey(STYLE)) {
            try {
                style = StyleParser.style(map.get(STYLE), at(path, STYLE), null);
            } catch (ThemeException e) {
                throw new SpecException(e.getMessage(), e);
            }
        }
        return new UiSpec.SurfaceSpec(
                SpecValues.name(map.get(ID), at(path, ID)),
                map.containsKey(ANCHORS)
                        ? SpecValues.anchors(map.get(ANCHORS), at(path, ANCHORS))
                        : Anchors.center(),
                map.containsKey(WIDTH)
                        ? SpecValues.sizing(map.get(WIDTH), at(path, WIDTH))
                        : Sizing.fit(),
                map.containsKey(HEIGHT)
                        ? SpecValues.sizing(map.get(HEIGHT), at(path, HEIGHT))
                        : Sizing.fit(),
                map.containsKey(LAYER)
                        ? SpecValues.choice(map.get(LAYER), Layer.class, at(path, LAYER))
                        : Layer.NORMAL,
                map.containsKey(MOVABLE) && SpecValues.bool(map.get(MOVABLE), at(path, MOVABLE)),
                map.containsKey(TRANSPARENT)
                        && SpecValues.bool(map.get(TRANSPARENT), at(path, TRANSPARENT)),
                map.containsKey(CLASSES)
                        ? SpecValues.names(map.get(CLASSES), at(path, CLASSES))
                        : List.of(),
                style);
    }

    /**
     * Read a node, expanding a template if it uses one.
     *
     * @param value The YAML map.
     * @param path Where it is, for messages.
     * @param templates The templates that can be used.
     * @param using The templates being expanded, to catch cycles.
     * @return The node.
     */
    private static NodeSpec node(
            Object value, String path, Map<String, Template> templates, Deque<String> using) {
        Map<String, Object> map = SpecValues.map(value, path);
        if (map.containsKey(USE)) {
            return use(map, path, templates, using);
        }
        if (!map.containsKey(TYPE)) {
            throw new SpecException(missing(path, TYPE));
        }
        if (!map.containsKey(ID)) {
            throw new SpecException(missing(path, ID));
        }
        String type = SpecValues.name(map.get(TYPE), at(path, TYPE));
        String id = SpecValues.name(map.get(ID), at(path, ID));

        List<NodeSpec> children = new ArrayList<>();
        if (map.containsKey(CHILDREN)) {
            List<?> list = SpecValues.list(map.get(CHILDREN), at(path, CHILDREN));
            for (int i = 0; i < list.size(); ++i) {
                children.add(node(list.get(i), path + ".children[" + i + "]", templates, using));
            }
        }
        NodeSpec.RepeatSpec repeat = null;
        if (map.containsKey(REPEAT)) {
            repeat = repeat(map.get(REPEAT), at(path, REPEAT), templates, using);
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        map.forEach(
                (key, property) -> {
                    if (!STRUCTURE.contains(key)) {
                        properties.put(key, property);
                    }
                });
        return new NodeSpec(
                type,
                id,
                java.util.Collections.unmodifiableMap(properties),
                List.copyOf(children),
                repeat,
                path);
    }

    /**
     * Expand a template.
     *
     * @param map The node that uses it: {@code use} and {@code with}.
     * @param path Where it is, for messages.
     * @param templates The templates that can be used.
     * @param using The templates being expanded, to catch cycles.
     * @return The expanded node.
     */
    private static NodeSpec use(
            Map<String, Object> map,
            String path,
            Map<String, Template> templates,
            Deque<String> using) {
        for (String key : map.keySet()) {
            if (!USE.equals(key) && !WITH.equals(key)) {
                throw new SpecException(
                        path
                                + "."
                                + key
                                + ": a node that uses a template only has "
                                + USE
                                + " and "
                                + WITH);
            }
        }
        String name = SpecValues.name(map.get(USE), at(path, USE));
        Map<String, Object> with =
                map.containsKey(WITH) ? SpecValues.map(map.get(WITH), at(path, WITH)) : Map.of();
        return expand(name, with, path, templates, using);
    }

    /**
     * Expand a template with parameters.
     *
     * @param name The template name.
     * @param with The parameter values.
     * @param path Where it is used, for messages.
     * @param templates The templates that can be used.
     * @param using The templates being expanded, to catch cycles.
     * @return The expanded node.
     */
    private static NodeSpec expand(
            String name,
            Map<String, Object> with,
            String path,
            Map<String, Template> templates,
            Deque<String> using) {
        Template template = templates.get(name);
        if (template == null) {
            throw new SpecException(
                    path + ": unknown template '" + name + "', known: " + templates.keySet());
        }
        if (using.contains(name)) {
            throw new SpecException(
                    path + ": template cycle " + String.join(" -> ", using) + " -> " + name);
        }
        for (String param : template.params()) {
            if (!with.containsKey(param)) {
                throw new SpecException(
                        at(path, WITH)
                                + ": missing parameter '"
                                + param
                                + "' for template "
                                + name);
            }
        }
        for (String key : with.keySet()) {
            if (!template.params().contains(key)) {
                throw new SpecException(
                        path + ".with." + key + ": template " + name + " has no parameter " + key);
            }
        }
        using.push(name);
        try {
            return node(
                    substitute(template.node(), with), path + "<" + name + ">", templates, using);
        } finally {
            using.pop();
        }
    }

    /**
     * Read a repeat.
     *
     * @param value The YAML map.
     * @param path Where it is, for messages.
     * @param templates The templates that can be used.
     * @param using The templates being expanded, to catch cycles.
     * @return The repeat.
     */
    private static NodeSpec.RepeatSpec repeat(
            Object value, String path, Map<String, Template> templates, Deque<String> using) {
        Map<String, Object> map = SpecValues.map(value, path);
        Set<String> known = Set.of(LIST, AS, TEMPLATE, WITH, KEY);
        for (String key : map.keySet()) {
            if (!known.contains(key)) {
                throw new SpecException(path + "." + key + ": unknown repeat option");
            }
        }
        for (String required : List.of(LIST, AS, TEMPLATE)) {
            if (!map.containsKey(required)) {
                throw new SpecException(missing(path, required));
            }
        }
        Map<String, Object> with =
                map.containsKey(WITH) ? SpecValues.map(map.get(WITH), at(path, WITH)) : Map.of();
        NodeSpec item =
                expand(
                        SpecValues.name(map.get(TEMPLATE), at(path, TEMPLATE)),
                        with,
                        path,
                        templates,
                        using);
        return new NodeSpec.RepeatSpec(
                SpecValues.name(map.get(LIST), at(path, LIST)),
                SpecValues.name(map.get(AS), at(path, AS)),
                item,
                map.containsKey(KEY) ? String.valueOf(map.get(KEY)) : null,
                path);
    }

    /**
     * Replace template parameters. A value that is exactly one parameter keeps the parameter's
     * value as is, so templates can take numbers and lists.
     *
     * @param value Part of a template, as written.
     * @param params The parameter values.
     * @return A copy with the parameters replaced.
     */
    private static Object substitute(Object value, Map<String, Object> params) {
        if (value instanceof String text) {
            Matcher whole = WHOLE_PARAMETER.matcher(text);
            if (whole.matches()) {
                return params.get(whole.group(1));
            }
            Matcher matcher = PARAMETER.matcher(text);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(
                        out,
                        Matcher.quoteReplacement(String.valueOf(params.get(matcher.group(1)))));
            }
            matcher.appendTail(out);
            return out.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> copy.put(key, substitute(child, params)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(child -> copy.add(substitute(child, params)));
            return copy;
        }
        return value;
    }

    /** Static loading only. */
    private SpecLoader() {}
}
