package com.ikalagaming.graphics.ui.style;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A theme with its variants chosen, ready to compute node styles. Computed styles are cached, since
 * many nodes share the same type and classes. Not thread safe: used on the render thread.
 *
 * <p>Values that can't be used, like a reference to a missing token, are logged once and left
 * unset, so a broken theme degrades to IkGui's own style instead of failing.
 */
@Slf4j
public final class ActiveTheme {
    /** No theme at all. */
    public static final ActiveTheme EMPTY = Theme.EMPTY.activate(Set.of());

    /**
     * Identifies a computed style in the cache.
     *
     * @param type The node type.
     * @param classes The node's classes, in order.
     * @param inline The node's inline style.
     */
    private record Key(String type, List<String> classes, Style inline) {}

    /**
     * The theme.
     *
     * @return The theme.
     */
    @Getter private final Theme theme;

    /**
     * The variants that are on.
     *
     * @return The active variants.
     */
    @Getter private final Set<String> variants;

    /** Token values as written, with the active variants' overrides applied. */
    private final Map<String, Object> tokens;

    /** Computed styles by what they were computed from. */
    private final Map<Key, ComputedStyle> cache = new HashMap<>();

    /** Problems already logged, so each is only logged once. */
    private final Set<String> reported = new HashSet<>();

    /**
     * Activate a theme.
     *
     * @param theme The theme.
     * @param variants The variants that apply.
     */
    ActiveTheme(@NonNull Theme theme, @NonNull Set<String> variants) {
        this.theme = theme;
        this.variants = Set.copyOf(variants);
        tokens = new HashMap<>(theme.tokens());
        // Variants are applied in the theme's order, so the result doesn't depend on set order
        for (Map.Entry<String, Map<String, Object>> variant : theme.variants().entrySet()) {
            if (this.variants.contains(variant.getKey())) {
                tokens.putAll(variant.getValue());
            }
        }
    }

    /**
     * Compute the style for a node.
     *
     * @param type The node's type name, such as {@code button}.
     * @param classes The node's classes, in order; later ones win.
     * @param inline The node's own style, which wins over its classes.
     * @return The computed style.
     */
    public ComputedStyle compute(
            @NonNull String type, @NonNull List<String> classes, @NonNull Style inline) {
        return cache.computeIfAbsent(
                new Key(type, List.copyOf(classes), inline), this::computeUncached);
    }

    /**
     * Compute a style, without the cache.
     *
     * @param key What to compute it from.
     * @return The computed style.
     */
    private ComputedStyle computeUncached(Key key) {
        Style merged = theme.types().getOrDefault(key.type(), Style.EMPTY);
        for (String className : key.classes()) {
            Style style = theme.classes().get(className);
            if (style == null) {
                report("Unknown style class '" + className + "' on a " + key.type());
            } else {
                merged = merged.with(style);
            }
        }
        merged = merged.with(key.inline());
        if (merged.isEmpty()) {
            return ComputedStyle.EMPTY;
        }

        Map<StyleKey, Object> base = resolveAll(merged.values());
        Map<StyleState, Map<StyleKey, Object>> states = new EnumMap<>(StyleState.class);
        merged.states().forEach((state, style) -> states.put(state, resolveAll(style.values())));
        return new ComputedStyle(base, states);
    }

    /**
     * Resolve tokens and convert every value of a style.
     *
     * @param values The values, as written.
     * @return The usable values, leaving out any that failed.
     */
    private Map<StyleKey, Object> resolveAll(Map<StyleKey, Object> values) {
        Map<StyleKey, Object> resolved = new EnumMap<>(StyleKey.class);
        values.forEach(
                (key, value) -> {
                    Object converted = resolve(value, key.getType(), key.getYamlName());
                    if (converted != null) {
                        resolved.put(key, converted);
                    }
                });
        return resolved;
    }

    /**
     * Resolve a value, following token references, and convert it.
     *
     * @param value The value, as written.
     * @param type The type it is needed as.
     * @param where What it is for, for reporting problems.
     * @return The value, or null if it can't be used.
     */
    private Object resolve(Object value, StyleKey.ValueType type, String where) {
        Set<String> seen = new LinkedHashSet<>();
        Object current = value;
        for (Token token = StyleValues.tokenOf(current);
                token != null;
                token = StyleValues.tokenOf(current)) {
            if (!seen.add(token.name())) {
                report("Token cycle " + String.join(" -> ", seen) + " -> " + token.name());
                return null;
            }
            if (!tokens.containsKey(token.name())) {
                report("Unknown token $" + token.name() + " used for " + where);
                return null;
            }
            current = tokens.get(token.name());
        }
        try {
            return StyleValues.convert(current, type);
        } catch (IllegalArgumentException e) {
            report(where + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Look up a token as a particular type, for drawing code that reads tokens directly.
     *
     * @param name The token name.
     * @param type The type wanted.
     * @return The value, or null if the token is missing or the wrong type.
     */
    public Object token(@NonNull String name, StyleKey.@NonNull ValueType type) {
        if (!tokens.containsKey(name)) {
            return null;
        }
        return resolve(new Token(name), type, "$" + name);
    }

    /**
     * Look up a color token.
     *
     * @param name The token name.
     * @param fallback The color to use if the token is missing.
     * @return The packed color.
     */
    public int color(@NonNull String name, int fallback) {
        Object value = token(name, StyleKey.ValueType.COLOR);
        return value instanceof Integer color ? color : fallback;
    }

    /**
     * Log a problem with the theme, once.
     *
     * @param problem The problem.
     */
    private void report(String problem) {
        if (reported.add(problem)) {
            log.warn("Theme {}: {}", theme.getName(), problem);
        }
    }

    @Override
    public String toString() {
        return "ActiveTheme[" + theme.getName() + ", " + variants + "]";
    }
}
