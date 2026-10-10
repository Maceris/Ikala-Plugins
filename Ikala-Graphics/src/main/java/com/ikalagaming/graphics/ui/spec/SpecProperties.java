package com.ikalagaming.graphics.ui.spec;

import com.ikalagaming.graphics.ui.Length;
import com.ikalagaming.graphics.ui.Node;
import com.ikalagaming.graphics.ui.style.StyleKey;
import com.ikalagaming.graphics.ui.style.StyleParser;
import com.ikalagaming.graphics.ui.style.Token;

import lombok.Getter;
import lombok.NonNull;

import java.util.HashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A spec node's properties, for a {@link NodeFactory} to read. Every getter marks its property as
 * used; properties nobody reads are reported as unknown when the spec is opened. Problems are
 * reported with where they are in the spec.
 */
public final class SpecProperties {
    /** The node being built. */
    private final NodeSpec spec;

    /** Where bindings are looked up and subscriptions kept. */
    private final SpecInstance.Scope scope;

    /** Properties that have been read. */
    private final Set<String> used = new HashSet<>();

    /** Theme tokens given for spacing, which become part of the node's inline style. */
    @Getter
    private final java.util.Map<StyleKey, Token> tokens = new java.util.EnumMap<>(StyleKey.class);

    /**
     * Wrap a node's properties.
     *
     * @param spec The node.
     * @param scope Where bindings are looked up.
     */
    SpecProperties(@NonNull NodeSpec spec, SpecInstance.@NonNull Scope scope) {
        this.spec = spec;
        this.scope = scope;
    }

    /**
     * Where the node is in the spec, for messages.
     *
     * @return The path.
     */
    public String path() {
        return spec.path();
    }

    /**
     * Whether a property is set.
     *
     * @param key The property name.
     * @return True if the spec gives it.
     */
    public boolean has(@NonNull String key) {
        return spec.properties().containsKey(key);
    }

    /**
     * A property as written, marking it used.
     *
     * @param key The property name.
     * @return The value, or null if it isn't set.
     */
    public Object raw(@NonNull String key) {
        used.add(key);
        return spec.properties().get(key);
    }

    /**
     * Where a property is, for messages.
     *
     * @param key The property name.
     * @return The path.
     */
    private String where(String key) {
        return spec.path() + "." + key;
    }

    /**
     * A plain string property, without bindings or localization.
     *
     * @param key The property name.
     * @param fallback The value if it isn't set.
     * @return The value.
     */
    public String string(@NonNull String key, String fallback) {
        Object value = raw(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /**
     * A number property.
     *
     * @param key The property name.
     * @param fallback The value if it isn't set.
     * @return The value.
     */
    public float number(@NonNull String key, float fallback) {
        Object value = raw(key);
        return value == null ? fallback : SpecValues.number(value, where(key));
    }

    /**
     * A true or false property.
     *
     * @param key The property name.
     * @param fallback The value if it isn't set.
     * @return The value.
     */
    public boolean bool(@NonNull String key, boolean fallback) {
        Object value = raw(key);
        return value == null ? fallback : SpecValues.bool(value, where(key));
    }

    /**
     * An enum property, written in lower case with dashes.
     *
     * @param key The property name.
     * @param type The enum.
     * @param fallback The value if it isn't set.
     * @return The value.
     * @param <E> The enum type.
     */
    public <E extends Enum<E>> E choice(@NonNull String key, @NonNull Class<E> type, E fallback) {
        Object value = raw(key);
        return value == null ? fallback : SpecValues.choice(value, type, where(key));
    }

    /**
     * A length property. A {@code $token} value is kept for the node's inline style under the given
     * style property, and null is returned, so the theme decides the length.
     *
     * @param key The property name.
     * @param styleKey The style property a token stands for, or null if tokens aren't allowed.
     * @return The length, or null if it isn't set or is a token.
     */
    public Length length(@NonNull String key, StyleKey styleKey) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        if (styleKey != null && StyleParser.isToken(value)) {
            tokens.put(styleKey, new Token(String.valueOf(value).substring(1)));
            return null;
        }
        return SpecValues.length(value, where(key));
    }

    /**
     * Bind a text property: localized if it starts with {@code @}, with {@code {name}} bindings
     * filled in, and updated whenever a bound observable changes.
     *
     * @param key The property name.
     * @param fallback The text if it isn't set.
     * @param apply Sets the text on the node; called now and on the render thread after changes.
     */
    public void text(
            @NonNull String key, @NonNull String fallback, @NonNull Consumer<String> apply) {
        Object value = raw(key);
        String text = value == null ? fallback : localize(String.valueOf(value), where(key));
        TextTemplate template = TextTemplate.parse(text, where(key));
        scope.bind(template, where(key), () -> apply.accept(scope.render(template)));
    }

    /**
     * Bind a true or false property: a literal, or a single {@code {name}} binding whose value is
     * true when it is {@code Boolean.TRUE} or the text {@code true}.
     *
     * @param key The property name.
     * @param fallback The value if it isn't set.
     * @param apply Sets the value on the node; called now and on the render thread after changes.
     */
    public void flag(@NonNull String key, boolean fallback, @NonNull Consumer<Boolean> apply) {
        Object value = raw(key);
        if (value == null || value instanceof Boolean) {
            apply.accept(value == null ? fallback : (Boolean) value);
            return;
        }
        TextTemplate template = TextTemplate.parse(String.valueOf(value), where(key));
        if (!template.isSingleBinding()) {
            throw new SpecException(where(key) + ": expected true, false or a {binding}");
        }
        String name = template.parts().getFirst().text();
        scope.bind(
                template,
                where(key),
                () -> {
                    Object bound = scope.lookup(name);
                    apply.accept(
                            Boolean.TRUE.equals(bound) || "true".equals(String.valueOf(bound)));
                });
    }

    /**
     * An event handler property, naming a handler in the spec's bindings.
     *
     * @param key The property name, like {@code onClick}.
     * @param node The node the event happens to.
     * @return Runs the handler, or null if the property isn't set.
     * @throws SpecException If the named handler wasn't supplied.
     */
    public Runnable handler(@NonNull String key, @NonNull Node<?> node) {
        Consumer<String> handler = valueHandler(key, node);
        return handler == null ? null : () -> handler.accept(null);
    }

    /**
     * An event handler property for events that carry a value, like a submitted text field.
     *
     * @param key The property name, like {@code onSubmit}.
     * @param node The node the event happens to.
     * @return Runs the handler with the value, or null if the property isn't set.
     * @throws SpecException If the named handler wasn't supplied.
     */
    public Consumer<String> valueHandler(@NonNull String key, @NonNull Node<?> node) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        String name = SpecValues.name(value, where(key));
        return scope.handler(name, where(key), node);
    }

    /**
     * An event handler property for events that can happen to different nodes, like a child of a
     * canvas being dragged.
     *
     * @param key The property name, like {@code onMove}.
     * @return Runs the handler with the node the event happened to and the event's value, or null
     *     if the property isn't set.
     * @throws SpecException If the named handler wasn't supplied.
     */
    public BiConsumer<Node<?>, String> nodeHandler(@NonNull String key) {
        Object value = raw(key);
        if (value == null) {
            return null;
        }
        String name = SpecValues.name(value, where(key));
        return scope.nodeHandler(name, where(key));
    }

    /**
     * Bind a piece of text that isn't a property of this node, like an edge's classes, with the
     * same {@code {name}} bindings as {@link #text}.
     *
     * @param value The text as written.
     * @param where Where it is, for messages.
     * @param apply Receives the filled in text; called now and on the render thread after changes.
     */
    public void bindText(
            @NonNull String value, @NonNull String where, @NonNull Consumer<String> apply) {
        TextTemplate template = TextTemplate.parse(value, where);
        scope.bind(template, where, () -> apply.accept(scope.render(template)));
    }

    /**
     * Bind a property to any value, not only text: a literal, or a single {@code {name}} binding
     * whose value is passed as it is, like a texture.
     *
     * @param key The property name.
     * @param apply Receives the value; called now and on the render thread after changes. Not
     *     called if the property isn't set.
     */
    public void value(@NonNull String key, @NonNull Consumer<Object> apply) {
        Object value = raw(key);
        if (value == null) {
            return;
        }
        if (!(value instanceof String text)) {
            apply.accept(value);
            return;
        }
        TextTemplate template = TextTemplate.parse(text, where(key));
        if (!template.isSingleBinding()) {
            apply.accept(text);
            return;
        }
        String name = template.parts().getFirst().text();
        scope.bind(template, where(key), () -> apply.accept(scope.lookup(name)));
    }

    /**
     * The list item this node was repeated for, for factories that want it.
     *
     * @return The item, or null outside repeats.
     */
    public Object item() {
        return scope.item();
    }

    /**
     * Localize text that starts with {@code @}. {@code @@} is a literal {@code @}.
     *
     * @param text The text.
     * @param where Where it is, for messages.
     * @return The text to show.
     */
    private String localize(String text, String where) {
        if (text.startsWith("@@")) {
            return text.substring(1);
        }
        if (!text.startsWith("@")) {
            return text;
        }
        ResourceBundle bundle = scope.bundle();
        if (bundle == null) {
            throw new SpecException(
                    where + ": " + text + " needs a resource bundle in the bindings");
        }
        try {
            return bundle.getString(text.substring(1));
        } catch (MissingResourceException e) {
            throw new SpecException(where + ": no text for " + text + " in the resource bundle", e);
        }
    }

    /**
     * Report properties nobody read.
     *
     * @throws SpecException If there are any.
     */
    void checkAllUsed() {
        for (Map.Entry<String, Object> entry : spec.properties().entrySet()) {
            if (!used.contains(entry.getKey())) {
                throw new SpecException(
                        where(entry.getKey())
                                + ": unknown property for a "
                                + spec.type()
                                + " node");
            }
        }
    }
}
