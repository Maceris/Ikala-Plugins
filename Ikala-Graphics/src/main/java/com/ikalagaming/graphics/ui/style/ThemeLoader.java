package com.ikalagaming.graphics.ui.style;

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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads themes from YAML. Problems are reported with where they are in the file, like {@code
 * classes.danger.background: unknown token $color.eror}.
 *
 * <pre>
 * name: my-theme
 * tokens:
 *   color.text: "#E6E6E6"       # #RRGGBB or #RRGGBBAA
 *   spacing.medium: 8           # UI units; also "8u", "50%", "1.5em"
 * variants:
 *   compact: { spacing.medium: 6 }
 * types:
 *   button:
 *     background: $color.accent # $name refers to a token
 *     padding: [3, 4]           # 1, 2 or 4 values, in CSS order
 *     hovered: { background: "#4296FAFF" }
 * classes:
 *   danger: { background: $color.error }
 * </pre>
 */
public final class ThemeLoader {
    /** The keys allowed at the top of a theme file. */
    private static final Set<String> TOP_LEVEL =
            Set.of("name", "tokens", "variants", "types", "classes");

    /** Path of the default theme in graphics' resources. */
    public static final String DEFAULT_THEME = "/ui/themes/default.yml";

    /**
     * Load the theme bundled with graphics.
     *
     * @return The default theme.
     * @throws ThemeException If it is missing or broken, which is a bug.
     */
    public static Theme loadDefault() {
        try (InputStream stream = ThemeLoader.class.getResourceAsStream(DEFAULT_THEME)) {
            if (stream == null) {
                throw new ThemeException("The default theme " + DEFAULT_THEME + " is missing");
            }
            return load(stream, DEFAULT_THEME, Theme.EMPTY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Load a theme from a file.
     *
     * @param file The file.
     * @param base The theme it will be layered on, whose tokens it may use. {@link Theme#EMPTY} for
     *     a complete theme.
     * @return The theme, not yet layered on the base.
     * @throws ThemeException If the theme is broken.
     * @throws UncheckedIOException If the file can't be read.
     */
    public static Theme load(@NonNull Path file, @NonNull Theme base) {
        try (InputStream stream = Files.newInputStream(file)) {
            return load(stream, file.toString(), base);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the theme " + file, e);
        }
    }

    /**
     * Load a theme.
     *
     * @param stream The YAML.
     * @param source Where it came from, for messages.
     * @param base The theme it will be layered on, whose tokens it may use. {@link Theme#EMPTY} for
     *     a complete theme.
     * @return The theme, not yet layered on the base.
     * @throws ThemeException If the theme is broken.
     */
    public static Theme load(
            @NonNull InputStream stream, @NonNull String source, @NonNull Theme base) {
        Object document;
        try {
            document = new Yaml(new SafeConstructor(new LoaderOptions())).load(stream);
        } catch (YAMLException e) {
            throw new ThemeException(source + ": not valid YAML: " + e.getMessage(), e);
        }
        if (document == null) {
            throw new ThemeException(source + ": the theme is empty");
        }
        Map<String, Object> root = map(document, source);
        for (String key : root.keySet()) {
            if (!TOP_LEVEL.contains(key)) {
                throw new ThemeException(source + ": unknown key '" + key + "'");
            }
        }
        Object name = root.getOrDefault("name", source);
        Theme.Builder builder = Theme.builder(String.valueOf(name));

        Map<String, Object> tokens = new HashMap<>(base.tokens());
        base.variants().values().forEach(tokens::putAll);
        if (root.containsKey("tokens")) {
            map(root.get("tokens"), "tokens")
                    .forEach(
                            (tokenName, value) -> {
                                builder.token(tokenName, value);
                                tokens.put(tokenName, value);
                            });
        }
        if (root.containsKey("variants")) {
            map(root.get("variants"), "variants")
                    .forEach(
                            (variant, overrides) ->
                                    map(overrides, "variants." + variant)
                                            .forEach(
                                                    (tokenName, value) -> {
                                                        builder.variant(variant, tokenName, value);
                                                        tokens.putIfAbsent(tokenName, value);
                                                    }));
        }
        checkTokens(tokens);
        if (root.containsKey("types")) {
            map(root.get("types"), "types")
                    .forEach(
                            (type, style) ->
                                    builder.type(
                                            type, style(style, "types." + type, tokens, true)));
        }
        if (root.containsKey("classes")) {
            map(root.get("classes"), "classes")
                    .forEach(
                            (className, style) ->
                                    builder.styleClass(
                                            className,
                                            style(style, "classes." + className, tokens, true)));
        }
        return builder.build();
    }

    /**
     * Parse a style.
     *
     * @param value The YAML map.
     * @param path Where it is, for messages.
     * @param tokens Every token it may refer to.
     * @param allowStates Whether state overrides are allowed, which they aren't inside a state.
     * @return The style.
     */
    private static Style style(
            Object value, String path, Map<String, Object> tokens, boolean allowStates) {
        Style.Builder builder = Style.builder();
        for (Map.Entry<String, Object> entry : map(value, path).entrySet()) {
            String where = path + "." + entry.getKey();
            StyleKey key = StyleKey.byName(entry.getKey());
            if (key != null) {
                builder.set(key, styleValue(entry.getValue(), key, where, tokens));
                continue;
            }
            StyleState state = StyleState.byName(entry.getKey());
            if (state != null && allowStates) {
                builder.state(state, style(entry.getValue(), where, tokens, false));
                continue;
            }
            throw new ThemeException(where + ": unknown style property '" + entry.getKey() + "'");
        }
        return builder.build();
    }

    /**
     * Parse one style value: a token reference, or a literal checked against the property's type.
     *
     * @param value The YAML value.
     * @param key The property.
     * @param where Where it is, for messages.
     * @param tokens Every token it may refer to.
     * @return A {@link Token} or a converted literal.
     */
    private static Object styleValue(
            Object value, StyleKey key, String where, Map<String, Object> tokens) {
        if (value == null) {
            throw new ThemeException(where + ": missing value");
        }
        Token token = StyleValues.tokenOf(value);
        if (token != null) {
            if (!tokens.containsKey(token.name())) {
                throw new ThemeException(where + ": unknown token " + token);
            }
            return token;
        }
        try {
            return StyleValues.convert(value, key.getType());
        } catch (IllegalArgumentException e) {
            throw new ThemeException(where + ": " + e.getMessage(), e);
        }
    }

    /**
     * Check that tokens only refer to tokens that exist, without cycles.
     *
     * @param tokens Every token.
     */
    private static void checkTokens(Map<String, Object> tokens) {
        for (String start : tokens.keySet()) {
            Set<String> seen = new LinkedHashSet<>();
            seen.add(start);
            Object value = tokens.get(start);
            for (Token token = StyleValues.tokenOf(value);
                    token != null;
                    token = StyleValues.tokenOf(value)) {
                if (!tokens.containsKey(token.name())) {
                    throw new ThemeException("tokens." + start + ": unknown token " + token);
                }
                if (!seen.add(token.name())) {
                    throw new ThemeException(
                            "tokens."
                                    + start
                                    + ": token cycle "
                                    + String.join(" -> ", seen)
                                    + " -> "
                                    + token.name());
                }
                value = tokens.get(token.name());
            }
        }
    }

    /**
     * Read a YAML map with string keys.
     *
     * @param value The YAML value.
     * @param path Where it is, for messages.
     * @return The map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ThemeException(path + ": expected a map of names to values");
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new ThemeException(path + ": the key " + key + " is not a name");
            }
        }
        return (Map<String, Object>) map;
    }

    /** Static loading only. */
    private ThemeLoader() {}
}
