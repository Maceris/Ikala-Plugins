package com.ikalagaming.graphics.ui.style;

import lombok.Getter;
import lombok.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * How retained UI looks: named tokens, default styles for each node type, classes nodes can opt
 * into, and variants that change tokens for some screens. Immutable; build one with {@link
 * #builder(String)} or load one with {@link ThemeLoader}.
 *
 * <p>Token values are kept as written, a number, a string or a list, or as typed values when built
 * in Java, and converted where they are used, so one token can be a gap, a padding and a font size.
 */
public final class Theme {
    /** A theme that sets nothing, so IkGui's own style shows. */
    public static final Theme EMPTY = builder("empty").build();

    /**
     * The theme's name.
     *
     * @return The name.
     */
    @Getter private final String name;

    /** Token values by name, as written. */
    private final Map<String, Object> tokens;

    /** Default styles by node type name. */
    private final Map<String, Style> types;

    /** Classes by name. */
    private final Map<String, Style> classes;

    /** Token overrides by variant name. */
    private final Map<String, Map<String, Object>> variants;

    /**
     * Create a theme.
     *
     * @param builder The builder holding its contents, which are copied.
     */
    private Theme(Builder builder) {
        name = builder.name;
        tokens = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tokens));
        types = Collections.unmodifiableMap(new LinkedHashMap<>(builder.types));
        classes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.classes));
        Map<String, Map<String, Object>> variantCopy = new LinkedHashMap<>();
        builder.variants.forEach(
                (variant, overrides) ->
                        variantCopy.put(
                                variant,
                                Collections.unmodifiableMap(new LinkedHashMap<>(overrides))));
        variants = Collections.unmodifiableMap(variantCopy);
    }

    /**
     * Start building a theme.
     *
     * @param name The theme's name.
     * @return A new builder.
     */
    public static Builder builder(@NonNull String name) {
        return new Builder(name);
    }

    /**
     * The tokens, as written.
     *
     * @return Token values by name.
     */
    public Map<String, Object> tokens() {
        return tokens;
    }

    /**
     * The default styles for node types.
     *
     * @return Styles by node type name.
     */
    public Map<String, Style> types() {
        return types;
    }

    /**
     * The classes.
     *
     * @return Styles by class name.
     */
    public Map<String, Style> classes() {
        return classes;
    }

    /**
     * The variants.
     *
     * @return Token overrides by variant name.
     */
    public Map<String, Map<String, Object>> variants() {
        return variants;
    }

    /**
     * Layer another theme on top of this one, for a plugin's own tokens and classes. Its tokens,
     * variants and classes win, and its type styles are layered on top of this one's.
     *
     * @param extension The theme to put on top.
     * @return The combined theme, keeping this theme's name.
     */
    public Theme with(@NonNull Theme extension) {
        Builder builder = new Builder(name);
        builder.tokens.putAll(tokens);
        builder.tokens.putAll(extension.tokens);
        builder.types.putAll(types);
        extension.types.forEach((type, style) -> builder.types.merge(type, style, Style::with));
        builder.classes.putAll(classes);
        extension.classes.forEach(
                (className, style) -> builder.classes.merge(className, style, Style::with));
        variants.forEach(
                (variant, overrides) -> builder.variants.put(variant, new HashMap<>(overrides)));
        extension.variants.forEach(
                (variant, overrides) ->
                        builder.variants
                                .computeIfAbsent(variant, v -> new HashMap<>())
                                .putAll(overrides));
        return builder.build();
    }

    /**
     * Turn on some variants and get a theme ready to style nodes with.
     *
     * @param activeVariants The variants that apply, such as {@code portrait}.
     * @return The active theme.
     */
    public ActiveTheme activate(@NonNull Set<String> activeVariants) {
        return new ActiveTheme(this, activeVariants);
    }

    @Override
    public String toString() {
        return "Theme[" + name + "]";
    }

    /** Builds a {@link Theme}. */
    public static final class Builder {
        /** The theme's name. */
        private final String name;

        /** Token values so far. */
        private final Map<String, Object> tokens = new LinkedHashMap<>();

        /** Type styles so far. */
        private final Map<String, Style> types = new LinkedHashMap<>();

        /** Classes so far. */
        private final Map<String, Style> classes = new LinkedHashMap<>();

        /** Variant token overrides so far. */
        private final Map<String, Map<String, Object>> variants = new LinkedHashMap<>();

        /**
         * Start a theme.
         *
         * @param name The theme's name.
         */
        private Builder(String name) {
            this.name = name;
        }

        /**
         * Set a token.
         *
         * @param tokenName The token name.
         * @param value A typed value (packed color, Length, Insets, Float), a number or string as a
         *     theme file would have it, or a {@link Token} to refer to another token.
         * @return This builder.
         */
        public Builder token(@NonNull String tokenName, @NonNull Object value) {
            tokens.put(tokenName, value);
            return this;
        }

        /**
         * Set the default style for a node type.
         *
         * @param type The node type name, such as {@code button}.
         * @param style The style.
         * @return This builder.
         */
        public Builder type(@NonNull String type, @NonNull Style style) {
            types.put(type, style);
            return this;
        }

        /**
         * Define a class.
         *
         * @param className The class name.
         * @param style The style.
         * @return This builder.
         */
        public Builder styleClass(@NonNull String className, @NonNull Style style) {
            classes.put(className, style);
            return this;
        }

        /**
         * Override a token when a variant is active.
         *
         * @param variant The variant, such as {@code compact}.
         * @param tokenName The token name.
         * @param value The value to use instead.
         * @return This builder.
         */
        public Builder variant(
                @NonNull String variant, @NonNull String tokenName, @NonNull Object value) {
            variants.computeIfAbsent(variant, v -> new LinkedHashMap<>()).put(tokenName, value);
            return this;
        }

        /**
         * Finish the theme.
         *
         * @return The theme.
         */
        public Theme build() {
            return new Theme(this);
        }
    }
}
